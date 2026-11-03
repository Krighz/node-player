# Porting Map: Android to Compose Multiplatform

| Android Feature | Desktop Adaptation | Shared Location |
| :--- | :--- | :--- |
| `AudioTrack` Output | JavaSound SourceDataLine / WASAPI Output | `core-common/.../AudioOutput.kt` -> `WasapiAudioOutput.kt` |
| MediaCodec / ExoPlayer Decoders | JLayer (MP3), jFLAC (FLAC), JavaSound (WAV/AIFF), FFmpeg Pipe (AAC/ALAC/Opus/OGG) | `core-common/.../AudioDecoder.kt` -> `CompositeAudioDecoder.kt` |
| MediaStore ContentProvider | NIO recursive directory walker + AudioHeaderParser | `core-common/.../MediaLibraryScanner.kt` -> `WindowsFileScanner.kt` |
| Room DB | SQLite JDBC | `core-library/.../TrackRepository.kt` -> `DesktopTrackDatabase.kt` |
| SharedPreferences / DataStore | Properties file in `%APPDATA%\NodePlayer\settings.properties` | `core-common/.../KeyValueStore.kt` -> `DesktopSettingsStore.kt` |
| MediaSession / Notification | MediaSessionBridge (SMTC integration stub) | `core-common/.../MediaSessionBridge.kt` |
| AGSL Shaders (`RenderEffect`) | Compose Skia Shaders / Pure Math Canvas Animation | `core-ui/.../LiquidGlassBackground.kt` |
| Now Playing / Equalizer / Customize UI | Compose Multiplatform screens (100% shared) | `core-ui/.../screens/` |
| Bottom Floating Dock | Compose Multiplatform `BottomPlayerDock` | `core-ui/.../player/BottomPlayerDock.kt` |
| Launcher Widgets | Not applicable on Desktop (Android-only feature) | Retained exclusively in `:app-android` |
| System-Wide EQ Session Hook | Not applicable on Desktop (Android audio server hook) | Retained exclusively in `:app-android` |
