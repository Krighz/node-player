# Node Player Settings Reference

This document provides a reference for all user-configurable settings in Node Player across Android and Windows.

---

## 1. Dock Glass Settings

The Dock Glass settings control the Convx-style liquid glass optical rendering for the navigation dock and mini player.

| Setting | Type | Default | Description | Effect Location |
| :--- | :--- | :--- | :--- | :--- |
| **`dockGlassLensEnabled`** | Boolean | `true` | Enables/disables edge lens refraction SDF shader on dock and mini player capsule. When disabled, falls back to plain frosted blur. | • Android: `FrostedNavAndPlayer.kt` (L351)<br>• Windows: `OpticalRailDock.kt` (L103) |
| **`dockGlassRimEnabled`** | Boolean | `true` | Enables/disables outer specular rim highlight stroke and chromatic dispersion along dock edge. | • Android: `FrostedNavAndPlayer.kt` (L359)<br>• Windows: `OpticalRailDock.kt` (L111) |
| **`dockGlassPuckEnabled`** | Boolean | `true` | Enables/disables animated liquid glass selection puck sliding under the active navigation tab. | • Android: `FrostedNavAndPlayer.kt` (L386)<br>• Windows: `OpticalRailDock.kt` (L132) |

---

## 2. Appearance & Glass Customization

Accessible via **Settings > Customize UI**:

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| **`glassOpacity`** | Float (0.0 .. 1.0) | `0.55f` | Base translucency of all glass surfaces and cards. |
| **`edgeHighlightStrength`** | Float (0.0 .. 1.0) | `0.70f` | Intensity of outer specular highlights and caustics. |
| **`lensRefractionStrength`** | Float (0.0 .. 1.0) | `0.60f` | Curvature distortion of background elements behind liquid glass cards. |
| **`isPerformanceMode`** | Boolean | `false` | Disables real-time shader passes and freezes fluid animations to maximize battery life and minimize CPU/GPU load. |
| **`isOledBlack`** | Boolean | `false` | Sets background to true black (#000000) for OLED battery savings and infinite contrast. |
| **`animateOnlyWhilePlaying`** | Boolean | `true` | Freezes background fluid animations when audio playback is paused. |

---

## 3. Audio & DSP

Accessible via **Settings > Equalizer & DSP**:

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| **`eqEnabled`** | Boolean | `false` | Master toggle for the 10-band / parametric equalizer. |
| **`bassBoost`** | Float (0 .. 100) | `0` | Harmonic bass booster intensity. |
| **`reverb`** | Float (0 .. 100) | `0` | Room acoustic reverberation wet level. |
| **`virtualizer`** | Float (0 .. 100) | `0` | Spatial soundstage expander intensity. |
| **`convolverEnabled`** | Boolean | `false` | True impulse response (IR) convolver. |

---

## 4. Android Home-Screen Widgets

Accessible via **Settings > Widget Settings**:

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| **`glassOpacity`** | Float (0.40 .. 0.95) | `0.52f` | Translucency of offscreen-rendered RemoteViews widget bitmaps. |
