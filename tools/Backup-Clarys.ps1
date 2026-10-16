#requires -Version 5.1
param([string] $CertificatePath = 'C:\Users\user\Downloads\prod-ca-2021.crt')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=Split-Path -Parent $PSScriptRoot
$secrets=Join-Path $env:USERPROFILE '.clarys-backup'
$config=Get-Content -LiteralPath (Join-Path $secrets 'clarys-backup.config.json') -Raw | ConvertFrom-Json
if($config.projectRef -ne 'mzqvmddxxlzjfruksgnz' -or $config.projectUrl -ne 'https://mzqvmddxxlzjfruksgnz.supabase.co') {
    throw 'Proyecto diferente de Clarys.'
}
if([int]$config.postgresPort -ne 5432 -or ($config.postgresHost -ne 'db.mzqvmddxxlzjfruksgnz.supabase.co' -and
    ($config.postgresHost -notmatch '^[a-z0-9.-]+\.pooler\.supabase\.com$' -or -not $config.postgresUser.EndsWith('.mzqvmddxxlzjfruksgnz')))) {
    throw 'Conexion PostgreSQL ajena a Clarys.'
}
$image='public.ecr.aws/supabase/postgres:17.6.1.165'
$base=Join-Path $secrets 'respaldos'
$runId=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$run=Join-Path $base $runId
$working=Join-Path $run 'working-private'
[void][IO.Directory]::CreateDirectory($base)
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
$acl=Get-Acl -LiteralPath $secrets
if(-not $acl.AreAccessRulesProtected) {throw 'La carpeta de configuracion debe tener ACL restringida.'}
foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])) {
    if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin @($sid.Value,'S-1-5-18')) {
        throw 'La carpeta privada permite acceso a otro principal; revisar su ACL antes del respaldo.'
    }
}
[void][IO.Directory]::CreateDirectory($working)

function Secret([string]$value) {
    $secure=ConvertTo-SecureString $value
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secure.Dispose()}
}
function Save-Json($value,[string]$path) {
    [IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 35),[Text.UTF8Encoding]::new($false))
}
function Docker($arguments) {
    $previous=$ErrorActionPreference
    try {$ErrorActionPreference='Continue';$output=& docker.exe @arguments 2>&1;$code=$LASTEXITCODE}
    finally {$ErrorActionPreference=$previous}
    if($code -ne 0) {
        [IO.File]::WriteAllLines((Join-Path $working 'docker-error-private.log'),[string[]]$output)
        throw 'Docker no completo el respaldo. Registro guardado en carpeta privada; no se imprimen datos sensibles.'
    }
    return $output
}
function Get-Storage([string]$path,[string]$destination='') {
    $request=[Net.HttpWebRequest]::Create($config.projectUrl+'/storage/v1/'+$path)
    $request.Headers.Add('apikey',$storageKey)
    if($storageKey -notmatch '^sb_secret_') {$request.Headers.Add('Authorization','Bearer '+$storageKey)}
    $request.Timeout=60000
    try {$response=$request.GetResponse()} catch {throw 'Storage no pudo completar una lectura del respaldo.'}
    try {
        if($destination) {
            $stream=$response.GetResponseStream();$file=[IO.File]::Create($destination)
            try {$stream.CopyTo($file)} finally {$file.Dispose();$stream.Dispose()}
            return $response.Headers['ETag']
        }
        $reader=[IO.StreamReader]::new($response.GetResponseStream())
        try {return ConvertFrom-Json -InputObject $reader.ReadToEnd()} finally {$reader.Dispose()}
    } finally {$response.Close()}
}
function Protect-File([string]$path) {
    $bytes=[IO.File]::ReadAllBytes($path)
    $protected=[Security.Cryptography.ProtectedData]::Protect($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes($path+'.dpapi',$protected)
    $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($path+'.dpapi'),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $hash=[Security.Cryptography.SHA256]::Create()
    try {
        if([Convert]::ToBase64String($hash.ComputeHash($bytes)) -ne [Convert]::ToBase64String($hash.ComputeHash($plain))) {
            throw 'Fallo la verificacion de cifrado.'
        }
    } finally {$hash.Dispose()}
    [Array]::Clear($plain,0,$plain.Length);[Array]::Clear($bytes,0,$bytes.Length)
    return $path+'.dpapi'
}
$started=[DateTime]::UtcNow.ToString('o')
$password=Secret $config.postgresPasswordProtected
$storageKey=Secret $config.storageServiceKeyProtected
try {
    $fields=@($config.postgresHost,[string]$config.postgresPort,$config.postgresDatabase,$config.postgresUser,$password)|
        ForEach-Object {$_.Replace('\','\\').Replace(':','\:')}
    [IO.File]::WriteAllText((Join-Path $working 'pgpass'),(($fields -join ':')+"`n"),[Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath $CertificatePath -Destination (Join-Path $working 'root.crt')
    $captureArgs=@('run','--rm','--entrypoint','sh',
        '--mount',"type=bind,source=$working,target=/backup",
        '--mount',"type=bind,source=$root,target=/workspace,readonly",
        '--env',"PGHOST=$($config.postgresHost)",'--env',"PGUSER=$($config.postgresUser)",
        '--env',"PGPORT=$($config.postgresPort)",'--env',"PGDATABASE=$($config.postgresDatabase)",
        '--env','PGSSLMODE=verify-full','--env','PGSSLROOTCERT=/backup/root.crt','--env','PGCONNECT_TIMEOUT=15',
        $image,'/workspace/tools/Capture-ClarysBackup.sh')
    [void](Docker $captureArgs)
    $inventory=Get-Content -LiteralPath (Join-Path $working 'inventory.json') -Raw|ConvertFrom-Json
    $objects=Get-Content -LiteralPath (Join-Path $working 'storage-objects.json') -Raw|ConvertFrom-Json
    $buckets=Get-Storage 'bucket'
    Save-Json $buckets (Join-Path $working 'buckets.json')
    $files=Join-Path $working 'storage-files'
    [void][IO.Directory]::CreateDirectory($files)
    $mapping=@();$index=0
    foreach($object in $objects) {
        $index++;$name=('{0:D5}.bin' -f $index);$path=Join-Path $files $name
        $encoded=@($object.name.Split('/')|ForEach-Object {[Uri]::EscapeDataString($_)}) -join '/'
        $etag=Get-Storage ('object/authenticated/'+[Uri]::EscapeDataString($object.bucket_id)+'/'+$encoded) $path
        $length=(Get-Item -LiteralPath $path).Length
        if($object.metadata.PSObject.Properties['size'] -and $length -ne [long]$object.metadata.size) {
            throw 'Un archivo Storage cambio de tamano durante la captura.'
        }
        if($object.metadata.PSObject.Properties['eTag'] -and $etag -and
            $etag.Trim('"') -ne ([string]$object.metadata.eTag).Trim('"')) {throw 'Un archivo Storage cambio durante la captura.'}
        $mapping+= [ordered]@{bucket=$object.bucket_id;name=$object.name;file=$name;bytes=$length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
    }
    Save-Json $mapping (Join-Path $files 'mapping.json')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($files,(Join-Path $working 'storage.zip'))
    [void](Protect-File (Join-Path $working 'database.dump'))
    [void](Protect-File (Join-Path $working 'storage.zip'))
    $fingerprints=@(Get-Content -LiteralPath (Join-Path $working 'fingerprints.jsonl')|ForEach-Object {$_|ConvertFrom-Json})
    $metadata=[ordered]@{runId=$runId;projectRef=$config.projectRef;startedUtc=$started;completedUtc=[DateTime]::UtcNow.ToString('o');
        responsible='Juan Esteban Sepulveda Sepulveda';postgres=$inventory.postgres_version;tableFingerprints=$fingerprints;
        buckets=@($buckets|ForEach-Object {$_.id});storageFiles=$index;protection='Windows DPAPI CurrentUser; ACL usuario y SYSTEM';
        sourceWrites=$false;restoration='PENDIENTE';storageRestoration='PENDIENTE';acceptance='PENDIENTE';workingDirectory=$working}
    Save-Json $metadata (Join-Path $run 'evidence.json')
    Write-Output ('PASS: captura consistente; tablas={0}, archivos Storage={1}.' -f $fingerprints.Count,$index)
    Write-Output ('Ruta privada del ejercicio: '+$run)
} finally {
    $password=$null;$storageKey=$null;$fields=$null
    $passPath=Join-Path $working 'pgpass'
    if(Test-Path -LiteralPath $passPath) {Remove-Item -LiteralPath $passPath -Force}
}
