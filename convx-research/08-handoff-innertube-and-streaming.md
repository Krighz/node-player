# 08-handoff-innertube-and-streaming.md — Developer Handoff: Implementing YouTube Music Streaming in Node Player

> **Target Project:** Node Player (`D:\AI MAKING\node-player`)  
> **Reference System:** Convx (`D:\Convx-main\innertube` & `YTPlayerUtils.kt`)  
> **Audience:** Next Development Session / Autonomous Coding Agent  
> **Mission:** Build and integrate YouTube Music online search, browsing, lazy stream resolution, cipher decryption, and ExoPlayer / Desktop playback into Node Player.

---

## 1. Executive Mission & Deliverables

The goal of this handoff is to enable Node Player to stream, search, and cache YouTube Music tracks seamlessly alongside its existing local audio library on both **Android** and **Windows Desktop**, while preserving its liquid glass aesthetic.

### Required Deliverables for Next Session:
1. **`core-network` KMP Module Expansion:**
   - InnerTube REST client communicating with `https://music.youtube.com/youtubei/v1/`.
   - Data models for `SearchResult`, `SongItem`, `AlbumPage`, `ArtistPage`, and `StreamFormat`.
2. **Multiplatform Stream Resolver (`StreamResolver`):**
   - Multi-client rotation (`ANDROID_VR` primary, `IOS` / `VISIONOS` fallback).
   - Cipher deobfuscation (`signatureCipher`) and `n`-parameter transformation.
3. **Lazy Playback Engine:**
   - Android: Media3 `ResolvingMediaSource` integrated into `PlaybackService.kt`.
   - Windows Desktop: URL streaming via `FFmpegAudioDecoder.kt`.
4. **UI Integration in `core-ui`:**
   - Search suggestion bar and results feed in `SearchCapsule.kt` and `LibraryView.kt`.
   - Stream audio quality badge (`OPUS 160k` / `AAC 128k`).

---

## 2. Technical Blueprint & Concrete Implementation Steps

### Task 1: InnerTube Client & Models in `core-network`
- **File:** `core-network/src/commonMain/kotlin/com/nodeplayer/network/innertube/InnerTubeClient.kt`
- **Responsibilities:**
  - Create a Ktor `HttpClient` configured with `ContentNegotiation` (Kotlinx Serialization JSON with `ignoreUnknownKeys = true`).
  - Configure default request headers mimicking `ANDROID_VR_1_43_32`:
    - `X-Goog-Api-Format-Version: 1`
    - `X-YouTube-Client-Name: 56`
    - `X-YouTube-Client-Version: 1.43.32`
    - `X-Origin: https://music.youtube.com`
    - `Referer: https://music.youtube.com/`
    - `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0`
- **Endpoints to implement:**
  - `/youtubei/v1/search` (Search queries with song/album filters)
  - `/youtubei/v1/player` (Playback format metadata extraction)
  - `/youtubei/v1/next` (Radio queue / related tracks)

### Task 2: Stream Resolution & Cipher Solver
- **File:** `core-network/src/commonMain/kotlin/com/nodeplayer/network/innertube/StreamResolver.kt`
- **Logic:**
  1. Post to `/player` with `videoId`.
  2. Parse `streamingData.adaptiveFormats`.
  3. Filter for audio formats: itag `251` (`audio/webm; codecs="opus"`, 160 kbps) or itag `140` (`audio/mp4; codecs="mp4a.40.2"`, 128 kbps).
  4. If direct `format.url` is present, return it.
  5. If `format.signatureCipher` is present:
     - On Android: Dispatch deciphering to an offscreen headless `WebView` or embedded JavaScript engine.
     - On Windows Desktop: Dispatch deciphering to embedded QuickJS or JVM script engine.
  6. Solve the `n`-parameter transform if URL contains `?n=...` to avoid 403 throttling.

### Task 3: Android Playback Integration (`app-android`)
- **File:** `app-android/src/main/kotlin/com/nodeplayer/music/service/ResolvingMediaSource.kt`
- **Pattern:** Re-implement the lightweight Media3 `CompositeMediaSource<Unit>` from Convx ([ResolvingMediaSource.kt:26-66](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/playback/ResolvingMediaSource.kt#L26-L66)):
  - Feed ExoPlayer with `MediaItem.fromUri("nodeplayer://youtube/$videoId")`.
  - When ExoPlayer prepares the track, resolve the live HTTPS streaming URL asynchronously on `Dispatchers.IO` and bind it to a `ProgressiveMediaSource` wrapped in `CacheDataSource` (so streamed songs are cached for offline repeat listening!).

### Task 4: Windows Playback Integration (`app-desktop`)
- **File:** `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/WindowsPlaybackEngine.kt`
- **Logic:**
  - In `CompositeAudioDecoder.kt`, detect network URIs (`http://` or `https://`).
  - Instruct `FFmpegAudioDecoder` to open the streaming protocol with an internal read-buffer.

### Task 5: UI & Search Integration in `core-ui`
- **Files:** `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/screens/HomeView.kt` and `SearchCapsule.kt`
- **Logic:**
  - Display search results with artwork, artist name, and duration.
  - Tapping a YouTube track triggers `viewModel.playTrack(...)` using the exact same flow as local tracks!

---

## 3. Risks, Pitfalls & Countermeasures

| Risk | Symptoms | Countermeasure Learned from Convx |
|---|---|---|
| **Google Bot Detection (HTTP 403)** | Streams fail to play or immediately return 403 Forbidden. | Fallback client rotation: If `ANDROID_VR` fails, immediately rotate to `IOS` or `VISIONOS` headers ([YTPlayerUtils.kt:298-319](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/utils/YTPlayerUtils.kt#L298-L319)). |
| **Stream Throttling (Buffer Stall)** | Audio plays for 15 seconds then buffers forever. | Apply `n`-parameter transform solver to unthrottle the URL download speed. |
| **URL Expiration** | Pre-resolved URLs fail after several hours. | Never pre-resolve URLs in queues; always resolve on-demand using `ResolvingMediaSource` when the song is about to buffer. |
| **Network Stalls during Fling** | UI stutters when scrolling search results while streaming. | Ensure all network calls and JSON decoding run strictly on `Dispatchers.IO`. |

---

## 4. Verification & Testing Checklist for the Builder Session

- [ ] Search query "Daft Punk" returns accurate YouTube Music tracks in Node Player UI.
- [ ] Tapping a track resolves an Opus/AAC stream within $< 800\text{ ms}$.
- [ ] ExoPlayer on Android successfully buffers and plays the stream.
- [ ] Windows Desktop FFmpeg decoder successfully plays the stream.
- [ ] Audio continues playing smoothly across song transitions in the queue.
- [ ] Streamed tracks are cached to disk and play offline on repeat.
- [ ] Glass dock and mini-player render smoothly over online artwork without frame drops.
