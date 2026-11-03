# Known Issues & Scope Delimitations (Windows v1.4.0)

## Out of Scope for v1 (By Design)
- **Android Widgets**: Desktop widgets are Android-only.
- **System-wide EQ**: Global audio loopback effect is an Android AudioEffect feature.
- **Floating overlay & Live wallpaper**: Android window manager features.
- **Node Connect / TV / Multiroom**: Cancelled from project scope.

## Platform Notes
- **Unsigned SmartScreen Warning**: Unsigned binaries trigger a Windows Defender SmartScreen advisory ("Windows protected your PC"). Click "More info" -> "Run anyway".
- **FFmpeg requirement for lossy codecs**: If system `ffmpeg` is not in `PATH`, the app falls back to pure JVM decoding, which natively handles WAV, AIFF, and uncompressed PCM formats.
