# Porting & Module Mapping

| Android Feature / Class | Pure Kotlin Module | Windows Implementation | Status |
| :--- | :--- | :--- | :--- |
| `DspChain`, `ParametricEqualizer`, `SoftLimiter` | `:core-dsp` | Directly reused via `:core-dsp` | Shared (100% code reuse) |
| `Track`, `EqBandConfig`, `HiResStatus` | `:core-model` | Directly reused via `:core-model` | Shared (100% code reuse) |
| `AudioHeaderParser` | `:core-library` | Directly reused via `:core-library` | Shared (100% code reuse) |
| `AudioTrack` output | `:core-common` (`AudioOutput`) | `WasapiAudioOutput` (WASAPI Shared & Exclusive) | Platform-specific |
| `MediaCodec` / `ExoPlayer` | `:core-common` (`AudioDecoder`) | `CompositeAudioDecoder` (JVM + FFmpeg) | Platform-specific |
| `SharedPreferences` | `:core-common` (`KeyValueStore`) | `DesktopSettingsStore` (`settings.properties`) | Platform-specific |
| `RoomDatabase` | Custom SQL | `DesktopTrackDatabase` (SQLite JDBC) | Platform-specific |
| Compose Android UI | Compose tokens | Compose Desktop + `SkiaGlassShader` | Reused design tokens |
