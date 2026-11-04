# Node Connect Security Architecture & Threat Model

**Document Classification:** Security Protocol & Implementation Guide  
**Status:** Updated for Node Connect Stabilization & Simple Mode  
**Active Default:** **Simple Mode** (Zero PIN / Unauthenticated TLS)  
**Alternative Mode:** **Strict Mode** (Mutual TLS + Cryptographic Pinning + 6-Digit PIN)

---

## 1. Security Levels: Simple Mode vs Strict Mode

To ensure frictionless connectivity across varied local network topologies while debugging, Node Connect provides a two-tier security level architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Node Connect Security Mode                      │
├────────────────────────────────────────┬───────────────────────────────┤
│           Simple Mode (Default)        │      Strict Mode (Opt-In)     │
├────────────────────────────────────────┼───────────────────────────────┤
│ • Transport: TLS (any cert) or Plain   │ • Transport: Strict mTLS      │
│ • Identity: Dynamic Device ID + Name   │ • Identity: Pinned SHA-256 FP │
│ • Pairing: Instant Hello / Allow Dialog│ • Pairing: 6-Digit PIN + OTT  │
│ • QR Payload: IP list + Port + Name    │ • QR Payload: FP + Token + IP │
│ • Network: Prompt on first connection  │ • Network: Dual Confirmation  │
│   (or "Trust all devices on network")  │                               │
└────────────────────────────────────────┴───────────────────────────────┘
```

### Warning Displayed in UI (Simple Mode)
> **Simple Security Active:** Connections on this local network are accepted without certificate pinning or PIN verification. Suitable for trusted home Wi-Fi and direct USB links. Switch to Strict Mode in public environments.

---

## 2. Threat Analysis & Risks of Simple Mode

While Simple Mode enables immediate, zero-hassle discovery and pairing, users should be aware of the following security trade-offs:

1. **Local Network Man-in-the-Middle (MitM):**
   - *Risk:* In Simple Mode with unauthenticated TLS or Plain TCP, an adversary on the same unsegmented network (e.g., public airport Wi-Fi) performing ARP spoofing or DNS poisoning could intercept or manipulate control commands, playback status, or metadata.
   - *Mitigation:* Simple mode is intended for home networks, direct phone hotspots, and USB debugging links. Strict Mode should be toggled on hostile networks.

2. **Rogue Peer Connection:**
   - *Risk:* If "Trust all devices on my network" is turned ON, any device running a compatible protocol on the subnet could initiate playback control or library queries without user intervention.
   - *Mitigation:* The "Allow <Device> to connect?" modal prompts the user on the first incoming connection by default (Allow Once, Always, Deny) unless "Trust all" is intentionally enabled by the user during testing.

3. **Plain TCP Eavesdropping (Debugging Only):**
   - *Risk:* When Encryption toggle is set to OFF, packet payloads (track names, artist names, volume levels, PCM audio cast packets) are transmitted in plaintext and inspectable via Wireshark.
   - *Mitigation:* The Encryption toggle defaults to ON (TLS enabled). Plain TCP is reserved for diagnostics.

---

## 3. Strict Mode Architecture (Preserved in Codebase)

The Strict Mode implementation (Mutual TLS, EC-256 certificate pinning, 128-bit One-Time Tokens, and 6-digit confirmation PINs) remains fully intact within `com.nodeplayer.network.crypto` and `com.nodeplayer.network.pairing`.

### Re-enabling Strict Mode
When toggled in Node Connect Settings:
1. `PinningTrustManager` enforces SHA-256 fingerprint matches against the persistent `DeviceRegistry`.
2. `NodeServer` sets `candidate.needClientAuth = true`.
3. Incoming unpinned connections trigger the 6-digit PIN / QR verification flow with rate limiting and 120-second expiration.

---

## 4. Opaque IDs & Data Protection Guarantees

Across **both** Simple and Strict modes, the following fundamental privacy invariants are strictly upheld:

1. **Zero Cloud Telemetry:** No remote accounts, no internet routing, no analytics. All traffic is bound to the local link.
2. **Zero Native Path Leakage:** Local file system paths (`C:\Users\...`, `/storage/emulated/0/...`) are never transmitted over the wire. Tracks and albums are addressed exclusively via opaque UUIDs (`node://track/<uuid>`).
3. **No Audio Persistence on Receiver:** Audio casting operates as an ephemeral real-time stream into the jitter buffer and direct audio sink. No audio frames are cached or written to disk on the receiver.
4. **Third-Party Session Isolation:** Third-party media session reflection (Spotify, YouTube Music) captures only playback metadata and basic transport commands; raw audio capture is strictly prohibited.
