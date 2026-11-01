param (
    [string]$Version = "1.0.0",
    [switch]$Draft,
    [switch]$PreRelease
)

$ErrorActionPreference = "Stop"

Write-Host "=== Node Player Release Automation ===" -ForegroundColor Cyan
Write-Host "Target Version: $Version"

# 1. Verify signing configuration
$userHome = [Environment]::GetFolderPath("UserProfile")
$signingDir = Join-Path $userHome "Documents\node-player-signing"
$keystorePath = Join-Path $signingDir "node-player-release.keystore"
$signingPropsPath = Join-Path $signingDir "signing.properties"

if (-not (Test-Path $keystorePath) -or -not (Test-Path $signingPropsPath)) {
    Write-Error "Release keystore or signing.properties not found in: $signingDir`nProduction release builds require official keystore. Aborting."
    exit 1
}

Write-Host "Using release keystore from: $signingDir" -ForegroundColor Green

# 2. Build Release APK
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

Write-Host "Building signed release APK with R8 and resource shrinking..." -ForegroundColor Yellow
& .\gradlew.bat assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
    exit $LASTEXITCODE
}

# 3. Verify APK with apksigner
$apkSrc = Join-Path $repoRoot "app-android\build\outputs\apk\release\app-android-release.apk"
if (-not (Test-Path $apkSrc)) {
    Write-Error "Release APK not found at: $apkSrc"
    exit 1
}

$buildTools = Join-Path (Join-Path $repoRoot "..\android-sdk") "build-tools\34.0.0"
$apksigner = Join-Path $buildTools "apksigner.bat"

if (Test-Path $apksigner) {
    Write-Host "Verifying APK signature..." -ForegroundColor Yellow
    & $apksigner verify --verbose $apkSrc
    if ($LASTEXITCODE -ne 0) {
        Write-Error "apksigner verification failed."
        exit $LASTEXITCODE
    }
    Write-Host "APK signature verified successfully." -ForegroundColor Green
}

# 4. Package Artifacts
$distDir = Join-Path $repoRoot "dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

$distApk = Join-Path $distDir "NodePlayer.apk"
$distChecksum = Join-Path $distDir "NodePlayer.apk.sha256"

Copy-Item -Path $apkSrc -Destination $distApk -Force

# Generate SHA-256
$hash = (Get-FileHash -Path $distApk -Algorithm SHA256).Hash.ToLower()
"$hash  NodePlayer.apk" | Set-Content -Path $distChecksum -Encoding ascii

Write-Host "Artifact ready: $distApk" -ForegroundColor Green
Write-Host "SHA-256: $hash" -ForegroundColor Green

# 5. Extract Notes from CHANGELOG.md
$changelogPath = Join-Path $repoRoot "CHANGELOG.md"
$releaseNotes = ""
if (Test-Path $changelogPath) {
    $lines = Get-Content $changelogPath
    $capturing = $false
    $notesList = @()
    foreach ($line in $lines) {
        if ($line -match "^##\s+\[$Version\]") {
            $capturing = $true
            continue
        }
        if ($capturing -and $line -match "^##\s+\[") {
            break
        }
        if ($capturing) {
            $notesList += $line
        }
    }
    $releaseNotes = ($notesList -join "`n").Trim()
}

if ([string]::IsNullOrWhiteSpace($releaseNotes)) {
    $releaseNotes = "Node Player release v$Version"
}

$notesPath = Join-Path $distDir "release_notes.txt"
$releaseNotes | Set-Content -Path $notesPath -Encoding utf8

# 6. Publish via GitHub CLI
$tag = "v$Version"
Write-Host "Publishing release $tag to GitHub..." -ForegroundColor Cyan

$ghArgs = @(
    "release", "create", $tag,
    $distApk, $distChecksum,
    "--title", $tag,
    "--notes-file", $notesPath
)

if ($Version -eq "1.0.0") {
    $ghArgs += "--latest"
}

if ($Draft) {
    $ghArgs += "--draft"
}
if ($PreRelease) {
    $ghArgs += "--prerelease"
}

& gh @ghArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "GitHub CLI release failed."
    exit $LASTEXITCODE
}

Write-Host "Release $tag published successfully to GitHub!" -ForegroundColor Green
