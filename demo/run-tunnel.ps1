# ==========================================
# Abre un tunel publico hacia la app local (puerto 8080) para probar el webhook de Wompi.
# Uso (en una terminal APARTE, con la app ya corriendo):  ./run-tunnel.ps1
# Usa ngrok si esta disponible; si no, cloudflared.
# ==========================================

$puerto = 8080

# Recargar PATH (una instalacion reciente no aparece en terminales ya abiertas)
$env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
            [Environment]::GetEnvironmentVariable("Path", "User")

function Buscar-Ejecutable($nombre, $rutasExtra) {
    $cmd = Get-Command $nombre -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($ruta in $rutasExtra) {
        if (Test-Path $ruta) { return $ruta }
    }
    return $null
}

$ngrok = Buscar-Ejecutable "ngrok" @(
    "$env:LOCALAPPDATA\Microsoft\WindowsApps\ngrok.exe",
    "$env:ProgramFiles\ngrok\ngrok.exe",
    "$env:LOCALAPPDATA\ngrok\ngrok.exe"
)
$cloudflared = Buscar-Ejecutable "cloudflared" @(
    "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe",
    "$env:ProgramFiles\cloudflared\cloudflared.exe"
)

Write-Host "URL del webhook para Wompi:  https://<URL_DEL_TUNEL>/api/wompi/webhook" -ForegroundColor Cyan
Write-Host "Al terminar: Ctrl+C y devuelve la URL de eventos en Wompi a la de Render." -ForegroundColor Yellow

if ($ngrok) {
    Write-Host "Usando ngrok: $ngrok" -ForegroundColor Green
    & $ngrok http $puerto
}
elseif ($cloudflared) {
    Write-Host "Usando cloudflared: $cloudflared" -ForegroundColor Green
    & $cloudflared tunnel --url "http://localhost:$puerto"
}
else {
    Write-Host "No se encontro ngrok ni cloudflared." -ForegroundColor Red
    Write-Host "Instala uno: winget install ngrok.ngrok   o   winget install --id Cloudflare.cloudflared" -ForegroundColor Yellow
    exit 1
}
