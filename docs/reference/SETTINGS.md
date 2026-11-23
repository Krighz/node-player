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

---

## 7. Discord Rich Presence Settings (`SettingsRegistry`)

Accessible via **Settings > Online, Connect & Network > Discord Rich Presence (Desktop Only)**:

| Setting Key | Data Type | Options / Range | Default Value | Platform | Description | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`discord_enabled`** | Boolean | `true`, `false` | `false` | Desktop | Master toggle for Discord IPC rich presence | `PresenceController.kt`, `DiscordRichPresenceCard.kt`, `Main.kt` |
| **`discord_incognito`** | Boolean | `true`, `false` | `false` | Desktop | Incognito mode: immediately clears presence and suspends updates | `PresenceController.kt`, `HomeProfileSheet.kt`, `DiscordRichPresenceCard.kt` |
| **`discord_show_cover`** | Boolean | `true`, `false` | `true` | Desktop | Displays track cover art image in profile presence card | `PresenceController.kt`, `DiscordActivityBuilder.kt` |
| **`discord_show_album`** | Boolean | `true`, `false` | `true` | Desktop | Displays album name alongside artist name | `PresenceController.kt`, `DiscordActivityBuilder.kt` |
| **`discord_show_while_paused`** | Boolean | `true`, `false` | `false` | Desktop | Retains presence card when playback is paused instead of clearing | `PresenceController.kt` |
| **`discord_app_id`** | String | Valid Discord Client ID | `""` | Desktop | Custom Application Client ID created in Discord Developer Portal | `PresenceController.kt`, `DiscordIpcTransport.kt` |

---

## 8. Audio Converter Settings (`SettingsRegistry`)

Accessible via **Settings > Library & Files > Audio Converter**:

| Setting Key | Data Type | Options / Range | Default Value | Platform | Description | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`convert_default_preset`** | Choice | `"192"`, `"128"`, `"96"`, `"CUSTOM"` | `"128"` | All | Default Opus bitrate preset | `AudioConverterView.kt`, `SettingsRegistry.kt` |
| **`convert_custom_bitrate`** | IntRange | 32 .. 320 kbps | `160` | All | Custom target bitrate when Custom preset is selected | `AudioConverterView.kt`, `SettingsRegistry.kt` |
| **`convert_vbr`** | Toggle | `true`, `false` | `true` | All | Dynamic bitrate allocation tailored to acoustic complexity | `AudioConverterView.kt`, `TranscodeJob.kt` |
| **`convert_application`** | Choice | `"audio"`, `"voip"`, `"lowdelay"` | `"audio"` | All | Opus encoder application mode tuning | `AudioConverterView.kt`, `TranscodeJob.kt` |
| **`convert_compression_level`** | IntRange | 0 .. 10 | `10` | All | Opus encoder computational complexity | `AudioConverterView.kt`, `TranscodeJob.kt` |
| **`convert_keep_original`** | Toggle | `true`, `false` | `true` | All | Keep original source FLAC files after verified conversion | `AudioConverterView.kt`, `TranscodeQueueManager.kt` |
| **`convert_output_mode`** | Choice | `"CUSTOM_FOLDER"`, `"NEXT_TO_ORIGINAL"` | `"CUSTOM_FOLDER"` | All | Output file destination location strategy | `AudioConverterView.kt`, `SettingsRegistry.kt` |
| **`convert_output_folder`** | Text | Valid directory path | `""` | All | Target folder path where converted Opus files are saved | `AudioConverterView.kt`, `SettingsRegistry.kt` |
| **`convert_name_template`** | Text | Template string | `"{title}"` | All | File naming pattern for transcoded outputs | `SettingsRegistry.kt` |
| **`convert_workers`** | IntRange | 1 .. 4 | `2` | All | Number of parallel transcode worker threads | `AudioConverterView.kt`, `TranscodeQueueManager.kt` |
| **`convert_write_r128`** | Toggle | `true`, `false` | `false` | All | Calculate and inject R128 loudness tags into output files | `AudioConverterView.kt`, `TranscodeJob.kt` |
| **`convert_ffmpeg_path`** | Text | Executable path | `""` | Desktop | Custom manual override for FFmpeg executable on Desktop | `DesktopFFmpegTranscoder.kt`, `Main.kt` |
| **`convert_rescan_after`** | Toggle | `true`, `false` | `true` | All | Automatically trigger local library scan when batch finishes | `TranscodeQueueManager.kt`, `SettingsRegistry.kt` |

---

## 11. Music Videos & Cinema Player Settings (`SettingsRegistry`)

Accessible via **Settings > Online, Connect & Network > Music Videos**:

| Setting Key | Data Type | Options / Range | Default Value | Platform | Description | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`video_auto_play`** | Toggle | `true`, `false` | `false` | All | Automatically start video for music tracks that have a video stream | `PlayerViewModel.kt`, `NowPlayingView.kt` |
| **`video_default_quality`** | Choice | `"auto"`, `"1080"`, `"720"`, `"480"`, `"360"` | `"auto"` | All | Preferred default resolution when playing music videos | `StreamResolver.kt`, `PlayerViewModel.kt` |
| **`video_seek_step_s`** | IntRange | 5 .. 30 | `10` | All | Step duration in seconds for double-tap seeking | `FullscreenCinemaPlayer.kt`, `VideoMath.kt` |
| **`video_gestures`** | Toggle | `true`, `false` | `true` | All | Enable swipe gestures (brightness on left, volume on right) | `FullscreenCinemaPlayer.kt` |
| **`video_pip`** | Toggle | `true`, `false` | `true` | Android | Auto-enter Picture-in-Picture when leaving the app while video is active | `MainActivity.kt` |
| **`video_background_audio_only`** | Toggle | `true`, `false` | `false` | Android | Data saver: switch video to audio-only background track when minimized | `MainActivity.kt` |
| **`video_controls_autohide_s`** | IntRange | 2 .. 10 | `4` | All | Seconds of inactivity before fading cinema player controls | `FullscreenCinemaPlayer.kt` |
| **`video_show_quality_pill`** | Toggle | `true`, `false` | `true` | All | Display interactive resolution badge pill in Now Playing hero card | `NowPlayingView.kt` |

---

## 12. Cinematic Kinetic & Lyrics Engine Settings (`LyricsSettings`)

Accessible via **Lyrics Screen Overflow Menu (`⋮`)** & **Settings > Lyrics**:

| Setting Key | Data Type | Options / Range | Default Value | Platform | Description | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`lyrics_style`** | Enum | `CINEMATIC_KINETIC`, `CLASSIC_FULLSCREEN` | `CINEMATIC_KINETIC` | All | Active lyrics rendering engine: Cinematic Kinetic canvas or Classic Full-Screen view | `FullScreenLyricsView.kt`, `CinematicKineticLyricsView.kt` |
| **`lyrics_common_zoom`** | Float | 0.40 .. 1.80 | `1.00` | All | Master scale applied to viewport zoom (Kinetic) and typography/layout (Classic) | `CinematicKineticLyricsView.kt`, `FullScreenLyricsView.kt` |
| **`lyrics_sparkle_density`** | Float | 0.00 .. 1.00 | `0.35` | All | Background 3-layer parallax floating starfield density | `CinematicKineticLyricsView.kt` |
| **`lyrics_lr_swing`** | Float | 0.00 .. 1.00 | `0.80` | All | Horizontal left/right choreography spread multiplier | `CinematicKineticLyricsView.kt` |
| **`lyrics_vertical_spacing`** | Float | 0.70 .. 1.60 | `1.15` | All | Multiplier for intra-phrase, inter-phrase, and stanza void gaps | `CinematicKineticLyricsView.kt` |
| **`lyrics_camera_glide`** | Float | 0.30 .. 1.50 | `1.00` | All | Critically-damped virtual camera spring responsiveness | `CinematicKineticLyricsView.kt` |
| **`lyrics_bounce`** | Float | 0.00 .. 1.50 | `1.00` | All | Active word pop scale and float lift energy | `CinematicKineticLyricsView.kt` |
| **`lyrics_glow_palette`** | Enum | `white`, `blue`, `gold`, `violet` | `white` | All | Active word illumination and ambient glow color palette | `CinematicKineticLyricsView.kt` |
| **`lyrics_show_top_bar`** | Boolean | `true`, `false` | `true` | All | Visibility toggle for the compact top header bar | `FullScreenLyricsView.kt` |
| **`lyrics_preferred_provider`** | Enum | `auto`, `lrcred`, `bini`, `musixmatch`, `unison`, `lrclib`, `betterlyrics`, `lyricsplus`, `kugou`, `local` | `auto` | All | Preferred synchronized lyrics provider with instant per-track interchange and status badges (Click to Search, Lyrics Found, Connected OK) | `LyricsRepository.kt`, `LyricsSettingsBottomSheet.kt` |
| **`lyrics_romanize_foreign`** | Boolean | `true`, `false` | `true` | All | Automatic offline romanization of foreign scripts (Hangul, Romaji, Pinyin, Urdu Nastaliq, Devanagari, Cyrillic) | `LyricsRomanizer.kt`, `LyricsSettingsBottomSheet.kt` |
| **`lyrics_display_mode`** | Enum | `ORIGINAL_ONLY`, `DUAL_LINE`, `ROMANIZED_ONLY` | `DUAL_LINE` | All | Apple Music style display mode: original glyphs only, dual-line stacked transliteration/translation, or romanized-only | `LyricLineItem.kt`, `WordByWordKaraokeText.kt`, `LyricsSettingsBottomSheet.kt` |

---

## 13. Online Music Services & Media Resolution Settings

Accessible via **Settings > Online Music & Streaming Services**:

| Setting Key | Data Type | Options / Range | Default Value | Platform | Description | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`streaming_audio_quality`** | Enum | `HIGH`, `MEDIUM`, `LOW` | `HIGH` | All | Online music audio bitrate: High (~160-256 kbps, Opus 160k / AAC 256k), Medium (~128 kbps, AAC/Opus 128k), Low (~48-70 kbps, Data Saver) | `StreamResolver.kt`, `MediaQualitySettings.kt`, `AppSettingsRepository.kt` |
| **`album_art_quality`** | Enum | `HIGH`, `MEDIUM`, `LOW` | `HIGH` | All | Target resolution for artwork: High (1080p-1200p HD, `=w1200`), Medium (544p Balanced, `=w544`), Low (256p Data Saver, `=w256`) | `ArtworkUrlResolver.kt`, `AudioArtworkFetcher.kt`, `ArtworkLoader.kt` |
| **`video_default_quality`** | Choice | `"auto"`, `"1080"`, `"720"`, `"480"`, `"360"` | `"auto"` | All | Preferred video stream playback resolution with client-side format ranking | `StreamResolver.kt`, `SettingsScreen.kt` |
| **`online_mode_enabled`** | Boolean | `true`, `false` | `true` | All | Master switch for online YouTube Music catalog browsing, recommendations, and search | `SettingsScreen.kt`, `OnlineHomeView.kt` |
| **`scrobble_enabled`** | Boolean | `true`, `false` | `false` | All | Last.fm real-time scrobbling and offline queue retry | `LastFmClient.kt`, `SettingsScreen.kt` |

---

## 14. Cloud Backup & Sync Settings (Google Drive `appDataFolder`)

Accessible via **Settings > Online Music & Cloud Sync > Cloud Sync (Drive) Tab**:

| Setting Key | Data Type | Options / Range | Default Value | Platform | Description | Consuming Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`cloud_sync_enabled`** | Boolean | `true`, `false` | `false` (Opt-in only) | Android | Master state for cloud backup. Disabled by default until user connects account. | `GoogleAuthManager.kt`, `CloudSyncManager.kt` |
| **`sync_frequency`** | Enum | `HOURLY`, `DAILY`, `MANUAL` | `MANUAL` | Android | Frequency for background WorkManager sync jobs. | `CloudSyncManager.kt`, `CloudSyncWorker.kt` |
| **`has_unsynced_changes`** | Boolean | `true`, `false` | `false` | Android | Local dirty flag optimization; skips cloud upload if nothing changed. | `CloudSyncManager.kt`, `PlaybackService.kt`, `PlayerViewModel.kt` |
| **`gdrive_custom_client_id`**| String | Freeform text | Injected from `local.properties` | Android | Google Cloud Console OAuth 2.0 client ID from `local.properties` via `BuildConfig.GDRIVE_CLIENT_ID`. | `GoogleAuthManager.kt` |
| **`gdrive_redirect_uri`**| String | Reverse-DNS URI | `com.googleusercontent.apps.<client_id_prefix>:/oauth2redirect` | Android | Reverse-DNS redirect URI conforming to RFC 8252 for Android clients. | `GoogleAuthManager.kt`, `AndroidManifest.xml` |








