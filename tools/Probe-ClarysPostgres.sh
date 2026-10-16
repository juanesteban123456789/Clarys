#!/bin/sh
set -eu
cp /clarys-private/pgpass /tmp/clarys-probe.pgpass
chmod 600 /tmp/clarys-probe.pgpass
export PGPASSFILE=/tmp/clarys-probe.pgpass
trap 'rm -f /tmp/clarys-probe.pgpass' EXIT HUP INT TERM
psql --no-password --quiet --no-align --tuples-only --set=ON_ERROR_STOP=1 <<'SQL'
begin read only;
select jsonb_build_object(
    'server_version_num', current_setting('server_version_num'),
    'extensions', (select jsonb_agg(extname order by extname) from pg_extension),
    'products', (select count(*) from public.products),
    'auth_users', (select count(*) from auth.users),
    'storage_objects', (select count(*) from storage.objects),
    'database', current_database()
);
rollback;
SQL
