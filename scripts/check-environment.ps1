Set-StrictMode -Version Latest

$ErrorActionPreference = "Continue"

function Show-CommandStatus {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [string]$FallbackPath = ""
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        if (-not [string]::IsNullOrWhiteSpace($FallbackPath) -and (Test-Path $FallbackPath)) {
            Write-Host "[found]   $Name -> $FallbackPath"
            return
        }
        Write-Host "[missing] $Name"
        return
    }

    Write-Host "[found]   $Name -> $($command.Source)"
}

Write-Host "ViviCast environment check"
Write-Host ""

Show-CommandStatus "git"
Show-CommandStatus "java" "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
Show-CommandStatus "javac" "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
Show-CommandStatus "adb" (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
Show-CommandStatus "scrcpy" (Join-Path $env:USERPROFILE "Tools\scrcpy-win64-v4.0\scrcpy.exe")
Show-CommandStatus "gradle"
Show-CommandStatus "sdkmanager" (Join-Path $env:LOCALAPPDATA "Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat")
Show-CommandStatus "avdmanager" (Join-Path $env:LOCALAPPDATA "Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat")
Show-CommandStatus "emulator" (Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe")

Write-Host ""
Write-Host "Environment variables"
foreach ($name in @("ANDROID_HOME", "ANDROID_SDK_ROOT", "JAVA_HOME")) {
    $value = [Environment]::GetEnvironmentVariable($name, "User")
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, "Machine")
    }

    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Host "[missing] $name"
    } else {
        Write-Host "[found]   $name=$value"
    }
}

Write-Host ""
$defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
if (Test-Path $defaultSdk) {
    Write-Host "[found]   Default Android SDK path: $defaultSdk"
} else {
    Write-Host "[missing] Default Android SDK path: $defaultSdk"
}

$adb = Join-Path $defaultSdk "platform-tools\adb.exe"
if (Test-Path $adb) {
    Write-Host "[found]   adb.exe at $adb"
} else {
    Write-Host "[missing] adb.exe at $adb"
}
