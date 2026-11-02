# Node Player - Project State

Last Updated: 2026-11-03 04:45:00 +05:30
Current Version: v1.3.0
Current Commit: `6f79e17`

---

## 1. What Is Built

### Core Architecture & Audio
- **Audio Engine**: 32-bit float audio pipeline, resampler options (Auto, 44.1k, 48k, 96k, 192k), gapless playback, crossfade manager.
- **DSP Chain**: 10-band equalizer, bass boost, virtualizer, reverb, loudness enhancer.
- **System-Wide EQ**: Background audio effect session interception via `AudioSessionReceiver` and `SystemWideEqService`.
- **MediaSession & Service**: AndroidX Media3 `MediaSessionService` (`PlaybackService`) with notification channel and lockscreen controls.

### UI & Styling (v1.3.0 Revamp)
- **Liquid-Glass Lens Dock**: Floating pill dock with magnifying bubble, specular rim, search action, and haptic feedback.
- **Background Shader Engine**: AGSL procedural backgrounds with 6 modes (Liquid blobs, Aurora, Mesh gradient, Album-art blur, Slow caustics, Static), speed 0.2x–3.0x, blue-noise dithering.
- **Static RemoteViews Widgets**: 4 launcher widgets (Card 4x2, Lyrics 4x3, Slim 4x1, Square 2x2) with baked shimmer bitmaps, live in-app preview (`WidgetSettingsScreen`), element visibility toggles.
- **Now Playing Sheet**: Reordered hierarchy (Artwork -> Title/Artist -> Clickable audio quality line -> Seekbar with times -> Transport row -> Secondary tools row) with 48dp touch targets.
- **Sectioned Settings Hub**: 2-column glass card grid for 12 sections with live search query filtering, subpages, and per-section reset.
- **Customize UI Area**: Theme presets, bundled fonts (Inter, Outfit, JetBrains Mono, System), density, corner radius, glass sliders, and `.nodetheme` JSON export/import.
- **Spring Motion Tokens**: Centralized spring token resolution (`MotionTokens.kt`), `.pressScale()`, `AnimationPlaygroundScreen`.

### Recent Crash Fixes (Commit `6f79e17`)
- **Fix 1 (Foreground Service Start Timeout)**: In `PlaybackService.kt`, `startInitialForegroundIfNeeded()` is called immediately on widget playback intents; `WidgetActionReceiver.kt` avoids starting service for seek actions when stopped.
- **Fix 2 (Nested Scroll Infinite Height Crash)**: In `SettingsScreen.kt`, `AnimationPlaygroundScreen` is rendered as a standalone root screen rather than nested inside the scrollable `Column`.
- **Fix 3 (FileProvider Root Missing Crash)**: In `file_paths.xml`, added `<cache-path name="cache" path="." />` and `<files-path name="files" path="." />` to permit sharing exported `.nodetheme` files from cache.

---

## 2. Verification Status & Evidence

| Component / Task | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| Unit Test Suite | `:app-android:testDebugUnitTest` | Done | Gradle output: `BUILD SUCCESSFUL in 39s`, 36 actionable tasks |
| Core DSP Unit Tests | `:core-dsp:test` | Done | Gradle output: `BUILD SUCCESSFUL` |
| Debug APK Build | `:app-android:assembleDebug` | Done | Gradle output: `BUILD SUCCESSFUL in 24s`, APK generated at `app-android/build/outputs/apk/debug/app-android-debug.apk` |
| Release APK Build | `:app-android:assembleRelease` | Done | Gradle output: `BUILD SUCCESSFUL in 1m 48s`, APK generated at `app-android/build/outputs/apk/release/app-android-release.apk` |
| APK Signing Verification | `apksigner verify --verbose` | Done | Output: `Verified using v2 scheme (APK Signature Scheme v2): true` |
| GitHub Release v1.3.0 | `gh release view v1.3.0` | Done | Release live at `https://github.com/Krighz/node-player/releases/tag/v1.3.0` with assets `NodePlayer.apk` & `NodePlayer.apk.sha256` |
| Crash Fix 1 on Device | Physical device test | Not verified | Device not connected via adb (`adb devices` output: empty) |
| Crash Fix 2 on Device | Physical device test | Not verified | Device not connected via adb (`adb devices` output: empty) |
| Crash Fix 3 on Device | Physical device test | Not verified | Device not connected via adb (`adb devices` output: empty) |
| Live Display Frame Times | `dumpsys gfxinfo` / Systrace | Not verified | No adb device connected; live frame profiling not run |

---

## 3. Known Issues & Unverified Items
- **On-device validation of crash fixes**: The three fixes (`ForegroundServiceDidNotStartInTimeException`, `IllegalStateException` nested vertical scroll, `IllegalArgumentException` FileProvider) compiled and passed unit tests, but have not been exercised on physical device hardware.
- **Realme GT 6T / ColorOS 14 frame rates**: Exact live refresh and 99th-percentile frame durations require physical device adb profiling with `dumpsys gfxinfo com.nodeplayer.music framestats`.

---

## 4. Key Architectural Decisions
- **Static RemoteViews for Widgets**: Launcher widgets use static bitmaps re-rendered only on track/playback changes to prevent battery drain; shaders are not run live in RemoteViews.
- **FileProvider Scope**: Exposes the root application cache and files directories to support sharing `.nodetheme` and diagnostic dump files securely.
- **Subpage Navigation in Settings**: Full sub-screens that contain their own scrolling containers must be hoisted outside the parent `Modifier.verticalScroll()` layout to prevent Compose infinite height constraint violations.
- **Service Foreground Promotion**: Any intent received by `PlaybackService` that could be initiated by `startForegroundService()` immediately invokes `ServiceCompat.startForeground()` with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` to satisfy the OS deadline.
