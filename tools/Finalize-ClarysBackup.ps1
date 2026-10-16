#requires -Version 5.1
param([Parameter(Mandatory=$true)][string] $RunPath,[switch] $RecordUserAcceptance)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Security
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=Split-Path -Parent $PSScriptRoot
$base=[IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.clarys-backup\respaldos'))
$run=[IO.Path]::GetFullPath($RunPath)
if(-not $run.StartsWith($base+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    (Split-Path $run -Leaf) -notmatch '^\d{8}T\d{6}Z-[a-f0-9]{8}$') {throw 'Ruta de ejercicio no valida.'}
$working=[IO.Path]::GetFullPath((Join-Path $run 'working-private'))
$evidencePath=Join-Path $run 'evidence.json'
$e=Get-Content -LiteralPath $evidencePath -Raw -Encoding UTF8|ConvertFrom-Json
if($e.projectRef -ne 'mzqvmddxxlzjfruksgnz' -or $e.restoration -notlike 'PASS*' -or
    $e.inventoryComparison -notlike 'PASS*' -or $e.businessRestorationTest -notlike 'PASS*') {
    throw 'No se permite cerrar una restauracion sin las verificaciones requeridas.'
}
foreach($name in @('database.dump','storage.zip')) {
    $cipher=Join-Path $working ($name+'.dpapi')
    $bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($cipher),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $hash=[Security.Cryptography.SHA256]::Create()
    try {
        $expected=(Get-FileHash -LiteralPath (Join-Path $working $name) -Algorithm SHA256).Hash
        $actual=[BitConverter]::ToString($hash.ComputeHash($bytes)).Replace('-','')
        if($actual -ne $expected) {throw 'El archivo cifrado no reproduce el original.'}
        $e|Add-Member -NotePropertyName ($name.Replace('.','_')+'Sha256') -NotePropertyValue $expected -Force
    } finally {$hash.Dispose();[Array]::Clear($bytes,0,$bytes.Length)}
    Copy-Item -LiteralPath $cipher -Destination (Join-Path $run ($name+'.dpapi'))
}
$metadata=Join-Path $working 'metadata-package'
[void][IO.Directory]::CreateDirectory($metadata)
foreach($file in @('inventory.json','inventory-restored.json','fingerprints.jsonl','roles.json','buckets.json',
    'storage-objects.json','archive-index.txt','restore-error-private.log','root.crt')) {
    $source=Join-Path $working $file
    if(Test-Path -LiteralPath $source -PathType Leaf) {Copy-Item -LiteralPath $source -Destination (Join-Path $metadata $file)}
}
$functions=Join-Path $metadata 'edge-functions'
foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'supabase\functions') -Recurse -File -Filter '*.ts') {
    $relative=$file.FullName.Substring((Join-Path $root 'supabase\functions').Length).TrimStart('\')
    $target=Join-Path $functions $relative
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $target))
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
Copy-Item -LiteralPath (Join-Path $root 'supabase\config.toml') -Destination (Join-Path $metadata 'config.toml')
$zip=Join-Path $working 'metadata.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($metadata,$zip)
$bytes=[IO.File]::ReadAllBytes($zip)
try {
    $cipher=[Security.Cryptography.ProtectedData]::Protect($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes((Join-Path $run 'metadata.zip.dpapi'),$cipher)
} finally {[Array]::Clear($bytes,0,$bytes.Length)}
$manifest=@()
foreach($file in Get-ChildItem -LiteralPath $run -File -Filter '*.dpapi') {
    $manifest+=[ordered]@{name=$file.Name;bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash}
}
[IO.File]::WriteAllText((Join-Path $run 'manifest.json'),($manifest|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
if($RecordUserAcceptance) {
    # Usar solo despues de recibir la aprobacion humana expresa, nunca inferirla.
    $e.acceptance='APROBADO CON LIMITACION: ensayo de datos y archivos; APK no probada contra servicios de la copia'
    $e|Add-Member -NotePropertyName acceptedBy -NotePropertyValue 'Juan Esteban Sepulveda Sepulveda' -Force
    $e|Add-Member -NotePropertyName acceptanceStatement -NotePropertyValue 'Apruebo con esa limitacion' -Force
    $e|Add-Member -NotePropertyName acceptedAtUtc -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
}
$e|Add-Member -NotePropertyName encryptedFiles -NotePropertyValue $manifest -Force
$e|Add-Member -NotePropertyName privacy -NotePropertyValue 'Sin copia de filas personales en el repositorio; archivos sensibles cifrados DPAPI y ACL restringida' -Force
$e|Add-Member -NotePropertyName portability -NotePropertyValue 'Requiere el mismo usuario Windows/equipo; no se certifica restauracion en otro PC' -Force
$e|Add-Member -NotePropertyName externalConfiguration -NotePropertyValue 'Secretos/configuracion OAuth, SMTP, claves JWT y roles de plataforma requieren gestion separada; no es un clon completo de servicios Supabase' -Force
$e.PSObject.Properties.Remove('workingDirectory')
[IO.File]::WriteAllText($evidencePath,($e|ConvertTo-Json -Depth 35),[Text.UTF8Encoding]::new($false))
foreach($file in $manifest) {
    if((Get-FileHash -LiteralPath (Join-Path $run $file.name) -Algorithm SHA256).Hash -ne $file.sha256) {throw 'Manifiesto no coincide.'}
}
# Target exacto creado por este ejercicio, verificado antes del borrado recursivo.
if((Split-Path $working -Leaf) -ne 'working-private' -or (Split-Path $working -Parent) -ne $run -or
    -not $working.StartsWith($base+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'No se permite limpiar una ruta ajena al ejercicio.'
}
Remove-Item -LiteralPath $working -Recurse -Force
Write-Output 'PASS: manifiesto SHA-256 verificado; copias temporales sin cifrar eliminadas.'
Write-Output ('Archivos cifrados: '+$manifest.Count+'; ruta: '+$run)
