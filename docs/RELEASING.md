# Node Player Release & Signing Guide

This document describes how releases are built, signed, and published for **Node Player**, including the GitHub Actions CI/CD pipeline and keystore security.

---

## 1. Release Architecture

When a version tag (e.g., `v0.1.0`) is pushed to the GitHub repository:
1. GitHub Actions triggers `.github/workflows/release.yml`.
2. The workflow checks for release signing credentials configured in **GitHub Secrets**.
3. If secrets are present, the release APK is signed with the official production keystore.
4. If secrets are not yet configured, the workflow falls back to debug signing so that an installable APK artifact is still produced and attached to the GitHub Release.
5. A GitHub Release is created automatically with release notes and the APK attached for direct download.

---

## 2. GitHub Secrets Configuration

To sign production builds automatically in CI, configure the following **Repository Secrets** in your GitHub repository (**Settings > Secrets and variables > Actions > New repository secret**):

| Secret Name | Description | Example / Note |
| :--- | :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded string of your `.keystore` or `.jks` file. | Generate with: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("path/to/keystore"))` |
| `KEYSTORE_PASSWORD` | The store password protecting the keystore. | `nodeplayer123` (or your custom password) |
| `KEY_ALIAS` | The key alias within the keystore. | `nodeplayer` |
| `KEY_PASSWORD` | The key password for the key alias. | `nodeplayer123` (or your custom password) |

---

## 3. Keystore Generation & Secure Backup

### Generating a New Keystore
If generating a new keystore from scratch, run the following command in PowerShell:

```powershell
keytool -genkeypair -v `
  -keystore "node-player-release.keystore" `
  -alias "nodeplayer" `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass "YOUR_STORE_PASSWORD" `
  -keypass "YOUR_KEY_PASSWORD" `
  -dname "CN=Node Player, OU=Audio, O=NodePlayer, L=Bangalore, ST=Karnataka, C=IN"
```

### Exporting Base64 for GitHub Secrets
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("node-player-release.keystore")) | Set-Clipboard
```
Then paste the clipboard content directly into the `KEYSTORE_BASE64` secret in GitHub.

> [!CAUTION]
> **Never commit your `.keystore` or `.jks` file into the git repository.**
> Store your keystore and passwords in a secure external location (e.g., Bitwarden, 1Password, or an encrypted offline backup). If you lose this keystore, you will not be able to issue app updates with matching signatures.

---

## 4. Triggering a Release

1. Ensure all changes are committed and pushed to `main`:
   ```bash
   git checkout main
   git pull origin main
   ```
2. Create and push a semver tag:
   ```bash
   git tag -a v0.1.0 -m "Release v0.1.0: Liquid Glass UI, Crossfade DSP, Widgets & Synced Lyrics"
   git push origin v0.1.0
   ```
3. Watch the workflow on GitHub (**Actions > Release**).
4. Once completed, your release and signed APK will be live on the **Releases** page (`https://github.com/<owner>/node-player/releases`).
