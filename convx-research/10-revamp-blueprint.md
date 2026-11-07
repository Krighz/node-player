# 10-revamp-blueprint.md — Complete AI Implementation Guide: Optical LiquidGlass, iOS Floating Dock & YouTube Music Revamp

> **Target Codebase:** Node Player (`D:\AI MAKING\node-player`)  
> **Reference Systems:** Convx (`D:\Convx-main`) & User Reference Mockups (`Aura` Desktop + `For You` Mobile)  
> **Target Audience:** Autonomous Coding Agent / Next Development Session  
> **Core Architecture:** Unified Cross-Platform LiquidGlass (Android AGSL + Windows Skia SkSL)  
> **Scope:** Complete Single-File Master Guide explaining *What to change*, *How everything works*, and *Exact step-by-step implementation tasks*.

---

## 1. Visual Design Synthesis from User Reference Images

The user provided two design mockups defining the target aesthetic for the revamp:
1. **Windows Desktop Reference (`Aura`):**
   - **Header Bar:** Sleek top search bar (`Ctrl K`), audio output selector (`WASAPI EXCLUSIVE 32-BIT / 192kHz`, `Sennheiser HD 800 S`), and user profile avatar.
   - **Vertical Floating Optical Glass Rail (Left):** Compact vertical pill dock (`Home`, `Library`, `Radio`, `Parametric`, `Settings`) with an interactive sliding selection puck and frosted glass backing.
   - **Curated Top Row Cards:** Glass cards for *Pop Mixes*, *Cinematic Mixes* (Spatial Atmos), *Electronic Mixes* (Lossless Stems), and *Master Audio Mixes* with clean category tags.
   - **Horizontal Shelves:** *Rediscover* and *Recently Played* with squircle artwork tiles, play count badges (`Old favorite`, `Last played 6m`), title, artist, and direct master stream queues.
   - **Bottom Glass Player Deck:** Translucent glass bar floating at the bottom, housing track metadata (`MASTER` badge), heart favorite, playback controls, live real-time spectrogram/waveform seekbar, time elapsed/remaining, and hi-res format pills (`FLAC 24-bit / 48kHz`).

2. **Mobile Android Reference (`For You`):**
   - **Top Header:** Clean modern title with `HI-RES` badge, Google Cast / stream icon, and avatar.
   - **Shortcuts Grid (Direct Engine):** 2x2 grid of rounded glass pills (*Offline Mix*, *Daily Radar*, *Hi-Res DAC*, *Radio DJ*).
   - **Recently Played Shelf:** Horizontal cards with play badges (`11x plays`, `8x plays`) and vibrant glowing artwork.
   - **Heavy Rotation List:** Grouped card list items (*Watson*, *Ambitionz Az a Ridah*, *Africa*) with track thumbnails, play counters, and overflow action menus.
   - **Soundscapes & Genres:** Dual card banners (*Cyber Synth*, *Deep Ambient*) with gradient fills.
   - **Floating Mini-Player & iOS Bottom Dock:**
     - Docked mini-player pill hovering above the bottom bar with thumbnail, title, artist, animated playing equalizer bars, play/pause, and skip buttons.
     - **iOS-style Floating Bottom Dock:** Rounded continuous capsule housing tabs (`For You`, `Library`, `Equalizer`, `Settings`) with a physical sliding translucent puck beneath the active tab.

---

## 2. Core Architectural Pillars

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Node Player UI Architecture                     │
├────────────────────────────────────────────────────────────────────────┤
│  Layer 1: Animated Procedural Background (LiquidGlassBackground.kt)    │
│           • 60/120Hz procedural mesh / aurora / caustics               │
│           • Quantized loop-bucket frame caching (zero blur repeats)    │
├────────────────────────────────────────────────────────────────────────┤
│  Layer 2: Shared 0.35x Downscaled Blur Pool (SharedGlassPool.kt)       │
│           • Zero-copy GPU GraphicsLayer capture at 0.35x resolution    │
│           • 88% reduction in pixel fill rate                           │
│           • Gesture scroll-freeze (47ms -> 1.9ms per frame)            │
├────────────────────────────────────────────────────────────────────────┤
│  Layer 3: Multi-Shape Single-Pass SDF Glass Shader                     │
│           • Evaluates MiniPlayer + Bottom Dock + Floating Rail in ONE  │
│             single GPU draw pass using Signed Distance Fields (SDF)    │
│           • Android: AGSL RuntimeShader | Windows: Skia SkSL Effect    │
├────────────────────────────────────────────────────────────────────────┤
│  Layer 4: Interactive iOS Floating Dock & Draggable Puck               │
│           • Continuous squircle geometry (ContinuousRoundedRectangle)  │
│           • Spring physics (critically damped, Spring.StiffnessMedium) │
│           • Finger-tracking InteractiveHighlight glow                  │
├────────────────────────────────────────────────────────────────────────┤
│  Layer 5: InnerTube YouTube Music Online Streaming Engine              │
│           • Multi-client rotation: ANDROID_VR (fast, zero poToken)     │
│           • Multiplatform cipher deobfuscation + n-param transform     │
│           • Lazy ResolvingMediaSource in ExoPlayer (Android) & FFmpeg  │
│             streaming decoder (Windows)                                │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Pillar A: The High-Performance LiquidGlass Engine

### 3.1 The 4-Tier Lag Elimination Pipeline
To stop the phone and Windows app from lagging when the procedural background animates:
1. **Downscaling (`0.35x` Default + User Slider):**  
   Capture the screen into an offscreen buffer at $0.35\times$ resolution. Blur the low-res buffer and upscale it with bilinear filtering. The blur completely conceals the upscaling.
2. **Single-Pass Multi-Shape Shader (`MultiRefraction`):**  
   Instead of the Mini-Player and the Bottom Dock running independent blur passes (which costs multiple offscreen render passes), both components register their bounding rects into `SharedGlassPool`. A single AGSL/SkSL shader loops through the rects and evaluates optical SDF refraction in **one single draw call**.
3. **Gesture Backdrop Freezing (`ScrollFreeze`):**  
   Attach a `NestedScrollConnection` to all LazyColumns. While the user is dragging or flinging a list, freeze the backdrop capture and replay the last texture. RenderThread time drops from **47ms to 1.9ms per frame**.
4. **Loop-Bucket Quantization (`LoopBucketCache`):**  
   Quantize continuous background time into 100ms buckets. Cache blurred frames in an LRU `GraphicsLayer` pool. After the background loops once, **zero GPU blurs are calculated**.

### 3.2 The Multi-Shape SDF Shader (AGSL for Android & SkSL for Windows Skia)
Both platforms will compile this exact mathematical logic:

```glsl
// Up to 8 rounded rectangles evaluated in ONE pass
uniform shader content;
uniform float4 rects[8];      // x1, y1, x2, y2 per shape
uniform float4 radii[8];      // top-left, top-right, bottom-right, bottom-left
uniform float4 lens[8];       // refractionHeight, refractionAmount, depthEffect, unused
uniform int count;

float radiusAt(float2 coord, float4 r) {
    if (coord.x >= 0.0) return (coord.y <= 0.0) ? r.y : r.z;
    return (coord.y <= 0.0) ? r.x : r.w;
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    return length(max(cornerCoord, 0.0)) - radius + min(max(cornerCoord.x, cornerCoord.y), 0.0);
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        float4 r = rects[i];
        if (coord.x >= r.x && coord.y >= r.y && coord.x <= r.z && coord.y <= r.w) {
            float2 halfSize = (r.zw - r.xy) * 0.5;
            float2 centeredCoord = coord - (r.xy + halfSize);
            float radius = radiusAt(centeredCoord, radii[i]);
            float refractionHeight = lens[i].x;
            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            
            if (-sd >= refractionHeight) {
                return content.eval(coord); // Flat glass core
            }
            sd = min(sd, 0.0);
            float d = circleMap(1.0 - -sd / refractionHeight) * lens[i].y;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
            if (lens[i].z > 0.5) {
                grad = normalize(grad + normalize(centeredCoord));
            }
            return content.eval(coord + d * grad);
        }
    }
    return content.eval(coord);
}
```

---

## 4. Pillar B: The iOS Floating Tab Bar & Interactive Dock

### 4.1 Component Structure
Port the floating tab bar architecture from Convx (`app/.../floatingtabbar/` and `hig/`):
- **Continuous Squircle Outline:** Built using `ContinuousRoundedRectangle(AppleTokens.CardCornerLarge)`.
- **Sliding Selection Puck:**
  - An animated glass capsule positioned behind the active tab icon and label.
  - As the user taps or drags between tabs, the puck moves using **critically damped spring physics** (`Spring.DampingRatioNoBouncy`, `Spring.StiffnessMediumLow`).
  - Puck wash adapts to the theme: translucent white wash on dark mode, dark tint on light mode.
- **Finger-Tracking Glow (`InteractiveHighlight`):**
  - While touching the bar, a soft radial specular highlight follows the touch position across the glass surface.
- **Accessory Stacking:**
  - On Mobile: The `FrostedMiniPlayer` sits directly above the `FloatingNavBar` with matching corner radii and margins.
  - On Windows: Adapts into the **Vertical Optical Rail Dock** shown on the left side of the `Aura` desktop mockup.

---

## 5. Pillar C: InnerTube & YouTube Music Streaming Engine

### 5.1 Architecture & Flow
- **Ktor Multiplatform Engine:** Placed in `core-network/src/commonMain/kotlin/com/nodeplayer/network/innertube/`.
- **Client Profile:** Uses `ANDROID_VR_1_43_32` headers as primary to stream direct Opus/AAC streams without requiring Google's Proof of Origin (`poToken`).
- **Lazy Resolution (`ResolvingMediaSource`):**
  - Android ExoPlayer loads lightweight tracks containing the 11-char `videoId`.
  - When the song is about to buffer, `ResolvingMediaSource` fetches the live stream URL asynchronously.
  - Downloaded audio is directed through `CacheDataSource` into Node Player's disk cache.
- **Multiplatform Cipher & Throttling Solver:**
  - If a track provides `signatureCipher` or throttled `?n=...` parameter:
    - Android: Evaluates YouTube's active `player.js` in a headless `WebView`.
    - Windows Desktop: Evaluates `player.js` in an embedded QuickJS engine or JVM script engine.
- **Desktop Streaming:** `FFmpegAudioDecoder.kt` and `WindowsPlaybackEngine.kt` accept the resolved HTTPS stream URL directly.

---

## 6. Exact File-by-File Change Plan for the AI Developer

### 1. `core-ui` (Multiplatform Shared UI)
- **New File:** `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/theme/ContinuousShapes.kt`  
  *Action:* Implement continuous curvature squircle shapes (`ContinuousRoundedRectangle`).
- **New File:** `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/SharedGlassPool.kt`  
  *Action:* Create the shared downscaled blur buffer, multi-shape geometry registration, and scroll freeze connection.
- **Update:** `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/LiquidGlassBackground.kt`  
  *Action:* Add loop-bucket frame caching to prevent re-rendering identical animation cycles.
- **New File:** `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/FloatingTabBar.kt`  
  *Action:* Implement the iOS-style floating tab bar with draggable selection puck, critically damped springs, and finger-tracking specular glow.
- **Update:** `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/screens/HomeView.kt`  
  *Action:* Revamp layout to match the provided mockups (curated mixes row, rediscovery shelf with play badges, soundscape cards, hi-res status pills).

### 2. `core-network` (InnerTube & Streaming)
- **New File:** `core-network/src/commonMain/kotlin/com/nodeplayer/network/innertube/InnerTubeClient.kt`  
  *Action:* Implement Ktor HTTP client configured for YouTube Music InnerTube endpoints (`search`, `player`, `next`, `browse`).
- **New File:** `core-network/src/commonMain/kotlin/com/nodeplayer/network/innertube/StreamResolver.kt`  
  *Action:* Implement multi-client stream resolution (`ANDROID_VR` primary, `IOS`/`VISIONOS` fallback), format selection (Opus 160k / AAC 128k), and caching.
- **New File:** `core-network/src/commonMain/kotlin/com/nodeplayer/network/cipher/CipherSolver.kt`  
  *Action:* Cross-platform signature deobfuscation and `n`-parameter transform solver.

### 3. `app-android` (Android Implementation)
- **Update:** `app-android/src/main/kotlin/com/nodeplayer/music/ui/components/GlassRefractionShader.kt`  
  *Action:* Upgrade AGSL shader to the multi-shape SDF shader (`MultiRefraction`) receiving up to 8 rects from `SharedGlassPool`.
- **New File:** `app-android/src/main/kotlin/com/nodeplayer/music/service/ResolvingMediaSource.kt`  
  *Action:* Media3 `CompositeMediaSource` resolving YouTube stream URLs on-demand before feeding ExoPlayer.
- **Update:** `app-android/src/main/kotlin/com/nodeplayer/music/MainActivity.kt`  
  *Action:* Replace legacy bottom dock with the new `FloatingTabBar` and integrate the `SharedGlassPool` container.

### 4. `app-desktop` (Windows Desktop Implementation)
- **New File:** `app-desktop/src/main/kotlin/com/nodeplayer/desktop/ui/SkiaGlassShader.kt`  
  *Action:* Implement the SkSL multi-shape SDF shader using Skia `RuntimeEffect`.
- **Update:** `app-desktop/src/main/kotlin/com/nodeplayer/desktop/Main.kt`  
  *Action:* Render the vertical floating glass navigation rail on the left and bottom glass player deck as shown in the `Aura` desktop mockup.
- **Update:** `app-desktop/src/main/kotlin/com/nodeplayer/desktop/audio/WindowsPlaybackEngine.kt`  
  *Action:* Support streaming network URLs through `FFmpegAudioDecoder`.

---

## 7. Verification & Definition of Done for Future Agent

1. **Optical Glass Performance:**
   - On Android: Scrolling a LazyColumn with animated background maintains $>110$ fps on 120Hz panels. `adb shell dumpsys gfxinfo` reports $<2\%$ janky frames.
   - On Windows: CPU usage under animated background remains $<5\%$.
2. **UI & Dock Interaction:**
   - The iOS bottom dock features an interactive sliding puck moving with critically damped spring motion.
   - The home screens on Android and Desktop match the visual layout of the reference mockups (*Curated Mixes*, *Rediscover*, *Heavy Rotation*, *Soundscapes*).
3. **YouTube Music Streaming:**
   - Search query returns YouTube Music tracks with artwork.
   - Tapping an online track resolves and starts playing audio within $<800$ ms.
   - Streamed tracks automatically cache to disk for offline replay.
