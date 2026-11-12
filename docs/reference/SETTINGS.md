# Node Player Settings Reference

This document provides an exhaustive reference for all user-configurable settings in Node Player across Android and Windows, including their type, default values, GPU cost impacts, and the exact source file and line numbers where they take effect in the rendering and physics pipeline.

---

## 1. Master Liquid Glass Controls (`GlassControlsConfig`)

Accessible via **Settings > Customize UI (Master Liquid Glass Controls Card)**:

| Setting Key | Data Type | Range / Options | Default Value | GPU Cost Impact | Description | Consuming Source File & Line Number(s) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`masterEnabled`** | Boolean | `true`, `false` | `true` | Critical (-2.5ms to -9.0ms when off) | Master on/off switch for all liquid glass effects. When disabled, all controls drop to plain continuous squircles with zero backdrop sampling. | • `CustomizeUiView.kt` (L431)<br>• `SharedPlayerLayout.kt` (L180)<br>• `LiquidControls.kt` (L63, L126, L288, L393)<br>• `ConvxLiquidDock.kt` (L100)<br>• `ConvxMiniPlayer.kt` (L79)<br>• `GlassControlsConfig.kt` (L109, L147) |
| **`preset`** | `GlassControlsPreset` | `FULL`, `BALANCED`, `LIGHT` | `BALANCED` | High (`FULL`) / Moderate (`BALANCED`) / Low (`LIGHT`) | Quality and motion preset profile. Prompted with safety confirmation before overwriting custom overrides. | • `CustomizeUiView.kt` (L444)<br>• `GlassControlsConfig.kt` (L110, L152-208)<br>• `LivePreviewCanvas.kt` (L97) |
| **`global.blurDp`** | Float | 0.0 .. 32.0 dp | `4.0 dp` | High (+0.08ms per dp) | Radius of Gaussian / dual-pass kawase blur applied to underlying content. | • `ConvxLiquidDock.kt` (L152)<br>• `ConvxMiniPlayer.kt` (L116)<br>• `LivePreviewCanvas.kt` (L98, L486, L841)<br>• `MasterGlassConfig.kt` (L10) |
| **`global.lensHeightDp`** | Float | 0.0 .. 32.0 dp | `14.0 dp` | Medium (+0.04ms per dp) | Convex curvature height of optical SDF refraction lens. | • `ConvxLiquidDock.kt` (L154)<br>• `ConvxMiniPlayer.kt` (L118)<br>• `LivePreviewCanvas.kt` (L99, L487, L842)<br>• `MasterGlassConfig.kt` (L11) |
| **`global.lensAmountDp`** | Float | 0.0 .. 30.0 dp | `12.0 dp` | Medium (+0.035ms per dp) | Maximum pixel refraction displacement across refractive surface boundaries. | • `ConvxLiquidDock.kt` (L155)<br>• `ConvxMiniPlayer.kt` (L119)<br>• `LivePreviewCanvas.kt` (L99, L488, L843)<br>• `MasterGlassConfig.kt` (L12) |
| **`global.vibrancy`** | Float | 0.0 .. 1.0 | `0.85` | Low | Color saturation boost applied to refracted background content. | • `ConvxLiquidDock.kt` (L158)<br>• `ConvxMiniPlayer.kt` (L122)<br>• `LivePreviewCanvas.kt` (L489, L844)<br>• `MasterGlassConfig.kt` (L13) |
| **`global.tintOpacity`** | Float | 0.0 .. 1.0 | `0.35` | Low | Translucent color fill opacity for glass containers and buttons. | • `LiquidControls.kt` (L305, L411, L503, L600)<br>• `LivePreviewCanvas.kt` (L490)<br>• `MasterGlassConfig.kt` (L14) |
| **`global.highlightStrength`**| Float | 0.0 .. 1.0 | `0.75` | Low | Specular Fresnel rim light highlight gradient and edge reflection strength. | • `LiquidControls.kt` (L311, L417, L499, L604, L731)<br>• `ConvxLiquidDock.kt` (L164)<br>• `ConvxMiniPlayer.kt` (L128)<br>• `LivePreviewCanvas.kt` (L492, L848)<br>• `MasterGlassConfig.kt` (L15) |
| **`global.shadowStrength`** | Float | 0.0 .. 1.0 | `0.25` | Low | Ambient occlusion shadow drop underneath elevated glass elements. | • `ConvxLiquidDock.kt` (L169)<br>• `ConvxMiniPlayer.kt` (L133)<br>• `LivePreviewCanvas.kt` (L495, L852)<br>• `MasterGlassConfig.kt` (L16) |
| **`global.chromaticAberration`**| Float | 0.0 .. 1.0 | `0.15` | High (+1.4ms when active) | Optical RGB dispersion along curved lens refractive boundaries. | • `ConvxLiquidDock.kt` (L156)<br>• `ConvxMiniPlayer.kt` (L120)<br>• `LivePreviewCanvas.kt` (L100, L493, L843)<br>• `MasterGlassConfig.kt` (L17) |
| **`global.cornerShapeDp`** | Float | 0.0 .. 32.0 dp | `16.0 dp` | Zero | Continuous squircle curvature radius applied to component containers. | • `LiquidControls.kt` (L287, L402, L496, L595, L721)<br>• `ConvxLiquidDock.kt` (L118)<br>• `ConvxMiniPlayer.kt` (L82)<br>• `MasterGlassConfig.kt` (L18) |
| **`global.pressScale`** | Float | 0.85 .. 1.00 | `0.96` | Zero | Scale factor during finger touch / pointer down state. | • `LiquidControls.kt` (L296, L399)<br>• `LivePreviewCanvas.kt` (L542, L749)<br>• `MasterGlassConfig.kt` (L19) |
| **`global.springStiffness`**| Float | 100.0 .. 1000.0 | `450.0` | Zero | Spring physics tension governing micro-interactions and puck movement. | • `LiquidControls.kt` (L297, L400, L617)<br>• `ConvxLiquidDock.kt` (L127)<br>• `LivePreviewCanvas.kt` (L545, L639, L889)<br>• `MasterGlassConfig.kt` (L20) |
| **`global.springDamping`** | Float | 0.40 .. 1.00 | `0.80` | Zero | Damping ratio (0.80 = smooth minimal overshoot, 0.65 = bouncy). | • `LiquidControls.kt` (L297, L400, L617)<br>• `ConvxLiquidDock.kt` (L128)<br>• `LivePreviewCanvas.kt` (L546, L640, L890)<br>• `MasterGlassConfig.kt` (L21) |

---

## 2. Per-Item Overrides (`ComponentGlassConfig`)

Accessible via **Settings > Customize UI (9 Collapsible Accordions)**:

| Category | Component Target | Setting Keys | Default (`useGlobal`) | List Override (`forceLighterInLists`) | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Buttons** | `LiquidButton`, `LiquidIconButton` | `useGlobal`, `blurDp`, `lensHeightDp`, `lensAmountDp`, `highlightStrength`, `pressScale`, `springStiffness`, `springDamping`, `forceLighterInLists` | `true` | Supported | `LiquidControls.kt` (L285, L390) |
| **2. Toggles & Switches** | `LiquidToggle`, `LiquidSwitch` | `useGlobal`, `springStiffness`, `springDamping`, `highlightStrength`, `forceLighterInLists` | `true` | Supported | `LiquidControls.kt` (L125) |
| **3. Sliders** | `LiquidSlider`, `LiquidGlassSlider` | `useGlobal`, `blurDp`, `lensHeightDp`, `lensAmountDp`, `highlightStrength`, `pressScale` | `true` | N/A | `LiquidControls.kt` (L62) |
| **4. Dock & Tabs** | `ConvxLiquidDock`, `LiquidSegmentedControl` | `useGlobal`, `blurDp`, `lensHeightDp`, `lensAmountDp`, `cornerShapeDp`, `springStiffness`, `springDamping` | `true` | N/A | `ConvxLiquidDock.kt` (L97)<br>`LiquidControls.kt` (L594) |
| **5. Chips** | `LiquidChip`, `LiquidGlassChip` | `useGlobal`, `cornerShapeDp`, `highlightStrength`, `tintOpacity`, `forceLighterInLists` | `true` | Supported | `LiquidControls.kt` (L495) |
| **6. Dialogs & Sheets** | `LiquidDialog`, modal sheets | `useGlobal`, `cornerShapeDp`, `highlightStrength`, `shadowStrength` | `true` | N/A | `LiquidControls.kt` (L720) |
| **7. Mini Player** | `ConvxMiniPlayer` | `useGlobal`, `blurDp`, `lensHeightDp`, `lensAmountDp`, `highlightStrength`, `shadowStrength`, `cornerShapeDp` | `true` | N/A | `ConvxMiniPlayer.kt` (L77) |
| **8. Now Playing** | `NowPlayingView`, deck | `useGlobal`, `blurDp`, `lensHeightDp`, `lensAmountDp`, `highlightStrength` | `true` | N/A | `NowPlayingView.kt` (L120) |
| **9. Widgets** | Android widgets & overlays | `useGlobal`, `blurDp`, `tintOpacity`, `highlightStrength` | `true` | N/A | `NodePlayerWidget.kt` (L64) |

---

## 3. Real-Time GPU Cost Classification & Live Preview

Integrated within `LivePreviewCanvas.kt` and `CustomizeUiView.kt`:

| Category Level | Frame-Time Threshold | Indicator Pill Color | Target Framerate | Pipeline Cost Factors |
| :--- | :--- | :--- | :--- | :--- |
| **LOW** | `< 3.0 ms` | Green (`#10B981`) | `~120fps (Zero overhead)` | Master off baseline (0.8ms) or plain squircle mode with zero backdrop capture. |
| **MODERATE** | `3.0 ms .. 6.5 ms` | Teal (`#06B6D4`) | `~120fps (Smooth)` | Standard 4dp blur, subtle 14dp lens, chromatic fringe disabled. |
| **HIGH** | `6.5 ms .. 10.5 ms` | Amber (`#F59E0B`) | `~90-120fps (Balanced)` | Heavy 8dp blur, 24dp lens refraction, active chromatic dispersion (> 0.05). |
| **INTENSE** | `> 10.5 ms` | Rose (`#F43F5E`) | `~60fps (Heavy shader load)`| Maximum blur (16-32dp), large refractive displacement, multiple full-pass overrides. |

**Formula:**
$$T_{\text{frame}} = T_{\text{base}} + T_{\text{blur}} + T_{\text{lens}} + T_{\text{chromatic}} + \sum T_{\text{overrides}}$$
- Implemented in: `LivePreviewCanvas.kt` (L90-134)
- Verified by: `GlassCustomizationConfigTest.kt` (L44-86)

---

## 4. Dock Scroll Auto-Hiding

Accessible via **Settings > Customize UI (Navigation Section)**:

| Setting Key | Data Type | Default | Description | Consuming Source File & Line Number(s) |
| :--- | :--- | :--- | :--- | :--- |
| **`dockAutoHideEnabled`** | Boolean | `false` | Automatically slides down and fades dock during downward scrolling on long lists (Home, Library). | • `DockScrollController.kt` (L29)<br>• `SharedPlayerLayout.kt` (L156, L163, L168) |
| **`dockAutoHideMiniPlayer`**| Boolean | `false` | When true, hides mini player capsule together with the dock; when false, keeps mini player visible. | • `SharedPlayerLayout.kt` (L172)<br>• `ConvxMiniPlayer.kt` (L105) |
| **`dockScrollSensitivity`** | Float (0.5 .. 2.0) | `1.0f` | Multiplier for scroll distance required to trigger dock auto-hide. | • `DockScrollController.kt` (L24)<br>• `SharedPlayerLayout.kt` (L154, L161) |
| **`dockReturnDelayMs`** | Long (500 .. 3000) | `1200L` | Milliseconds of scroll stillness before the dock automatically returns. | • `DockScrollController.kt` (L51)<br>• `SharedPlayerLayout.kt` (L155, L162) |

---

## 5. Atmosphere, Palette & Display Architecture

Accessible via **Settings > Customize UI**:

| Setting Key | Data Type | Default | Description | Consuming Source File & Line Number(s) |
| :--- | :--- | :--- | :--- | :--- |
| **`fixedAccentColorArgb`** | Long | `0xFF6366F1L` (Amethyst) | Base accent tint color applied to highlights, active tabs, and sliders. | • `CustomizeUiView.kt` (L635)<br>• `SharedPlayerLayout.kt` (L117) |
| **`backgroundAnimationStyle`**| String | `"LIQUID_BLOBS"` | Background atmosphere shader (`LIQUID_BLOBS`, `AURORA`, `MESH_GRADIENT`, `ALBUM_ART_BLUR`, `SLOW_CAUSTICS`, `STATIC`). | • `CustomizeUiView.kt` (L678)<br>• `LiquidGlassBackground.kt` (L52) |
| **`isOledBlack`** | Boolean | `false` | Forces pure black (`#000000`) backgrounds for OLED display energy conservation. | • `GlassTheme.kt` (L134)<br>• `CustomizeUiView.kt` (L329) |
| **`isPerformanceMode`** | Boolean | `false` | Master performance switch: freezes animated backdrops, disables shaders, and drops to flat frosted Compose styling. | • `GlassTheme.kt` (L150)<br>• `ConvxLiquidDock.kt` (L100)<br>• `ConvxMiniPlayer.kt` (L79) |

---

## 6. Versioned Serialization & Reset Engine

Export and restore format specifications:

| Component | Format Identifier | Version | Schema Rules | Consuming Source File & Line Number(s) |
| :--- | :--- | :--- | :--- | :--- |
| **JSON Serializer** | `NodePlayerGlassControls` | `1` | Strict field validation, 64KB length cap, clamped slider parameters | `GlassControlsSerializer.kt` (L12-65) |
| **Two-Tier Reset** | Runtime Action | N/A | Tier 1: Reset to Global per accordion.<br>Tier 2: Reset All to Factory Defaults. | `CustomizeUiView.kt` (L124, L558)<br>`GlassControlsSerializer.kt` (L180-230) |

---

## 7. Diagnostics & Optical Test Screen

Accessible via **Settings > Diagnostics & Graphics > Liquid Glass Optical Test** (Android) and **Settings > Liquid Glass Optical Test** (Shared Compose):

| Feature / Pattern | Description | Consuming Source File & Line Number(s) |
| :--- | :--- | :--- |
| **Monochrome Stripe Zone** | Alternating high-contrast black/white vertical & diagonal bands to test edge refraction and blur diffusion. | `OpticalDiagnosticsView.kt` (L130-187) |
| **Dense Typography Zone** | Multi-size, multi-weight typography blocks (Display, Headline, Body, Caption, Code) to verify zero sharp text inside dock. | `OpticalDiagnosticsView.kt` (L234-291) |
| **Pixel Alignment Grid Zone**| High-frequency grid lines and concentric circles to make SDF lens curvature and barrel distortion visible. | `OpticalDiagnosticsView.kt` (L296-360) |
| **Active Chrome Overlay** | Renders 64dp capsule dock, 56dp sliding puck with chromatic dispersion, 56dp mini player, search button, and live interactive `LiquidSlider`. | `OpticalDiagnosticsView.kt` (L222-228, L384-428) |
| **Settings Route Wiring** | Navigation entries in shared and Android settings screens. | • `SettingsView.kt` (L110)<br>• `SettingsScreen.kt` (L85)<br>• `MainActivity.kt` (L146) |

---

## 8. Appearance & Sub-System Configuration Models (`GlassThemeConfig`)

Added in Milestone v2.3 to manage navigation, home feed layout, and lyrics customization:

| Configuration Sub-Model | Setting Key | Data Type | Default Value | Description | Consuming Source File |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`DockConfig`** | `visibleTabs` | `List<PlayerNavDestination>` | `[HOME, NEW, LIBRARY]` | Visible tabs on the floating liquid dock | `AudioAndThemeConfig.kt`, `ConvxLiquidDock.kt` |
| **`DockConfig`** | `autoHide` | `Boolean` | `true` | Collapses dock on scroll down | `SharedPlayerLayout.kt`, `ConvxLiquidDock.kt` |
| **`DockConfig`** | `autoHideSensitivity` | `Float` | `0.75f` | Velocity/offset sensitivity for scroll-collapse | `DockScrollController.kt` |
| **`HomeLayoutConfig`** | `shelfOrder` | `List<String>` | `[Quick Picks, Videos, Trending, Playlists, Artists]` | Order of dynamic home feed shelves | `OnlineHomeView.kt` |
| **`HomeLayoutConfig`** | `hiddenShelves` | `Set<String>` | `emptySet()` | Shelves hidden from home feed | `OnlineHomeView.kt` |
| **`HomeLayoutConfig`** | `regionOverride` | `String?` | `null` | Manual YouTube Music region ISO override | `OnlineHomeView.kt` |
| **`LyricsViewConfig`** | `fontSizeSp` | `Float` | `28.0f` | Font size for active and inactive lyrics | `LyricLineItem.kt`, `FullScreenLyricsView.kt` |
| **`LyricsViewConfig`** | `blurInactiveLines` | `Boolean` | `true` | Apply blur ladder to inactive lines | `LyricLineItem.kt` |
| **`LyricsViewConfig`** | `autoHideDelaySec` | `Int` | `5` | Inactivity seconds before fading controls | `FullScreenLyricsView.kt` |
| **`LyricsViewConfig`** | `keepScreenOn` | `Boolean` | `true` | Prevent screen sleep while in lyrics view | `FullScreenLyricsView.kt`, `NowPlayingSheet.kt` |
| **`LiquidGlassBackgroundConfig`** | `animateOnlyWhilePlaying` | `Boolean` | `true` | When true, stops infinite gradient animation loop while audio is paused, preventing unnecessary full-screen invalidations | `PlayerViewModel.kt`, `LiquidGlassBackground.kt`, `AudioAndThemeConfig.kt` |

---

## 9. Motion System & Physics Tokens (`GlassThemeConfig`)

Added in Milestone v2.5 (Phase 3) to provide centralized physics tokens, interactive spring animations, container morphing, and system accessibility integration across Android and Windows Desktop:

| Setting Key | Data Type | Range / Options | Default Value | GPU Cost Impact | Description | Consuming Source File & Line Number(s) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`motionLevel`** | `MotionLevel` | `FULL`, `REDUCED`, `OFF` | `FULL` | Zero | Top-level motion mode. `FULL` enables physics springs; `REDUCED` switches to gentle damping and clamps durations; `OFF` immediately returns `Instant` snap specs (0ms). | • `MotionTokens.kt`<br>• `ScreenTransitions.kt`<br>• `SharedPlayerLayout.kt`<br>• `MotionModifiers.kt` |
| **`reduceMotion`** | Boolean | `true`, `false` | `false` | Zero | When true (or when OS accessibility "Remove animations" / "Reduce motion" is detected), overrides motion level and forces `MotionTokens.Instant` and 0ms duration. | • `MotionTokens.kt`<br>• `ScreenTransitions.kt`<br>• `PlayerViewModel.kt`<br>• `Theme.kt` |
| **`motionSpeed`** | Float | 0.5 .. 2.0 | `1.0f` | Zero | Global speed multiplier for motion durations and spring stiffnesses. (Higher value produces faster transitions). | • `MotionTokens.kt`<br>• `ScreenTransitions.kt` |
| **`animationPreset`** | String | `"SMOOTH"`, `"SNAPPY"`, `"BOUNCY"`, `"GENTLE"`, `"MINIMAL"`, `"CUSTOM"`, `"OFF"` | `"SMOOTH"` | Zero | Spring physics profile preset governing damping ratio and stiffness across UI interactions. | • `MotionTokens.kt`<br>• `AudioAndThemeConfig.kt`<br>• `CustomizeUiView.kt` |
| **`springStiffness`** | Float | 150.0 .. 1000.0 | `380.0f` | Zero | Custom spring stiffness tension when `animationPreset == "CUSTOM"`. | • `MotionTokens.kt`<br>• `CustomizeUiView.kt` |
| **`springBounciness`** | Float | 0.30 .. 1.00 | `0.82f` | Zero | Custom spring damping ratio when `animationPreset == "CUSTOM"` (lower value produces more overshoot/bounce). | • `MotionTokens.kt`<br>• `CustomizeUiView.kt` |
| **`pressScaleEnabled`** | Boolean | `true`, `false` | `true` | Zero | Enables tactile scale contraction on button down / touch presses. Automatically skipped when motion is reduced/off. | • `MotionModifiers.kt`<br>• `MotionTokens.kt` |
| **`pressScaleStrength`** | Float | 0.85 .. 1.00 | `0.96f` | Zero | Target scale factor during touch down when press scaling is enabled. | • `MotionModifiers.kt`<br>• `CustomizeUiView.kt` |
| **`staggeredListAnimation`**| Boolean | `true`, `false` | `true` | Zero | Applies staggered entrance cascade to lazy list items, deferred strictly to `Modifier.graphicsLayer`. | • `MotionModifiers.kt`<br>• `OnlineHomeView.kt` |
| **`screenTransitionStyle`** | String | `"FADE_THROUGH"`, `"SHARED_AXIS_X"`, `"SHARED_AXIS_Y"`, `"SLIDE"` | `"FADE_THROUGH"` | Zero | Transition style for top-level screen and destination changes in `SharedPlayerLayout`. | • `ScreenTransitions.kt`<br>• `SharedPlayerLayout.kt` |





