#requires -Version 5.1
param([string] $CertificatePath = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
$secretRoot = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.clarys-backup'))
$configPath = Join-Path $secretRoot 'clarys-backup.config.json'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Configura primero Configure-ClarysBackup.ps1.' }
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
if ($config.projectRef -ne 'mzqvmddxxlzjfruksgnz' -or
    $config.projectUrl -ne 'https://mzqvmddxxlzjfruksgnz.supabase.co') {
    throw 'No se permite consultar un proyecto diferente de Clarys.'
}
if ([int]$config.postgresPort -ne 5432 -or
    ($config.postgresHost -ne 'db.mzqvmddxxlzjfruksgnz.supabase.co' -and
        ($config.postgresHost -notmatch '^[a-z0-9.-]+\.pooler\.supabase\.com$' -or
        -not $config.postgresUser.EndsWith('.mzqvmddxxlzjfruksgnz', [StringComparison]::Ordinal)))) {
    throw 'La conexion PostgreSQL no corresponde a la configuracion admitida de Clarys.'
}

function Read-ProtectedSecret([string] $ProtectedValue) {
    $secure = ConvertTo-SecureString $ProtectedValue
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}

# GET: comprueba la clave en el servidor, sin descargar ni modificar objetos.
$key = Read-ProtectedSecret $config.storageServiceKeyProtected
try {
    $request = [Net.HttpWebRequest]::Create($config.projectUrl + '/storage/v1/bucket')
    $request.Headers.Add('apikey', $key)
    if ($key -notmatch '^sb_secret_') { $request.Headers.Add('Authorization', 'Bearer ' + $key) }
    $request.Timeout = 20000
    try { $response = $request.GetResponse() }
    catch [Net.WebException] {
        if ($_.Exception.Response) { $_.Exception.Response.Close() }
        throw 'Storage no acepto el acceso administrativo o no hay conexion.'
    }
    try {
        $reader = [IO.StreamReader]::new($response.GetResponseStream())
        try { $buckets = ConvertFrom-Json -InputObject $reader.ReadToEnd() }
        finally { $reader.Dispose() }
    } finally { $response.Close() }
    Write-Output ('PASS: Storage administrativo; buckets disponibles: {0}.' -f @($buckets).Count)
} finally { $key = $null }

$null = & docker version --format '{{.Server.Version}}' 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Inicia Docker Desktop y vuelve a ejecutar la verificacion.' }
if ([string]::IsNullOrWhiteSpace($CertificatePath)) {
    foreach ($candidate in @((Join-Path $secretRoot 'supabase-ca.crt'),
            (Join-Path $env:USERPROFILE 'Downloads\prod-ca-2021.crt'))) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $CertificatePath = $candidate; break }
    }
}
if (-not $CertificatePath -or -not (Test-Path -LiteralPath $CertificatePath -PathType Leaf)) {
    throw 'Falta el certificado CA de Supabase. Descargalo desde Database > Settings > SSL Configuration y usa -CertificatePath.'
}
$certificate = Get-Content -LiteralPath $CertificatePath -Raw
if ($certificate -notmatch '-----BEGIN CERTIFICATE-----' -or $certificate -match 'PRIVATE KEY') {
    throw 'Se requiere un certificado CA PEM, sin claves privadas.'
}
$privateDirectory = [IO.Path]::GetFullPath((Join-Path $secretRoot ('probe-' + [Guid]::NewGuid().ToString('N'))))
if (-not $privateDirectory.StartsWith($secretRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'Ruta temporal no valida.' }
$passFile = Join-Path $privateDirectory 'pgpass'
$certificateFile = Join-Path $privateDirectory 'root.crt'
[void][IO.Directory]::CreateDirectory($privateDirectory)
$password = Read-ProtectedSecret $config.postgresPasswordProtected
try {
    $fields = @($config.postgresHost, [string]$config.postgresPort, $config.postgresDatabase,
        $config.postgresUser, $password) | ForEach-Object { $_.Replace('\', '\\').Replace(':', '\:') }
    [IO.File]::WriteAllText($passFile, (($fields -join ':') + "`n"), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($certificateFile, $certificate, [Text.UTF8Encoding]::new($false))
    # psql 16 puede consultar servidores mas nuevos; este paso NO ejecuta pg_dump.
    $arguments = @('run','--rm','--network','bridge',
        '--mount', "type=bind,source=$privateDirectory,target=/clarys-private,readonly",
        '--mount', "type=bind,source=$root,target=/workspace,readonly",
        '--env', "PGHOST=$($config.postgresHost)", '--env', "PGPORT=$($config.postgresPort)",
        '--env', "PGUSER=$($config.postgresUser)", '--env', "PGDATABASE=$($config.postgresDatabase)",
        '--env', 'PGSSLMODE=verify-full', '--env', 'PGSSLROOTCERT=/clarys-private/root.crt',
        '--env', 'PGCONNECT_TIMEOUT=15', 'postgres:16-alpine',
        'sh', '/workspace/tools/Probe-ClarysPostgres.sh')
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 convierte stderr nativo en ErrorRecord.
        $ErrorActionPreference = 'Continue'
        $result = & docker @arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($exitCode -ne 0) {
        $errorText = $result -join "`n"
        if ($errorText -match 'password authentication failed') { throw 'PostgreSQL: contrasena o usuario rechazados.' }
        if ($errorText -match 'certificate|root certificate|SSL error') {
            throw 'PostgreSQL: falta verificar el certificado TLS. No se deshabilito la verificacion.'
        }
        throw 'PostgreSQL: no se pudo completar la consulta segura. No se modificaron datos.'
    }
    $probe = ($result -join "`n") | ConvertFrom-Json
    if ($probe.database -ne 'postgres') { throw 'La base consultada no es la configurada para Clarys.' }
    Write-Output ('PASS: PostgreSQL autenticado con TLS; version {0}, lectura public/auth/storage.' -f $probe.server_version_num)
    Write-Output ('Extensiones: ' + ($probe.extensions -join ', '))
    Write-Output ('Conteos de control: productos={0}; usuarios={1}; objetos Storage={2}.' -f
        $probe.products, $probe.auth_users, $probe.storage_objects)
    Write-Output 'No se realizaron respaldos, restauraciones ni escrituras productivas.'
} finally {
    $password = $null
    $fields = $null
    if (Test-Path -LiteralPath $passFile) { Remove-Item -LiteralPath $passFile -Force }
    if (Test-Path -LiteralPath $certificateFile) { Remove-Item -LiteralPath $certificateFile -Force }
    if (Test-Path -LiteralPath $privateDirectory) { Remove-Item -LiteralPath $privateDirectory -Force }
}
