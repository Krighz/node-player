# Testing Patterns

**Analysis Date:** 2026-11-05

## Test Framework

**Runner:**
- JUnit 4 (`junit:junit:4.13.2`) across all JVM modules (`:core-common`, `:core-dsp`, `:core-library`, `:core-network`, `:integration-api`, `:app-desktop`).
- AndroidX Test runner (`androidx.test.ext:junit:1.1.5` with `androidx.test.runner.AndroidJUnitRunner`) for Android instrumentation tests in `:app-android`.
- Kotlin Test (`org.jetbrains.kotlin:kotlin-test`).

**Assertion Library:**
- `org.junit.Assert` (`assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, `assertArrayEquals`, `assertNotSame`).
- `kotlin.test.assertTrue` for custom failure message predicates.

**Run Commands:**
```bash
./gradlew test                                   # Run all unit tests across all modules
./gradlew :core-dsp:test --stacktrace            # Run pure JVM DSP unit tests
./gradlew :core-common:test                      # Run common architecture boundary tests
./gradlew :core-network:test                     # Run network protocol & mTLS tests
./gradlew :app-desktop:test                      # Run desktop audio & database tests
./gradlew :app-android:testDebugUnitTest         # Run Android local unit tests
./gradlew :app-android:connectedDebugAndroidTest # Run on-device Android instrumentation tests
```

## Test File Organization

**Location:**
- Separate test source roots mirroring production package structures:
  - JVM Unit Tests: `<module>/src/test/kotlin/`
  - Android Instrumentation Tests: `app-android/src/androidTest/kotlin/`
- Zero production code contamination; test utilities isolated in test sources or explicit `test` packages (e.g. `core-network/src/main/kotlin/com/nodeplayer/network/test/HeadlessTestPeer.kt`).

**Naming:**
- Suffix test files with `Test` matching their subject class or subsystem (e.g., `DspChainTest.kt`, `CryptoTest.kt`, `CustomizeUiTest.kt`).
- Integration tests explicitly suffixed with `IntegrationTest` (e.g., `CrossfadeIntegrationTest.kt`, `AudioCastIntegrationTest.kt`, `MtlsIntegrationTest.kt`).

**Structure:**
```
[module]/
├── src/
│   ├── main/
│   │   └── kotlin/com/nodeplayer/[module]/
│   └── test/
│       └── kotlin/com/nodeplayer/[module]/
│           └── [Subject]Test.kt
```

## Test Structure

**Suite Organization:**
```kotlin
package com.nodeplayer.dsp

import com.nodeplayer.model.AudioEffectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

class DspChainTest {

    @Test
    fun testLimiterNeverExceedsFullScale() {
        val limiter = SoftLimiter()
        limiter.enabled = true
        limiter.threshold = 0.90f

        val step = 0.005f
        var input = -5.0f
        while (input <= 5.0f) {
            val output = limiter.process(input)
            assertTrue("Output must not exceed 1.0f (full scale). Was: $output", abs(output) <= 1.00001f)
            input += step
        }
    }
}
```

**Patterns:**
- **Deterministic Math Assertions:** Audio processing tests inject known trigonometric test signals (e.g. 1000 Hz sine wave) and assert output RMS gain within strict floating-point deltas (e.g., `0.10 dB`).
- **Teardown Lifecycle:** Network and client socket tests define `@After fun teardown()` to terminate active background listeners, headless peers, and thread pools to avoid port leakage.
- **RunOnMainSync for Android UI/Media:** Android instrumentation tests run lifecycle and manager calls inside `InstrumentationRegistry.getInstrumentation().runOnMainSync { ... }`.

## Mocking

**Framework:**
- No heavyweight dynamic bytecode reflection/mocking frameworks (e.g., Mockito or MockK are avoided for pure Kotlin tests).
- Prefer **Headless Test Peers**, **Fakes**, and **Object Implementations** implementing core interfaces (e.g., `PlatformMediaSessionBridge`).

**Patterns:**
```kotlin
val lastCommandApp = AtomicReference<String>("")
val lastCommandAction = AtomicReference<String>("")

val mockBridge = object : PlatformMediaSessionBridge {
    override fun sendTransportCommand(appName: String, action: String) {
        lastCommandApp.set(appName)
        lastCommandAction.set(action)
    }
}
```

**What to Mock:**
- Platform hardware integrations: Windows SMTC / MediaSession bridges, device graphics hardware querying, camera hardware for QR scanning.
- External cloud APIs when testing offline caching or failure paths (Last.fm endpoints).

**What NOT to Mock:**
- Core DSP algorithms and filters (must test raw 32-bit float calculations).
- Cryptographic key generation, TLS certificates, and frame encoding/decoding.
- Audio header byte parsing (`AudioHeaderParser` parses actual synthetic binary streams).
- Serialization/Deserialization logic (tested with full JSON round-trips).

## Fixtures and Factories

**Test Data:**
```kotlin
val testTrack = Track(
    id = 555L,
    title = "Aishite Aishite Aishite",
    artist = "Ado",
    album = "Kyogen",
    durationMs = 255000L,
    path = "C:\\Music\\ado_aishite.flac",
    sampleRate = 48000,
    bitDepth = 24,
    channels = 2,
    isLossless = true
)
```

**Location:**
- Synthetic binary generator functions inlined in tests or packaged in test fixtures (`testJvmWavAudioDecoder()` generates in-memory RIFF headers and PCM samples via `ByteArrayOutputStream`).
- Headless test peer factory: `core-network/src/main/kotlin/com/nodeplayer/network/test/HeadlessTestPeer.kt`.

## Coverage

**Requirements:**
- Core DSP audio algorithms require 100% deterministic assertion coverage on signal clipping, filter boost/cut accuracy, and bypass guarantees.
- CI pipeline (`.github/workflows/ci.yml`) enforces automated test execution on every pull request and push to `main`:
  `./gradlew :core-dsp:test --stacktrace`

**View Coverage:**
```bash
./gradlew test --info
```

## Test Types

**Unit Tests:**
- Pure Kotlin JVM tests in `:core-dsp`, `:core-library`, `:core-model`, `:core-common`.
- Fast execution, zero emulator or Android SDK runtime overhead.

**Integration Tests:**
- Network socket communication over TLS (`MtlsIntegrationTest`, `AudioCastIntegrationTest`, `ExternalSessionTest`) using local loopback ports (`127.0.0.1`).
- Android ExoPlayer audio processor chain integration (`CrossfadeIntegrationTest`, `ColdStartPlayTapIntegrationTest`).
- Architectural boundary validation (`ArchitectureBoundaryTest`).

**E2E Tests:**
- Android on-device tests located in `app-android/src/androidTest/`.
- Automated frame time benchmark harnesses (`testMeasureFrameTimesAtMonitorRefreshRate()` in `DesktopAudioAndDatabaseTest.kt`) measuring spring physics and rendering latency against 144 Hz display refresh targets (< 6.94 ms budget).

## Common Patterns

**Async Testing:**
- Use `kotlinx.coroutines.runBlocking` for coroutine testing with controlled step delays (`delay(100)`).
```kotlin
@Test
fun testCastStartAndPcmAudioStreaming() = runBlocking {
    val sender = HeadlessTestPeer("Sender-PC", "Windows")
    val receiver = HeadlessTestPeer("Receiver-Phone", "Android")
    ...
}
```

**Error Testing:**
- Test resilience against corrupted audio headers, missing files, or untrusted network handshakes:
```kotlin
@Test
fun testUntrustedPeerIsRejectedImmediately() = runBlocking {
    val a = HeadlessTestPeer("Secure-Node-PC", "Windows")
    val rogue = HeadlessTestPeer("Rogue-Device", "Unknown")
    val port = a.startServer(0)

    var handshakeFailed = false
    try {
        rogue.connectTo("127.0.0.1", port)
    } catch (e: Exception) {
        handshakeFailed = true
    }
    assertTrue("Untrusted mTLS connection must be rejected during handshake", handshakeFailed)
}
```

---

*Testing analysis: 2026-11-05*
