# Windows Porting Guide & Architecture

## Shared Modules (100% Shareable with Windows / Desktop)

The following modules contain zero Android dependencies and run natively on Windows via JVM or Kotlin Multiplatform (KMP):

1. **`core-model`** (100% Pure Kotlin):
   - Data structures: `Track`, `Album`, `Artist`, `Playlist`, `Lyrics`, `AudioEffectConfig`, `AudioOutputInfo`, `GlassThemeConfig`, `CrossfadeConfig`, `AutoEqProfile`.
   - Shared between Android and Windows targets.

2. **`core-dsp`** (100% Pure Kotlin):
   - High-performance audio DSP engine:
     - `BiquadFilter` & `ParametricEqualizer`: 10-band biquad peaking & shelf filters.
     - `SoftLimiter`: Strict linear pass-through below 0.90 with soft-knee compression above. Bit-exact bypass on flat EQ.
     - `DirectVolumeProcessor`: 32-bit float internal volume scaling with zero-allocation XorShift PRNG Triangular Probability Density Function (TPDF) dither.
     - `LoudnessCompensator`: Dynamic ISO 226 equal-loudness contour bass/treble boost.
     - `HarmonicBassEnhancer`: Missing fundamental psychoacoustic synthesizer with phone speaker cutoff protection.
     - `PartitionedConvolver`: Real-time partitioned impulse response convolver with 2x mirrored contiguous history arrays.
     - `AbCompareHelper`: Pink-noise weighted loudness offset computer for level-matched A/B comparison.
     - `AudioProfileSerializer`: Pure Kotlin JSON serializer for DSP profile export/import.
   - 100% unit tested on JVM. Tested with sine sweeps, impulse responses, and gain verification.
   - Runs directly on Windows via Java Sound API, WASAPI float streams, or ASIO buffers.

3. **`core-library`** (100% Pure Kotlin):
   - `LrcParser`: LRC synchronized and unsynchronized lyric parser.
   - `CueSheetParser`: CUE sheet track splitter.
   - `M3uParser`: Playlist import and export.
   - `AutoEqParser`: Parametric EQ text profile parser.

---

## Android-Specific Implementations (Platform-Dependent)

The following components rely on Android runtime APIs and must be adapted for Windows:

1. **System-Wide Audio Effects Interception:**
   - **Android**: `SystemWideEqService` and `AudioSessionReceiver` rely on Android's `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast and `android.media.audiofx.DynamicsProcessing`.
   - **Windows Equivalent**: Windows does not broadcast session IDs to user-space apps. System-wide EQ on Windows requires:
     - An Audio Processing Object (APO) driver (e.g., Equalizer APO architecture).
     - Or a virtual audio cable / WASAPI loopback capture driver routing system audio through `DspChain`.

2. **Crossfade Dual-Engine Playback:**
   - **Android**: `CrossfadePlayerManager` coordinates dual `ExoPlayer` instances with custom `AudioSink` implementations.
   - **Windows Equivalent**: Implement dual WASAPI Exclusive/Shared streams or ASIO output channels fed by `DspChain.processInterleavedFloat()`.

3. **Glass Refraction Shader:**
   - **Android**: `GlassRefractionShader.kt` uses Android 13+ AGSL (`RuntimeShader` and `RenderEffect.createRuntimeShaderEffect()`).
   - **Windows Equivalent**: In Compose Multiplatform Desktop, implement via Skia SkSL (`org.jetbrains.skia.RuntimeEffect`) or Direct3D HLSL shaders.

4. **Home Screen Widgets:**
   - **Android**: `NodePlayerWidget` uses `AppWidgetProvider` and `RemoteViews`.
   - **Windows Equivalent**: System tray icon with media flyout, Windows 11 Widget Board provider, or Windows Media Transport Controls (`SystemMediaTransportControls`).

5. **Audio Device Callbacks & DAC Mixer Bypass:**
   - **Android**: `AudioDeviceCallback` and Android 14 `AudioManager.setPreferredMixerAttributes` with `AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT`.
   - **Windows Equivalent**: WASAPI Exclusive Mode (`AUDCLNT_SHAREMODE_EXCLUSIVE`) natively bypasses the Windows Audio Engine (WASAPI mixer) for bit-perfect output.
