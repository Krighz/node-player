package com.nodeplayer.model

enum class LyricsStyle {
    CINEMATIC_KINETIC,
    CLASSIC_FULLSCREEN
}

enum class LyricsProviderChoice(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isSyllableCapable: Boolean
) {
    AUTO("auto", "Auto (Best Match)", "Syllable-first concurrent discovery", true),
    LRC_RED("lrcred", "lrc.red", "Apple Music studio syllable TTML", true),
    BINI("bini", "Bini Lyrics", "Apple Music TTML with ISRC discovery", true),
    MUSIXMATCH("musixmatch", "Musixmatch", "RichSync word timing & 14M+ songs", true),
    UNISON("unison", "Unison", "Community karaoke & Asian sync catalog", true),
    LRCLIB("lrclib", "LrcLib", "Open community synchronized lyrics database", false),
    BETTER_LYRICS("betterlyrics", "BetterLyrics", "Apple TTML & QQ Music Portato", true),
    LYRICS_PLUS("lyricsplus", "LyricsPlus", "Spotify & Musixmatch community mirror", true),
    KUGOU("kugou", "KuGou", "Extensive Asian & global catalog", false),
    LOCAL("local", "Local File", "Embedded tags or companion .lrc / .ttml", true);

    companion object {
        fun fromId(id: String?): LyricsProviderChoice {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: AUTO
        }
    }
}

enum class LyricsGlowPalette(
    val id: String,
    val displayName: String,
    val litColorArgb: Long,
    val midColorArgb: Long,
    val glowColorArgb: Long,
    val ambientColorArgb: Long
) {
    CINEMA_WHITE(
        id = "white",
        displayName = "Cinema White",
        litColorArgb = 0xFFFFFFFF,
        midColorArgb = 0xFFE8F2FF,
        glowColorArgb = 0x85FFFFFF,
        ambientColorArgb = 0x14C8DCFF
    ),
    GLACIAL_BLUE(
        id = "blue",
        displayName = "Glacial Blue",
        litColorArgb = 0xFFF4FAFF,
        midColorArgb = 0xFF7CD4FF,
        glowColorArgb = 0x946ECDFF,
        ambientColorArgb = 0x1A46A5FF
    ),
    WARM_GOLD(
        id = "gold",
        displayName = "Warm Gold",
        litColorArgb = 0xFFFFFDF5,
        midColorArgb = 0xFFFFD57E,
        glowColorArgb = 0x8CFFCD6E,
        ambientColorArgb = 0x17FFB450
    ),
    LUNAR_VIOLET(
        id = "violet",
        displayName = "Lunar Violet",
        litColorArgb = 0xFFFAF5FF,
        midColorArgb = 0xFFC89BFF,
        glowColorArgb = 0x8FC084FC,
        ambientColorArgb = 0x1AA855F7
    );

    companion object {
        fun fromId(id: String): LyricsGlowPalette {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: CINEMA_WHITE
        }
    }
}

enum class AlbumArtShape(
    val id: String,
    val displayName: String
) {
    SQUIRCLE("squircle", "Squircle"),
    CIRCLE("circle", "Circle"),
    HEART("heart", "Heart");

    companion object {
        fun fromId(id: String?): AlbumArtShape {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: SQUIRCLE
        }
    }
}

data class LyricsSettings(
    val style: LyricsStyle = LyricsStyle.CINEMATIC_KINETIC,
    val commonZoom: Float = 1.00f,
    val sparkleDensity: Float = 0.35f,
    val lrSwing: Float = 0.80f,
    val verticalSpacing: Float = 1.15f,
    val cameraGlide: Float = 1.00f,
    val bounce: Float = 1.00f,
    val palette: LyricsGlowPalette = LyricsGlowPalette.CINEMA_WHITE,
    val showTopBar: Boolean = true,
    val preferredProvider: LyricsProviderChoice = LyricsProviderChoice.AUTO,
    val romanizeForeignLyrics: Boolean = true,
    val lyricsDisplayMode: LyricsDisplayMode = LyricsDisplayMode.DUAL_LINE,
    val albumArtShape: AlbumArtShape = AlbumArtShape.SQUIRCLE,
    val hideStatusBar: Boolean = false
)
