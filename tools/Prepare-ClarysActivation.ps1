$ErrorActionPreference = 'Stop'
$clarysRoot = Split-Path -Parent $PSScriptRoot
$parts = @('supabase/migrations/20260912_production_hardening.sql',
           'supabase/migrations/20261013_inventory_orders.sql',
           'supabase/migrations/20261014_order_statuses.sql')
$builder = [System.Text.StringBuilder]::new()
[void]$builder.AppendLine('-- Clarys: actualizacion completa, generada desde las migraciones versionadas.')
[void]$builder.AppendLine('-- Ejecutar en Supabase SQL Editor. Toda la actualizacion es una transaccion.')
[void]$builder.AppendLine('begin;')
foreach ($part in $parts) {
    $sql = Get-Content -LiteralPath (Join-Path $clarysRoot $part) -Raw
    # Las migraciones solo usan begin;/commit; a nivel superior.
    $sql = [regex]::Replace($sql, '(?m)^begin;\r?$', '')
    $sql = [regex]::Replace($sql, '(?m)^commit;\r?$', '')
    [void]$builder.AppendLine($sql)
}
[void]$builder.AppendLine('commit;')
$target = Join-Path $clarysRoot 'ACTIVACION_SUPABASE_CLARYS.sql'
[System.IO.File]::WriteAllText($target,$builder.ToString(),[System.Text.UTF8Encoding]::new($false))
Write-Output $target
