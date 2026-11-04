# Node Connect Security Architecture & Threat Model

**Document Classification:** Security Protocol & Implementation Guide  
**Status:** Under Review (Assumed Defaults Documented)  
**Security Level:** Zero-Trust Local Area Network (mTLS + Cryptographic Pinning)  

---

## 1. Zero-Trust Security Philosophy

Node Connect operates under the strict assumption that **local area networks (Wi-Fi, LAN) are inherently untrusted and hostile**. Public coffee shops, shared dormitory networks, and office Wi-Fi routinely carry untrusted devices, ARP spoofing, and rogue mDNS advertisements.

Therefore:
1. **No Plaintext Mode**: Unencrypted TCP connections are permanently rejected. There is no fallback or downgrade to HTTP/1 or unauthenticated streams.
2. **Mutual TLS (mTLS) Everywhere**: Every connection requires both the client and the server to present cryptographic X.509 certificates generated on-device with pinned public key fingerprints.
3. **Off by Default**: Node Connect discovery, advertising, and listening sockets remain completely shut down until explicitly enabled by the user in Settings.
4. **Zero Cloud Dependencies**: Keys, certificates, pairing secrets, and authentication records are stored strictly on-device. No external servers or third-party certificate authorities are ever consulted.

---

## 2. Pairing & Trust Establishment

Trust between two devices is established out-of-band via **QR Code Pairing** or a **6-Digit Secure PIN**.

```
Device A (Initiator)                                 Device B (Responder)
        │                                                     │
        │ [User clicks "Pair New Device"]                     │
        ├──────────── Generates ephemeral pairing session ────┤
        │             • 128-bit One-Time Token (OTT)          │
        │             • Public Key Fingerprint (SHA-256)      │
        │             • Local IPv4/IPv6 Addresses + Port      │
        │                                                     │
   [Displays QR Code / 6-Digit PIN]                           │
        │                                                     │
        │ <── Scans QR Code or enters 6-digit PIN ────────────┤
        │                                                     │
        ├──── Connects via TLS (Validates Peer Fingerprint) ──┤
        ├──── Transmits OTT over encrypted TLS tunnel ───────>│
        │                                                     │
   [Both screens show Confirmation Dialog with Device Names]  │
   "Pair with 'Satya's Phone' (Fingerprint: 3F:2A:...:89)?"   │
        │                                                     │
        ├──── User approves on BOTH devices ──────────────────┤
        ├──── Exchange permanent device IDs & certs ─────────>│
        │<─── Store pinned cert fingerprints in registry ─────┤
```

### Pairing Security Controls
- **Rate Limiting & Attempt Caps**: If an incorrect 6-digit PIN is entered 3 consecutive times, pairing is locked out for 60 seconds with exponential backoff.
- **Ephemeral OTT Expiration**: Pairing QR codes and PINs automatically expire after 120 seconds.
- **Mandatory Dual Confirmation**: A connection is never completed until the human user confirms the pairing request on **both** devices simultaneously.

---

## 3. Cryptographic Key Management & Storage

Each Node Player installation generates a unique 256-bit elliptic curve key pair (`secp256r1` / `prime256v1`) and a self-signed X.509 certificate with a 10-year validity:

| Platform | Key Generation | Secure Storage Location | Protection Mechanism |
| :--- | :--- | :--- | :--- |
| **Android** | `KeyGenParameterSpec` with `PURPOSE_SIGN \| PURPOSE_VERIFY` | **Android Keystore Provider** | Hardware-backed StrongBox / TEE with hardware isolation. Keys never leave the secure enclave. |
| **Windows Desktop** | Java `KeyPairGenerator` (`EC 256`) | `%APPDATA%\NodePlayer\keys\node_connect.p12` | Protected using **Windows Data Protection API (DPAPI)** via `CryptProtectData`. Decryptable only by the active Windows user account. |

### Pinned Certificate Fingerprint Validation
When a TLS handshake occurs, the custom `X509TrustManager` ignores standard system root CAs. It inspects the peer's public key SHA-256 hash against the trusted fingerprint stored in `DeviceRegistry`. Any certificate with an unpinned fingerprint is rejected before any application payload is processed.

---

## 4. Granular Per-Device Permissions & Instant Revocation

Every paired device possesses an independent capability profile configured in the **Connected Devices** screen:

| Permission Flag | Default for Newly Paired Device | Description |
| :--- | :---: | :--- |
| `canReceiveAudio` | **Allowed (Prompt on 1st)** | Allows this remote device to cast its audio stream to this device. |
| `canSendAudio` | **Allowed** | Allows this local device to stream audio to the remote device. |
| `canBrowseLibrary` | **Allowed** | Allows remote device to search and view track metadata and artwork thumbnails. |
| `canControlPlayback` | **Allowed** | Allows remote device to issue play, pause, seek, volume, and queue commands. |
| `canShareFiles` | **Forbidden (Strictly Off)** | Allows physical copying of audio file bytes. Requires explicit master toggle and per-device approval. |
| `canSyncPlaylists` | **Allowed** | Allows playlist synchronization over the Transfer rail. |
| `canSyncFavorites` | **Allowed** | Allows favorite track ratings and bookmarks to sync. |
| `canSyncSettings` | **Forbidden (Off)** | Allows theme, motion tokens, and shared UI preferences to sync. |
| `canSyncEqProfiles` | **Allowed** | Allows 10-band EQ and DSP acoustic profiles to sync. |
| `canShareThirdPartySessions` | **Forbidden (Off)** | Allows viewing Spotify / YouTube Music Now Playing metadata and remote play/pause. |

### Instant Revocation
Tapping **"Revoke Device"** or **"Unpair"** in the UI:
1. Immediately severs all active gRPC streams and closes TCP sockets with that device.
2. Purges the device's public key fingerprint from `DeviceRegistry`.
3. Erases any pending transfer tokens or cached remote metadata associated with that device ID.
4. Any subsequent TLS handshake from that device is terminated during certificate exchange.

---

## 5. Network Exposure & Windows Firewall Considerations

- **Opaque Identifiers**: In discovery and RPCs, audio files are addressed strictly by opaque random UUIDs (e.g. `node://track/7f2c8a14-3b2e-48a1-...`). Native operating system file paths (`C:\Users\...`, `/storage/emulated/0/...`) are never exposed across the network.
- **Randomized Instance Names**: In mDNS advertising, devices advertise randomized or user-chosen display names (`"Node Player (Living Room)"`), never exposing user account names or hardware serial numbers.
- **Windows Firewall Guidance**: Windows 10/11 requires an inbound rule on the private network profile for TCP port 47200. On first launch of Node Connect, Node Player displays a non-blocking modal explaining the Windows Defender Firewall prompt, guiding the user to check "Private networks" without performing silent or insecure administrative workarounds.

---

*Security specification maintained by the Node Player Core Engineering Team.*
