package com.nodeplayer.library

import com.nodeplayer.model.EqBandConfig
import java.util.regex.Pattern

object AutoEqParser {
    // Matches: Filter 1: ON PK Fc 105 Hz Gain -3.2 dB Q 1.41
    private val FILTER_REGEX = Pattern.compile(
        "Filter\\s+(\\d+):\\s+ON\\s+PK\\s+Fc\\s+([\\d.]+)\\s*Hz\\s+Gain\\s+([+-]?[\\d.]+)\\s*dB\\s+Q\\s+([\\d.]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(text: String): List<EqBandConfig> {
        val bands = mutableListOf<EqBandConfig>()
        var index = 0
        for (line in text.lineSequence()) {
            val matcher = FILTER_REGEX.matcher(line.trim())
            if (matcher.find()) {
                val fc = matcher.group(2).toFloatOrNull() ?: continue
                val gain = matcher.group(3).toFloatOrNull() ?: continue
                val q = matcher.group(4).toFloatOrNull() ?: 1.414f
                bands.add(EqBandConfig(index = index++, centerFreq = fc, gainDb = gain, q = q))
                if (bands.size >= 10) break
            }
        }
        return bands
    }
}

object M3uParser {
    fun parse(content: String): List<String> {
        val paths = mutableListOf<String>()
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            paths.add(trimmed)
        }
        return paths
    }

    fun export(paths: List<String>): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (path in paths) {
            sb.append(path).append("\n")
        }
        return sb.toString()
    }
}
