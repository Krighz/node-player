# Known Issues & Verification Status

1. **Audio Decoding**:
   - MP3 and FLAC are decoded natively in pure Java via JLayer and jFLAC (verified with unit tests).
   - Uncompressed WAV/AIFF are decoded via JavaSound (verified with unit tests).
   - AAC, ALAC, Opus, and OGG utilize `FFmpegAudioDecoder` which expects `ffmpeg.exe` to be available in PATH or NodePlayer directory.
2. **Audio Output**:
   - `WasapiAudioOutput` supports 44.1 kHz, 48 kHz, and 96 kHz standard rates through JavaSound with WASAPI shared pipeline. Direct COM IAudioClient exclusive mode hook requires dynamic link helper for bit-perfect output switching.
3. **Packaging & Code Signing**:
   - `NodePlayer-1.4.0.msi` is generated unsigned. Windows SmartScreen may present an unknown publisher prompt until an Authenticode EV code-signing certificate is applied.
4. **Hardware Acceleration**:
   - Skia rendering leverages DirectX on Windows when available. If GPU hardware acceleration is disabled, fallback software rendering is utilized for liquid background shaders.
