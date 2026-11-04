# Compose Multiplatform Porting Map

**Status:** v1.6.0 Unified Architecture  

---

## Module Porting & Architecture State

| Feature / Subsystem | Shared (`core-ui` / `core-common`) | Windows Desktop (`app-desktop`) | Android (`app-android`) |
| :--- | :--- | :--- | :--- |
| **Design System & Tokens** | Pure Compose Multiplatform `GlassTheme`, `MotionTokens`, `LocalGlassTokens` | Rendered via Skia Direct3D/OpenGL canvas | Rendered via Jetpack Compose + AGSL runtime shaders |
| **Optical Rail Lens Dock** | `OpticalRailDock` (shared component) | Native mouse hover, dragging, spring animation, Skia refraction | Jetpack Compose + AGSL lens distortion bubble |
| **Waveform Seekbar** | `WaveformSeekbar` with peak cache | Mouse wheel seek $\pm5\text{s}$, cached audio peaks | Touch scrub + haptic feedback, cached audio peaks |
| **Now Playing & Deck** | `BottomPlayerDeck` & `NowPlayingView` | Pinned floating pill deck with full hotkeys | Floating pill dock + full Now Playing bottom sheet |
| **Audio Output** | `AudioOutput` abstraction | Low-latency WASAPI Shared / Exclusive + DirectSound | AndroidX Media3 `AudioSink` / AudioTrack 32-bit float |
| **Audio Decoding** | `AudioDecoder` abstraction | Pure Java FLAC, MP3 (`jlayer`), WAV, FFmpeg fallback | Android `MediaCodec` + native stagefright |
| **Node Connect Core** | `core-network` (mTLS, protobuf, gRPC) | Windows DPAPI key storage + JmDNS | Android Keystore + `NsdManager` mDNS |
| **Third-Party Sessions** | Shared model `ExternalSessionState` | Windows SMTC native bridge | Android `NotificationListenerService` + `MediaSessionManager` |
