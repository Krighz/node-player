# Node Connect Security Model: Simple vs. Strict Mode

This document details the security architecture of Node Connect, comparing **Simple Mode** (default for connectivity and developer ease) and **Strict Mode** (cryptographically authenticated zero-trust transport).

---

## 1. Zero Cloud Guarantee
Node Connect operates exclusively over the local network (LAN / Wi-Fi / Hotspot / USB Tethering / USB ADB debugging).
- **No external servers, cloud brokers, STUN/TURN, or signaling servers** are used.
- **No analytics, telemetry, or metadata** leave the local subnet.
- All discovery, handshakes, commands, and streams occur directly peer-to-peer between devices.

---

## 2. Mode Comparison

| Feature | Simple Mode (Current Default) | Strict Mode (Opt-in) |
| :--- | :--- | :--- |
| **Transport Layer** | TLS 1.3 / 1.2 (or optional Plain TCP) | TLS 1.3 mandatory |
| **Peer Authentication** | Opportunistic (Self-signed cert accepted) | Mutual X.509 EC-256 Cert Pinning |
| **PIN Verification** | None | 6-digit dynamic pairing PIN |
| **QR Code Payload** | Plain IP list, port, device ID, device name | Includes SHA-256 cert fingerprint & one-time PIN |
| **Connection Approval** | "Allow <Device> to connect?" prompt or "Trust All" toggle | Pinned identity required + mutual approval |
| **Rogue Device Protection** | Prompt-based (user can deny unknown devices) | Cryptographically enforced (unpinned certs dropped at handshake) |
| **Intended Environment** | Trusted Home Wi-Fi, Hotspot, USB debugging | Untrusted / Public Wi-Fi networks |

---

## 3. Risks of Simple Mode

While Simple Mode enables immediate, zero-friction discovery and connection without certificate-pinning failures or PIN synchronization mismatches, it carries distinct security trade-offs:

1. **Lack of Identity Verification (Man-in-the-Middle on Untrusted Networks):**
   - Simple Mode uses an unauthenticated TrustManager (`accept any certificate`).
   - On an untrusted or compromised Wi-Fi network (such as public coffee shop Wi-Fi), an active adversary capable of ARP spoofing or DNS redirection could intercept the TLS connection.
2. **Local Impersonation:**
   - Any device on the same local subnet broadcasting a valid Node Connect hello packet can request a connection.
   - *Mitigation in Simple Mode:* The user is prompted with an **"Allow <Device> to connect?"** dialog (Allow Once, Always, Deny) unless the user explicitly toggles **"Trust all devices on my network"** on.
3. **Plain Transport Risk (if Encryption toggled OFF):**
   - If the user turns Encryption **OFF** (available as a debug toggle in Settings), frames are transmitted over raw TCP plaintext. Track metadata, transport controls, and audio PCM packets can be observed by packet sniffers (e.g., Wireshark) on the same network segment.

---

## 4. UI Warning
When Simple Mode is active, the Node Connect screen prominently displays:
> **⚠️ Security Warning:** Simple Mode is active. Connections on this local network are opportunistic without certificate pinning. Use Strict Mode on public or untrusted Wi-Fi networks.

---

## 5. Transitioning to Strict Mode
To enable Strict Mode:
1. Open **Settings → Node Connect**.
2. Change **Security Level** from **Simple** to **Strict**.
3. Strict Mode requires both devices to scan each other's QR code (containing the SHA-256 certificate fingerprint) or enter the dynamic 6-digit PIN. Once paired, certificates are persisted in secure local storage and verified on every TLS session.
