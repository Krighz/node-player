# Node Player - Build & Install Script for PowerShell
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Node Player - Fast Build and Install on Android Device " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

Write-Host "`n[1/3] Building debug APK..." -ForegroundColor Yellow
cmd.exe /c "gradlew.bat assembleDebug"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle assembleDebug failed with exit code $LASTEXITCODE"
}

Write-Host "`n[2/3] Checking connected ADB devices..." -ForegroundColor Yellow
adb devices

Write-Host "`n[3/3] Installing APK onto target device..." -ForegroundColor Yellow
$apkPath = Join-Path $repoRoot "app-android\build\outputs\apk\debug\app-android-debug.apk"
adb install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Error "ADB install failed"
}

Write-Host "`n[DONE] Launching Node Player..." -ForegroundColor Green
adb shell am start -n com.nodeplayer.music/.MainActivity
Write-Host "Node Player deployed and running!" -ForegroundColor Green
