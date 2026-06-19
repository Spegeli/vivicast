param(
    [Parameter(Mandatory=$true)]
    [string]$HostAddress
)

Set-StrictMode -Version Latest

$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adb) {
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path $candidate)) {
        Write-Error "adb was not found. Run scripts\configure-android-env.ps1 after installing Android SDK Platform-Tools."
        exit 1
    }
    $adbPath = $candidate
} else {
    $adbPath = $adb.Source
}

& $adbPath connect $HostAddress
& $adbPath devices
