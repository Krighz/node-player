# Function Index (core-model)

| File Path | Symbols & Line Ranges |
| :--- | :--- |
| `core-model/src/main/kotlin/com/nodeplayer/model/AudioAnalysisModels.kt` | `class` (L2) |
| `core-model/src/main/kotlin/com/nodeplayer/model/AudioAndThemeConfig.kt` | `isTrulyBitPerfect` (L20), `class` (L28), `class` (L34), `default10Bands` (L137), `createPreset` (L159), `class` (L173), `class` (L186), `class` (L192), `isShaderActive` (L392), `chromaticFringeValue` (L402), `grainStrengthValue` (L407) |
| `core-model/src/main/kotlin/com/nodeplayer/model/ContextMenuConfig.kt` | `class` (L6), `fromId` (L20), `getActiveTrackActions` (L35) |
| `core-model/src/main/kotlin/com/nodeplayer/model/Entities.kt` | Script / module file |
| `core-model/src/main/kotlin/com/nodeplayer/model/ExploreModels.kt` | Script / module file |
| `core-model/src/main/kotlin/com/nodeplayer/model/GenreCrossfadeModels.kt` | `matches` (L11), `defaultRules` (L23), `GenreCrossfadeResolver` (L52), `resolve` (L63) |
| `core-model/src/main/kotlin/com/nodeplayer/model/GlassControlsConfig.kt` | `class` (L2), `resolve` (L40), `class` (L95), `getComponent` (L122), `updateComponent` (L133), `resolve` (L145) |
| `core-model/src/main/kotlin/com/nodeplayer/model/GlassControlsSerializer.kt` | `GlassControlsSerializer` (L10), `toJson` (L15), `fromJson` (L40), `resetComponentToGlobal` (L92), `resetAllToFactoryDefaults` (L116), `appendMaster` (L119), `appendComponent` (L134), `parseMaster` (L151), `parseComponent` (L170), `extractObjectBlock` (L191), `extractBool` (L209), `extractFloat` (L214), `extractString` (L224), `extractInt` (L229) |
| `core-model/src/main/kotlin/com/nodeplayer/model/Lyrics.kt` | `class` (L2), `charsSungAt` (L18), `revealedChars` (L77) |
| `core-model/src/main/kotlin/com/nodeplayer/model/PlayerPresentationState.kt` | Script / module file |
| `core-model/src/main/kotlin/com/nodeplayer/model/SmartPlaylistModels.kt` | `class` (L2), `class` (L22), `matches` (L39), `matchString` (L60), `matchNumber` (L70), `matchBoolean` (L80), `class` (L90), `matches` (L97), `filterTracks` (L114), `defaultPlaylists` (L131) |
| `core-model/src/main/kotlin/com/nodeplayer/model/Track.kt` | Script / module file |
| `core-model/src/main/kotlin/com/nodeplayer/model/presence/PresenceModels.kt` | `PresenceState` (L6), `PresenceStatus` (L19), `DiscordIpcConstants` (L31), `PresenceTransport` (L49) |
| `core-model/src/main/kotlin/com/nodeplayer/model/WidgetConfig.kt` | `class` (L2) |
| `core-model/src/main/kotlin/com/nodeplayer/model/YouTubeAccount.kt` | `LoggedOut` (L21), `Loading` (L22), `SessionExpired` (L24) |
| `core-model/src/test/kotlin/com/nodeplayer/model/AppearanceConfigTest.kt` | `AppearanceConfigTest` (L7), `testDefaultDockConfig` (L11), `testDefaultHomeLayoutConfig` (L20), `testDefaultLyricsViewConfig` (L30), `testGlassThemeConfigNestingAndCopy` (L39) |
| `core-model/src/test/kotlin/com/nodeplayer/model/GlassControlsConfigTest.kt` | `GlassControlsConfigTest` (L7), `testDefaultValues` (L11), `testPresets` (L37), `testInheritanceCascadeWhenUseGlobalIsTrue` (L62), `testIndependentValuesWhenUseGlobalIsFalse` (L81), `testForceLighterInListsResolution` (L103), `testMasterDisabledReturnsLighterSquircle` (L125), `testComponentTypeSupportsListOverride` (L136), `testGetComponentAndUpdateComponentAllTypes` (L150), `testGlassThemeConfigEmbedding` (L164), `testSerializerRoundtrip` (L175), `testSerializerFallbackOnCorruptOrInvalidFormat` (L236), `testSerializerIncompleteJsonFallback` (L259), `testSerializerNumericClamping` (L279), `testTwoTierResetEngine` (L304) |
| `core-model/src/test/kotlin/com/nodeplayer/model/LyricsModelTest.kt` | `LyricsModelTest` (L8), `testDefaultParameterCompatibilityForExistingCallers` (L12), `testCharsSungAtWithoutSyllables` (L41), `testCharsSungAtWithSyllables` (L60), `testZeroDurationDivisionGuard` (L90), `testDuetAlignmentAndBackgroundVocals` (L113), `testExploreModelsAndYouTubeAccountParity` (L132) |
| `core-model/src/test/kotlin/com/nodeplayer/model/LyricsViewConfigTest.kt` | `LyricsViewConfigTest` (L6), `testDefaultLyricsViewConfigValues` (L10), `testCopyAndCustomValues` (L21) |
| `core-model/src/main/kotlin/com/nodeplayer/model/video/VideoModels.kt` | `VideoQuality` (L3), `GestureZone` (L22), `SurfacePriority` (L27), `VideoPlayerState` (L33), `VideoAspectMath` (L46), `VideoMath` (L77), `MediaArtConstants` (L105), `ResolvedPlaybackSource` (L122) |
| `core-model/src/main/kotlin/com/nodeplayer/model/video/CanvasModels.kt` | `CanvasType` (L3), `CanvasItem` (L10) |
| `core-model/src/test/kotlin/com/nodeplayer/model/video/VideoQualityMathTest.kt` | `VideoQualityMathTest` (L8), `testChooseHeight` (L11), `testGestureZoneResolution` (L30), `testDoubleTapSeekDelta` (L37) |
| `core-model/src/test/kotlin/com/nodeplayer/model/video/VideoMathTest.kt` | `VideoMathTest` (L6), `testCalculateRateNudgeDeadzone` (L9), `testCalculateRateNudgeGradualAndClamped` (L17), `testCalculateDimensionsLetterboxFit` (L34), `testCalculateDimensionsCropCover` (L48), `testSurfacePriorityOrdering` (L62) |
