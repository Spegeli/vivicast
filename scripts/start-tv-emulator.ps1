# Android TV emulator launcher. Parametric API level so we test the supported floor (Android 9 / API 28)
# and ceiling (API 36), not just one. Bugs that only surface on older scoped-storage/permission paths
# stay hidden if we only ever run the newest image.
# NOTE: param() must be the first executable statement — keep it above Set-StrictMode.
param(
    [ValidateSet(28, 36)]
    [int]$Api = 36
)

Set-StrictMode -Version Latest

$emulator = Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
if (-not (Test-Path $emulator)) {
    Write-Error "Android Emulator was not found at $emulator."
    exit 1
}

$avdName = "ViviCast_AndroidTV_API$Api"

# List AVDs via the emulator itself (no extra tool dependency) and fail early with a clear hint.
$known = & $emulator -list-avds 2>$null
if ($known -notcontains $avdName) {
    Write-Error "AVD '$avdName' not found. Create it first (API 28 image: system-images;android-28;android-tv;x86)."
    exit 1
}

Start-Process -FilePath $emulator -ArgumentList "-avd", $avdName
Write-Host "Started Android TV emulator: $avdName (API $Api)"
