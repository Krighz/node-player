# Feature Map

| Feature | Primary Module | Key Classes |
| :--- | :--- | :--- |
| Audio Models & Track | `:core-model` | `Track.kt`, `AudioAndThemeConfig.kt`, `AudioAnalysisModels.kt` |
| Master 32-bit DSP Chain | `:core-dsp` | `DspChain.kt`, `DspProcessors.kt`, `BiquadFilter.kt`, `LoudnessR128.kt` |
| Audio Header Parsing | `:core-library` | `AudioHeaderParser.kt`, `LrcParser.kt`, `LastFmSigner.kt` |
| Cross-Platform Contracts | `:core-common` | `AudioOutput.kt`, `AudioDecoder.kt`, `KeyValueStore.kt`, `FileScanner.kt` |
| Integration API | `:integration-api` | `NodePlayerContract.kt` |
| Android Player Service | `:app-android` | `PlaybackService.kt`, `ExoPlayerForwarder.kt` |
| Liquid Glass Shaders | `:app-android` | `GlassRefractionShader.kt`, `LiquidGlassBackground.kt`, `GlassTheme.kt` |
| Equalizer Screen | `:app-android` | `EqualizerScreen.kt`, `ParametricEqCurveGraph.kt` |
| Settings & Customize UI | `:app-android` | `SettingsScreen.kt`, `CustomizeUiScreen.kt` |
| Now Playing & Dock | `:app-android` | `NowPlayingSheet.kt`, `FrostedNavAndPlayer.kt` |
