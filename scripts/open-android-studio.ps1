Set-StrictMode -Version Latest

$studio = "C:\Program Files\Android\Android Studio\bin\studio64.exe"
if (-not (Test-Path $studio)) {
    Write-Error "Android Studio was not found at $studio."
    exit 1
}

Start-Process -FilePath $studio -ArgumentList "`"$PWD`"" -WindowStyle Hidden
