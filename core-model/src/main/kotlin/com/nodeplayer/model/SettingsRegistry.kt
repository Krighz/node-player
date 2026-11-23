package com.nodeplayer.model

enum class SettingPlatform {
    ANDROID,
    DESKTOP
}

enum class SettingSection(val id: String, val displayName: String, val order: Int) {
    APPEARANCE("appearance", "Appearance & Layout", 1),
    PLAYBACK("playback", "Playback", 2),
    LIBRARY_FILES("library_files", "Library & Files", 3),
    ONLINE_CONNECT_NETWORK("online_connect_network", "Online, Connect & Network", 4),
    STORAGE("storage", "Storage", 5)
}

enum class SettingSubsection(val id: String, val displayName: String) {
    YOUTUBE_ACCOUNT("youtube_account", "YouTube Account"),
    NODE_CONNECT_DEVICES("node_connect_devices", "Node Connect & Devices"),
    DISCORD_PRESENCE("discord_presence", "Discord Rich Presence"),
    AUDIO_ENGINE("audio_engine", "Audio Engine & Crossfade"),
    RECOMMENDATIONS("recommendations", "Recommendations"),
    LIBRARY_MANAGEMENT("library_management", "Library & Scanning"),
    PLAYLIST_IMPORT("playlist_import", "Playlist Import"),
    AUDIO_CONVERTER("audio_converter", "Audio Converter"),
    CACHE_MANAGEMENT("cache_management", "Caches & Storage"),
    MUSIC_VIDEOS("music_videos", "Music Videos")
}

sealed interface SettingType<T> {
    val default: T
    data class Toggle(override val default: Boolean) : SettingType<Boolean>
    data class IntRange(override val default: Int, val min: Int, val max: Int, val step: Int = 1) : SettingType<Int>
    data class FloatRange(override val default: Float, val min: Float, val max: Float, val step: Float = 0.1f) : SettingType<Float>
    data class Choice(override val default: String, val options: List<String>) : SettingType<String>
    data class Text(override val default: String) : SettingType<String>
}

data class SettingDefinition<T>(
    val key: String,
    val section: SettingSection,
    val subsection: SettingSubsection? = null,
    val title: String,
    val summary: String? = null,
    val type: SettingType<T>,
    val keywords: List<String> = emptyList(),
    val platforms: Set<SettingPlatform> = setOf(SettingPlatform.ANDROID, SettingPlatform.DESKTOP),
    val dependsOnKey: String? = null
)

object SettingsRegistry {

    // --- Online, Connect & Network Section ---
    val IS_ONLINE_MODE = SettingDefinition(
        key = "is_online_mode",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.YOUTUBE_ACCOUNT,
        title = "Online Streaming Mode",
        summary = "Stream music from YouTube Music catalogs via in-house InnerTube client",
        type = SettingType.Toggle(default = true),
        keywords = listOf("online", "streaming", "youtube", "innertube")
    )

    val NODE_CONNECT_ENABLED = SettingDefinition(
        key = "node_connect_enabled",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.NODE_CONNECT_DEVICES,
        title = "Node Connect Peer Discovery",
        summary = "Discover and cast lossless audio to nearby desktop and mobile players over LAN/mDNS",
        type = SettingType.Toggle(default = true),
        keywords = listOf("connect", "cast", "wifi", "network", "remote", "mdns")
    )

    val LASTFM_SCROBBLE_ENABLED = SettingDefinition(
        key = "lastfm_scrobble_enabled",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        title = "Last.fm Scrobbler",
        summary = "Submit now-playing status and scrobble tracks automatically",
        type = SettingType.Toggle(default = false),
        keywords = listOf("lastfm", "scrobble", "history")
    )

    val DISCORD_ENABLED = SettingDefinition(
        key = "discord_enabled",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.DISCORD_PRESENCE,
        title = "Discord Rich Presence",
        summary = "Broadcast active track title, artist, album, and live playback timestamps to Discord",
        type = SettingType.Toggle(default = false),
        keywords = listOf("discord", "rpc", "presence", "listening"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    val DISCORD_INCOGNITO = SettingDefinition(
        key = "discord_incognito",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.DISCORD_PRESENCE,
        title = "Incognito Mode",
        summary = "Temporarily suspend Discord presence updates and clear active listening status",
        type = SettingType.Toggle(default = false),
        keywords = listOf("discord", "incognito", "private", "stealth"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    val DISCORD_SHOW_COVER = SettingDefinition(
        key = "discord_show_cover",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.DISCORD_PRESENCE,
        title = "Display Artwork",
        summary = "Display track cover art image in rich presence profile card",
        type = SettingType.Toggle(default = true),
        keywords = listOf("discord", "cover", "artwork", "image"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    val DISCORD_SHOW_ALBUM = SettingDefinition(
        key = "discord_show_album",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.DISCORD_PRESENCE,
        title = "Display Album Name",
        summary = "Include album title alongside artist in rich presence status",
        type = SettingType.Toggle(default = true),
        keywords = listOf("discord", "album"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    val DISCORD_SHOW_WHILE_PAUSED = SettingDefinition(
        key = "discord_show_while_paused",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.DISCORD_PRESENCE,
        title = "Show While Paused",
        summary = "Retain rich presence card when playback is paused instead of clearing immediately",
        type = SettingType.Toggle(default = false),
        keywords = listOf("discord", "paused", "pause"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    val DISCORD_APP_ID = SettingDefinition(
        key = "discord_app_id",
        section = SettingSection.ONLINE_CONNECT_NETWORK,
        subsection = SettingSubsection.DISCORD_PRESENCE,
        title = "Discord Application ID",
        summary = "Custom Application Client ID created in Discord Developer Portal",
        type = SettingType.Text(default = ""),
        keywords = listOf("discord", "appid", "clientid", "developer"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    // --- Playback Section ---
    val AUTO_FETCH_LYRICS = SettingDefinition(
        key = "auto_fetch_lyrics",
        section = SettingSection.PLAYBACK,
        title = "Auto-fetch Synced Lyrics",
        summary = "Query LRCLIB and TTML syllable lyrics when playing songs",
        type = SettingType.Toggle(default = true),
        keywords = listOf("lyrics", "synced", "ttml", "lrclib")
    )

    val GENRE_CROSSFADE_ENABLED = SettingDefinition(
        key = "genre_crossfade_enabled",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.AUDIO_ENGINE,
        title = "Smart Crossfade",
        summary = "Smooth equal-power audio transition between consecutive tracks",
        type = SettingType.Toggle(default = true),
        keywords = listOf("crossfade", "gapless", "fade", "transition")
    )

    val LOUDNESS_SCAN_ENABLED = SettingDefinition(
        key = "loudness_scan_enabled",
        section = SettingSection.PLAYBACK,
        title = "EBU R128 Loudness Normalization",
        summary = "Prevent sudden volume jumps between diverse tracks",
        type = SettingType.Toggle(default = true),
        keywords = listOf("loudness", "r128", "normalization", "volume", "lufs")
    )

    // --- Taste Engine Settings (Playback > Recommendations) ---
    val TASTE_USE_HISTORY = SettingDefinition(
        key = "taste_use_history",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "History-Based Recommendations",
        summary = "Build recommendations exclusively from real YouTube Music history",
        type = SettingType.Toggle(default = true),
        keywords = listOf("taste", "history", "recommendations", "mix", "youtube")
    )

    val TASTE_HISTORY_LIMIT = SettingDefinition(
        key = "taste_history_limit",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "History Fetch Limit",
        summary = "Number of recent history entries to analyze (50-500)",
        type = SettingType.IntRange(default = 200, min = 50, max = 500, step = 10),
        keywords = listOf("taste", "history", "limit", "depth")
    )

    val TASTE_RECENT_EXCLUDE = SettingDefinition(
        key = "taste_recent_exclude",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Exclude Recent Tracks",
        summary = "Number of most recently played songs to strictly exclude from recommendations",
        type = SettingType.IntRange(default = 15, min = 0, max = 50, step = 1),
        keywords = listOf("taste", "exclude", "recent", "cooldown")
    )

    val TASTE_TOP_ARTISTS = SettingDefinition(
        key = "taste_top_artists",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Top Artists Pool Size",
        summary = "Number of top artists ranked by play count to consider for seeding",
        type = SettingType.IntRange(default = 10, min = 3, max = 20, step = 1),
        keywords = listOf("taste", "artists", "pool")
    )

    val TASTE_SEED_ARTISTS = SettingDefinition(
        key = "taste_seed_artists",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Seed Artists Count",
        summary = "Number of seed artists drawn via weighted probability for radio queues",
        type = SettingType.IntRange(default = 3, min = 1, max = 5, step = 1),
        keywords = listOf("taste", "seed", "artists")
    )

    val TASTE_DAILY_MIX_SIZE = SettingDefinition(
        key = "taste_daily_mix_size",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Daily Mix Tracks Count",
        summary = "Number of tracks drawn from listening history with a stable intra-day seed",
        type = SettingType.IntRange(default = 12, min = 0, max = 30, step = 1),
        keywords = listOf("taste", "daily", "mix", "tracks")
    )

    val TASTE_SEED_RADIO_SIZE = SettingDefinition(
        key = "taste_seed_radio_size",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Radio Queue Tracks Count",
        summary = "Number of tracks drawn from seed artist radio queues",
        type = SettingType.IntRange(default = 13, min = 0, max = 30, step = 1),
        keywords = listOf("taste", "radio", "tracks")
    )

    val TASTE_TIME_OF_DAY_MOODS = SettingDefinition(
        key = "taste_time_of_day_moods",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Time-of-Day Mood Shelf",
        summary = "Display contextual mood recommendations matching current hour",
        type = SettingType.Toggle(default = true),
        keywords = listOf("taste", "mood", "time", "contextual")
    )

    val TASTE_CLEAR_CACHE = SettingDefinition(
        key = "taste_clear_cache",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.RECOMMENDATIONS,
        title = "Clear Recommendation Cache",
        summary = "Force rebuild of 'Your Mix' session blend on next refresh",
        type = SettingType.Toggle(default = false),
        keywords = listOf("taste", "clear", "cache", "reset")
    )

    // --- Library & Files Section ---
    val MIN_DURATION_SECONDS = SettingDefinition(
        key = "min_duration_seconds",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.LIBRARY_MANAGEMENT,
        title = "Filter Short Audio Clips",
        summary = "Ignore notifications, ringtones, and tracks under threshold duration",
        type = SettingType.IntRange(default = 30, min = 0, max = 120, step = 5),
        keywords = listOf("duration", "filter", "library", "short")
    )

    val IMPORT_ARTIST_TITLE_ORDER = SettingDefinition(
        key = "import_artist_title_order",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Text Line Track Order",
        summary = "Default ordering for ambiguous 'A - B' text lines in TXT imports",
        type = SettingType.Choice(default = "artist_title", options = listOf("artist_title", "title_artist")),
        keywords = listOf("import", "order", "artist", "title", "txt", "playlist")
    )

    val IMPORT_AUTO_ACCEPT = SettingDefinition(
        key = "import_auto_accept",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Auto-Accept Match Threshold",
        summary = "Minimum confidence percentage to automatically accept candidate without review",
        type = SettingType.IntRange(default = 90, min = 50, max = 100, step = 5),
        keywords = listOf("import", "auto", "accept", "threshold", "confidence")
    )

    val IMPORT_REVIEW_MIN = SettingDefinition(
        key = "import_review_min",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Review Minimum Threshold",
        summary = "Candidates with confidence between this threshold and auto-accept require manual confirmation",
        type = SettingType.IntRange(default = 60, min = 30, max = 90, step = 5),
        keywords = listOf("import", "review", "threshold", "confidence")
    )

    val IMPORT_DURATION_WINDOW_MS = SettingDefinition(
        key = "import_duration_window_ms",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Duration Match Window (ms)",
        summary = "Allowed duration deviation between input and candidate track",
        type = SettingType.IntRange(default = 3000, min = 0, max = 10000, step = 500),
        keywords = listOf("import", "duration", "window", "ms")
    )

    val IMPORT_MAX_LINES = SettingDefinition(
        key = "import_max_lines",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Maximum Import Lines",
        summary = "Safety limit on total items processed from a single playlist file",
        type = SettingType.IntRange(default = 5000, min = 100, max = 20000, step = 500),
        keywords = listOf("import", "limit", "max", "lines")
    )

    val IMPORT_MAX_MB = SettingDefinition(
        key = "import_max_mb",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Maximum File Size (MB)",
        summary = "Maximum permitted file size for CSV and TXT playlist files",
        type = SettingType.IntRange(default = 5, min = 1, max = 50, step = 1),
        keywords = listOf("import", "limit", "max", "mb", "file")
    )

    val IMPORT_ONLINE_CONCURRENCY = SettingDefinition(
        key = "import_online_concurrency",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Online Search Concurrency",
        summary = "Simultaneous network search requests during remote candidate matching",
        type = SettingType.IntRange(default = 2, min = 1, max = 4, step = 1),
        keywords = listOf("import", "online", "concurrency", "parallel")
    )

    val IMPORT_ALSO_YOUTUBE = SettingDefinition(
        key = "import_also_youtube",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.PLAYLIST_IMPORT,
        title = "Also Create on YouTube Music",
        summary = "Create a remote playlist on authenticated YouTube Music account when importing",
        type = SettingType.Toggle(default = false),
        keywords = listOf("import", "youtube", "remote", "playlist")
    )

    // --- Audio Converter Settings ---
    val CONVERT_DEFAULT_PRESET = SettingDefinition(
        key = "convert_default_preset",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Default Transcode Preset",
        summary = "Default Opus target bitrate preset",
        type = SettingType.Choice(default = "128", options = listOf("192", "128", "96", "CUSTOM")),
        keywords = listOf("convert", "transcode", "opus", "preset", "bitrate")
    )

    val CONVERT_CUSTOM_BITRATE = SettingDefinition(
        key = "convert_custom_bitrate",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Custom Target Bitrate (kbps)",
        summary = "Target Opus bitrate when Custom preset is selected",
        type = SettingType.IntRange(default = 160, min = 32, max = 320, step = 1),
        keywords = listOf("convert", "transcode", "opus", "custom", "bitrate")
    )

    val CONVERT_VBR = SettingDefinition(
        key = "convert_vbr",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Variable Bitrate (VBR)",
        summary = "Enable dynamic bitrate allocation tailored to audio complexity",
        type = SettingType.Toggle(default = true),
        keywords = listOf("convert", "transcode", "opus", "vbr")
    )

    val CONVERT_APPLICATION = SettingDefinition(
        key = "convert_application",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Opus Encoder Tuning",
        summary = "Opus encoder application mode tuning",
        type = SettingType.Choice(default = "audio", options = listOf("audio", "voip", "lowdelay")),
        keywords = listOf("convert", "transcode", "opus", "tuning", "application")
    )

    val CONVERT_COMPRESSION_LEVEL = SettingDefinition(
        key = "convert_compression_level",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Compression Effort Level",
        summary = "Opus encoder computational complexity (0 to 10)",
        type = SettingType.IntRange(default = 10, min = 0, max = 10, step = 1),
        keywords = listOf("convert", "transcode", "opus", "compression", "effort")
    )

    val CONVERT_KEEP_ORIGINAL = SettingDefinition(
        key = "convert_keep_original",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Keep Original Source Files",
        summary = "Keep original FLAC files after conversion. If disabled, replaces source files only after verifying output",
        type = SettingType.Toggle(default = true),
        keywords = listOf("convert", "transcode", "keep", "delete", "replace", "original")
    )

    val CONVERT_OUTPUT_MODE = SettingDefinition(
        key = "convert_output_mode",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Output Destination Mode",
        summary = "Save converted Opus files to a custom folder or next to original FLAC files",
        type = SettingType.Choice(default = "CUSTOM_FOLDER", options = listOf("CUSTOM_FOLDER", "NEXT_TO_ORIGINAL")),
        keywords = listOf("convert", "output", "mode", "destination")
    )

    val CONVERT_OUTPUT_FOLDER = SettingDefinition(
        key = "convert_output_folder",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Converted Files Destination",
        summary = "Directory path where converted Opus files will be saved",
        type = SettingType.Text(default = ""),
        keywords = listOf("convert", "output", "folder", "path", "directory")
    )

    val CONVERT_NAME_TEMPLATE = SettingDefinition(
        key = "convert_name_template",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Output File Name Template",
        summary = "Pattern for output file names (e.g. {title})",
        type = SettingType.Text(default = "{title}"),
        keywords = listOf("convert", "filename", "template")
    )

    val CONVERT_WORKERS = SettingDefinition(
        key = "convert_workers",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Concurrent Transcode Workers",
        summary = "Number of parallel audio conversion threads (1 to 4)",
        type = SettingType.IntRange(default = 2, min = 1, max = 4, step = 1),
        keywords = listOf("convert", "concurrency", "workers", "threads")
    )

    val CONVERT_WRITE_R128 = SettingDefinition(
        key = "convert_write_r128",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Write R128 Loudness Tags",
        summary = "Calculate and inject R128 loudness tags into output Opus files",
        type = SettingType.Toggle(default = false),
        keywords = listOf("convert", "r128", "loudness", "gain")
    )

    val CONVERT_FFMPEG_PATH = SettingDefinition(
        key = "convert_ffmpeg_path",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Custom FFmpeg Binary Path",
        summary = "Optional manual override for FFmpeg executable path on Desktop",
        type = SettingType.Text(default = ""),
        keywords = listOf("convert", "ffmpeg", "binary", "path"),
        platforms = setOf(SettingPlatform.DESKTOP)
    )

    val CONVERT_RESCAN_AFTER = SettingDefinition(
        key = "convert_rescan_after",
        section = SettingSection.LIBRARY_FILES,
        subsection = SettingSubsection.AUDIO_CONVERTER,
        title = "Auto-Rescan Library After Conversion",
        summary = "Automatically trigger local library scan when batch conversion finishes",
        type = SettingType.Toggle(default = true),
        keywords = listOf("convert", "rescan", "library")
    )

    // --- Storage & Downloads Section ---
    val DOWNLOAD_CONCURRENT_LIMIT = SettingDefinition(
        key = "download_concurrent_limit",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Concurrent Downloads",
        summary = "Number of simultaneous active download workers (1 to 4)",
        type = SettingType.IntRange(default = 2, min = 1, max = 4, step = 1),
        keywords = listOf("download", "concurrency", "workers", "parallel")
    )

    val DOWNLOAD_DEFAULT_QUALITY = SettingDefinition(
        key = "download_default_quality",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Default Download Quality",
        summary = "Preferred audio quality preset or prompt on each download",
        type = SettingType.Choice(default = "ASK", options = listOf("ASK", "HIGHEST", "BALANCED", "DATA_SAVER")),
        keywords = listOf("download", "quality", "bitrate", "preset")
    )

    val DOWNLOAD_PREFERRED_FORMAT = SettingDefinition(
        key = "download_preferred_format",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Preferred Audio Container",
        summary = "Container format preference for downloaded offline tracks",
        type = SettingType.Choice(default = "AUTO", options = listOf("AUTO", "OPUS", "M4A")),
        keywords = listOf("download", "format", "container", "codec", "opus", "m4a")
    )

    val DOWNLOAD_WIFI_ONLY = SettingDefinition(
        key = "download_wifi_only",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Download over Wi-Fi Only",
        summary = "Automatically pause active downloads when connected to mobile data",
        type = SettingType.Toggle(default = true),
        keywords = listOf("download", "wifi", "metered", "cellular")
    )

    val CACHE_LIMIT_IMAGES_MB = SettingDefinition(
        key = "cache_limit_images_mb",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Cover Art & Image Cache Limit",
        summary = "Maximum disk storage allocated for cached artwork and thumbnails",
        type = SettingType.IntRange(default = 200, min = 50, max = 1000, step = 50),
        keywords = listOf("cache", "images", "artwork", "limit")
    )

    val CACHE_LIMIT_LYRICS_MB = SettingDefinition(
        key = "cache_limit_lyrics_mb",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Lyrics Cache Limit",
        summary = "Maximum disk storage allocated for synchronized lyrics files",
        type = SettingType.IntRange(default = 50, min = 10, max = 200, step = 10),
        keywords = listOf("cache", "lyrics", "lrc", "limit")
    )

    val CACHE_LIMIT_FEED_MB = SettingDefinition(
        key = "cache_limit_feed_mb",
        section = SettingSection.STORAGE,
        subsection = SettingSubsection.CACHE_MANAGEMENT,
        title = "Home Feed Disk Cache Limit",
        summary = "Maximum disk storage allocated for offline home feed responses",
        type = SettingType.IntRange(default = 20, min = 5, max = 100, step = 5),
        keywords = listOf("cache", "feed", "home", "limit")
    )

    // --- Music Videos Section (Playback) ---
    val VIDEO_AUTO_PLAY = SettingDefinition(
        key = "video_auto_play",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Auto-Play Music Videos",
        summary = "Automatically load and play video stream when available instead of audio only",
        type = SettingType.Toggle(default = false),
        keywords = listOf("video", "autoplay", "music video", "stream")
    )

    val VIDEO_DEFAULT_QUALITY = SettingDefinition(
        key = "video_default_quality",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Default Video Quality",
        summary = "Preferred video resolution",
        type = SettingType.Choice(default = "auto", options = listOf("auto", "1080", "720", "480", "360")),
        keywords = listOf("video", "quality", "resolution", "1080p", "720p", "480p", "360p")
    )

    val VIDEO_SEEK_STEP_S = SettingDefinition(
        key = "video_seek_step_s",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Double-Tap Seek Step",
        summary = "Seconds to seek backward or forward on double-tap in cinema mode",
        type = SettingType.Choice(default = "10", options = listOf("5", "10", "15", "30")),
        keywords = listOf("video", "seek", "double tap", "gesture", "step")
    )

    val VIDEO_GESTURES = SettingDefinition(
        key = "video_gestures",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Cinema Gestures",
        summary = "Swipe vertically on left half for brightness and right half for volume",
        type = SettingType.Toggle(default = true),
        keywords = listOf("video", "gesture", "brightness", "volume", "swipe")
    )

    val VIDEO_PIP = SettingDefinition(
        key = "video_pip",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Picture-in-Picture",
        summary = "Automatically enter PiP mode when leaving the app while a video is playing",
        type = SettingType.Toggle(default = true),
        keywords = listOf("video", "pip", "picture in picture", "background"),
        platforms = setOf(SettingPlatform.ANDROID)
    )

    val VIDEO_BACKGROUND_AUDIO_ONLY = SettingDefinition(
        key = "video_background_audio_only",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Background Audio-Only Mode",
        summary = "Stop video decoding to save cellular data when backgrounded without PiP",
        type = SettingType.Toggle(default = false),
        keywords = listOf("video", "background", "data saver", "audio only"),
        platforms = setOf(SettingPlatform.ANDROID)
    )

    val VIDEO_CONTROLS_AUTOHIDE_S = SettingDefinition(
        key = "video_controls_autohide_s",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Cinema Controls Auto-Hide",
        summary = "Seconds before fullscreen playback overlay controls automatically fade out",
        type = SettingType.IntRange(default = 4, min = 2, max = 10, step = 1),
        keywords = listOf("video", "controls", "autohide", "overlay", "cinema")
    )

    val VIDEO_SHOW_QUALITY_PILL = SettingDefinition(
        key = "video_show_quality_pill",
        section = SettingSection.PLAYBACK,
        subsection = SettingSubsection.MUSIC_VIDEOS,
        title = "Show Quality Badge Pill",
        summary = "Display interactive video resolution pill on the Now Playing container",
        type = SettingType.Toggle(default = true),
        keywords = listOf("video", "pill", "badge", "resolution")
    )

    // --- Master Registry Map ---
    private val entries: Map<String, SettingDefinition<*>> = listOf(
        IS_ONLINE_MODE,
        NODE_CONNECT_ENABLED,
        LASTFM_SCROBBLE_ENABLED,
        DISCORD_ENABLED,
        DISCORD_INCOGNITO,
        DISCORD_SHOW_COVER,
        DISCORD_SHOW_ALBUM,
        DISCORD_SHOW_WHILE_PAUSED,
        DISCORD_APP_ID,
        AUTO_FETCH_LYRICS,
        GENRE_CROSSFADE_ENABLED,
        LOUDNESS_SCAN_ENABLED,
        TASTE_USE_HISTORY,
        TASTE_HISTORY_LIMIT,
        TASTE_RECENT_EXCLUDE,
        TASTE_TOP_ARTISTS,
        TASTE_SEED_ARTISTS,
        TASTE_DAILY_MIX_SIZE,
        TASTE_SEED_RADIO_SIZE,
        TASTE_TIME_OF_DAY_MOODS,
        TASTE_CLEAR_CACHE,
        MIN_DURATION_SECONDS,
        IMPORT_ARTIST_TITLE_ORDER,
        IMPORT_AUTO_ACCEPT,
        IMPORT_REVIEW_MIN,
        IMPORT_DURATION_WINDOW_MS,
        IMPORT_MAX_LINES,
        IMPORT_MAX_MB,
        IMPORT_ONLINE_CONCURRENCY,
        IMPORT_ALSO_YOUTUBE,
        CONVERT_DEFAULT_PRESET,
        CONVERT_CUSTOM_BITRATE,
        CONVERT_VBR,
        CONVERT_APPLICATION,
        CONVERT_COMPRESSION_LEVEL,
        CONVERT_KEEP_ORIGINAL,
        CONVERT_OUTPUT_MODE,
        CONVERT_OUTPUT_FOLDER,
        CONVERT_NAME_TEMPLATE,
        CONVERT_WORKERS,
        CONVERT_WRITE_R128,
        CONVERT_FFMPEG_PATH,
        CONVERT_RESCAN_AFTER,
        DOWNLOAD_CONCURRENT_LIMIT,
        DOWNLOAD_DEFAULT_QUALITY,
        DOWNLOAD_PREFERRED_FORMAT,
        DOWNLOAD_WIFI_ONLY,
        CACHE_LIMIT_IMAGES_MB,
        CACHE_LIMIT_LYRICS_MB,
        CACHE_LIMIT_FEED_MB,
        VIDEO_AUTO_PLAY,
        VIDEO_DEFAULT_QUALITY,
        VIDEO_SEEK_STEP_S,
        VIDEO_GESTURES,
        VIDEO_PIP,
        VIDEO_BACKGROUND_AUDIO_ONLY,
        VIDEO_CONTROLS_AUTOHIDE_S,
        VIDEO_SHOW_QUALITY_PILL
    ).associateBy { it.key }

    fun getAll(): Collection<SettingDefinition<*>> = entries.values

    fun getByKey(key: String): SettingDefinition<*>? = entries[key]

    fun getBySection(section: SettingSection): List<SettingDefinition<*>> =
        entries.values.filter { it.section == section }

    fun getBySubsection(subsection: SettingSubsection): List<SettingDefinition<*>> =
        entries.values.filter { it.subsection == subsection }
}
