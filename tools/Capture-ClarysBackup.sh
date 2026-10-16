#!/bin/sh
set -eu
cp /backup/pgpass /tmp/clarys-capture.pgpass
chmod 600 /tmp/clarys-capture.pgpass
export PGPASSFILE=/tmp/clarys-capture.pgpass
mkfifo /tmp/clarys-snapshot-input
psql --no-password -qAt -v ON_ERROR_STOP=1 < /tmp/clarys-snapshot-input > /tmp/clarys-snapshot-output 2>/tmp/clarys-snapshot-error &
keeper=$!
trap 'kill "$keeper" 2>/dev/null || true; rm -f /tmp/clarys-capture.pgpass' EXIT HUP INT TERM
exec 3>/tmp/clarys-snapshot-input
printf 'begin isolation level repeatable read read only;\nselect pg_export_snapshot();\n' >&3
for i in $(seq 1 100); do
    if test -s /tmp/clarys-snapshot-output; then break; fi
    sleep 0.1
done
snapshot=$(head -n 1 /tmp/clarys-snapshot-output)
case "$snapshot" in ''|*[!0-9A-Fa-f-]*) echo 'No se obtuvo snapshot de lectura.' >&2; exit 1;; esac
pg_dump --no-password --format=custom --snapshot="$snapshot" --file=/backup/database.dump
query_snapshot() {
    {
        printf "begin isolation level repeatable read read only;\nset transaction snapshot '%s';\n" "$snapshot"
        cat "$1"
        printf '\nrollback;\n'
    } | psql --no-password -qAt -v ON_ERROR_STOP=1 > "$2"
}
sed '/^begin read only;$/d; /^commit;$/d' /workspace/supabase/verification/backup_inventory.sql > /tmp/clarys-inventory.sql
query_snapshot /tmp/clarys-inventory.sql /backup/inventory.json
query_snapshot /workspace/supabase/verification/backup_fingerprints.sql /backup/fingerprints.jsonl
printf "select coalesce(jsonb_agg(to_jsonb(o) order by o.bucket_id,o.name),'[]'::jsonb) from storage.objects o;\n" >/tmp/clarys-objects.sql
query_snapshot /tmp/clarys-objects.sql /backup/storage-objects.json
printf "select jsonb_agg(jsonb_build_object('name',rolname,'login',rolcanlogin,'superuser',rolsuper,'inherit',rolinherit,'createrole',rolcreaterole,'createdb',rolcreatedb,'bypassrls',rolbypassrls) order by rolname) from pg_roles;\n" >/tmp/clarys-roles.sql
query_snapshot /tmp/clarys-roles.sql /backup/roles.json
pg_restore --list /backup/database.dump > /backup/archive-index.txt
printf 'rollback;\n\\q\n' >&3
exec 3>&-
wait "$keeper"
echo 'PASS: respaldo logico e inventario desde un snapshot consistente, solo lectura.'
