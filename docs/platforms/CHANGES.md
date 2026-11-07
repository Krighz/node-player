# Platform Implementation Changes (v1.6.0)

**Branch:** feature/liquid-dock  
**Base Release:** v1.5.0  

---

### [2026-11-08] - Ground-Up Liquid Glass Rebuild: Phase 4 Global Settings, Per-Item Overrides & Real-Time Live Preview
- **Master Liquid Glass Controls Card & Presets (SETTINGS-01)**:
  - Implemented `MasterControlsCard` in `CustomizeUiView.kt` providing a global master on/off switch (`masterEnabled`), 3 tiered presets (`Full`, `Balanced`, `Light`), and 12 shared master optical and spring sliders.
  - Added preset change safety dialog ("Keep existing overrides" vs "Reset all to preset") preventing accidental overwrite of custom adjustments.
  - Master OFF switch turns all components into high-performance zero-sampling continuous squircles (`ResolvedGlassParams.LIGHTER_SQUIRCLE`).
- **9 Per-Item Override Accordions & List Optimization (SETTINGS-02)**:
  - Built 9 collapsible accordion cards in `CustomizeUiView.kt` for Buttons, Toggles, Sliders, Dock, Chips, Dialogs, Mini Player, Now Playing, and Widgets.
  - Implemented immediate "Use Global" switch with smooth expand/collapse chevron animation and "Reset to Global" per-item restoration.
  - Added granular "Force Lighter Style in Lists" toggle for Buttons, Toggles, and Chips, eliminating heavy shader passes in dense lists.
- **Unified Domain Models, Versioned Serialization & Persistence (SETTINGS-03)**:
  - Created pure Kotlin `GlassControlsConfig.kt` in `:core-model` with zero Android framework dependencies.
  - Implemented `GlassControlsSerializer.kt` handling versioned JSON format (`NodePlayerGlassControls` v1) with 64KB bounds, numeric clamping, and corrupt payload recovery.
  - Added 1-click clipboard export/import dialogs and factory reset engine.
  - Integrated persistent reactive storage via `AppSettingsRepository.kt` (DataStore) on Android and `DesktopSettingsStore.kt` on Windows Desktop.
- **Interactive Live Preview Canvas & Dynamic GPU Cost Meter (SETTINGS-04)**:
  - Built `LivePreviewCanvas.kt` pinned/sticky at top on mobile portrait and in a 2-column split (45% preview / 55% controls) on desktop 16:9 (>840dp).
  - Implemented dynamic animated caustics and chromatic metaballs backdrop (`DynamicPreviewCausticsBackdrop`) using stable root textures (`rememberLayerBackdrop()`).
  - Added real-time GPU cost meter with empirical frame-time calculation (`calculateGpuCost`) and color-coded classification badge (`LOW`, `MODERATE`, `HIGH`, `INTENSE`).
  - Rendered complete miniature interactive suite: mini player capsule with animated equalizer bars, mini button with spring press scale, draggable mini toggle, selectable mini chip, continuous mini slider with touch slop isolation, and mini dock with sliding tab puck.
- **Dynamic Parameter Wiring & Exhaustive SETTINGS.md Audit (SETTINGS-05)**:
  - Provided `LocalGlassControlsConfig` in `SharedPlayerLayout.kt`, dynamically consumed by `LiquidControls.kt`, `ConvxLiquidDock.kt`, and `ConvxMiniPlayer.kt`.
  - Updated `docs/reference/SETTINGS.md` with an exhaustive audit of all 15+ settings parameters, line references, GPU cost formulas, and JSON schemas.
- **Multiplatform Verification**:
  - All tests passing in `:core-model:test` and `:core-ui:desktopTest`.
  - Full project compilation clean across `:app-android:compileDebugKotlin` and `:app-desktop:compileKotlin`.
  - Visual inspections cataloged as "human check pending" in `04-UAT.md`.


### [2026-11-08] - Ground-Up Liquid Glass Rebuild: Phase 3 Drag Controls Everywhere & Dock Auto-Hide
- **Continuous Gesture Draggable `LiquidSlider` (CTRL-01)**:
  - Upgraded `LiquidSlider.kt` with continuous gesture dragging using `DampedDragAnimation`, horizontal velocity stretch (`scaleX`), vertical press-squish (`scaleY`), and spring release snapping.
  - Enforced $\ge$48dp touch hitboxes on both thumb and container (`Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`), tap-to-seek, division-by-zero protection, and safe clamped bounds.
- **Unified `LiquidControls.kt` Suite & Tap/Drag `LiquidToggle` (CTRL-02)**:
  - Enhanced `LiquidToggle.kt` supporting both instantaneous tap toggling and fluid continuous drag tracking with spring snap at the 0.5f threshold.
  - Built comprehensive `LiquidControls.kt` suite in `core-ui`: `LiquidButton`, `LiquidIconButton`, `LiquidChip`, `LiquidSegmentedControl`, `LiquidDialog`, `LiquidCheckbox`, and `LiquidRadioButton`.
  - Added lighter glass styling mode (`lighterStyle = true`) utilizing plain continuous squircle geometry (`ContinuousRoundedRectangle`) without heavy backdrop sampling for high frame rate (60/120 fps) list items.
  - Converted legacy `LiquidGlassControls.kt` into a backwards-compatible delegate façade.
- **Systematic Plain Material Controls Replacement (CTRL-03)**:
  - Replaced all direct uses of plain Material controls (`Button`, `IconButton`, `Switch`, `Slider`, `FilterChip`, `TabRow`, `SegmentedButton`, `AlertDialog`, `RadioButton`, `Checkbox`) across all app screens: `EqualizerView`, `SettingsView`, `CustomizeUiView`, `LibraryView`, `NowPlayingView`, `BottomPlayerDeck`, `BottomPlayerDock`, and `NodeConnectPairingDialog`.
- **Intelligent Dock Nested Scroll Auto-Hide & Return Triggers (CTRL-04)**:
  - Implemented `DockScrollController.kt` managing `offsetFraction` and 4 return triggers: 1) scrolling up, 2) list top reached, 3) 2-second scroll pause timeout, 4) bottom edge tap (~40dp invisible touch target active when hidden).
  - Wired `SharedPlayerLayout.kt` to translate floating dock and mini player by `120.dp * offsetFraction` with spring physics (`dampingRatio = 0.82f, stiffness = 380f`) and alpha fade (`1f - offsetFraction`).
  - Respected user settings: `dockAutoHideEnabled`, `dockAutoHideMiniPlayer`, `dockScrollSensitivity`, and `dockReturnDelayMs`.
- **Node Connect Header Integration & List Smoothness (CTRL-05)**:
  - Preserved `DeviceConnectPill` in `HomeView.kt` mobile portrait header, `HomeScreen.kt`, desktop top bar, and Windows rail.
  - Maintained 100% operational peer discovery, pairing, and audio casting with lighter styling across list rows.
- **Multiplatform Verification**:
  - All tests passing in `:core-ui:desktopTest` (including `LiquidControlsGestureTest`, `LiquidSliderMathTest`, and `DockScrollControllerTest`).
  - Full project compilation verified across `:app-android:compileDebugKotlin` and `:app-desktop:compileKotlin`.
  - Visual inspections marked as "human check pending" in `03-UAT.md`.


### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 2 Layering & LiquidBottomTabs Dock + Mini Player
- **Root Layer Stacking Inversion (D-01, DOCK-01)**:
  - Inverted the root Compose layout hierarchy in `SharedPlayerLayout.kt`: root full-bleed `Box` records scrolling page content and background into a single `pageBackdrop` using `Modifier.layerBackdrop(pageBackdrop)`.
  - Floating chrome (`ConvxMiniPlayer`, `LiquidDock`, `LiquidButton`) anchored as sibling overlays above the page layer in z-order, receiving `pageBackdrop`.
  - Introduced `LocalBottomBarPadding` dynamic composition local providing 136dp (when mini player is active) and 80dp (dock-only) so scrollable lists glide edge-to-edge behind the glass.
- **Elimination of Page Card Self-Refraction (D-02, DOCK-02)**:
  - Stripped all `drawBackdrop` and lens refraction shaders from inner page cards and badges in `GlassComponents.kt` and screens (`HomeView.kt`, `LibraryView.kt`, `EqualizerView.kt`, `CustomizeUiView.kt`, `SettingsView.kt`).
  - Rendered all page cards using plain squircle surfaces (`ContinuousRoundedRectangle`), eliminating rainbow edge fringes and self-sampling artifacts.
- **Upstream LiquidBottomTabs Dock & Search Companion (D-03, D-04, DOCK-03, DOCK-04)**:
  - Built `LiquidDock.kt` integrating upstream `LiquidBottomTabs`: 64dp capsule bar, 8dp blur, vibrancy, (24dp, 24dp) lens, ~40% alpha container, and hidden tab copy with accent color tint.
  - Implemented 56dp sliding puck using `rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)`, press-scaled lens, chromatic aberration, and `DampedDragAnimation` over 4 tabs (Home, Library, Equalizer, Settings).
  - Built companion 56dp circular Search `LiquidButton` floating adjacent to the dock capsule, sharing `pageBackdrop`.
- **Floating Mini Player Capsule (D-05, DOCK-04)**:
  - Refined `ConvxMiniPlayer.kt` to a 56dp squircle capsule floating 8dp above the dock capsule.
  - Samples `pageBackdrop` without sampling the dock, featuring squircle album artwork thumbnail, title, artist, live audio-reactive equalizer bars, and isolated transport controls.
- **Desktop Windows Rail & Deck Integration (D-06, DOCK-05)**:
  - Enabled `pageBackdrop` sampling across `OpticalRailDock.kt` (left vertical rail) and `BottomPlayerDeck.kt` (bottom player deck) on Windows desktop via SkSL shaders.
- **Multiplatform Verification**:
  - `:core-ui:desktopTest`, `:app-desktop:compileKotlin`, and `:app-android:compileDebugKotlin` all compile and pass cleanly. Visual inspections marked as "human check pending" in `02-UAT.md`.

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 1 Plan 02 Staged Clean Slate & Legacy Purge
- **Callers Migrated to `com.kyant.backdrop.*`**:
  - `ConvxLiquidDock.kt`, `ConvxMiniPlayer.kt`, `OpticalRailDock.kt`, `LiquidGlassControls.kt`, `NowPlayingView.kt`, `BottomPlayerDock.kt`, and `NowPlayingSheet.kt` switched from `com.nodeplayer.ui.component.backdrop.*` to `com.kyant.backdrop.*`.
  - `GlassShaderSelfTest.kt` in `app-android`: Re-pointed AGSL shader diagnostic self-test to `com.kyant.backdrop.internal.RoundedRectRefractionShaderString` with updated uniform bindings (`size`, `offset`, `cornerRadii`, `refractionHeight`, `refractionAmount`, `depthEffect`), preserving offscreen pixel validation and safe fallback.
  - `app-android/proguard-rules.pro`: Replaced legacy package keep rules with `com.kyant.backdrop.**`.
- **Legacy Code Purged (37 files deleted)**:
  - Deleted old cut-down vendored package directory and platform counterparts:
    - `core-ui/src/androidMain/kotlin/com/nodeplayer/ui/component/backdrop/Platform.android.kt`
    - `core-ui/src/androidMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShader.android.kt`
    - `core-ui/src/androidMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/RenderEffect.android.kt`
    - `core-ui/src/desktopMain/kotlin/com/nodeplayer/ui/component/backdrop/Platform.desktop.kt`
    - `core-ui/src/desktopMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShader.desktop.kt`
    - `core-ui/src/desktopMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/RenderEffect.desktop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/Backdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/BackdropEffectScope.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/CompatibilityShims.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/DrawBackdropModifier.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/Platform.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShader.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/RuntimeShaderCache.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/Backdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/CanvasBackdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/EmptyBackdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/backdrops/LayerBackdrop.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidBottomTab.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidBottomTabs.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidButton.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidSlider.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidToggle.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/utils/DampedDragAnimation.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/utils/DragGestureInspector.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/utils/InteractiveHighlight.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/Blur.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/ColorFilter.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/Lens.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/effects/RenderEffect.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/highlight/Highlight.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/highlight/HighlightStyle.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/Outline.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/RenderEffect.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/Shaders.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/internal/ShapeProvider.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/shadow/InnerShadow.kt`
    - `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/shadow/Shadow.kt`
  - Removed temporary `CompatibilityShims.kt`.
  - Zero references to `com.nodeplayer.ui.component.backdrop` remain across all production code.

---

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 3 Real Drag Controls, Animations & Dock Auto-Hide
- **Continuous Gesture Dragging for Liquid Sliders (core-ui)**:
  - LiquidSlider.kt: Refactored pointer handling to capture drag gestures across the full container row (detectDragGestures).
  - Added real-time tracking with thumb velocity squish and stretch, live onValueChange dispatch, and spring release.
  - Guaranteed minimum 48dp touch interaction target across all glass controls (LiquidGlassSlider, LiquidGlassSwitch, LiquidGlassButton, LiquidGlassIconButton, LiquidGlassChip, LiquidGlassSegmentedControl).
- **Dock Scroll Auto-Hide Integration (pp-android)**:
  - MainActivity.kt: Wired DockScrollController and 
ememberDockScrollConnection to screen content with 
`nestedScroll`.
  - Smooth vertical slide-down and fade animation during downward list scrolling, restoring on upward scroll, top of list, or pause timeout.
  - Respects dockAutoHideEnabled, dockAutoHideMiniPlayer, dockScrollSensitivity, and dockReturnDelayMs.
- **Spring Physics & Motion Tokens**:
  - Applied Convx critically damped springs (dampingRatio = 0.82f, stiffness = 380f) across dock puck, tab switches, and sheets.
  - Respected user Motion Level settings (Off, Reduced, Full) and system 'remove animations' accessibility preference.
- **Unit Testing**:
  - Added LiquidControlsGestureTest.kt verifying slider normalized delta conversions, clamping bounds, and toggle snap thresholds.
- **Status**: Completed Phase 3. Both Android assembleDebug and Desktop tests pass cleanly. Visual and device checks marked 'human check pending'.

---

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 2 Convx-Style Dock & Mini Player
- **Convx-Style Liquid Glass Dock (core-ui, app-android, app-desktop)**:
  - ConvxLiquidDock.kt: Built continuous squircle capsule dock (ContinuousCapsule) matching Convx reference.
  - Interactive sliding lens puck with DampedDragAnimation and Convx critically damped spring physics (dampingRatio = 0.82f, stiffness = 380f).
  - Active tab accent tinting and circular glass Search button.
- **Convx-Style Floating Mini Player (core-ui, app-android, app-desktop)**:
  - ConvxMiniPlayer.kt: Floating continuous squircle capsule (ContinuousRoundedRectangle(20.dp)) hovering directly above the dock.
  - Squircle artwork thumbnail, title/artist metadata, audio-reactive animated RMS equalizer bars, play/pause, and skip controls.
- **Clean Layer Separation (Zero Torn Text)**:
  - Text, icons, artwork, and buttons rendered strictly in a clean pass above the glass substrate, eliminating torn text or self-refraction artifacts.
- **Desktop Windows Rail & Player Alignment**:
  - OpticalRailDock.kt: Updated with continuous squircle geometry (ContinuousRoundedRectangle(48.dp)) and Convx spring puck.
- **Obsolete Code Purge**:
  - Deleted FrostedNavAndPlayer.kt. Updated CustomizeUiView.kt and SETTINGS.md tracing to point to ConvxLiquidDock.kt and ConvxMiniPlayer.kt.
- **Status**: Completed Phase 2. Android assembleDebug, desktopTest, and app-desktop test all pass cleanly. Visual and physical checks marked 'human check pending'.

---

### [2026-11-07] - Ground-Up Liquid Glass Rebuild: Phase 1 Clean Slate & Full Upstream Library Integration
- **Full Upstream Kyant0 Library & Desktop Runtime Effect Verification**:
  - Full Kyant0 backdrop engine and complete demo catalog integrated with complete multiplatform support (DampedDragAnimation.kt, DragGestureInspector.kt, InteractiveHighlight.kt, LiquidSlider.kt, LiquidToggle.kt, LiquidButton.kt, LiquidBottomTabs.kt).
  - Desktop Skia SkSL Engine: Verified high-fidelity RuntimeEffect.makeForShader SkSL multiplatform shader bridge (Platform.desktop.kt, RuntimeShader.desktop.kt, RenderEffect.desktop.kt) on Windows Compose Multiplatform.
  - Multiplatform Continuous Squircle Geometry: Implemented ContinuousRoundedRectangle and ContinuousCapsule (core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/shapes/ContinuousRoundedRectangle.kt) providing continuous iOS HIG squircle curves without platform-specific dependencies.
  - Verified compilation: :core-ui:desktopTest, :app-desktop:compileKotlin, :app-android:compileDebugKotlin, and :app-android:assembleDebug all build and pass cleanly.
- **Purge Plan Audit & Retention**:
  - Obsolete glass prototypes scheduled for replacement in Phase 2 & 3: FrostedNavAndPlayer.kt (Android dock), OpticalRailDock.kt, and BottomPlayerDock.kt (Windows dock & deck).
  - Preserved critical architectures: GlassThemeConfig settings persistence, AppSettingsRepository, playback engines (PlaybackService, WindowsPlaybackEngine), NodeConnectHub Device Connect, and GlassShaderSelfTest fallback mechanisms.
- **Status**: Completed Phase 1. Android assembleDebug and Desktop tests pass. Visual and device checks marked 'human check pending'.

---

### [2026-11-07] - Liquid Glass Revamp: Phase 5 More Liquid Glass Settings in Customize UI
- **Live Preview & Granular Controls (`core-ui`, `core-model`)**:
  - `GlassLivePreview.kt`: Interactive liquid glass sample card displaying real-time feedback as optical parameters are adjusted.
  - Granular parameters: Added `glassRefractionHeightDp`, `glassRefractionAmountDp`, `glassChromaticFringe`, and `glassBackdropScale`.
  - Component toggles: Added independent toggles `dockGlassLensEnabled`, `miniPlayerGlassEnabled`, `nowPlayingGlassEnabled`, `controlsGlassEnabled`, and `widgetsGlassEnabled`.
  - Presets & Reset: Integrated GPU cost indicators and "Reset to recommended" preset action in `GlassThemeConfig`.
- **Exhaustive Settings Reference (`docs/reference/SETTINGS.md`)**:
  - Comprehensive documentation of all settings with types, defaults, GPU impact hints, and source file + line number traceability.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 4 Dock Hides While Scrolling
- **Intelligent Dock Scroll Auto-Hide (`core-ui`, `core-model`)**:
  - `DockScrollController.kt`: State holder tracking scroll delta accumulation and spring-animating dock slide-down and fade. Includes automatic timeout timer to restore dock after user stops scrolling.
  - `rememberDockScrollConnection`: Intercepts `onPreScroll` and `onPreFling` on long lists (Home, Library) with edge fling detection.
  - `GlassThemeConfig`: Added preferences `dockAutoHideEnabled`, `dockAutoHideMiniPlayer`, `dockScrollSensitivity`, and `dockReturnDelayMs`.
  - Windows Desktop: Kept stationary rail and deck visible by default, with opt-in support.
  - `DockScrollControllerTest.kt`: Unit tests verifying scroll down hide, scroll up restore, and timeout recovery.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 3 Smooth iOS-like Animations (like Convx)
- **Spring Physics Engine (`core-ui`, `core-model`)**:
  - `MotionTokens.kt`: Added Convx-matched spring configurations (`ConvxPuck`: `dampingRatio = 0.82f`, `stiffness = 380f`; `DragTracking`: `dampingRatio = 0.95f`, `stiffness = 900f`).
  - `MotionLevel`: Added `FULL`, `REDUCED`, and `OFF` modes to `GlassThemeConfig` in `core-model`.
  - System Accessibility: Automatically drops spring animations down to instantaneous snap transitions when `reduceMotion` or system animations are disabled.
  - `MotionTokensTest.kt`: Unit tests verifying spring resolution across all motion tiers.
- **Draw/Layout Phase Optimization (`core-ui`)**:
  - Enforced GPU-accelerated layer transforms (`graphicsLayer { scaleX = ...; scaleY = ... }`) for press scales and thumb bounce, avoiding per-frame recomposition.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 2 Revamp the Glass Code
- **Unified Glass Architecture (`core-model`, `core-ui`)**:
  - `GlassQuality`: Introduced `FULL`, `BALANCED`, and `LIGHT` quality modes to `GlassThemeConfig` in `core-model`.
  - `GlassDesignTokens.kt`: Added canonical design tokens in `core-ui` unifying radii, optics, blur, rim specular alpha, and vibrancy, dynamically adapting based on `GlassQuality`.
  - `GlassTokensTest.kt`: Unit tests verifying token resolution for all three glass quality tiers.
- **Fallbacks & Multiplatform Interface**:
  - Single multiplatform backdrop API in `core-ui` backed by AGSL on Android and SkSL on Windows Desktop.
  - Verified `GlassShaderSelfTest` and frosted fallback mode functionality.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`, `:core-common:test`). Device checks marked "human check pending".

---

### [2026-11-07] - Liquid Glass Revamp: Phase 1 Fix What Is Broken
- **LiquidSlider Math & Layout Safety (`core-ui`)**:
  - `LiquidSlider.kt`: Clamped computed track width and placeable bounds to `>= 0`, guarding against negative dimensions and division-by-zero on drag.
  - `LiquidSliderMathTest.kt`: Added unit test covering boundary cases, zero widths, and narrow constraints.
- **Dock Refraction & Aberration Tuning (`core-ui`, `app-android`)**:
  - `FrostedNavAndPlayer.kt` & `OpticalRailDock.kt`: Scaled refraction lens parameters proportionally to container heights; tuned default chromatic fringe from 0.8f down to 0.25f–0.35f to eliminate color fringing and edge glitches.
- **Controls Optimization & Plain-Compose Glass Fallback (`core-ui`)**:
  - `LiquidGlassControls.kt`: Switched `LiquidGlassIconButton`, `LiquidGlassSegmentedControl`, and `LiquidGlassChip` to lightweight plain-Compose styling (translucent fills, specular border gradient, highlight, shadow) without costly backdrop passes. Reserved lens shaders for hero components.
- **Build & Diagnostics (`core-ui`, `app-android`)**:
  - `proguard-rules.pro`: Added keep rules for liquid glass backdrop runtime shaders and render effects for R8 release builds.
  - `FrameTimeOverlay.kt`: Created on-screen real-time FPS and frame-time profiling overlay.
- **Status**: Completed, tests pass (`:core-ui:desktopTest`, `:app-desktop:test`, `:core-dsp:test`, `:core-common:test`, `:core-network:test`). Device checks marked "human check pending".

---

## Change Log

### [2026-11-04] - Step 1: Documentation Honesty & Dynamic Verification Pass
- **Shared Code (`core-model`, `core-library`, `core-ui`)**:
  - `AudioEffectConfig.kt`: Added `isDspBypassed` calculating whether all 8 DSP modules are flat/bypassed.
  - `AudioOutputInfo.kt`: Added `isTrulyBitPerfect(srcRate, srcDepth, dspBypassed)`.
  - `AudioHeaderParser.kt`: Restored in-memory `parseBytes` function and sub-parsers (`parseFlacBytes`, `parseWavBytes`, `parseMp3Bytes`, `parseOggOrOpusBytes`, `parseM4aOrAlacBytes`) to ensure full test suite compatibility.
  - `SharedPlayerLayout.kt`: Dynamically computes truthful output status string (e.g. "Bit-Perfect Output" vs. "32-bit Float DSP Active" vs. "Direct").
- **Android (`app-android`)**:
  - `TrackDetailsSheet.kt`: Evaluates `isBitPerfect` strictly against source format and DSP bypass state. Shows "Bit-perfect direct stream" only when true.
- **Documentation**:
  - `PRIVACY.md`: Updated to "No network use unless you opt in" and documented the 4 opt-in network capabilities.
  - `README.md` & `site/index.html`: Replaced "100% offline" with opt-in disclosures. Clarified that Windows MSI/EXE are Authenticode-unsigned (SmartScreen prompt on initial run) and APK is signed with release keystore (v2 scheme). Emphasized SHA-256 as primary trust hash. Added v0.1.0 to version history.
- **Status**: Completed, tests pass, committed locally (`bf47ffb`).

---

### [2026-11-04] - Step 2: Node Connect & UI Overhaul Specification
- **Ecosystem Specs Created**:
  - `docs/ecosystem/NODE_CONNECT.md`: Comprehensive protocol, roles, gRPC service definitions, message flows, and timing constants.
  - `docs/ecosystem/NODE_CONNECT_SECURITY.md`: Zero-trust mTLS architecture, QR / PIN pairing, Android Keystore & Windows DPAPI key storage, and granular permissions.
  - `docs/ecosystem/SYNC_CATEGORIES.md`: Complete sync category rules, shared/per-platform scopes, Murmur3+MD5 track fingerprint, and 3-snapshot undo engine.
  - `docs/ecosystem/NODE_CONNECT_TESTS.md`: Automated test matrix for mTLS, fault injection, streaming, and conflict resolution.
  - `docs/ecosystem/NODE_CONNECT_DEVICE_TESTS.md`: Human verification sheet with 23 step-by-step test cases.
  - `docs/platforms/windows-ui.md`: Windows UI overhaul reference map and token definitions.
- **Status**: Completed, specification ready for implementation.

---

### [2026-11-04] - Step 3: Windows UI Overhaul Matching Reference Image
- **Compose Multiplatform Components (`core-ui`)**:
  - `SearchCapsule.kt`: Top-centered search bar with Ctrl+F auto-focus and quick clear button.
  - `DeviceConnectPill.kt`: Top-right pill button with dynamic glowing status indicator dot (Cyan = Connected, Amber = Discovering, Gray = Disconnected) and peer quick-sheet.
  - `OpticalRailDock.kt`: Left navigation rail featuring a convex liquid-glass optical lens bubble with spring physics animation and pop-out scale on drag/selection.
  - `WaveformSeekbar.kt`: Real audio peak rendering with smooth scrub support, unplayed bar dimming, and linear fallback.
  - `BottomPlayerDeck.kt`: Floating liquid glass player deck pinned to bottom edge matching reference image (`media_1793787814079_13a6cdd8.jpg`) with track artwork, favorite toggle, transport controls, waveform seekbar, volume control, authoritative quality pill (`Track.formatBadgeText`), and cast icon.
  - `HomeView.kt`: Overhauled with FOR YOU local genre mix cards (dynamic multi-artwork collages), REDISCOVER row with real play-history badges ("Last played 6 months ago", "Old favorite"), and RECENTLY PLAYED row with horizontal mouse-wheel scrolling.
  - `SettingsView.kt`: Re-architected into 2-column liquid glass card grid with search filtering, sub-sections for Audio Routing / DAC (WASAPI Exclusive mode switch), DSP & EQ, Appearance & Glass (OLED Pure Black switch), General (hotkey sheet, library rescan), and Node Connect Sync Center.
  - `SharedPlayerLayout.kt`: Unified expanded 16:9 layout with global keyboard shortcuts (<kbd>Space</kbd>, <kbd>Ctrl+F</kbd>, <kbd>Ctrl+1/2/3</kbd>, <kbd>Arrows</kbd>, <kbd>M</kbd>), keeping Android portrait layout 100% intact.
- **Status**: Completed, tests pass, committed locally (`facfbff`).

---

### [2026-11-04] - Step 4: Foundation (`core-network` Module & mTLS Security)
- **Module Creation (`core-network`)**:
  - Added `:core-network` module in `settings.gradle.kts` and configured `build.gradle.kts` (JVM 17 / Kotlin Multiplatform).
  - `KeyManager.kt`: EC-256 (secp256r1) keypair generation, pure Kotlin ASN.1 DER self-signed X.509 certificate generator (10-year validity, zero external dependencies/licenses), and colon-hex SHA-256 cert fingerprinting.
  - `PinningTrustManager.kt` & `NodeSslContext.kt`: Enforces mutual TLS (TLSv1.3 with TLSv1.2 fallback) with certificate pinning. Untrusted / unpinned certificates rejected during TLS handshake before byte processing.
  - `SecureKeyStorage.kt`: Persistent device key storage in `%APPDATA%\NodePlayer\keys` (Windows) and app-private directory (Android).
  - `NodeFraming.kt`: Binary packet framing with 4-byte magic (`NODE`), version byte, 2-byte message type, sequence ID, length, and CRC32 payload checksum.
  - `NodeMessages.kt`: Zero-reflection JSON/binary message serializers for handshakes, control commands, audio cast chunks, and sync payloads.
  - `DeviceRegistry.kt` & `PairingManager.kt`: One-Time Token (OTT) and 6-digit PIN pairing state machine with role permissions and instant revocation.
  - `HeadlessTestPeer.kt`: In-process peer runner for automated integration testing and fault injection (packet drop, jitter delay).
- **Status**: Completed, all tests pass, committed locally (`8936ec7`).

---

### [2026-11-04] - Step 5: Control Plane (Remote Control, Library Browse & Mirror)
- **Services (`core-network`)**:
  - `NodeConnectLibraryService.kt`: Remote library browse and universal search with pagination. Privacy guarantee: local absolute filesystem paths are replaced with virtual remote identifiers (`nodeconnect://remote/track/<id>`). Remote album art thumbnail retrieval.
  - `NodeConnectControlService.kt`: Remote transport command execution (Play, Pause, Seek, Next, Prev, Volume, Shuffle, Repeat) and state broadcasting to authorized controllers.
  - `NodeConnectMirrorService.kt`: Now Playing mirror service for displaying remote playback on larger screens (display only; zero local audio output).
  - `NodeConnectHub.kt`: Central lifecycle coordinator binding discovery, server, registry, and control services.
- **Status**: Completed, all tests pass, committed locally (`fb99b51`).

---

### [2026-11-04] - Step 6: Audio Sharing (Pre-DSP PCM Streaming & Jitter Buffer)
- **Audio Streaming Engine (`core-network`)**:
  - `NodeConnectCastService.kt`: Live pre-DSP raw PCM audio streamer. Zero file transmission, receiver never writes audio bytes to disk. Immediate `CAST_FLUSH` packet on seek/skip with sequence counter increment.
  - `JitterBuffer.kt`: Dynamic ring buffer (target 150-300ms) with stale packet dropping, buffer underrun/overrun protection, and instant zero-latency flush on track seek.
- **Status**: Completed, all tests pass, committed locally (`ccbe5c6`).

---

### [2026-11-04] - Step 7: Handoff & Sync Extras
- **Handoff Service (`core-network`)**:
  - `NodeConnectHandoffService.kt`: Seamless mid-track playback handoff moving active track, millisecond position, play queue, active EQ preset, and sleep timer remaining milliseconds to the target device.
- **Status**: Completed, all tests pass, committed locally (`c36e1c4`).

---

### [2026-11-04] - Step 8: Data Transfer & Sync Rail
- **Conflict-Free Sync Engine (`core-network`)**:
  - `HlcClock.kt`: Hybrid Logical Clock tracking causality across distributed devices without synchronized wall clocks.
  - `SyncEngine.kt`: Multi-domain synchronization (Playlists, Favorites, Play History & Stats, EQ Profiles). 30-day tombstones preventing resurrected deleted items. Stats union merge (`max(playCount)`, `max(lastPlayedTimestamp)`).
  - **3-Snapshot Undo Engine**: Automatically captures pre-sync snapshots before applying remote changes. Allows rolling back the last 3 sync operations with a single click.
  - `NodeConnectTransferService.kt`: Low-priority chunked transfer in 256 KB slices over TLS with progress tracking.
- **Status**: Completed, all tests pass, committed locally (`31e60f0`).

---

### [2026-11-04] - Step 9: Third-Party Media Sessions
- **Session Bridge (`core-network`)**:
  - `ExternalSessionService.kt`: Transmits now-playing metadata and forwards play, pause, and skip commands for third-party media players (Spotify, YouTube Music).
  - **Security Guarantee**: Zero audio capture or transmission. Strictly metadata and control.
  - Off by default; requires explicit `canControlExternalSessions` device permission.
- **Status**: Completed, all tests pass, committed locally (`4fcc29c`).

---

### [2026-11-05] - Step 11: Node Connect Operationalization & Diagnostics Overhaul
- **Core Network Foundation (`core-network`, `core-common`)**:
  - `NodeConnectHub.kt`: Resolved double-handshake bug on outgoing client connections. Factored out `registerAndProcessConnection(conn)` for completed handshakes (`connectToHost`, `pairWithHost`), eliminating spurious 8s timeout terminations. Added persistent device caching upon handshake completion.
  - `NodeServer.kt`: Added `SecurityLevel` configuration (`SIMPLE` as default, `STRICT` for certificate pinning and PIN requirement). Dynamic port binding across fallback range `54820..54830` with `0.0.0.0` all-interface listener.
  - `PinningTrustManager.kt` & `NodeSslContext.kt`: Added `PermissiveTrustManager` enabling unauthenticated TLS in Simple Mode without sacrificing stream encryption.
  - `NodeDiscovery.kt`: Overhauled to multi-method discovery: global broadcast (`255.255.255.255`), per-interface subnet broadcasts (`x.x.x.255`), multicast (`239.255.77.88`), default gateway probe, and 150ms parallel sweep across local `/24` subnets.
  - `NodeLog.kt`: Persistent file logging to app-private log directory with structured timestamping and feature tags (`nodeconnect/discovery`, `nodeconnect/connect`, `nodeconnect/server`, `nodeconnect/hub`).
- **Android Platform (`app-android`)**:
  - Manifest & Permissions: Added `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, and `CAMERA`.
  - `PlaybackService.kt`: Added `acquireMulticastLock()` via `WifiManager.createMulticastLock("node_player_discovery")` to prevent Android Wi-Fi power-saving from dropping UDP broadcasts.
  - `QrScannerSheet.kt`: Built offline CameraX camera scanner with ZXing `MultiFormatReader`, scan reticle, and torch control.
  - `MainActivity.kt`: Integrated "Allow Connection?" modal dialog for incoming peers with "Allow Once" / "Always Allow" / "Deny" choices.
- **Desktop Platform & Shared UI (`app-desktop`, `core-ui`)**:
  - `Main.kt`: Fixed Compose layout imports and wired `onAllowPrompt` modal. Standalone executable packaging via `createDistributable`.
  - `NodeConnectPairingDialog.kt`: 3-tab layout: "Devices" (peer list with discovery type badges, one-tap Connect, manual IP connect), "My QR Code" (compact offline URI QR code with candidate IPs and Simple Mode warning banner), and "Doctor" (adapter table, port listener status, interactive TCP ping tool, gateway probe, log export).
- **Status**: Completed, 21 unit tests pass, real-device bidirectional discovery verified in logs over phone Wi-Fi hotspot with 4-9ms latency.

---

### [2026-11-05] - Step 12: Live Control Verification, USB ADB Link & Granular Feature Switches
- **Core Network & Discovery (`core-network`)**:
  - `AdbBridge.kt`: Implemented deterministic zero-Wi-Fi USB debugging tunnel using `adb forward tcp:54825 tcp:54820` (PC to Phone) and `adb reverse tcp:54826 tcp:54820` (Phone to PC). Added `AdbBridgeTest.kt` unit test.
  - `NodeConnectHub.kt`: Surfaced `connectedPeers: StateFlow<List<ConnectedPeer>>` with live heartbeat RTT (down to 5-8ms). Added `startUsbDebuggingLink()` and transport helper functions (`sendRemotePlayPause`, `sendRemoteNext`, `sendRemotePrevious`, `sendRemoteSeek`, `sendRemoteVolume`, `disconnectDevice`).
  - `NodeConnectConfig.kt`: Expanded config to support 10 granular feature toggles, discovery method toggles (mDNS, UDP Broadcast, Hotspot Gateway Probe, Subnet Scan), Transport Encryption (TLS 1.3), Security Level (Simple vs Strict), and Verbose Network Logging with persistent storage.
- **Shared UI & Components (`core-ui`)**:
  - `NodeConnectPairingDialog.kt`:
    - Tab 0 (Devices): Active connection card with live RTT latency badge, transport controls (`[⏮]`, `[▶⏸]`, `[⏭]`), Disconnect button, and action buttons (`[Cast Audio]`, `[⇄ Handoff]`). Added Simple Mode security warning banner.
    - Tab 1 (My QR): Clean offline QR code containing candidate IP list without certificate pinning or PIN code bloat.
    - Tab 2 (Doctor): Added 1-click `[Setup USB Debugging Link (ADB 127.0.0.1)]` button alongside Socket Ping Test and Gateway Probe.
    - Tab 3 (Settings): Added 4 sections: Master & Access, Security & Trust (Security Level, Trust All, TLS 1.3 toggle), Discovery Methods (mDNS, UDP broadcast, Gateway probe, Subnet scan), Feature Toggles (10 independent switches), and Advanced / Tuning (Verbose logging, Jitter buffer, Keepalive interval).
- **Live Physical Verification (realme RMX3853 Android 16 ↔ Windows 11 PC)**:
  - Verified remote control execution from phone to PC with real log lines: `PLAY_PAUSE`, `NEXT`, `PREVIOUS` executed locally on PC playback engine.
  - Verified ADB USB loopback forwarding with `TcpTestSucceeded: True` and `REVERSE_SUCCESS`.
- **Status**: Completed (L3 Evidence achieved across all core functions).

---

### [2026-11-05] - Step 13: Pre-DSP PCM Audio Casting, Handoff & L4 User Verification
- **Audio Cast & ExoPlayer Threading (`app-android`, `core-network`)**:
  - `PlaybackService.kt`: Resolved `IllegalStateException: Player is accessed on the wrong thread` by reading `currentPosition` strictly on the main thread before dispatching raw PCM capture taps to `Dispatchers.IO`.
  - `NodeConnectCastService.kt`: Added `nodeconnect/cast` logging tags and clean start/stop lifecycle management.
  - `WindowsPlaybackEngine.kt`: Added handler for incoming remote cast stream initialization and PCM audio buffer queuing.
- **Mid-Track Handoff (`core-network`)**:
  - `NodeConnectHandoffService.kt`: Implemented fuzzy track matching fallback on desktop when identical local file paths do not exist.
- **Live Evidence & L4 User Verification**:
  - Audio Cast start and stop confirmed live between realme RMX3853 and Windows PC.
  - User confirmed core Node Connect functionality: **L4 Verified by User** ("its working").
- **Status**: Completed (L4 achieved for Core Link, Remote Control, Audio Cast, and Handoff).



### [2026-11-05] - Step 7: Node Connect UI Polish, Cross-Platform Search, Sleep Timer & Stability
- **Phone UI & Shaders**:
  - `GlassRefractionShader.kt` & `FrostedNavAndPlayer.kt`: Removed `glassLensBubble` AGSL shader distortion from the dock tab selector pill. Flattened the middle field and softened the border to 0.75dp glass shimmer (`alpha = 0.14f`), completely eliminating the "Saturn / satin ring" artifact while maintaining crystal-clear liquid-glass transparency.
  - `CrossfadePlayerManager.kt`: Fixed playback `FileNotFoundException` by checking local filesystem paths (`java.io.File(track.path)`) with `Uri.fromFile` before falling back to MediaStore IDs. Resolves song playback failures for re-scanned or searched tracks.
- **Desktop UI & Navigation**:
  - `OpticalRailDock.kt`: Removed pointer drag gesture handler that previously snapped navigation back to Home (index 0) upon clicking Library or other tabs. Dock container made translucent (`Color(0xFF0F172A).copy(alpha = 0.22f)`).
  - `SharedPlayerLayout.kt`: Enhanced Search Capsule to transition directly from Home or any screen into Library search results upon typing. Added dynamic artwork accent extraction (`extractVibrantColor`) using Skia that smoothly animates the desktop aura and highlights (650ms tween).
  - `BottomPlayerDeck.kt`: Added Sleep Timer button with dropdown menu (15, 30, 45, 60, 90 mins, turn off).
- **Node Connect Cross-Platform Search & Artwork Transfer**:
  - `NodeConnectLibraryService.kt`: Added `sequenceId = reqId` in `sendPayload` for `ARTWORK_RESULT` frames, fixing thumbnail request-response matching. Added string unescaping.
  - `NodeConnection.kt`: Supported explicit `sequenceId` in `sendPayload`.
  - `NodeConnectHub.kt` & `PlaybackService.kt`: Passed `artworkProvider` lambda returning compressed JPEG thumbnail bytes. Prefetched and cached remote artwork into in-memory `ArtworkCache`.
  - `PlatformArtworkProvider.desktop.kt`: Checked `ArtworkCache.getBytes` in `loadArtwork` before attempting local file reads, enabling instant rendering of remote peer thumbnails and cast album art.
- **Audio Streaming & Jitter Buffer**:
  - `JitterBuffer.kt` & `NodeConnectCastService.kt`: Increased buffer settings (`targetBufferMs = 700`, `minBufferMs = 450`, `maxBufferMs = 2500`) to eliminate audio jitter and micro-underruns over Wi-Fi.
  - `WindowsPlaybackEngine.kt`: Improved underrun handling by cleanly pre-buffering to `isReadyForPlayback` before resuming stream consumption. Protected WASAPI line writes against race conditions.

---

### [2026-11-07] - Step 5: Convx-Style Liquid Glass Engine & Controls (feature/liquid-dock)

- **Library & Vendoring**:
  - Vendored io.github.kyant0:backdrop (Apache 2.0) source into core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/ with license intact to guarantee multiplatform binary compatibility with Kotlin 1.9 / Compose Multiplatform 1.6.11.
  - **Android Platform**: Hardware-accelerated AGSL runtime shader (RuntimeShader.android.kt) + RenderEffect chaining via reflection (RenderEffect.android.kt).
  - **Windows / Desktop Platform**: Hardware-accelerated SkSL runtime shader via Skia RuntimeEffect (RuntimeShader.desktop.kt) and Skia ImageFilter composition (RenderEffect.desktop.kt).
- **Bottom Dock & Floating Mini Player**:
  - Android (FrostedNavAndPlayer.kt): Replaced dock and mini player with Convx-style LensDock continuous squircle capsule, edge lens refraction (L351), specular rim (L359), and sliding spring puck (L386).
  - Windows (OpticalRailDock.kt & BottomPlayerDock.kt): Convex optical lens rail dock (lens L103, rim L111, puck L132) and floating player deck using SkSL liquid glass backdrop.
- **Shared Glass Controls (LiquidGlassControls.kt)**:
  - LiquidGlassSwitch: Liquid toggle with convex knob and smooth spring sliding physics.
  - LiquidGlassSlider: Liquid slider with continuous backdrop refraction track and tactile thumb.
  - LiquidGlassButton / LiquidGlassIconButton: Capsule glass buttons with dynamic lens refraction.
  - LiquidGlassSegmentedControl & LiquidGlassChip: Filter chips and tab controls with sliding glass selection puck.
  - All controls strictly maintain touch targets >= 48 dp and full accessibility semantics (Role.Switch, Role.Button, ProgressBarRangeInfo).
- **Now Playing Screen**:
  - Multiplatform NowPlayingView.kt and Android NowPlayingSheet.kt: Updated seekbar to LiquidGlassSlider, playback controls and transport action buttons to liquid glass styling.
- **Menus & Settings**:
  - CustomizeUiView.kt and EqualizerView.kt: Replaced standard Switches and Sliders with liquid glass counterparts.
  - Added 'Dock glass' configuration group (dockGlassLensEnabled, dockGlassRimEnabled, dockGlassPuckEnabled) wired directly to drawing code.
- **Android Home-screen Widgets**:
  - Updated WidgetShaderRenderer.kt with enhanced static liquid glass rendering for offscreen RemoteViews bitmaps (squircle capsule, top-left refraction sheen, chromatic specular rim highlight).
- **Diagnostics & Safety**:
  - Verified GlassShaderSelfTest and fallback to plain frosted blur in Performance mode or on shader compilation failure.

---

### [2026-11-07] - Phase 4: Device Connect Rebuild & Comprehensive Screen Redesign (feature/liquid-dock)

- **Device Connect (Node Connect) Rebuild (`core-ui`, `app-android`, `app-desktop`)**:
  - `DeviceConnectPill.kt`: Rebuilt with continuous squircle geometry (`ContinuousRoundedRectangle(19.dp)`), specular border rim gradient, active status indicator dot with animated outer glow (Cyan `#00F0FF` for Connected, pulsing Amber `#F59E0B` for Discovering, translucent white for Disconnected), device count badges, and Convx spring press physics (`pressScale(0.95f)`).
  - Multiplatform pairing triggers, peer casting callbacks, and cast permission toggles remain 100% operational.
  - Mobile header integration: Added responsive glass pill to Android `HomeScreen.kt` top bar with real connection state from `NodeConnectHub`.
  - Desktop header integration: Preserved inline peer management and casting dropdown in `SharedPlayerLayout.kt`.
- **Now Playing Redesign (`core-ui`, `app-android`)**:
  - `NowPlayingSheet.kt` (Android): Replaced legacy RenderEffect reflection hacks with upstream Kyant0 `drawBackdrop` lens shader, specular highlight, and ambient shadow for hero play/pause button. Replaced standard corners with continuous squircle hero artwork (`ContinuousRoundedRectangle(24.dp)`). Draggable seekbar and secondary tools meet >= 48dp touch bounds with tactile spring feedback.
  - `NowPlayingView.kt` (Desktop/Common): Updated hero play button with `pressScale(0.92f)` and `drawBackdrop` lens refraction, continuous squircle artwork, and fluid animated transitions.
  - `ArtworkLoader.kt`: Upgraded `TrackArtwork` to continuous squircle clipping (`ContinuousRoundedRectangle`) with specular border rim.
- **Comprehensive Screen & Dialog Redesign (`core-ui`, `app-android`)**:
  - `HomeScreen.kt`: Replaced raw icon button with responsive `DeviceConnectPill`. Redesigned `ArtworkThumbnail` and `SmartPlaylistChip` to use continuous squircle shapes with specular border rim gradients.
  - `LibraryScreen.kt`: Modernized tab selector pills and song list artwork thumbnails to continuous squircle geometry and spring press physics.
  - `GlobalSearchSheet.kt`: Modernized bottom sheet container and search input field with `ContinuousRoundedRectangle`.
  - `SearchCapsule.kt`: Modernized top search capsule container with continuous squircle corners and specular border.
  - `EqualizerScreen.kt` & `EqualizerView.kt`: Replaced standard shapes with continuous squircle styling and draggable glass sliders.
  - `NodeConnectPairingDialog.kt`, `FirstRunOnboardingDialog.kt`, `DiagnosticsDumpDialog.kt`, `TrackDetailsSheet.kt`: Upgraded dialog modal sheets and cards to continuous squircle glass containers.
- **Windows Desktop 16:9 Alignment (`core-ui`)**:
  - `OpticalRailDock.kt`: Added dedicated Equalizer tab (`PlayerNavDestination.EQUALIZER`) to the left optical rail dock, matching Android navigation parity.
  - `BottomPlayerDeck.kt`: Modernized floating bottom deck container with continuous squircle capsule shape (`ContinuousRoundedRectangle(32.dp)`).
- **Android Home-Screen Widgets (`app-android`)**:
  - `WidgetShaderRenderer.kt`: Verified static hardware AGSL shader bitmaps (and software Canvas fallbacks) preserve matching obsidian liquid glass aesthetic without regressions.
- **Verification**:
  - Automated tests pass: `:core-ui:desktopTest`, `:app-desktop:test`, `:app-android:testDebugUnitTest`.
  - Android debug APK assembled cleanly: `:app-android:assembleDebug`.
  - Visual and device checks marked "human check pending".
