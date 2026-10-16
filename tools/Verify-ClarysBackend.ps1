param()
$ErrorActionPreference = 'Stop'
$clarysRoot = Split-Path -Parent $PSScriptRoot
$gradle = Get-Content -LiteralPath (Join-Path $clarysRoot 'app/build.gradle') -Raw
$projectUrl = [regex]::Match($gradle, 'SUPABASE_URL.*?"(https://[^"\\]+)').Groups[1].Value
$publicKey = [regex]::Match($gradle, 'sb_publishable_[A-Za-z0-9_-]+').Value
if (-not $projectUrl -or -not $publicKey) { throw 'No se pudo leer la configuracion publica del proyecto.' }

function Get-Probe([string] $Path, [string] $Method = 'GET') {
    $request = [System.Net.HttpWebRequest]::Create($projectUrl + $Path)
    $request.Method = $Method
    $request.Headers.Add('apikey', $publicKey)
    $request.Timeout = 20000
    if ($Method -eq 'POST') {
        # Sin JWT y sin datos: comprueba el rechazo, nunca elimina ni genera contenido.
        $request.ContentType = 'application/json'
        $bytes = [System.Text.Encoding]::UTF8.GetBytes('{}')
        $request.ContentLength = $bytes.Length
        $stream = $request.GetRequestStream()
        try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    }
    try { $reply = $request.GetResponse() } catch [System.Net.WebException] {
        if (-not $_.Exception.Response) { throw }
        $reply = $_.Exception.Response
    }
    try {
        $reader = [System.IO.StreamReader]::new($reply.GetResponseStream())
        try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
        return @{ Status = [int] $reply.StatusCode; Body = $body }
    } finally { $reply.Close() }
}

# Solo consultas de lectura; no imprime claves, filas de clientes ni tokens.
$capabilities = Get-Probe '/rest/v1/rpc/get_clarys_capabilities'
if ($capabilities.Status -ne 200) {
    Write-Output ('PENDIENTE: migracion de inventario (HTTP {0}).' -f $capabilities.Status)
    exit 1
}
$version = $capabilities.Body | ConvertFrom-Json
if ($version.version -ne 2 -or -not $version.reservations) { throw 'Version de backend no compatible.' }
foreach ($orderStatus in @('pending','contacted','approved','preparing','completed','rejected')) {
    if ($orderStatus -notin $version.order_statuses) {
        throw 'Falta aplicar supabase/migrations/20261014_order_statuses.sql para los seis estados de pedido.'
    }
}
$catalog = Get-Probe '/rest/v1/products?select=id,stock,reserved_stock,variants&limit=1'
if ($catalog.Status -ne 200) { throw ('Catalogo no disponible: HTTP {0}' -f $catalog.Status) }
$privateCosts = Get-Probe '/rest/v1/products?select=purchase_price&limit=1'
if ($privateCosts.Status -eq 200) { throw 'El rol anonimo puede leer costos privados.' }
$orders = Get-Probe '/rest/v1/contact_requests?select=id&limit=1'
if ($orders.Status -eq 200) {
    $rows = $orders.Body | ConvertFrom-Json
    if (@($rows).Count -ne 0) { throw 'El rol anonimo puede leer pedidos directamente.' }
}
Write-Output 'PASS: backend v2, catalogo publico y restricciones de lectura.'
foreach ($functionName in @('delete-account', 'generate-order-description')) {
    $path = '/functions/v1/' + $functionName
    $preflight = Get-Probe $path 'OPTIONS'
    if ($preflight.Status -ne 200) {
        throw ('Funcion {0} no disponible: HTTP {1}' -f $functionName, $preflight.Status)
    }
    $unauthorized = Get-Probe $path 'POST'
    if ($unauthorized.Status -ne 401) {
        throw ('Funcion {0} no rechaza la solicitud sin sesion con HTTP 401.' -f $functionName)
    }
    $errorResponse = $unauthorized.Body | ConvertFrom-Json
    if (-not $errorResponse.error) {
        throw ('No se confirmo el rechazo de sesion dentro de {0}.' -f $functionName)
    }
    Write-Output ('PASS: {0} disponible y protegida sin sesion.' -f $functionName)
}
