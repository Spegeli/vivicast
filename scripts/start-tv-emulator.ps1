Set-StrictMode -Version Latest

$emulator = Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
if (-not (Test-Path $emulator)) {
    Write-Error "Android Emulator was not found at $emulator."
    exit 1
}

$avdName = "ViviCast_AndroidTV_API36"

Start-Process -FilePath $emulator -ArgumentList "-avd", $avdName
Write-Host "Started Android TV emulator: $avdName"
