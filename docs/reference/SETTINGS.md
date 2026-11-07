# Node Player Settings Reference

This document provides an exhaustive reference for all user-configurable settings in Node Player across Android and Windows, including their type, default values, GPU cost impacts, and the exact source file and line numbers where they take effect in the rendering pipeline.

---

## 1. Liquid Glass Subsystem & Optics

Accessible via **Settings > Customize UI (UI Tab)**:

| Setting | Type | Default | GPU Cost | Description | Effect Location |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`glassQuality`** | `GlassQuality` (`FULL`, `BALANCED`, `LIGHT`) | `BALANCED` | High / Med / Low | Global quality profile controlling backdrop capture scale, refraction passes, and dispersion. | • Shared: `GlassTokens.kt` (L40)<br>• Common: `GlassThemeConfig.kt` (L194) |
| **`motionLevel`** | `MotionLevel` (`FULL`, `REDUCED`, `OFF`) | `FULL` | Low | Master motion throttle: applies fluid spring physics (`FULL`), smooth non-bouncy fades (`REDUCED`), or instant snaps (`OFF`). | • Shared: `MotionTokens.kt` (L86)<br>• Common: `GlassThemeConfig.kt` (L195) |
| **`dockGlassLensEnabled`** | Boolean | `true` | Medium | Enables edge lens refraction SDF shader on dock and mini player capsule. When disabled, falls back to plain frosted blur. | • Android: `FrostedNavAndPlayer.kt` (L351)<br>• Windows: `OpticalRailDock.kt` (L103) |
| **`dockGlassRimEnabled`** | Boolean | `true` | Low | Enables outer specular rim highlight stroke and chromatic dispersion along dock edge. | • Android: `FrostedNavAndPlayer.kt` (L359)<br>• Windows: `OpticalRailDock.kt` (L111) |
| **`dockGlassPuckEnabled`** | Boolean | `true` | Low | Enables animated liquid glass selection puck sliding under the active navigation tab. | • Android: `FrostedNavAndPlayer.kt` (L386)<br>• Windows: `OpticalRailDock.kt` (L132) |
| **`miniPlayerGlassEnabled`** | Boolean | `true` | Medium | Enables liquid glass shader on floating mini player capsule. | • Android: `FrostedNavAndPlayer.kt` (L140)<br>• Windows: `BottomPlayerDeck.kt` (L90) |
| **`nowPlayingGlassEnabled`** | Boolean | `true` | High | Enables real-time glass backdrop and lens effect on Now Playing screen hero controls. | • Android: `NowPlayingSheet.kt` (L210)<br>• Desktop: `Main.kt` (L680) |
| **`controlsGlassEnabled`** | Boolean | `false` | Low (Plain) | Toggles between plain-Compose glass styling and expensive lens backdrops on small controls (sliders, chips, switches). | • Shared: `LiquidGlassControls.kt` (L48-350) |
| **`widgetsGlassEnabled`** | Boolean | `true` | Low | Enables liquid glass backdrop rendering on Android home-screen widgets. | • Android: `NodePlayerWidget.kt` (L64) |
| **`glassRefractionHeightDp`**| Float (0.0 .. 32.0) | `18.0f` | Medium | Physical curvature depth of optical lens refraction. | • Shared: `GlassDesignTokens.kt` (L18)<br>• Android: `FrostedNavAndPlayer.kt` (L361) |
| **`glassRefractionAmountDp`**| Float (0.0 .. 30.0) | `16.0f` | Medium | Maximum pixel displacement caused by liquid glass lens. | • Shared: `GlassDesignTokens.kt` (L19)<br>• Android: `FrostedNavAndPlayer.kt` (L362) |
| **`glassChromaticFringe`** | Float (0.0 .. 1.0) | `0.25f` | High | Optical RGB dispersion intensity along refractive edges. | • Shared: `GlassDesignTokens.kt` (L22)<br>• Android: `FrostedNavAndPlayer.kt` (L364) |
| **`glassBackdropScale`** | Float (0.25 .. 1.0) | `0.50f` | High | Resolution scale factor for offscreen backdrop texture capture. Lower values dramatically boost framerates. | • Shared: `SharedGlassPool.kt` (L45)<br>• Android: `LiquidGlassBackground.kt` (L88) |

---

## 2. Dock Scroll Auto-Hiding

Accessible via **Settings > Customize UI (Navigation Section)**:

| Setting | Type | Default | Description | Effect Location |
| :--- | :--- | :--- | :--- | :--- |
| **`dockAutoHideEnabled`** | Boolean | `false` | Automatically slides down and fades dock during downward scrolling on long lists (Home, Library). | • Shared: `DockScrollController.kt` (L29)<br>• Android: `FrostedNavAndPlayer.kt` (L340) |
| **`dockAutoHideMiniPlayer`**| Boolean | `false` | When true, hides mini player capsule together with the dock; when false, keeps mini player visible. | • Android: `FrostedNavAndPlayer.kt` (L130) |
| **`dockScrollSensitivity`** | Float (0.5 .. 2.0) | `1.0f` | Multiplier for scroll distance required to trigger dock auto-hide. | • Shared: `DockScrollController.kt` (L24) |
| **`dockReturnDelayMs`** | Long (500 .. 3000) | `1200L` | Milliseconds of scroll stillness before the dock automatically returns. | • Shared: `DockScrollController.kt` (L51) |

---

## 3. General Appearance & Theme

| Setting | Type | Default | Description | Effect Location |
| :--- | :--- | :--- | :--- | :--- |
| **`glassOpacity`** | Float (0.0 .. 1.0) | `0.55f` | Base translucency fill opacity of cards and containers. | • Shared: `GlassTheme.kt` (L45) |
| **`edgeHighlightStrength`**| Float (0.0 .. 1.0) | `0.70f` | Specular rim gradient and highlight intensity. | • Shared: `GlassTheme.kt` (L50) |
| **`isPerformanceMode`** | Boolean | `false` | Master performance switch: freezes animated backdrops, disables shaders, and drops to flat frosted Compose styling. | • Android: `MainActivity.kt` (L120)<br>• Windows: `Main.kt` (L210) |
| **`isOledBlack`** | Boolean | `false` | Forces pure black (`#000000`) backgrounds for OLED display energy conservation. | • Shared: `GlassTheme.kt` (L140) |

---

## 4. Audio & DSP Architecture

Accessible via **Settings > Equalizer & DSP**:

| Setting | Type | Default | Description | Effect Location |
| :--- | :--- | :--- | :--- | :--- |
| **`eqEnabled`** | Boolean | `false` | Master toggle for 10-band IIR biquad equalizer. | • DSP: `DspChain.kt` (L45) |
| **`bassBoost`** | Float (0 .. 100) | `0` | Harmonic bass booster gain. | • DSP: `DspProcessors.kt` (L88) |
| **`virtualizer`** | Float (0 .. 100) | `0` | Spatial soundstage widening filter. | • DSP: `DspProcessors.kt` (L120) |
