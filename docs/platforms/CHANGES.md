# Platform Implementation Changes (v1.6.0)

**Branch:** `feature/node-connect`  
**Base Release:** v1.5.0  

---

## Change Log

### [2026-11-04] - Step 1: Documentation Honesty & Dynamic Verification Pass
- **Shared Code (`core-model`, `core-library`, `core-ui`)**:
  - `AudioEffectConfig.kt`: Added `isDspBypassed` calculating whether all 8 DSP modules are flat/bypassed.
  - `AudioOutputInfo.kt`: Added `isTrulyBitPerfect(srcRate, srcDepth, dspBypassed)`.
  - `AudioHeaderParser.kt`: Restored in-memory `parseBytes` function and sub-parsers (`parseFlacBytes`, `parseWavBytes`, `parseMp3Bytes`, `parseOggOrOpusBytes`, `parseM4aOrAlacBytes`) to ensure full test suite compatibility.
  - `SharedPlayerLayout.kt`: Dynamically computes truthful output status string (e.g. "Bit-Perfect Output" vs. "32-bit Float DSP Active" vs. "Direct").
- **Android (`app-android`)**:
  - `TrackDetailsSheet.kt`: Evaluates `isBitPerfect` strictly against source format and DSP bypass state. Shows "Bit-perfect direct stream" only when true.
- **Documentation**:
  - `PRIVACY.md`: Updated to "No network use unless you opt in" and documented the 4 opt-in network capabilities.
  - `README.md` & `site/index.html`: Replaced "100% offline" with opt-in disclosures. Clarified that Windows MSI/EXE are Authenticode-unsigned (SmartScreen prompt on initial run) and APK is signed with release keystore (v2 scheme). Emphasized SHA-256 as primary trust hash. Added v0.1.0 to version history.
- **Status**: Completed, tests pass, committed locally (`bf47ffb`).

---

### [2026-11-04] - Step 2: Node Connect & UI Overhaul Specification
- **Ecosystem Specs Created**:
  - `docs/ecosystem/NODE_CONNECT.md`: Comprehensive protocol, roles, gRPC service definitions, message flows, and timing constants.
  - `docs/ecosystem/NODE_CONNECT_SECURITY.md`: Zero-trust mTLS architecture, QR / PIN pairing, Android Keystore & Windows DPAPI key storage, and granular permissions.
  - `docs/ecosystem/SYNC_CATEGORIES.md`: Complete sync category rules, shared/per-platform scopes, Murmur3+MD5 track fingerprint, and 3-snapshot undo engine.
  - `docs/ecosystem/NODE_CONNECT_TESTS.md`: Automated test matrix for mTLS, fault injection, streaming, and conflict resolution.
  - `docs/ecosystem/NODE_CONNECT_DEVICE_TESTS.md`: Human verification sheet with 23 step-by-step test cases.
  - `docs/platforms/windows-ui.md`: Windows UI overhaul reference map and token definitions.
- **Status**: Completed, specification ready for implementation.
