# Node Player Release & Local Signing Guide

This document describes how releases are built, signed with production credentials, and published for **Node Player**.

---

## 1. Local Production Signing Model

To preserve the security of signing credentials and eliminate third-party cloud secrets exposure, Node Player uses a **local signing architecture**:
- Release APKs are compiled, minified with R8, resource-shrunk, and cryptographically signed on the developer's workstation.
- Official releases and checksums are verified locally via `apksigner` and uploaded directly using the GitHub CLI (`gh`).
- Continuous Integration (`.github/workflows/ci.yml`) strictly runs unit tests and builds unsigned debug APKs for PR validation.

---

## 2. Keystore Location & Structure

Production release keys are stored exclusively outside the git repository:

```
C:\Users\<username>\Documents\node-player-signing\
├── node-player-release.keystore   # 2048-bit RSA release signing keystore
└── signing.properties             # Keystore path, alias, and passwords
```

### `signing.properties` Format
```properties
storeFile=C:/Users/<username>/Documents/node-player-signing/node-player-release.keystore
storePassword=<STORE_PASSWORD>
keyAlias=nodeplayer
keyPassword=<KEY_PASSWORD>
```

> [!CAUTION]
> **Back up your keystore in at least two separate secure offline or encrypted locations.**
> If this keystore is lost, Android devices will refuse to update existing installs without uninstalling first and losing all local app data.

---

## 3. Automated Release Process (`scripts/release.ps1`)

The release script [`scripts/release.ps1`](../scripts/release.ps1) executes the end-to-end release pipeline:

1. **Keystore Check**: Confirms the production keystore exists in `Documents\node-player-signing\`. If missing, the build terminates immediately (debug fallback is strictly disabled for release builds).
2. **Optimized Build**: Executes Gradle `assembleRelease` with full R8 code shrinking and resource optimization enabled.
3. **Cryptographic Verification**: Validates the APK signature with Android SDK `apksigner verify --verbose`.
4. **Packaging & Checksum**: Copies the APK to `dist/NodePlayer.apk` and generates an accompanying SHA-256 hash file `dist/NodePlayer.apk.sha256`.
5. **Changelog Extraction**: Extracts the corresponding release notes section from `CHANGELOG.md`.
6. **GitHub Release Publication**: Uses `gh release create` to tag the release, publish the release notes, and attach `NodePlayer.apk` and its checksum.

### Triggering a Release
To release version 1.0.0:
```powershell
.\scripts\release.ps1 -Version "1.0.0"
```

---

## 4. Verifying Release Artifacts

Before publishing, releases can be inspected using Android SDK build tools:

```powershell
# Verify APK signature scheme (v2)
$apksigner = "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat"
& $apksigner verify --verbose dist\NodePlayer.apk

# Inspect package metadata and version codes
$aapt = "$env:ANDROID_HOME\build-tools\34.0.0\aapt.exe"
& $aapt dump badging dist\NodePlayer.apk | Select-String -Pattern "package: name|versionCode|versionName"
```
