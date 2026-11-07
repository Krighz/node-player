# 02-ui-analysis.md — UI Architecture, Design Tokens & Motion Systems

> **Project Reference:** Convx (`D:\Convx-main`)  
> **Audience:** Node Player UI/UX & Shader Engineering  
> **Mode:** Pure Research & Analysis (Read-Only)

---

## 1. UI Toolkit, Versions & Minimum Requirements

### 1.1 UI Stack
- **Framework:** Jetpack Compose (`1.10.2`) running on Kotlin `2.3.10`.
- **Target OS:** Android 14 / 15 / 16 (API Level 34–36).
- **Minimum OS Supported:** Android 8.0 (API Level 26).
- **Hardware Glass Requirement:** Android 12 (API Level 31 / Snow Cone) for `android.graphics.RenderEffect`. Full optical SDF lens refraction requires Android 13 (API Level 33 / Tiramisu) for `android.graphics.RuntimeShader` (AGSL).
- **Fallback Thresholds:** Evaluated via `isGlassAllowed()` ([GlassEffect.kt:181-183](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/GlassEffect.kt#L181-L183)):
  1. Devices with `Build.VERSION.SDK_INT < 31` automatically fall back to translucent solid tint surfaces (`shouldUseTranslucentGlassFallback`).
  2. Devices flagged as low RAM via `ActivityManager.isLowRamDevice` fall back to solid background clips to prevent Out-Of-Memory (OOM) and GPU stall conditions.

### 1.2 Glass & Blur Libraries Used
- **Primary Optical Glass:** Kyant0 Backdrop v2.0.0 (vendored in `com.convx.music.ui.component.backdrop`), modified to remove external dependencies and support scaled layer recording (`GlassPoolScale = 0.35f`).
- **Secondary Frosted Chrome:** Chris Banes Haze (`1.3.1`), used selectively on legacy dialogs and secondary bottom sheets.
- **Cupertino Design Engine:** Vendored and custom-built Cupertino library in `:hig` (`zone.ien.hig`), providing native iOS-exact blur, segmented controls, activity indicators, and sheet presentation.

---

## 2. Design Tokens: Anatomy & Location

All visual constants and design language tokens are centrally defined in [AppleTokens.kt](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/theme/AppleTokens.kt) and [GlassTintTokens.kt](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/theme/GlassTintTokens.kt).

### 2.1 Color Tokens
- **Backgrounds:**
  - `AppleTokens.Bg`: `#121212` (Default soft dark background, preventing OLED smearing).
  - `AppleTokens.BgElevated`: `#1A1A1A` (Layered surfaces).
  - `AppleTokens.Card`: `#1C1C1E` (Standard iOS-style card background).
  - `AppleTokens.CardSecondary`: `#2C2C2E` (Nested card or grouped list background).
  - `PureBlack`: `#000000` (User-selectable OLED power-saving mode).
- **Accent & Interaction:**
  - `AppleTokens.AccentRed`: `#FA2D48` (Apple Music signature crimson accent).
  - Dynamic artwork tint derived via `MaterialKolor` and `Palette` HCT color spaces.
- **Glass Tinting:**
  - `GlassTintTokens.BaseSurfaceOpacityDark`: `0.50f`
  - `GlassTintTokens.BaseSurfaceOpacityLight`: `0.20f`
  - `GlassTintTokens.pluckColor`: Specular white highlight adjusted for hero artwork tints.

### 2.2 Corner Radii & Continuous Shapes
Convx utilizes continuous curvature (squircle / superellipse) shapes rather than circular arcs, matching iOS HIG (`ContinuousRoundedRectangle`):
- `AppleTokens.Artwork`: `10.dp` (Album cover tiles).
- `AppleTokens.Control`: `14.dp` (Buttons and input capsules).
- `AppleTokens.CardCorner`: `20.dp` (Standard floating cards and mini-player).
- `AppleTokens.CardCornerLarge`: `26.dp` (Bottom sheet dialogs and modals).
- `PillCorner`: `999.dp` (Floating pill bars and navigation pucks).

### 2.3 Typography Scale
Defined in [AppleTokens.kt:53-70](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/theme/AppleTokens.kt#L53-L70) with fixed line-height pairings to eliminate Compose default leading issues:
- `TitleLarge`: `34.sp` (Line height: `41.sp`, SemiBold)
- `SectionHeader`: `22.sp` (Line height: `28.sp`, Bold)
- `ItemTitle`: `15.sp` (Line height: `20.sp`, Medium)
- `ItemSubtitle`: `13.sp` (Line height: `18.sp`, Regular)
- `Caption`: `11.sp` (Line height: `13.sp`, Regular)

### 2.4 Layout Gutters & Spacing
- `AppleTokens.Gutter`: `20.dp` (Unified horizontal screen margin).
- `AppleTokens.ItemGap`: `16.dp` (Inter-item spacing in lazy grids).
- `AppleTokens.SectionGap`: `24.dp` (Inter-section vertical spacing).
- `AppleTokens.TextGap`: `2.dp` (Title-to-artist line gap).

---

## 3. Screen Inventory & Reusable Component System

### 3.1 Screen Catalog
Located in `app/src/main/kotlin/com/convx/music/ui/screens/`:
1. **Home (`HomeScreen.kt` / `HomeLocalContent.kt`):** Dynamic feed containing shelves, recently played, speed dial, and featured albums.
2. **Explore (`ExploreScreen.kt`):** New releases, charts, and genre explorations.
3. **Library (`LibraryScreen.kt` / `library/`):** Local & remote music library filtered by Songs, Albums, Artists, and Playlists.
4. **Player (`ui/player/Player.kt`):** Full-screen Now Playing view with synchronized dynamic artwork mesh background, karaoke lyrics, and queue sheets.
5. **Artist & Album Details (`ArtistScreen.kt`, `AlbumScreen.kt`):** Parallax hero header with real-time backdrop blur.
6. **Equalizer (`equalizer/`):** Multi-band parametric EQ, AutoEQ profile importer, and convolver impulse response loader.
7. **Settings (`settings/`):** Granular customization panels (`GlassEffectSettings.kt`, appearance, audio engine).

### 3.2 Reusable Glass & Interaction Components
Located in `app/src/main/kotlin/com/convx/music/ui/component/`:
- **`FloatingNavBar` & `FloatingSideBar`:** Refractive glass docks housing tabs with an interactive physical puck.
- **`MiniPlayer`:** Docked playback pill featuring swipe gestures (swipe-to-skip, drag-up-to-expand) and progress bar.
- **`GlassCircleButton`:** Floating circular controls with specular rim highlights and outer dark boundary outlines.
- **`CupertinoModalBottomSheet`:** High-performance bottom sheet with gesture velocity tracking and soft spring settling.

---

## 4. Animation, Spring Physics & Transition Architecture

Convx mandates that motion must explain user interaction rather than serve as continuous distraction ([convx-android-motion/SKILL.md](file:///D:/Convx-main/contributor/skills/convx-android-motion/SKILL.md)).

### 4.1 Spring vs. Tween Policies
- **Spring Physics for Physical Interactions:** Used for all drag, swipe, expand, and fling gestures:
  - `PlayerSheetStiffness`: `Spring.StiffnessMediumLow` (Smooth opening velocity without abrupt clamping).
  - `DampingRatio`: `Spring.DampingRatioNoBouncy` (`1.0f`, critically damped) prevents visual wobbling on glass surfaces.
- **Tweens for Transitions & Crossfades:**
  - `GlassThemeFadeMillis`: `280ms` linear/ease-in-out tween for crossfading between light and dark glass palettes ([GlassEffect.kt:512](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/GlassEffect.kt#L512)).
  - `NavTransition`: `200ms` slide and fade between NavHost destinations.

### 4.2 Drawing Phase Deferral
To achieve fluid 120Hz rendering on ProMotion / high-refresh displays:
- Per-frame animation reads are strictly deferred to Compose draw scopes (`Modifier.drawBehind`, `graphicsLayer { ... }`) to bypass the Composition and Layout phases.
- Floating bar scroll collapses run through `NestedScrollConnection` offsets applied directly to translation vectors.

---

## 5. Lighting, Edge-to-Edge, Adaptation & Accessibility

### 5.1 Light and Dark Handling
- Automatic luminance evaluation via `glassUsesDarkAppearance()` ([GlassEffect.kt:218-222](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/GlassEffect.kt#L218-L222)).
- Monochromatic text contrast calculation: Text color checks background luminance; if contrast ratio drops below `4.5:1` (WCAG AA), text lightness is adjusted stepwise ([AppleTokens.kt:164-167](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/theme/AppleTokens.kt#L164-L167)).

### 5.2 Edge-to-Edge System
- Configured in Activity via `WindowCompat.setDecorFitsSystemWindows(window, false)`.
- Insets are distributed through `WindowInsets.statusBars` and `WindowInsets.navigationBars`. Glass docks sample beneath the navigation bar area for a seamless edge bleed.

### 5.3 Adaptive Layouts
- Dual phone and tablet support via `LocalTabView` ([AppRootContent.kt:762](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/AppRootContent.kt#L762)):
  - Phones render `FloatingNavBar` at the bottom.
  - Foldables / Tablets in landscape dynamically switch to `FloatingSideBar` with independent glass configurations (`sidePanelEnabled`, `sidePanelBlurRadius`).
