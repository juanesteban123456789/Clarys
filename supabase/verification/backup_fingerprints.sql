-- Ejecutar dentro de una transaccion de lectura; solo salen conteos y SHA-256.
set timezone = 'UTC';
select format(
    'select jsonb_build_object(''schema'',%L,''table'',%L,''count'',count(*),''sha256'',
     encode(sha256(convert_to(coalesce(string_agg(h,'''' order by h),''''),''UTF8'')),''hex''))
     from (select encode(sha256(convert_to(to_jsonb(t)::text,''UTF8'')),''hex'') h from %I.%I t) hashes;',
    n.nspname,c.relname,n.nspname,c.relname)
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where c.relkind in ('r','p') and c.relpersistence='p'
and n.nspname !~ '^pg_' and n.nspname<>'information_schema'
order by n.nspname,c.relname
\gexec
