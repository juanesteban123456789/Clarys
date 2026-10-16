#requires -Version 5.1
param([Parameter(Mandatory=$true)][string] $RunPath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Security
$root=Split-Path -Parent $PSScriptRoot
$base=[IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.clarys-backup\respaldos'))
$run=[IO.Path]::GetFullPath($RunPath)
if(-not $run.StartsWith($base+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    (Split-Path $run -Leaf) -notmatch '^\d{8}T\d{6}Z-[a-f0-9]{8}$') {
    throw 'Solo se permiten respaldos de la carpeta privada de Clarys.'
}
$working=Join-Path $run 'working-private'
$evidencePath=Join-Path $run 'evidence.json'
$evidence=Get-Content -LiteralPath $evidencePath -Raw -Encoding UTF8|ConvertFrom-Json
if($evidence.projectRef -ne 'mzqvmddxxlzjfruksgnz') {throw 'Respaldo ajeno a Clarys.'}
$image='public.ecr.aws/supabase/postgres:17.6.1.165'
$name='clarys-restore-'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$started=$false
$finalized=Test-Path -LiteralPath (Join-Path $run 'manifest.json') -PathType Leaf
if($finalized) {$working=Join-Path $run ('verify-private-'+[Guid]::NewGuid().ToString('N'))}

function Docker($arguments) {
    $previous=$ErrorActionPreference
    try {$ErrorActionPreference='Continue';$output=& docker.exe @arguments 2>&1;$code=$LASTEXITCODE}
    finally {$ErrorActionPreference=$previous}
    if($code -ne 0) {
        [IO.File]::WriteAllLines((Join-Path $working 'restore-error-private.log'),[string[]]$output)
        throw 'Fallo una operacion en la copia aislada; consultar su registro privado.'
    }
    return $output
}
function Unprotect-File([string]$source,[string]$target) {
    $bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($source),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    try {[IO.File]::WriteAllBytes($target,$bytes)} finally {[Array]::Clear($bytes,0,$bytes.Length)}
}
try {
    if($finalized) {
        $manifest=Get-Content -LiteralPath (Join-Path $run 'manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
        foreach($fileName in @('database.dump.dpapi','storage.zip.dpapi','metadata.zip.dpapi')) {
            $entry=@($manifest|Where-Object {$_.name -eq $fileName})
            $path=Join-Path $run $fileName
            if($entry.Count -ne 1 -or (Get-Item -LiteralPath $path).Length -ne $entry[0].bytes -or
                (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry[0].sha256) {
                throw 'El manifiesto del respaldo no coincide.'
            }
        }
        [void][IO.Directory]::CreateDirectory($working)
        Unprotect-File (Join-Path $run 'metadata.zip.dpapi') (Join-Path $working 'metadata.zip')
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $working 'metadata.zip'),$working)
        foreach($fileName in @('database.dump.dpapi','storage.zip.dpapi')) {
            Copy-Item -LiteralPath (Join-Path $run $fileName) -Destination (Join-Path $working $fileName)
        }
    }
    Unprotect-File (Join-Path $working 'database.dump.dpapi') (Join-Path $working 'restored.dump')
    $expectedDumpHash=if($finalized) {$evidence.database_dumpSha256} else {
        (Get-FileHash -LiteralPath (Join-Path $working 'database.dump') -Algorithm SHA256).Hash
    }
    if($expectedDumpHash -ne
        (Get-FileHash -LiteralPath (Join-Path $working 'restored.dump') -Algorithm SHA256).Hash) {throw 'El dump descifrado no coincide.'}
    Unprotect-File (Join-Path $working 'storage.zip.dpapi') (Join-Path $working 'restored-storage.zip')
    if($finalized -and (Get-FileHash -LiteralPath (Join-Path $working 'restored-storage.zip') -Algorithm SHA256).Hash -ne
        $evidence.storage_zipSha256) {throw 'El archivo Storage descifrado no coincide.'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $storageDestination=Join-Path $working ('storage-restored-'+[Guid]::NewGuid().ToString('N').Substring(0,6))
    [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $working 'restored-storage.zip'),$storageDestination)
    $mapping=Get-Content -LiteralPath (Join-Path $storageDestination 'mapping.json') -Encoding UTF8 -Raw|ConvertFrom-Json
    foreach($file in $mapping) {
        if($file.file -notmatch '^\d{5}\.bin$') {throw 'Nombre de archivo no seguro en el respaldo.'}
        $path=Join-Path $storageDestination $file.file
        if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256 -or
            (Get-Item -LiteralPath $path).Length -ne $file.bytes) {throw 'Un archivo restaurado no coincide.'}
    }
    if(@($mapping).Count -ne $evidence.storageFiles) {throw 'Cantidad de archivos restaurados distinta.'}
    $roles=Get-Content -LiteralPath (Join-Path $working 'roles.json') -Raw -Encoding UTF8
    $roleSql=@'
do $clarys$
declare role_name text;
begin
    for role_name in select value->>'name' from jsonb_array_elements('__ROLES__'::jsonb) loop
        if not exists(select 1 from pg_roles where rolname=role_name) then
            execute format('create role %I nologin',role_name);
        end if;
    end loop;
end $clarys$;
create database clarys_restored;
-- Solo en la copia sin red: permite reproducir el event trigger de la plataforma.
alter role postgres superuser;
'@
    $roleSql=$roleSql.Replace('__ROLES__',$roles.Replace("'","''"))
    [IO.File]::WriteAllText((Join-Path $working 'restore-roles.sql'),$roleSql,[Text.UTF8Encoding]::new($false))
    [void](Docker @('run','--rm','-d','--name',$name,'--network','none',
        '--mount',"type=bind,source=$working,target=/backup,readonly",'--mount',"type=bind,source=$root,target=/workspace,readonly",
        '--env','POSTGRES_PASSWORD=clarys-isolated-verification-only',$image))
    $started=$true
    $ready=$false
    for($i=0;$i -lt 60;$i++) {
        & docker.exe exec $name pg_isready -h 127.0.0.1 -U supabase_admin 2>$null|Out-Null
        if($LASTEXITCODE -eq 0) {$ready=$true;break}
        Start-Sleep -Seconds 1
    }
    if(-not $ready) {throw 'El PostgreSQL aislado no inicio a tiempo.'}
    [void](Docker @('exec',$name,'psql','-U','supabase_admin','-d','postgres','-v','ON_ERROR_STOP=1','-f','/backup/restore-roles.sql'))
    [void](Docker @('exec',$name,'pg_restore','-U','supabase_admin','--dbname=clarys_restored',
        '--exit-on-error','--single-transaction','/backup/restored.dump'))
    [void](Docker @('exec',$name,'psql','-U','supabase_admin','-d','clarys_restored','-v','ON_ERROR_STOP=1',
        '-c','alter role postgres nosuperuser;'))
    $raw=Docker @('exec',$name,'psql','-U','supabase_admin','-d','clarys_restored','-qAt','-v','ON_ERROR_STOP=1',
        '-f','/workspace/supabase/verification/backup_fingerprints.sql')
    $restored=@($raw|ForEach-Object {$_|ConvertFrom-Json})
    $original=@($evidence.tableFingerprints)
    if($restored.Count -ne $original.Count) {throw 'Cantidad de tablas distinta en la restauracion.'}
    foreach($table in $original) {
        $match=@($restored|Where-Object {$_.schema -eq $table.schema -and $_.table -eq $table.table})
        if($match.Count -ne 1 -or $match[0].count -ne $table.count -or $match[0].sha256 -ne $table.sha256) {
            throw ('No coincide el contenido de '+$table.schema+'.'+$table.table)
        }
    }
    $inventoryOriginal=Get-Content -LiteralPath (Join-Path $working 'inventory.json') -Raw -Encoding UTF8|ConvertFrom-Json
    $inventoryRaw=Docker @('exec',$name,'psql','-U','supabase_admin','-d','clarys_restored','-qAt',
        '-v','ON_ERROR_STOP=1','-c','set search_path=public,extensions',
        '-f','/workspace/supabase/verification/backup_inventory.sql')
    $inventoryCopy=($inventoryRaw -join "`n")|ConvertFrom-Json
    [IO.File]::WriteAllText((Join-Path $working 'inventory-restored.json'),($inventoryCopy|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
    foreach($section in @('tables','policies','constraints','extensions')) {
        $left=@($inventoryOriginal.$section|ForEach-Object {$_|ConvertTo-Json -Depth 20 -Compress}|Sort-Object)
        $right=@($inventoryCopy.$section|ForEach-Object {$_|ConvertTo-Json -Depth 20 -Compress}|Sort-Object)
        if(($left -join "`n") -cne ($right -join "`n")) {throw ('Inventario distinto: '+$section)}
    }
    $left=@($inventoryOriginal.functions|ForEach-Object {$_|ConvertTo-Json -Depth 20 -Compress}|Sort-Object)
    $right=@($inventoryCopy.functions|ForEach-Object {$_|ConvertTo-Json -Depth 20 -Compress}|Sort-Object)
    if(($left -join "`n") -cne ($right -join "`n")) {throw 'Funciones distintas en la copia.'}
    $business=Docker @('exec',$name,'psql','-U','supabase_admin','-d','clarys_restored','-q',
        '-v','ON_ERROR_STOP=1','-f','/workspace/supabase/verification/inventory_orders_tests.sql')
    if(($business -join "`n") -notmatch 'PASS: reservas') {throw 'No se confirmaron las pruebas de negocio restauradas.'}
    $evidence|Add-Member -NotePropertyName inventoryComparison -NotePropertyValue 'PASS: tablas/columnas, funciones, RLS, restricciones y extensiones' -Force
    $evidence|Add-Member -NotePropertyName businessRestorationTest -NotePropertyValue 'PASS: reservas, cancelacion, entrega, idempotencia, variantes e historial; datos sinteticos revertidos' -Force
    $evidence.restoration='PASS: todas las tablas coinciden en conteo y SHA-256'
    $evidence.storageRestoration='PASS: archivos extraidos y SHA-256 identicos; no se subieron al Supabase origen'
    $timeProperty=if($finalized) {'reverifiedAtUtc'} else {'restoredAtUtc'}
    $evidence|Add-Member -NotePropertyName $timeProperty -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
    $evidence|Add-Member -NotePropertyName target -NotePropertyValue 'PostgreSQL 17 aislado, base clarys_restored, sin puertos publicados y sin red' -Force
    [IO.File]::WriteAllText($evidencePath,($evidence|ConvertTo-Json -Depth 35),[Text.UTF8Encoding]::new($false))
    Write-Output ('PASS: restauracion aislada, {0} tablas y {1} archivos verificados.' -f $restored.Count,@($mapping).Count)
    Write-Output 'La copia no incluyo servicios Auth/REST/Storage; esas pruebas de API requieren un entorno adicional.'
} finally {
    try {
        if($started) { [void](Docker @('stop',$name)) }
    } finally {
        if($finalized -and (Test-Path -LiteralPath $working)) {
            $cleanup=[IO.Path]::GetFullPath($working)
            if((Split-Path $cleanup -Parent) -ne $run -or
                (Split-Path $cleanup -Leaf) -notmatch '^verify-private-[a-f0-9]{32}$' -or
                -not $cleanup.StartsWith($base+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
                throw 'No se permite limpiar una ruta ajena a esta verificacion.'
            }
            Remove-Item -LiteralPath $cleanup -Recurse -Force
        }
    }
}
