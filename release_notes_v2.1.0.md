## Node Player v2.1.0 — Google Drive Cloud Sync & Landscape FullScreen Lyrics

### 🌟 What's New in v2.1.0

#### 1. Opt-In Privacy-First Google Drive Cloud Backup & Sync
- **Zero Developer Servers / Complete Privacy**: Sync your music library directly to your personal Google Drive. No developer-hosted server or database is required or involved.
- **Strict Scope Isolation (`appDataFolder`)**: Uses Google OAuth 2.0 PKCE with exclusively the `https://www.googleapis.com/auth/drive.appdata` scope. Files are stored in Google Drive's hidden `appDataFolder` (`spaces=appDataFolder`). Node Player never accesses, sees, or touches your personal Drive files.
- **Smart Merge Conflict Resolution**: Deterministic sync snapshot merging ensures multi-device consistency without accidental data loss (newest timestamp wins for playlists and favorites, highest listening time and play count win for song stats, playback history deduplicated and trimmed).
- **WorkManager Background Engine**: Scheduled background sync with configurable frequency (**Every Hour**, **Once a Day**, or **Manual Only**) and a dirty-flag optimizer that skips uploads when no songs have been played or playlists modified.
- **Seamless Settings Integration**: Located under **Settings > Online Music & Streaming Services > Cloud Sync**. Includes sign-in/sign-out, sync frequency selector, real-time "Sync Now" button, last synced timestamp, and "Delete Cloud Backup" option.
- **Hardware Security**: Protected via Android Keystore AES-256-GCM encrypted token storage.

#### 2. Landscape Two-Column FullScreen Lyrics & Shapeable Album Art
- **Responsive Split Landscape Layout**: On tablets, car head-units, and landscape phones, enjoy a dedicated two-column view with an album art hero and transport controls on the left, and synchronized lyrics on the right.
- **4.5s Auto-Collapsing Transport Controls**: Inactivity timer smoothly tucks left transport controls into the display edge during playback for an unobstructed visual canvas. Tap anywhere or hit the left floating chevron to expand.
- **Shapeable Album Art Customizer**: Switch between **Squircle**, **Circle**, and custom Bezier **Heart** shapes via the top-right `( ⋮ )` menu.
- **Edge-to-Edge Cutout Immersion**: Added `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` and "Hide Status Bar" toggles for edge-to-edge cinematic playback without black bars or letterboxing around camera cutouts.
- **Selectable Lyrics**: Full text selection enabled across synchronized lyrics so you can copy and share lines easily.

#### 3. Multi-Provider Synced Lyrics & Offline Romanization
- **Musixmatch & Unison Integration**: Expanded lyrics search network with duration tolerance matching and visual status indicators (Searching, Found, Estimated).
- **Offline Phonetic Romanization**: Embedded multi-script romanization engine for Japanese, Korean, Chinese (Pinyin), Hindi, Cyrillic, Greek, and Arabic with dual-line display support.

#### 4. Audiophile Engine & UI Fixes
- **Genuine Listening Metrics**: Removed placeholder fallback calculations on the Library screen; now displays genuine real-time statistics from track play counts and daily listening history.
- **1200p Ultra-HD Cover Art**: High-resolution cover art upscaling pipeline for crystal-clear album covers.
- **Kinetic Sweep Playback Pause**: Pausing playback immediately freezes lyrics sweeping and dynamic background motion.
- **Float Audio Sink Clamping**: Hardware 32-bit float audio sink protection against PCM clipping on older Android devices.

---

### 📦 Release Assets & Checksums

| File | Size | SHA-256 Checksum |
| :--- | :--- | :--- |
| `NodePlayer-2.1.0-android.apk` | ~52.2 MB | `0dea3e9b6883494aec52346dd87285f0d7c05a9f35555ec91e8f1220a25138df` |
| `NodePlayer-2.1.0-windows-x64.zip` | ~125.2 MB | `405582564e4a1e452b339f39ce9b5240d6aaa6d85dfb1b7e95d4772328a62eb2` |
