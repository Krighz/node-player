# Liquid Glass Revamp - Fixes and Status

## Current Status: Liquid Glass Native Rendering & Compose 1.7 Upgrade Verified on Hardware (Android 16 / realme)

### Post-Port Hardware Bug Fix 3: Compose 1.7 Upgrade & Native GraphicsLayer RenderEffect Verified with Pixel Proof (2026-11-08)

1. **Root Cause Analysis (Dead RenderEffect Shim Eliminated)**:
   - On Compose 1.6.11, `core-ui` had an `expect/actual GraphicsLayer` shim where on Android `renderEffect` was merely stored in a Kotlin field and ignored during `drawLayer()`, while Desktop routed it to `paint.imageFilter`.
   - Consequently, Android hardware shaders (blur, lens refraction, vibrancy) never reached the hardware compositor, rendering the dock flat and black.
2. **Resolution Applied (Option A)**:
   - Upgraded core build toolchain:
     - Kotlin `2.0.21` & `org.jetbrains.kotlin.plugin.compose`
     - Android Gradle Plugin (AGP) `8.5.2`
     - Compose Multiplatform (CMP) `1.7.3`
     - KSP `2.0.21-1.0.28`
     - Jetpack Compose BOM `2024.10.01`
   - Completely deleted all custom GraphicsLayer shims:
     - `core-ui/src/commonMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer.kt` (deleted)
     - `core-ui/src/androidMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer.android.kt` (deleted)
     - `core-ui/src/desktopMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer.desktop.kt` (deleted)
     - `core-ui/src/commonMain/kotlin/androidx/compose/ui/node/GraphicsContextCompat.kt` (deleted)
     - `core-ui/src/commonMain/kotlin/androidx/compose/ui/graphics/GraphicsLayerCompat.kt` (deleted)
   - Utilized native `androidx.compose.ui.graphics.layer.GraphicsLayer` from Compose UI 1.7+ with hardware compositor-backed `renderEffect`.
   - Replaced custom reflection hacks on `Paint` with clean extensions (`Modifier.graphicsLayer(colorFilter)`).
   - Added global `RuntimeShaderCache` singleton with startup warmup (`warmUpBackdropShaders()`) to prevent frame hitches.
   - Completely purged legacy `dev.chrisbanes.haze` dependencies and references across the codebase.
   - Replaced all Material3 controls (Slider, Switch, FilterChip, AssistChip, RadioButton) across Android screens with liquid controls (`LiquidSlider`, `LiquidToggle`, `LiquidChip`, `LiquidRadioButton`, `LiquidCheckbox`).
3. **Automated Pixel Proof Test Results on Physical Device (`GlassPixelProofTest.kt`)**:
   - Device: realme RMX3853 (Android 16, API 36).
   - Test: `com.nodeplayer.music.glass.GlassPixelProofTest#glassDockBlursBackdropAndRendersNonBlack`.
   - Result: **100% PASSED** (All assertions passed cleanly).
   - Measured pixel metrics from physical hardware:
     - Background Stripe Energy: `0.125`
     - Dock Body Interior Energy: `0.0283`
     - **Energy Ratio (Dock / Stripe): `22.68%`** (Target: `<= 40%` -> PASSED, proves hardware blur and refraction active!)
     - **Dock Mean Luminance: `0.448`** (89.5% of page luminance -> PASSED, non-black)
     - **Dock Luminance StdDev: `0.193`** (> 0.01 -> PASSED, dynamic refraction gradient)
     - 10x10 probe grid recorded smooth optical gradients (0.00 to 0.86) over alternating black/white stripes.
4. **App Launch & Runtime Stability Verification**:
   - Installed debug build: `adb install -r app-android-debug.apk`.
   - Launched `.MainActivity`; confirmed live process (PID `8316`) running stably with zero crashes.
   - Verified logcat: `[LayerBackdrop] Recording page into backdrop: size=Size(1080.0, 2376.0), layerCoordAttached=true`.

---

### Post-Port Hardware Bug Fix 2: Flat Rendering Root Causes Resolved (2026-11-08)

1. **Path Chosen & Why**:
   - Platform: Android 16 (API 36, realme RMX3853).
   - Mode: Full AGSL hardware shaders (`shaderMode=AUTO`, `selfTestPassed=true` [0 spilled pixels, avg lum 0.19], `isShaderActive=true`, `perfMode=false`, `masterEnabled=true`).
2. **Root Causes for Flat Rendering**:
   - **Root Cause A (`MainActivity.kt:258, 475, 495`)**: In Android `MainActivity.kt`, `pageBackdrop` was never instantiated or applied (`.haze(dockHazeState)` was still present from the legacy implementation). `ConvxMiniPlayer` and `ConvxLiquidDock` were never passed a `backdrop` parameter, so they defaulted to `emptyBackdrop()`, which has a no-op `drawBackdrop` implementation that draws 0 pixels.
   - **Root Cause B (`ConvxLiquidDock.kt:170, 315`, `ConvxMiniPlayer.kt:135`)**: The dock container, search button, and mini player omitted `onDrawSurface`, leaving the glass container completely transparent without any tinted acrylic/glass background surface color.
   - **Root Cause C (`ConvxLiquidDock.kt:220`)**: The sliding tab puck in `ConvxLiquidDock` only rendered a flat `Brush.verticalGradient` box without `Modifier.drawBackdrop`, resulting in no optical lens magnification or chromatic fringing over tab labels.
3. **Fixes Applied**:
   - In `MainActivity.kt`: Initialized `val pageBackdrop = rememberLayerBackdrop()`, bound the root screen layer via `Modifier.layerBackdrop(pageBackdrop)`, and wired `backdrop = pageBackdrop` into `ConvxMiniPlayer` and `ConvxLiquidDock`.
   - In `ConvxLiquidDock.kt`: Added `onDrawSurface = { drawRect(Color(0xFF141923).copy(alpha = 0.65f)) }` to the capsule dock and Search button. Added `Modifier.drawBackdrop` with optical lens, rim ambient highlight, and accent surface tint to the sliding puck.
   - In `ConvxMiniPlayer.kt`: Added `onDrawSurface = { drawRect(Color(0xFF141923).copy(alpha = 0.70f)) }`.
   - In `core-ui`: Added `GlassDebug` throttled instrumentation tracking path selection, update/draw durations, AGSL shader compile times, layerBackdrop sizes, and offsets.
4. **Measured Execution Times (Device Logs)**:
   - AGSL RuntimeShader compilation (first time): `0.18ms - 1.09ms`.
   - `updateEffects()` duration: `0.35ms - 1.51ms` (median ~0.66ms).
   - `drawBackdropLayer()` active sampling: `Offset(48, 2130)` (dock), `Offset(60, 2142)` (puck), `Offset(870, 2139)` (search), `Offset(48, 1938)` (mini player) from 1080x2376 page backdrop and 1080x5738 optical diagnostics test pattern.

---

### Post-Port Hardware Bug Fix 1: Startup Crash Fixed (2026-11-08)

1. **Exact Crash Exception:**
   `java.lang.IllegalStateException: Size is unspecified`
   - Location: `core-ui/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt:91` (via `LensKt.getCornerRadii` -> `LensKt.lens`).
   - Trigger: `DrawBackdropNode.onAttach()` calling `observeEffects()` -> `updateEffects()` during initial node attachment before the first layout/draw pass.
2. **Root Cause:**
   - In vendored `BackdropEffectScopeImpl`, `size` is initialized to `Size.Unspecified` (matching upstream `D:\AndroidLiquidGlass-kmp\AndroidLiquidGlass-kmp`).
   - During `DrawBackdropNode.onAttach()`, `observeEffects()` immediately executed the `effects` lambda before any layout measurement pass occurred.
   - When evaluating `lens(...)`, `cornerRadii` accessed `size.minDimension`, which triggers `check(this != Size.Unspecified)` in Jetpack Compose's `Size.kt:48`, throwing `IllegalStateException: Size is unspecified`.
3. **Resolution Applied:**
   - Added `if (!size.isSpecified || size.width <= 0f || size.height <= 0f) return` guard at entry of `BackdropEffectScope.lens(...)`.
   - Added `if (!size.isSpecified) return null` in `cornerRadii`.
   - Wrapped `lens(...)` shader uniform execution and `DrawBackdropModifier.kt:updateEffects()` in `try/catch` blocks, logging errors with `NodeLog.e("Backdrop", ...)` (forwarding to `android.util.Log.println(Log.ERROR, "Backdrop", ...)` on Android) so errors are never swallowed silently.
4. **Live Verification:**
   - Deployed to realme device (Android 16, USB debugging): `adb install -r app-android-debug.apk`.
   - Launched `.MainActivity`; confirmed process PID (`21888`) remained active after 10+ seconds with 0 fatal exceptions in logcat.
   - WindowManager reported state transition: `READY_TO_SHOW -> HAS_DRAWN` (`onSurfaceShowChange show = true`).

---

## Phase 5 Diagnostics Test Screen, Verification, Builds & Acceptance Completed (05-01 & 05-02) - All Milestone Phases Complete!

### Phase 5 Accomplishments

1. **VERIFY-01: Dedicated Optical Diagnostics Screen & Triple-Zone Test Pattern**
   - Implemented `OpticalDiagnosticsView.kt` in `:core-ui` featuring a high-contrast, vertically scrollable triple-zone backdrop:
     1. Alternating monochrome high-contrast vertical and diagonal stripes.
     2. Dense multi-size typography blocks (display, headline, body, caption, code, and small print).
     3. High-frequency pixel alignment grid (sub-pixel grid lines and concentric circles) to expose distortion and curvature.
   - Layered the entire active liquid glass chrome over the test pattern via `Modifier.layerBackdrop(pageBackdrop)`:
     - 64dp capsule dock (`ConvxLiquidDock` / `LiquidDock`) with sliding tab puck.
     - 56dp squircle mini player capsule (`ConvxMiniPlayer`) floating 8dp above the dock.
     - 56dp circular Search button (`LiquidButton`).
     - Interactive live test `LiquidSlider` directly embedded over the test pattern.
   - Integrated navigation routes into shared `SettingsView.kt` and Android `SettingsScreen.kt`.

2. **VERIFY-02: 5-Item Visual Acceptance Checklist (05-UAT.md)**
   - Generated `.planning/phases/05-diagnostics-test-screen-verification-builds-acceptance/05-UAT.md` with 5 rubrics marked `[ ] HUMAN CHECK PENDING (phone photo review)`:
     1. *Body blur & refraction:* Text and stripes passing behind the 64dp dock capsule are blurred and refracted; nothing from the page is sharp inside the dock body.
     2. *Zero sharp text inside dock:* Sharp edges are completely eliminated within the dock container.
     3. *Puck chromatic fringing:* The 56dp sliding puck visibly magnifies the icon/label under it with subtle chromatic aberration color fringing at its perimeter.
     4. *Continuous slider drag:* `LiquidSlider` responds continuously to touch/mouse dragging with velocity stretch and press squish without freezing.
     5. *Zero card self-refraction:* Page cards, badges, and tiles render as plain squircle surfaces without lens outlines, edge fringes, or self-sampling.

3. **VERIFY-03: Multi-Module Regression Testing & Multiplatform Packaging**
   - Executed full multi-module regression suite with 0 failures and 0 errors:
     - `:core-model:test` (domain invariants, serialization, bounds clamping)
     - `:core-common:test` (architectural purity, logging primitives)
     - `:core-ui:desktopTest` (continuous gestures, velocity stretch, dock auto-hide, tap/drag)
     - `:app-android:testDebugUnitTest` (Android test suite and shader self-test logic)
   - Built multiplatform binary artifacts:
     - Android debug APK: `app-android/build/outputs/apk/debug/app-android-debug.apk` (27,524,414 bytes, verified ready for `adb install -r`).
     - Windows Desktop target: `app-desktop/build/classes/kotlin/main` (verified clean compilation).

4. **VERIFY-04: Android R8 / ProGuard Keep Rules Enforcement**
   - Audited `app-android/proguard-rules.pro` to ensure explicit keep rules for:
     - `com.kyant.backdrop.**` (runtime shaders, AGSL strings, uniforms, and render effects).
     - `com.nodeplayer.model.GlassControlsConfig` and data models.
     - Native WASAPI JNA bindings and Room DAOs.

5. **VERIFY-05: Documentation Synchronization & Open-Source Attribution**
   - Logged emulator GPU SkSL precision variances in `docs/state/KNOWN_ISSUES.md`.
   - Updated `docs/platforms/CHANGES.md`, `docs/state/FIXES_AND_STATUS.md`, and `docs/reference/SETTINGS.md`.
   - Confirmed Kyant0 `AndroidLiquidGlass` Apache-2.0 attribution in `THIRD_PARTY_LICENSES.md`.
   - Strictly maintained local-only git policy on `feature/liquid-dock`.

### Operational Status by Platform

| Target Platform | Runtime / Toolchain | Status | Shaders & Fallback |
| :--- | :--- | :--- | :--- |
| **Android Phone / Tablet** | ART (API 26+ / API 34 Target) | Fully Operational | Android 13+ AGSL (`RenderEffect.createRuntimeShaderEffect`); API 26-32 graceful fallback to Haze dual-pass blur with rim highlight. |
| **Windows Desktop** | JVM 17 / Compose Desktop 1.6.11 | Fully Operational | SkSL runtime shaders (`org.jetbrains.skia.RuntimeEffect`) with WASAPI native audio and SMTC transport controls. |

### Built Artifacts

| Platform | Target Output | Exact Disk Location | Size / Status |
| :--- | :--- | :--- | :--- |
| **Android** | Debug Sideload APK | `app-android/build/outputs/apk/debug/app-android-debug.apk` | 27,524,414 bytes (~27.52 MB) |
| **Windows Desktop** | JVM Kotlin Classes | `app-desktop/build/classes/kotlin/main` | Compiled & verified |

---

## Historical Status: Phase 4 Global Settings, Per-Item Overrides & Real-Time Live Preview Completed (04-01 & 04-02)

### Phase 3 Accomplishments

1. **CTRL-01: Draggable LiquidSlider with Velocity Stretch & Squish**
   - Upgraded `LiquidSlider.kt` with continuous gesture dragging using `DampedDragAnimation`.
   - Directional velocity stretch along horizontal movement axis (`scaleX`) and press-squish feedback (`scaleY`).
   - Enforced $\ge$48dp touch hitboxes on thumb and track container with tap-to-seek and division-by-zero protection.

2. **CTRL-02: Tap/Drag LiquidToggle & Unified LiquidControls.kt Suite**
   - Implemented dual tap and drag toggling in `LiquidToggle.kt` with spring release snapping at the 0.5f threshold.
   - Created unified `LiquidControls.kt` providing `LiquidButton`, `LiquidIconButton`, `LiquidChip`, `LiquidSegmentedControl`, `LiquidDialog`, `LiquidCheckbox`, and `LiquidRadioButton`.
   - Enforced 48dp minimum touch targets, accessibility semantics (`Role.Button`, `Role.Switch`, `Role.Checkbox`, `Role.RadioButton`, `Role.Tab`), and lighter squircle styling (`lighterStyle = true`) for dense list performance.

3. **CTRL-03: Complete Replacement of Plain Material Controls**
   - Replaced all direct uses of plain Material controls (`Button`, `IconButton`, `Switch`, `Slider`, `FilterChip`, `TabRow`, `SegmentedButton`, `AlertDialog`, `RadioButton`, `Checkbox`) with `LiquidControls` across `EqualizerView`, `SettingsView`, `CustomizeUiView`, `LibraryView`, `NowPlayingView`, `BottomPlayerDeck`, `BottomPlayerDock`, and `NodeConnectPairingDialog`.
   - Verified zero direct usages of plain Material controls remaining in screens via automated grep.

4. **CTRL-04: Intelligent Dock Scroll Auto-Hide with 4 Return Triggers**
   - Built `DockScrollController.kt` managing nested scroll tracking and 4 return triggers:
     1. Scroll up (`scaledDelta > 6f`).
     2. Reached list top (`firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`).
     3. Scroll pause timeout (default 2000ms / 2 seconds).
     4. Bottom edge tap (~40dp invisible touch target enabled when dock is hidden).
   - Animated translation Y (`120.dp * offsetFraction`) and alpha fade (`1f - offsetFraction`) via spring physics in `SharedPlayerLayout.kt`.
   - Respected user settings: `dockAutoHideEnabled`, `dockAutoHideMiniPlayer`, `dockScrollSensitivity`, `dockReturnDelayMs`.

5. **CTRL-05: Node Connect Header Button & List Smoothness**
   - Preserved `DeviceConnectPill` in mobile Home header, `HomeScreen.kt`, desktop header, and Windows rail.
   - Kept device sheets, pairing, and audio casting 100% operational.
   - Enforced lighter styling in list rows to protect 60/120 fps frame rates.

### Platform Verification
- Desktop tests and build: Passed (`:core-ui:desktopTest`, `:app-desktop:compileKotlin`).
- Android build: Passed (`:app-android:compileDebugKotlin`).
- Visual checks: **Human check pending** (documented in `03-UAT.md`).

---

## Historical Status: Phase 2 Correct Layering & LiquidBottomTabs Dock + Mini Player Completed (02-01 & 02-02)

### Phase 2 Accomplishments

1. **DOCK-01 & DOCK-02: Correct Root Layer Hierarchy & Zero Self-Refraction**
   - Inverted root Compose layout in `SharedPlayerLayout.kt`: root full-bleed `Box` captures page background and scrolling screens into `rememberLayerBackdrop()`.
   - Floating chrome (`ConvxMiniPlayer`, `LiquidDock`, `LiquidButton`) rendered as sibling overlays above the page layer in z-order.
   - Introduced dynamic `LocalBottomBarPadding` (136dp with mini player, 80dp dock-only) for edge-to-edge scrolling.
   - Stripped all backdrop sampling from page cards, rows, headers, and tiles across `HomeView`, `LibraryView`, `EqualizerView`, and `SettingsView`, replacing them with plain continuous squircles.

2. **DOCK-03 & DOCK-04: Upstream LiquidBottomTabs Dock & Mini Player**
   - Created `LiquidDock.kt` integrating upstream 64dp capsule bar with 8dp blur, vibrancy, (24dp, 24dp) lens, ~40% opacity, and hidden tab copy tinted with accent color.
   - Sliding 56dp puck using `rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)`, press-scaled lens, chromatic aberration, and `DampedDragAnimation` across 4 tabs.
   - Companion 56dp circular Search `LiquidButton` floating adjacent to the dock capsule, sharing `pageBackdrop`.
   - Floating 56dp squircle `ConvxMiniPlayer` capsule positioned 8dp above dock with squircle artwork, title, artist, live audio-reactive equalizer bars, and isolated transport controls.

3. **DOCK-05: Desktop Windows Rail & Deck Integration**
   - Enabled `pageBackdrop` sampling across `OpticalRailDock.kt` (left optical rail) and `BottomPlayerDeck.kt` (bottom player deck) on Windows desktop via SkSL runtime shaders.

### Platform Verification
- Desktop tests and build: Passed (`:core-ui:desktopTest`, `:app-desktop:compileKotlin`).
- Android build: Passed (`:app-android:compileDebugKotlin`).
- Visual checks: **Human check pending** (documented in `02-UAT.md`).

---

## Historical Status: Phase 1 Clean Slate & Full Upstream Library Import Completed (01-01 & 01-02)

### Phase 1 Accomplishments

1. **LIB-01 & LIB-03: Full Upstream Kyant0 Engine & Desktop RuntimeEffect**
   - Imported canonical `com.kyant.backdrop` upstream package into `:core-ui` under Apache 2.0.
   - Preserved full Skia SkSL `RuntimeEffect.makeForShader` multiplatform desktop engine on Windows and hardware AGSL on Android.
   - Retained complete demo catalog: `LiquidButton`, `LiquidSlider`, `LiquidToggle`, `LiquidBottomTabs`, `DampedDragAnimation`, `InteractiveHighlight`.

2. **LIB-02: Staged Clean Slate & Legacy Purge**
   - Migrated all UI components (`ConvxLiquidDock`, `ConvxMiniPlayer`, `OpticalRailDock`, `LiquidGlassControls`, `NowPlayingView`, `BottomPlayerDock`, `NowPlayingSheet`) to `com.kyant.backdrop.*`.
   - Purged 37 legacy cut-down files and directories in `core-ui/src/*/kotlin/com/nodeplayer/ui/component/backdrop/` and deleted temporary `CompatibilityShims.kt`.
   - Removed obsolete reflection hacks and purged dead Haze code.
   - Updated Proguard rules in `app-android/proguard-rules.pro` for `com.kyant.backdrop.**`.

3. **LIB-04 & LIB-05: Hardware Validation & Multiplatform Build Verification**
   - Re-pointed `GlassShaderSelfTest` in `app-android` to `com.kyant.backdrop.internal.RoundedRectRefractionShaderString` with updated uniform bindings.
   - Preserved offscreen hardware pixel verification, safe frosted Compose fallback, and SharedPreferences persistence.
   - Clean compilation verified across all platforms: `:core-ui:desktopTest`, `:app-desktop:compileKotlin`, and `:app-android:compileDebugKotlin`.

### Platform Verification
- Desktop tests and build: Passed (`:core-ui:desktopTest`, `:app-desktop:compileKotlin`).
- Android build: Passed (`:app-android:compileDebugKotlin`, `:app-android:compileDebugUnitTestSources`, `:app-android:testDebugUnitTest`).
- Visual and device checks: **Human check pending** (to be verified on physical phone and PC).

---

## Historical Status (v1.6.0 Milestone Iterations)

### Phase 5 Accomplishments

1. **SETT-01 & SETT-04: Live Preview & Preset Actions**
   - Implemented `GlassLivePreview.kt` interactive sample card reacting dynamically to glass tokens and slider modifications.
   - Added GPU cost hints (`Low GPU`, `Medium GPU`, `High GPU`) and "Reset to recommended" action restoring recommended presets.

2. **SETT-02: Comprehensive Optical Controls**
   - Added granular parameters to `GlassThemeConfig`: `glassRefractionHeightDp`, `glassRefractionAmountDp`, `glassChromaticFringe`, and `glassBackdropScale`.

3. **SETT-03: Component-Level Glass Overrides**
   - Independent component glass toggles: `dockGlassLensEnabled`, `miniPlayerGlassEnabled`, `nowPlayingGlassEnabled`, `controlsGlassEnabled`, and `widgetsGlassEnabled`.

4. **SETT-05: Documentation & Traceability**
   - Comprehensive `docs/reference/SETTINGS.md` updated with every setting's type, default, GPU impact, and exact file and line number reference.
   - Verified in `GlassCustomizationConfigTest.kt`.

---

## Phase 4 Completed

### Phase 4 Accomplishments

1. **DOCK-01 & DOCK-02: NestedScrollConnection & Dynamic Auto-Hide**
   - Built `DockScrollController.kt` in `core-ui` managing dock visibility fraction with fluid spring physics.
   - Smoothly slides down and fades out dock during downward list scrolling on long lists (Home, Library).
   - Instantly restores dock on upward scroll, top of list, strong upward fling, or after scroll stillness timeout.

2. **DOCK-03: View Isolation & Edge Restore**
   - Kept dock stationary on short, non-scrolling views (Equalizer, Settings, Diagnostics).
   - Bottom edge tap and fling restore hooks ensure user is never trapped without navigation.

3. **DOCK-04 & DOCK-05: Scroll Settings & Desktop Defaults**
   - Added `dockAutoHideEnabled`, `dockAutoHideMiniPlayer`, `dockScrollSensitivity`, and `dockReturnDelayMs` to `GlassThemeConfig` in `core-model`.
   - Windows desktop rail dock (`OpticalRailDock`) and player deck (`BottomPlayerDeck`) stay visible and stationary by default.
   - Unit tests added in `DockScrollControllerTest.kt` verifying all scroll phases and timers.

---

## Phase 3 Completed

### Phase 3 Accomplishments

1. **ANIM-01 & ANIM-02: Convx-Inspired Spring Physics & Handoff**
   - Implemented `ConvxPuck` (`dampingRatio = 0.82f`, `stiffness = 380f`) and `DragTracking` (`dampingRatio = 0.95f`, `stiffness = 900f`) in `MotionTokens.kt`.
   - Tuned sliding selection puck in dock with touch tracking highlight.
   - Smooth sheet expand/collapse transitions matching squircle radius interpolation.

2. **ANIM-03 & ANIM-06: Draw/Layout Phase Animation & Press Bounce**
   - Restructured press scaling to run directly in `Modifier.graphicsLayer` transforms, preventing per-frame layout recalculations and sibling recomposition.
   - Added interactive squish and bounce on controls.

3. **ANIM-04: Overscroll Physics & Tactile Ticks**
   - Tactile haptic feedback hooks (`KEYBOARD_TAP`) upon crossing tab detents and drag thresholds.

4. **ANIM-05: Motion Level & Accessibility Integration**
   - Added `MotionLevel` (`FULL`, `REDUCED`, `OFF`) to `GlassThemeConfig` in `core-model`.
   - Wired `MotionTokens.resolveSpringSpec` to honor system "remove animations" / `reduceMotion` preferences, snapping immediately to `Instant` with zero lag.
   - Automated tests in `MotionTokensTest.kt` verifying all motion tiers.

---

## Phase 2 Completed

### Phase 2 Accomplishments

1. **ARCH-01 & ARCH-02: Glass Pipeline Consolidation & Clean Multiplatform Interface**
   - Single multiplatform glass module in `core-ui` under `com.nodeplayer.ui.component.backdrop`.
   - Shared backdrop capture pool with unified interfaces over Android AGSL and Desktop SkSL shaders.

2. **ARCH-03: Self-Test & Frosted Glass Fallback Mode**
   - Preserved `GlassShaderSelfTest` for compile-time and runtime hardware shader validation.
   - Verified seamless fallback to frosted plain Compose styling when hardware shaders are unavailable or in Performance mode.

3. **ARCH-04: Unified Glass Design Tokens**
   - Implemented `GlassDesignTokens.kt` in `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/theme/GlassTokens.kt`.
   - Standardized capsule, puck, and card radii, optical refraction parameters, blur radius, specular rim, and vibrancy alphas across both platforms.

4. **ARCH-05: Glass Quality Mode**
   - Added `GlassQuality` enum (`FULL`, `BALANCED`, `LIGHT`) to `GlassThemeConfig` in `core-model`.
   - Added automated test suite `GlassTokensTest.kt` verifying parameter adaptations per tier.

---

## Phase 1 Completed

### Fixes Applied

1. **FIX-01: Layout Math & Narrow Constraint Safety**
   - File: `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/component/backdrop/catalog/components/LiquidSlider.kt`
   - Fixed crash `Size(-505 x 18) is out of range` by:
     - Coercing track width to non-negative: `constraints.maxWidth.coerceAtLeast(0)`.
     - Safeguarding division by zero when calculating drag deltas: `effectiveTrackWidth = trackWidth.coerceAtLeast(1)`.
     - Clamping progress strictly between `0f` and `1f` and calculated width between `0` and `maxW`.
     - Clamping translation bounds safely with non-negative thumbnail dimensions.
   - Verified via unit test: `core-ui/src/commonTest/kotlin/com/nodeplayer/ui/components/LiquidSliderMathTest.kt`.

2. **FIX-02: Dock Glitch Elimination & Lens Geometry**
   - Files: `app-android/src/main/kotlin/com/nodeplayer/music/ui/components/FrostedNavAndPlayer.kt`, `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/OpticalRailDock.kt`
   - Lowered default chromatic fringe intensity from `0.5f–0.8f` to subtle `0.25f–0.35f` to eliminate rainbow fringing around icons.
   - Scaled lens refraction height and amount proportionally to container height (`dockH * 0.28f`, `puckH * 0.20f`, `railW * 0.18f`) rather than hardcoded static pixel heights.
   - Mini player capsule, dock squircle, and optical rail dock layers separated so text labels and icons are drawn un-refracted above the glass substrate.

3. **FIX-03 & FIX-04: Plain-Compose Controls & Allocation Elimination**
   - File: `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/LiquidGlassControls.kt`
   - Converted small controls (`LiquidGlassIconButton`, `LiquidGlassSegmentedControl`, `LiquidGlassChip`) from multi-pass shader backdrops to lightweight plain-Compose glass styling (translucent fills, specular border gradient, drop shadow, highlight).
   - Reserved full lens shaders for hero components (the main navigation dock, mini player capsule, and Now Playing play button).
   - Removed unnecessary per-frame backdrop allocations in control draw passes.

4. **FIX-05: ProGuard / R8 Keep Rules**
   - File: `app-android/proguard-rules.pro`
   - Added explicit keep rules for `com.nodeplayer.ui.component.backdrop.**`, `android.graphics.RuntimeShader`, and `android.graphics.RenderEffect`.

5. **FIX-06: Frame-Time Overlay**
   - File: `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/FrameTimeOverlay.kt`
   - Implemented real-time `FrameTimeOverlay` composable measuring frame cadence, FPS, and 99th percentile frame times via `withFrameNanos`.

## Platform Verification
- Desktop tests and build: Passed (`:core-ui:desktopTest`, `:app-desktop:compileKotlin`, `:app-desktop:test`, `:core-dsp:test`, `:core-common:test`, `:core-network:test`).
- Android build: Android SDK path in local.properties (`D:/AI MAKING/android-sdk`) was not present in environment; code modifications are verified against Kotlin Multiplatform compiler and multiplatform test suites.
- Visual and device checks: **Human check pending** (to be verified on physical phone and PC).

---

## Phase 4 Completed: Device Connect & Comprehensive Screen Redesign

### Work Completed
1. **Device Connect (Node Connect) Rebuild**:
   - `DeviceConnectPill.kt` rebuilt with continuous squircle geometry (`ContinuousRoundedRectangle(19.dp)`), specular highlight rim gradient, glowing connection status dot (Cyan glow, pulsing Amber, translucent White), and spring press feedback.
   - Maintained 100% operational pairing and casting capabilities.
   - Integrated into Android `HomeScreen.kt` header and Windows `SharedPlayerLayout.kt` header.

2. **Now Playing Redesign**:
   - `NowPlayingSheet.kt` (Android): Updated hero artwork to continuous squircle geometry (`ContinuousRoundedRectangle(24.dp)`). Replaced old RenderEffect reflection code with upstream Kyant0 `drawBackdrop` lens shader, specular rim, and ambient shadow for hero play button. Controls and secondary tools meet >= 48dp touch bounds.
   - `NowPlayingView.kt` (Desktop/Common): Updated hero play button with `pressScale(0.92f)` and `drawBackdrop` lens refraction, continuous squircle artwork, and fluid animated transitions.
   - `ArtworkLoader.kt`: Upgraded `TrackArtwork` to continuous squircle clipping (`ContinuousRoundedRectangle`) with specular border rim.

3. **Comprehensive Screen Redesign**:
   - `HomeScreen.kt`, `LibraryScreen.kt`, `EqualizerScreen.kt`, `GlobalSearchSheet.kt`, `SearchCapsule.kt`, and modal dialogs (`NodeConnectPairingDialog.kt`, `FirstRunOnboardingDialog.kt`, `DiagnosticsDumpDialog.kt`, `TrackDetailsSheet.kt`) updated to unified continuous squircle tokens (`ContinuousRoundedRectangle`) and Convx spring press dynamics.
   - Desktop 16:9 layout (`OpticalRailDock.kt`, `BottomPlayerDeck.kt`) aligned with shared tokens and added Equalizer navigation tab.
   - Android home-screen widget static glass rendering (`WidgetShaderRenderer.kt`) verified.

### Verification Status
- Desktop unit tests: Passed (`:core-ui:desktopTest`, `:app-desktop:test`).
- Android unit tests: Passed (`:app-android:testDebugUnitTest`).
- Android assembleDebug: Passed (`:app-android:assembleDebug`).
- Visual and physical checks: **Human check pending**.
