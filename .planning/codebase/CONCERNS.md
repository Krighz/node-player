<!-- refreshed: 2026-11-11 -->
# Codebase Concerns, Risks & Technical Debt

**Analysis Date:** 2026-11-11

## Tech Debt

**Desktop Audio Decoding vs External FFmpeg Subprocess:**
- Issue: `CompositeAudioDecoder` delegates AAC, ALAC, Opus, and OGG formats on Windows to `FFmpegAudioDecoder`, which executes a CLI `ProcessBuilder("ffmpeg", ...)` per stream invocation. If FFmpeg is not installed or discoverable on the user's `PATH`, playback for non-MP3/FLAC/WAV audio files silently fails to open.
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/CompositeAudioDecoder.kt`, `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/FFmpegAudioDecoder.kt`
- Impact: Non-technical users installing the Windows MSI or ZIP package without a system-wide FFmpeg binary cannot play modern AAC/M4A/Opus tracks. Additionally, `FFmpegAudioDecoder.seekTo()` returns `false` (unsupported), meaning seeking in FFmpeg-decoded files is completely non-functional on desktop.
- Fix approach: Bundle a statically linked FFmpeg shared library via JNA/JavaCPP or integrate standard cross-platform multi-format decoders (e.g., native libavformat / libavcodec JNI or Vorbis/Opus Java decoders). Implement ffmpeg `-ss` argument in seek or stream offset handling.

**Desktop Track Repository Incomplete Implementations:**
- Issue: `DesktopTrackRepositoryAdapter` leaves several CRUD functions as no-ops or dummy returns (`incrementPlayCount`, `setFavorite`, `insertScannedFolders`, `setFolderEnabled`, `setTracksHiddenForFolder`, `updateTrackAudioAnalysis`, `recordTrackPlayed`).
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/data/DesktopTrackRepositoryAdapter.kt`
- Impact: While `DesktopTrackDatabase` contains columns and tables for favorites, play counts, and audio analysis, UI interactions or background analysis that call through `TrackRepository` on desktop do not mutate or persist favorite toggles, play count statistics, or audio analysis metrics into `library.db`.
- Fix approach: Implement corresponding SQL update queries in `DesktopTrackDatabase` (`UPDATE tracks SET is_favorite = ? WHERE id = ?`, `UPDATE tracks SET play_count = play_count + 1 WHERE id = ?`) and hook them up in `DesktopTrackRepositoryAdapter`.

**Hardcoded Fallback Secret in Production Source:**
- Issue: `FALLBACK_API_SECRET` ("0d6137535634972ec6758055afd0959e") is hardcoded directly into `LastFmClient.kt` as a default fallback when `LASTFM_API_SECRET` is blank in build configuration.
- Files: `app-android/src/main/kotlin/com/nodeplayer/music/library/LastFmClient.kt`
- Impact: Source code exposure of API secret in public version control; can lead to quota exhaustion or revocation by Last.fm if abused.
- Fix approach: Remove the hardcoded secret string literal. Require explicit user key entry or inject secrets strictly during CI/CD build pipelines through Gradle BuildConfig fields.

**Android Keystore Hardcoded Path Fallback in Gradle:**
- Issue: In `app-android/build.gradle.kts`, `signingConfigs.release` specifies hardcoded local user path `File("C:/Users/satya/Documents/node-player-signing/signing.properties")`.
- Files: `app-android/build.gradle.kts`
- Impact: Non-portable build script; leaks local developer filesystem layout and fails cleanly on other development machines or CI runners without environment variables.
- Fix approach: Rely exclusively on standard environment variables (`NODEPLAYER_SIGNING_PROPS`, `KEYSTORE_FILE`) or Gradle project properties (`-PsigningProps=...`).

---

## Known Bugs

**FFmpeg Audio Decoder Cannot Seek:**
- Symptoms: When playing AAC, ALAC, OGG, or Opus tracks on Windows Desktop, seeking via timeline scrub or keyboard shortcuts (`Left`/`Right` arrow) fails to seek and track restarts or ignores seek request.
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/FFmpegAudioDecoder.kt`
- Trigger: Dragging the seek bar or pressing seek keys while playing an FFmpeg-routed format on Windows.
- Workaround: Listen without seeking, or transcode files to FLAC, WAV, or MP3.

**FLAC Desktop Seeking Performance on Large Lossless Files:**
- Symptoms: Seeking to the end of a long FLAC track (e.g., 20+ minute live recordings or symphonies) causes noticeable audio freeze / UI delay on desktop.
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/FlacAudioDecoder.kt` (lines 183-195)
- Trigger: In `FlacAudioDecoder.seekTo()`, seeking requires reopening the stream from byte 0 and iteratively decoding all FLAC frames via `dec.readNextFrame()` until `currentSample + 4096 < targetSample`.
- Workaround: None without native seek table parsing.

---

## Security Considerations

**Node Connect Simple Security Mode Permissive Trust:**
- Risk: In default `SecurityLevel.SIMPLE`, Node Connect utilizes `PermissiveTrustManager` which accepts any self-signed X.509 certificate without certificate pinning or dynamic PIN verification. On shared or public Wi-Fi networks (e.g. coffee shops, dorms, transit), an active attacker performing ARP spoofing or DNS redirection can man-in-the-middle Node Connect control commands, audio stream packets, and file transfer payloads.
- Files: `core-network/src/main/kotlin/com/nodeplayer/network/crypto/PinningTrustManager.kt`, `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt`, `NODE_CONNECT_SECURITY.md`
- Current mitigation: Node Connect prompts the user with an "Allow <Device> to connect?" dialog (displaying device name and ID) before establishing session, unless "Trust all devices on my network" is toggled on. Clear security warnings are documented in `NODE_CONNECT_SECURITY.md` advising users to switch to Strict Mode on public Wi-Fi.
- Recommendations: Promote Strict Mode pairing (QR / PIN code fingerprint exchange) as primary flow or enforce subnet restriction checks for non-RFC1918 private subnets.

**Unsigned Windows Packages Triggering SmartScreen:**
- Risk: Windows distributables (`NodePlayer.exe`, `NodePlayer-1.5.0.msi`) are not Authenticode code-signed with a trusted Extended Validation (EV) certificate.
- Files: `dist/NodePlayer.exe`, `dist/NodePlayer-1.5.0.msi`, `app-desktop/build.gradle.kts`
- Current mitigation: Authoritative SHA-256 checksums are published in release notes.
- Recommendations: Integrate SignPath or hardware EV code-signing in release build workflow to prevent SmartScreen untrusted publisher warnings and potential executable tampering.

---

## Performance Bottlenecks

**Desktop Audio Resampling in WasapiAudioOutput:**
- Problem: `WasapiAudioOutput.resampleBuffer()` performs manual linear interpolation on 32-bit float audio chunks in memory when hardware output rate (e.g., 48000 Hz) does not match source rate (e.g., 44100 Hz or 96000 Hz).
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/WasapiAudioOutput.kt` (lines 134-178)
- Cause: Software sample-by-sample linear interpolation introduces interpolation error (high-frequency roll-off and aliasing distortion) compared to polyphase sinc or bandlimited resamplers, while allocating a new `FloatArray(outSamples)` on every audio write cycle.
- Improvement path: Pre-allocate reusable float buffers to avoid garbage collection churn on the real-time audio thread, and integrate a high-grade polyphase sinc resampler (such as libsamplerate / SRC) for audiophile resampling.

**Synchronous Artwork Base64 Encoding During Track Start:**
- Problem: When `onTrackStartedCallback` fires on desktop, `getDesktopTrackThumbnailBase64` extracts image bytes from disk or cache and encodes to Base64 to broadcast control events and start cast streams.
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt` (lines 41-59, 178-204)
- Cause: Large embedded album art (2MB+ hi-res PNGs/JPEGs) can produce huge Base64 strings, triggering transient memory allocation and network packet chunking overhead.
- Improvement path: Downsample artwork bitmaps before Base64 serialization, or transfer artwork on-demand via the dedicated `ARTWORK_QUERY` / `ARTWORK_RESULT` channel (`0x0022` / `0x0023`) rather than inlining into control state packets.

---

## Fragile Areas

**Dual ExoPlayer Handshake & Crossfade Race Conditions:**
- Files: `app-android/src/main/kotlin/com/nodeplayer/music/audio/CrossfadePlayerManager.kt`
- Why fragile: Coordinating two concurrent `ExoPlayer` instances (`playerA` and `playerB`), swapping MediaSession active player references, and synchronizing independent DSP processors (`DspAudioProcessor`) during seeking, rapid skipping, or playlist mutation can cause race conditions or state desynchronization (e.g., volume ramps getting interrupted mid-crossfade).
- Safe modification: When modifying crossfade math or track transition logic, verify with `app-android/src/androidTest/kotlin/com/nodeplayer/music/CrossfadeIntegrationTest.kt` and `CrossfadeLivePlaybackTest.kt`.
- Test coverage: Android instrumentation tests exist for standard transitions, but rapid user scrub during active crossfade remains sensitive.

**JitterBuffer Lock Contention Under Network Fluctuations:**
- Files: `core-network/src/main/kotlin/com/nodeplayer/network/cast/JitterBuffer.kt`
- Why fragile: `JitterBuffer` uses a `ReentrantLock` shared between the network receiver coroutine (`push`) and the audio sink hardware thread (`read`). If the network thread experiences bursts or lock contention, the audio consumer thread can miss its deadline, causing underrun pops.
- Safe modification: Ensure lock hold time remains minimal (no memory allocations or logging inside `lock.withLock`). Consider lock-free ring buffers (single-producer single-consumer circular buffer).
- Test coverage: Tested via `AudioCastIntegrationTest.kt`.

---

## Scaling Limits

**Desktop SQLite Batch Track Ingestion:**
- Current capacity: Catalogues tens of thousands of tracks smoothly using `DesktopTrackDatabase.insertOrUpdateTracks()` with prepared batch statements.
- Limit: `DesktopTrackDatabase.getAllTracks()` reads the entire `tracks` table into an in-memory `List<Track>` on every query without cursor pagination or `LIMIT`/`OFFSET` clauses.
- Scaling path: For massive libraries (> 100,000 tracks), implement paging with Compose Multiplatform lazy list virtual scrolling or SQLite cursor streams.

**Node Framing In-Memory Frame Allocations:**
- Current capacity: `NodeFraming.MAX_PAYLOAD_SIZE` allows frames up to 4 MB.
- Limit: Each frame allocates a full byte array (`ByteArray(payloadLength)`). Multiple concurrent file sync operations or album art queries can cause significant heap allocation spikes.
- Scaling path: Stream large file sync payloads in chunked buffers (e.g., 64KB blocks) rather than allocating entire files into single framed messages.

---

## Dependencies at Risk

**javazoom:jlayer (v1.0.1):**
- Risk: Mature, unmaintained library (last release over 15 years ago). Lacks modern SIMD optimizations and has edge cases with certain non-standard ID3v2 tags or variable bitrate (VBR) Xing headers.
- Impact: Potential decoding glitches on malformed MP3s.
- Migration plan: Mitigated by `AudioHeaderParser` handling the header parsing before handoff; long-term alternative is native FFmpeg or modern Kotlin MP3 decoder.

**org.jflac:jflac-codec (v1.5.2):**
- Risk: Pure Java FLAC port with limited maintenance. Does not support native seek table seeking efficiently without reading frames.
- Impact: Higher CPU usage during initial frame synchronization and slow seeking on huge FLAC files.
- Migration plan: Enhance seeking with FLAC `SEEKTABLE` binary parsing or utilize libFLAC via JNA.

---

## Missing Critical Features

**Desktop Track Metadata Tag Editing & Playlist Persistence:**
- Problem: `DesktopTrackRepositoryAdapter` does not persist custom playlists or user tag edits into `DesktopTrackDatabase`.
- Blocks: Users on Windows cannot create, modify, or save custom playlists that survive application restarts.

**Desktop Direct Wasapi Exclusive Audio Hardware Selection:**
- Problem: Desktop audio engine routes through JavaSound `AudioSystem.getLine()` wrapper rather than direct native WASAPI COM interfaces (`IAudioClient`, `IAudioRenderClient`).
- Blocks: True bit-perfect hardware sample rate lock and exclusive device enumeration on non-default soundcards without JVM layer resampling.

---

## Test Coverage Gaps

**Desktop Audio Decoding Pipeline Unit Tests:**
- What's not tested: End-to-end decoding and seeking of `Mp3AudioDecoder`, `FlacAudioDecoder`, `JvmAudioDecoder`, and `FFmpegAudioDecoder` under corrupted streams or unexpected stream truncations.
- Files: `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/`
- Risk: Decoder hangs or unhandled exceptions when encountering corrupt audio files from scanned music libraries.
- Priority: High

**Node Connect Network Failure & Auto-Reconnect Scenarios:**
- What's not tested: Handling of sudden Wi-Fi disconnections, IP address changes, and mid-stream audio cast interruptions during physical device roaming.
- Files: `core-network/src/main/kotlin/com/nodeplayer/network/service/NodeConnectHub.kt`, `core-network/src/main/kotlin/com/nodeplayer/network/cast/NodeConnectCastService.kt`
- Risk: Cast playback may hang silently or leave orphan sockets without restoring audio output to local speakers.
- Priority: Medium

---

*Concerns audit: 2026-11-05*
