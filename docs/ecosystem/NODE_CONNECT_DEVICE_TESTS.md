# Node Connect & Windows UI: Device Verification Sheet (Human Testing)

**Target Milestone:** Node Player v1.6.0  
**Testing Protocol:** All physical device testing and visual evaluations are executed by the human tester.  
**Allowed Status Values:**
- `not done`
- `implemented, automated tests pass, human check pending`
- `verified by human on <date>`
- `failed on device`

*(The agent never sets a test to "verified" or "looks right"; only the human tester updates the Result, Tester, and Date columns).*

---

## 1. Node Connect Protocol & Device Pairing Tests

| ID | Feature / Case | Step-by-Step Test Instructions | Expected Result | Status | Result (Pass/Fail) | Tester | Date | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NC-01** | **Pairing by QR Code** | 1. On Windows PC, click top right *Device Connect* pill &rarr; *Pair New Device*.<br>2. Select *Show Pairing QR*.<br>3. On Android phone, open Settings &rarr; *Node Connect* &rarr; *Scan QR*.<br>4. Point phone camera at PC screen.<br>5. Confirm matching fingerprints on both screens and tap *Approve*. | Both devices show success banner. Device names appear in Connected Devices list with green status dot. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-02** | **Pairing by 6-Digit PIN** | 1. On Windows PC, click *Pair New Device* &rarr; *Generate PIN* (shows 6-digit code).<br>2. On Android phone, select *Enter PIN Manually* and type the code.<br>3. Confirm on both screens. | TLS handshake succeeds; certificate fingerprints are pinned in both registries. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-03** | **PIN Rate Limiting** | 1. Enter an invalid 6-digit PIN 3 consecutive times from Android phone. | Third attempt displays "Too many failed attempts. Locked for 60 seconds." Subsequent entry blocked until timer expires. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-04** | **Revoke & Unpair Device** | 1. On Windows PC, open *Device Connect* &rarr; *Paired Devices*.<br>2. Click *Revoke Device* next to Android phone.<br>3. Attempt to cast or browse library from the phone. | Connection immediately severed. Phone shows "Device connection revoked". Any reconnect attempt rejected at TLS handshake. | `implemented, automated tests pass, human check pending` | | | | |

---

## 2. Audio Streaming & Playback Handoff Tests

| ID | Feature / Case | Step-by-Step Test Instructions | Expected Result | Status | Result (Pass/Fail) | Tester | Date | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NC-05** | **Cast Phone to PC** | 1. On Android phone, start playing a local FLAC or MP3 track.<br>2. Tap *Cast to...* in Now Playing.<br>3. Select *Windows PC* from device list.<br>4. Inspect PC audio output. | Phone displays "Playing on Windows PC". Audio immediately streams through PC speakers/DAC at native sample rate. No file is created on PC disk. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-06** | **Cast PC to Phone** | 1. On Windows PC, play a track.<br>2. Click *Cast to...* on bottom player deck &rarr; select *Android Phone*. | PC displays "Playing on Android Phone". Phone plays stream cleanly via its speaker/headphones. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-07** | **Screen Off During Cast** | 1. While casting audio from phone to PC, lock the phone (screen off).<br>2. Allow playback to continue for 5 minutes. | Audio continues playing continuously without stutter. Foreground service notification remains visible on lockscreen. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-08** | **Timeline Seek During Cast** | 1. While casting phone to PC, drag seekbar on phone to 2:15.<br>2. Release seekbar. | PC immediately flushes buffer (<150ms) and resumes playback smoothly from 2:15 without pops, hissing, or audio repeats. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-09** | **Pause & Resume During Cast**| 1. Tap Pause on phone during active cast.<br>2. Wait 5 seconds, then tap Play. | Audio pauses instantly on PC. Play resumes cleanly from exact pause position. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-10** | **Gapless Track Transition** | 1. Queue two continuous album tracks (e.g. live concert) during cast.<br>2. Let track 1 transition to track 2. | Transition is completely gapless with zero silence or buffering pop between tracks. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-11** | **Network Drop & Reconnect** | 1. While casting, toggle Wi-Fi OFF on phone for 3 seconds, then toggle ON. | PC soft-fades out audio buffer, displays "Reconnecting...". When Wi-Fi restores, stream automatically resumes from last position. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-12** | **Opus Adaptive Fallback** | 1. Introduce heavy network congestion/distance from router during uncompressed 24-bit/96kHz cast. | UI quality badge transitions to "Opus 320 kbps (Adaptive)"; playback stays smooth without buffer runout. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-13** | **Playback Handoff** | 1. Play track on phone at 1:45.<br>2. Open track menu &rarr; select *Handoff to PC*. | Phone stops playback; PC takes ownership of session, loads queue, and begins playing from 1:45 using its local decoder and sound card. | `implemented, automated tests pass, human check pending` | | | | |

---

## 3. Remote Control, Mirror & Library Browsing Tests

| ID | Feature / Case | Step-by-Step Test Instructions | Expected Result | Status | Result (Pass/Fail) | Tester | Date | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NC-14** | **Remote Control** | 1. From phone, open *Remote Control* for PC.<br>2. Tap Next, Previous, Volume slider, Shuffle, Repeat. | PC immediately responds to every command. UI updates synchronously on both screens. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-15** | **Now Playing Mirror** | 1. On PC, click *Mirror Phone Screen* from Device Connect pill.<br>2. Play different songs on phone. | PC displays full-screen Now Playing mirror showing phone's cover art, synced lyrics, and visualizer in read-only mode. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-16** | **Remote Library Browse** | 1. On PC, open Library &rarr; select *Phone Library* tab.<br>2. Browse remote albums and artists.<br>3. Click a remote track. | PC browses phone library metadata and artwork thumbnails smoothly. Clicking a track streams audio from phone. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-17** | **Universal Search** | 1. Type search query in PC search capsule with phone connected. | Results display merged songs grouped by device: `[This PC]` and `[Phone]`. Clicking phone result initiates streaming. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-18** | **Third-Party Sessions** | 1. Play Spotify or YouTube Music on phone with notification listener permission granted.<br>2. Open PC player deck. | PC displays Spotify track title, artist, album art, and controls Play/Pause/Skip without audio capture. | `implemented, automated tests pass, human check pending` | | | | |

---

## 4. Sync Center & Transfer Rail Tests

| ID | Feature / Case | Step-by-Step Test Instructions | Expected Result | Status | Result (Pass/Fail) | Tester | Date | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NC-19** | **Playlist Sync (Bidirectional)**| 1. Create playlist "Favorites 2026" on phone with 5 tracks.<br>2. In Sync Center, tap *Sync Now*.<br>3. Check PC Library &rarr; Playlists. | "Favorites 2026" appears on PC. Matching tracks link to local files; missing tracks appear as dimmed "Unresolved". | `implemented, automated tests pass, human check pending` | | | | |
| **NC-20** | **EQ Profile Transfer** | 1. Create custom EQ preset "Acoustic Warmth" on PC.<br>2. Open Sync Center &rarr; send EQ profiles to Phone. | "Acoustic Warmth" appears in phone Equalizer preset dropdown with exact 10-band slider gains. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-21** | **Listening Stats Merge** | 1. Play 10 songs on phone while offline for 2 days.<br>2. Connect phone to PC and trigger Sync. | PC listening history appends the 10 unique plays. Top artists and play counts increase accurately with zero double-counting. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-22** | **Conflict Resolution** | 1. Edit playlist "Chill" on PC and phone independently while disconnected.<br>2. Reconnect and trigger Sync. | Sync Center prompts with conflict dialog ("Keep Mine", "Keep Theirs", "Keep Both"). Selection applies cleanly without data corruption. | `implemented, automated tests pass, human check pending` | | | | |
| **NC-23** | **Undo Last Sync** | 1. Complete a sync that modifies playlists.<br>2. In Sync Center, click *Undo Last Sync*. | Database rolls back to pre-sync snapshot. Previous playlists restored identically. | `implemented, automated tests pass, human check pending` | | | | |

---

## 5. Windows UI Overhaul & Reference Design Tests

| ID | Feature / Case | Step-by-Step Test Instructions | Expected Result | Status | Result (Pass/Fail) | Tester | Date | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **UI-01** | **16:9 Expanded Layout** | 1. Launch Windows app in 1080p / 1440p (16:9 widescreen).<br>2. Resize window smoothly from minimum size (1024x600) to full screen. | Layout matches reference image cleanly: Left vertical rail, top search + device pill, main content area, pinned bottom player deck. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-02** | **Lens Dock Glide & Drag** | 1. Hover and click between *Home*, *Library*, *Settings* on left rail.<br>2. Click and drag the convex bubble between tabs.<br>3. Use keyboard hotkeys <kbd>Ctrl+1</kbd>, <kbd>Ctrl+2</kbd>, <kbd>Ctrl+3</kbd>. | Bubble glides with fluid spring physics matching mobile lens dock, displaying specular highlight, chromatic edge, and underlying magnification. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-03** | **For You Mix Cards** | 1. Open Home view on desktop.<br>2. Inspect *FOR YOU* row. | Displays wide horizontal mix cards (e.g. Pop Mixes, Cinematic Mixes, Electronic Mixes) built from local library with album art collages. Cards lift slightly on hover. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-04** | **Rediscover Section** | 1. Scroll down Home view to *REDISCOVER*. | Shows tracks not played recently with real history badges ("Last played 6 months ago", "Old favorite"). Horizontal mouse wheel scrolls row. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-05** | **Waveform Player Deck** | 1. Play track with cached peak data.<br>2. Observe bottom player deck. | Displays true waveform seekbar with played/unplayed shading. If peaks not yet calculated, shows plain clean seekbar without fake drawings. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-06** | **Mouse Wheel Seek on Deck** | 1. Place mouse cursor over waveform seekbar.<br>2. Scroll mouse wheel UP, then DOWN. | Wheel UP seeks forward $+5\text{s}$; wheel DOWN seeks backward $-5\text{s}$. Time labels update in real time. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-07** | **Quality Badge Truthfulness** | 1. Play a 24-bit/48kHz FLAC file.<br>2. Inspect quality badge on right side of player deck.<br>3. Enable Equalizer or Reverb. | Badge reads "FLAC 24-bit / 48kHz". Clicking badge opens Signal Path showing exact source format, DSP status, and WASAPI output sink. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-08** | **Device Connect Pill & Modal** | 1. Click *Device Connect* pill in top right bar. | Glass modal opens displaying local node status, paired devices, "Cast to...", pairing button, and link to Sync Center. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-09** | **Desktop Settings Hub** | 1. Click *Settings* on left rail.<br>2. Search for a setting.<br>3. Open *Audio Routing & DAC Output*, *DSP & Equalizer*, *Appearance & Glass*, *General*. | Settings match mobile 2-column glass card grid. Real-time search filters cards. All existing v1.5.0 settings remain fully reachable. | `implemented, automated tests pass, human check pending` | | | | |
| **UI-10** | **Reduced Motion Compliance** | 1. Enable "Reduce animations" in Windows Settings.<br>2. Interact with tabs, cards, and player deck. | Animations instantly switch to fast cross-fades without bouncy springs or parallax motions. | `implemented, automated tests pass, human check pending` | | | | |

---

*Test sheet created by Node Player Core Team. Results must be populated by the human tester.*
