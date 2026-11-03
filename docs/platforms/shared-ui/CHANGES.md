# Architecture Changes for Shared Compose Multiplatform UI

1. **Gradle Build Configuration**:
   - Added Compose Multiplatform plugin `1.6.11` to root build.
   - Set `kotlin.mpp.androidGradlePluginCompatibility.nowarn=true` in `gradle.properties` for AGP 8.4.2 compatibility.
   - Included `:core-ui` and `:app-desktop` in `settings.gradle.kts`.

2. **Core Abstractions in `:core-common` and `:core-library`**:
   - `AudioOutput.kt`: Standardized interface for platform audio outputs (buffer write, clock tracking, latency measurement, volume).
   - `AudioDecoder.kt`: Unified PCM stream decoding contract across diverse container formats.
   - `MediaLibraryScanner.kt`: Reactive flow scanner interface for file tree and device storage discovery.
   - `KeyValueStore.kt`: Key-value storage abstraction powering settings across platforms.
   - `TrackRepository.kt`: Reactive Kotlin coroutines flow repository for track query, album indexing, artist groupings, and playlist persistence.

3. **Multiplatform UI Layer (`:core-ui`)**:
   - Extracted shared design system: tactile spring curves (`MotionTokens`), glass surface shaders (`GlassTheme`), and visual components (`GlassCard`, `GlassButton`, `SoundWaveVisualizer`, `SpectrogramView`).
   - Implemented parametric EQ visualization with interactive draggable touch/mouse nodes and live biquad response curve.
   - Ported 6 liquid-glass ambient shader modes to multiplatform canvas Skia math rendering.
   - Built responsive container `SharedPlayerLayout` providing 16:9 widescreen layout with sidebar for desktop/tablets and vertical layout for phones.

4. **Desktop Shell (`:app-desktop`)**:
   - Created pure JVM playback engine integrating pure Java decoders (`jlayer`, `jflac`), `DspChain`, and WASAPI / JavaSound audio pipeline.
   - Added local SQLite JDBC track library database and `%APPDATA%\NodePlayer` settings store.
   - Configured native Windows distribution with Compose Desktop jpackage packaging (`NodePlayer-1.4.0.msi` and standalone application directory).
