Set-StrictMode -Version Latest

$sdkPath = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$platformTools = Join-Path $sdkPath "platform-tools"
$androidStudioJdk = "C:\Program Files\Android\Android Studio\jbr"
$androidStudioJdkBin = Join-Path $androidStudioJdk "bin"
$scrcpyBin = Join-Path $env:USERPROFILE "Tools\scrcpy-win64-v4.0"
$cmdlineToolsBin = Join-Path $sdkPath "cmdline-tools\latest\bin"
$emulatorBin = Join-Path $sdkPath "emulator"

if (-not (Test-Path $sdkPath)) {
    Write-Error "Android SDK was not found at $sdkPath. Install Android Studio and the Android SDK first."
    exit 1
}

& setx ANDROID_HOME $sdkPath | Out-Host
& setx ANDROID_SDK_ROOT $sdkPath | Out-Host
if (Test-Path $androidStudioJdk) {
    & setx JAVA_HOME $androidStudioJdk | Out-Host
}

$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ([string]::IsNullOrWhiteSpace($currentPath)) {
    $currentPath = ""
}

$pathParts = [System.Collections.Generic.List[string]]::new()
foreach ($part in ($currentPath -split ";" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
    if (-not $pathParts.Contains($part)) {
        $pathParts.Add($part)
    }
}

foreach ($requiredPath in @($scrcpyBin, $androidStudioJdkBin, $cmdlineToolsBin, $platformTools, $emulatorBin)) {
    if ((Test-Path $requiredPath) -and -not $pathParts.Contains($requiredPath)) {
        $pathParts.Add($requiredPath)
    }
}

$newPath = ($pathParts | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ";"
if ($newPath -ne $currentPath) {
    & setx PATH $newPath | Out-Host
    Write-Host "Updated the user PATH."
} else {
    Write-Host "Required Android paths already present in the user PATH."
}

Write-Host "ANDROID_HOME=$sdkPath"
Write-Host "ANDROID_SDK_ROOT=$sdkPath"
if (Test-Path $androidStudioJdk) {
    Write-Host "JAVA_HOME=$androidStudioJdk"
}
Write-Host "Close and reopen PowerShell, then run: adb version"
