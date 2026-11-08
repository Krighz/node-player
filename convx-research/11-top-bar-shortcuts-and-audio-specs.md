# 11-top-bar-shortcuts-and-audio-specs.md — Detailed Specifications for Header Bar, Audio Outputs, Shortcuts & Formats

> **Target Codebase:** Node Player (`D:\AI MAKING\node-player`)  
> **Audience:** Next Development Session / Autonomous AI Coding Agent  
> **Mission:** Exact blueprint for Top Header Bar Output Selection, Dynamic Codec/Quality Highlighting (Local & YouTube Music Opus), Mobile Shortcuts Grid, and Recently Played Counter.

---

## 1. Top Header Bar: Real Physical Audio Output Selection

### 1.1 The Requirement
The top bar output selector must **not** display abstract or redundant bit-depth options like `32-BIT` or `24-BIT`. Instead, it must dynamically show the **actual physical output endpoint** currently routing the audio stream:
- E.g.: `Realtek High Definition Audio`, `Speakers (Realtek(R) Audio)`, `NVIDIA High Definition Audio (HDMI)`, `Headphones (Sennheiser HD 800 S)`, `USB DAC (FiiO K7)`.

### 1.2 Underlying Technology & Library
- **LiquidGlass Aesthetic:** Must use the shared single-pass downscaled LiquidGlass pool (inspired by Convx's `GlassPool` and `Modifier.liquidGlass`) with SDF rounded corners and top specular highlight.
- **Windows Implementation:** 
  - `WasapiAudioOutput.kt` enumerates endpoints via WASAPI `IMMDeviceEnumerator` (`eRender`, `DEVICE_STATE_ACTIVE`).
  - The dropdown menu lists physical device friendly names and allows instant device swapping via `setTargetDevice(deviceSpec)`.
- **Android Implementation:**
  - Uses Android `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` to detect Bluetooth Headset (A2DP), USB DAC, or Built-in Speaker.

---

## 2. Dynamic Audio Format & Codec Badge Highlighting

The format badge in the top bar and bottom player deck must dynamically reflect the active track source and codec rather than static labels:

### 2.1 YouTube Music Stream Mode:
- **Format Label:** `OPUS 160 kbps` or `AAC 128 kbps` (matching the resolved itag).
- **Quality Indicators:**
  - Codec: `Opus` (`audio/webm`) or `AAC-LC` (`audio/mp4`).
  - Bitrate: e.g. `160 kbps CBR` (itag 251) / `128 kbps` (itag 140).
  - Sample Rate: `48,000 Hz` (Native Opus rate).
- **Badge Styling:** Amber or Cyan frosted glass pill (`OPUS • 160k`).

### 2.2 Local File / Hi-Res Mode:
- **Actual Hi-Res Verification:** On Android, do **not** blindly show a fake `HI-RES` banner. Only display the `HI-RES` badge if the file is **strictly verified** as high-resolution:
  - Sample Rate $> 48,000\text{ Hz}$ (e.g. `96 kHz`, `192 kHz`) OR Bit Depth $> 16\text{ bit}$ (e.g. `24-bit`, `32-bit Float`).
- **Standard Lossless:** Display `FLAC • 16-bit / 44.1kHz` or `ALAC`.
- **Lossy / MP3:** Display `MP3 • 320k` or `AAC`.

---

## 3. Mobile Android Reference: The 4-Shortcut Grid (Direct Engine)

In the mobile reference (`For You`), replace the generic placeholder cards with the user's four specific shortcuts:

```
┌──────────────────────────────────────┬──────────────────────────────────────┐
│  Shortcut 1: Offline Music           │  Shortcut 2: Device Connect Settings │
│  • Label: "Offline Music"            │  • Label: "Device Connect"           │
│  • Subtitle: "Downloaded & Local"    │  • Subtitle: "Node Connect Wireless" │
│  • Action: Navigates to local cache  │  • Action: Opens Device Pairing /    │
│    and downloaded YouTube tracks     │    Sync Settings sheet               │
├──────────────────────────────────────┼──────────────────────────────────────┤
│  Shortcut 3: Hi-Res Library          │  Shortcut 4: User Playlists          │
│  • Label: "Hi-Res Collection"        │  • Label: "My Playlists"             │
│  • Subtitle: "24-bit / FLAC Only"    │  • Subtitle: "Custom Curated"        │
│  • Action: Instant filter for        │  • Action: Opens user-created        │
│    lossless/master FLAC tracks       │    playlists list                    │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

### Shortcut Interaction & Styling:
- Each shortcut card is rendered using `GlassCard` with a continuous squircle shape (`ContinuousRoundedRectangle(16.dp)`).
- Micro-interaction: `Modifier.pressScale(0.97f)` on tap.
- Glowing icon badges:
  - Offline: Cyan icon with download badge.
  - Device Connect: Pulsing Amber/Cyan `DeviceConnectPill` icon.
  - Hi-Res: Golden diamond icon.
  - Playlists: Purple/Indigo music stack icon.

---

## 4. Recently Played Shelf with Play Count Indicators

Underneath the shortcuts, render the **Recently Played** shelf:
- **Play Count Badges:** Display exact play counters as a glass badge over each album/track thumbnail (e.g., `11x plays`, `8x plays`, `29 plays`).
- **Data Source:** Pull from Node Player's track play-count database (`track.playCount`).
- **Visual Presentation:**
  - Horizontal scrolling row of squircle album covers (`ContinuousRoundedRectangle(12.dp)`).
  - Floating pill on the top-left of each artwork showing the play count with high contrast.
  - Track title and artist underneath using `AppleTokens.ItemTitle` (15sp) and `AppleTokens.ItemSubtitle` (13sp) to eliminate line height drift.

---

## 5. Summary of Instructions for the AI Implementer

1. **Top Bar Output:** Bind `WasapiAudioOutput.deviceList` (Windows) and `AudioManager` (Android) to the output dropdown. Display the friendly physical device name. Remove hardcoded 24/32-bit text.
2. **Quality Badging:** Format badge reads `AudioOutputInfo`. If source is YouTube Music, display `OPUS 160k` or `AAC 128k`. If source is local, verify actual sample rate/bit depth before displaying `HI-RES`.
3. **Shortcuts:** Implement the 4 specified tiles: `Offline Music`, `Device Connect`, `Hi-Res FLAC`, and `My Playlists`.
4. **Play Counters:** Add `track.playCount` badge to Recently Played items.
5. **Glass Shader:** Use the shared 0.35x downscaled multi-shape SDF pool across all components.
