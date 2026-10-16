#requires -Version 5.1
param([switch] $Check, [switch] $SelfTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRef = 'mzqvmddxxlzjfruksgnz'
$credentialDirectory = Join-Path $env:USERPROFILE '.clarys-backup'
$credentialFile = Join-Path $credentialDirectory 'clarys-backup.config.json'

function Assert-Connection([string] $HostName, [int] $Port, [string] $UserName) {
    if ([string]::IsNullOrWhiteSpace($HostName) -or [string]::IsNullOrWhiteSpace($UserName)) {
        throw 'Host y usuario PostgreSQL son obligatorios.'
    }
    if ($Port -ne 5432) { throw 'Usa el puerto 5432 del Session pooler o conexion directa, no Transaction pooler.' }
    if ($HostName -eq "db.$projectRef.supabase.co") { return }
    if ($HostName -notmatch '^[a-z0-9.-]+\.pooler\.supabase\.com$' -or
        -not $UserName.EndsWith(".$projectRef", [StringComparison]::Ordinal)) {
        throw 'La conexion debe corresponder al proyecto Clarys indicado en Connect > Session pooler.'
    }
}

function Assert-StorageKey([System.Security.SecureString] $Secret) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try {
        $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ($value -match '^sb_secret_[A-Za-z0-9_-]{16,}$') { return }
        $parts = $value.Split('.')
        if ($parts.Count -ne 3) { throw 'Usa una secret key administrativa o la clave legacy service_role, nunca la publishable/anon.' }
        $payload = $parts[1].Replace('-', '+').Replace('_', '/')
        $payload = $payload.PadRight($payload.Length + ((4 - $payload.Length % 4) % 4), '=')
        try { $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json }
        catch { throw 'El formato de la clave administrativa no es valido.' }
        if (-not $claims.PSObject.Properties['role'] -or $claims.role -ne 'service_role') {
            throw 'La clave ingresada no tiene el rol service_role.'
        }
        if ($claims.PSObject.Properties['ref'] -and $claims.ref -ne $projectRef) {
            throw 'La clave pertenece a otro proyecto; no se guardara.'
        }
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $value = $null
    }
}

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Este script usa DPAPI de Windows para el usuario actual.'
}

if ($SelfTest) {
    $sample = ConvertTo-SecureString 'verification-not-a-real-secret' -AsPlainText -Force
    $protected = ConvertFrom-SecureString $sample
    $restored = ConvertTo-SecureString $protected
    if ($restored.Length -ne $sample.Length -or $protected -match 'verification-not-a-real-secret') {
        throw 'Fallo la comprobacion de cifrado DPAPI.'
    }
    Assert-Connection "db.$projectRef.supabase.co" 5432 'postgres'
    Assert-Connection 'aws-0-test.pooler.supabase.com' 5432 "postgres.$projectRef"
    $rejected = $false
    try { Assert-Connection 'localhost' 5432 'postgres' } catch { $rejected = $true }
    if (-not $rejected) { throw 'No se rechazo una conexion ajena a Clarys.' }
    $rejected = $false
    try { Assert-Connection 'aws-0-test.pooler.supabase.com' 6543 "postgres.$projectRef" }
    catch { $rejected = $true }
    if (-not $rejected) { throw 'No se rechazo Transaction pooler.' }
    $rejected = $false
    try { Assert-StorageKey (ConvertTo-SecureString 'sb_publishable_verification' -AsPlainText -Force) }
    catch { $rejected = $true }
    if (-not $rejected) { throw 'No se rechazo una clave publica.' }
    Write-Output 'PASS: DPAPI y validaciones de proyecto/clave. No se guardaron credenciales.'
    exit 0
}

if ($Check) {
    if (-not (Test-Path -LiteralPath $credentialFile -PathType Leaf)) {
        Write-Output 'PENDIENTE: ejecuta Configure-ClarysBackup.ps1 sin -Check para configurar el acceso.'
        exit 1
    }
    $config = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
    if ($config.projectRef -ne $projectRef -or $config.secretProtection -ne 'Windows-DPAPI-CurrentUser') {
        throw 'Configuracion no compatible con Clarys.'
    }
    Assert-Connection $config.postgresHost $config.postgresPort $config.postgresUser
    $password = ConvertTo-SecureString $config.postgresPasswordProtected
    $key = ConvertTo-SecureString $config.storageServiceKeyProtected
    if ($password.Length -eq 0) { throw 'No hay contrasena PostgreSQL configurada.' }
    Assert-StorageKey $key
    Write-Output 'PASS: configuracion local cifrada legible por este usuario. Conexion remota aun no comprobada.'
    exit 0
}

Write-Host 'CLARYS: configuracion local para respaldo. No pegues secretos en el chat.'
Write-Host 'Supabase > Connect > Session pooler: copia host y usuario; puerto 5432.'
$hostName = (Read-Host 'Host PostgreSQL, sin protocolo ni contrasena').Trim().ToLowerInvariant()
$userName = (Read-Host 'Usuario PostgreSQL mostrado en Connect').Trim()
Assert-Connection $hostName 5432 $userName
$password = Read-Host 'Contrasena de la BASE DE DATOS (entrada oculta)' -AsSecureString
if ($password.Length -eq 0) { throw 'La contrasena no puede estar vacia.' }
Write-Host 'Para Storage: Settings > API Keys > secret key, o legacy service_role.'
Write-Host 'Esta clave permite acciones administrativas. Solo se usara para lectura del origen.'
$storageKey = Read-Host 'Clave administrativa de Storage (entrada oculta)' -AsSecureString
Assert-StorageKey $storageKey

if (Test-Path -LiteralPath $credentialFile) {
    if ((Read-Host 'Ya existe configuracion. Escribe ACTUALIZAR para reemplazarla') -cne 'ACTUALIZAR') {
        throw 'No se reemplazo la configuracion existente.'
    }
}
[void][IO.Directory]::CreateDirectory($credentialDirectory)
$acl = Get-Acl -LiteralPath $credentialDirectory
$acl.SetAccessRuleProtection($true, $false)
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.SetOwner($currentUser)
foreach ($sid in @($currentUser, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $rule = [Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl',
        'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $credentialDirectory -AclObject $acl
$config = [ordered]@{
    projectRef = $projectRef
    projectUrl = "https://$projectRef.supabase.co"
    postgresHost = $hostName
    postgresPort = 5432
    postgresDatabase = 'postgres'
    postgresUser = $userName
    secretProtection = 'Windows-DPAPI-CurrentUser'
    postgresPasswordProtected = ConvertFrom-SecureString $password
    storageServiceKeyProtected = ConvertFrom-SecureString $storageKey
    configuredAtUtc = [DateTime]::UtcNow.ToString('o')
}
[IO.File]::WriteAllText($credentialFile, ($config | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
Write-Output 'Configuracion cifrada guardada fuera del proyecto. No se realizo todavia ningun respaldo.'
Write-Output "Ubicacion: $credentialFile"
