# Function Index (core-common)

| File Path | Symbols & Line Ranges |
| :--- | :--- |
| `core-common/src/main/kotlin/com/nodeplayer/common/AppModeState.kt` | `AppModeState` (L17), `setOnlineMode` (L20), `toggleMode` (L24) |
| `core-common/src/main/kotlin/com/nodeplayer/common/dispatchers/DispatcherProvider.kt` | `DispatcherProvider` (L5), `DefaultDispatcherProvider` (L12) |
| `core-common/src/main/kotlin/com/nodeplayer/common/logging/NodeLog.kt` | `class` (L15), `format` (L29), `interface` (L48), `log` (L50), `NodeLog` (L52), `initFileLogging` (L70), `ensureWriterThreadStarted` (L86), `flushFileLogging` (L134), `addSink` (L148), `removeSink` (L155), `v` (L158), `d` (L160), `i` (L161), `w` (L162), `e` (L163), `log` (L164), `getHistory` (L187), `exportFormattedLog` (L189), `getEntriesByFeature` (L193), `getEntriesGroupedByFeature` (L198), `clearHistory` (L202) |
| `core-common/src/main/kotlin/com/nodeplayer/common/media/CipherSolver.kt` | `CipherSolver` (L9), `deobfuscateStreamUrl` (L13), `transformNParamInUrl` (L19) |
| `core-common/src/main/kotlin/com/nodeplayer/common/platform/AudioDecoder.kt` | `AudioDecoder` (L11), `open` (L14), `decodeFrame` (L16), `seekTo` (L17), `close` (L18) |
| `core-common/src/main/kotlin/com/nodeplayer/common/platform/AudioOutput.kt` | `AudioOutput` (L19), `open` (L25), `write` (L27), `pause` (L28), `resume` (L29), `flush` (L30), `close` (L31), `getPlaybackPositionMs` (L32) |
| `core-common/src/main/kotlin/com/nodeplayer/common/platform/KeyValueStore.kt` | `KeyValueStore` (L2), `getString` (L4), `putString` (L5), `getBoolean` (L6), `putBoolean` (L8), `getFloat` (L9), `putFloat` (L11), `getInt` (L12), `putInt` (L14), `getLong` (L15), `putLong` (L17), `contains` (L18), `remove` (L20), `clear` (L21) |
| `core-common/src/main/kotlin/com/nodeplayer/common/platform/MediaLibraryScanner.kt` | `MediaLibraryScanner` (L13), `scanPath` (L15) |
| `core-common/src/main/kotlin/com/nodeplayer/common/platform/MediaSessionBridge.kt` | `MediaSessionBridge` (L2), `setPlaybackState` (L4), `setMetadata` (L5), `setEventListener` (L6), `MediaSessionEventListener` (L8), `onPlay` (L10), `onPause` (L11), `onNext` (L12), `onPrevious` (L13), `onSeekTo` (L14) |
| `core-common/src/main/kotlin/com/nodeplayer/common/platform/PlatformServices.kt` | `PlatformClock` (L2), `currentTimeMillis` (L4), `nanoTime` (L5), `SystemClock` (L7), `currentTimeMillis` (L9), `nanoTime` (L10), `PlatformFileSystem` (L12), `exists` (L14), `isDirectory` (L15), `listFiles` (L16), `readFileBytes` (L17), `readFileText` (L18), `writeFileBytes` (L19), `writeFileText` (L20), `delete` (L21), `getFileSize` (L22), `PlatformHttpClient` (L24), `get` (L26), `post` (L27) |
| `core-common/src/main/kotlin/com/nodeplayer/common/result/NodeResult.kt` | `getOrNull` (L9), `getOrThrow` (L14), `map` (L19), `runCatching` (L26) |
| `core-common/src/test/kotlin/com/nodeplayer/common/ArchitectureBoundaryTest.kt` | `ArchitectureBoundaryTest` (L6), `verifyCoreModulesDoNotImportAndroidFramework` (L10), `verifyFeaturesDoNotImportOtherFeaturesInternals` (L39), `findProjectRoot` (L68) |
| `core-common/src/test/kotlin/com/nodeplayer/common/NodeLogTest.kt` | `NodeLogTest` (L10), `setup` (L14), `testFeatureTagGrouping` (L19), `testNodeResultSuccessAndFailure` (L38), `testAsyncFileLoggingFlushesOffThread` (L49) |
