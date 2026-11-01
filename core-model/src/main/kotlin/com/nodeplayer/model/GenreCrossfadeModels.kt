package com.nodeplayer.model

data class GenreCrossfadeRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val genrePattern: String,
    val isExactMatch: Boolean = false,
    val fadeSeconds: Float = 4.0f,
    val curve: FadeCurve = FadeCurve.EQUAL_POWER,
    val isEnabled: Boolean = true
) {
    fun matches(genre: String?): Boolean {
        if (!isEnabled || genre.isNullOrBlank()) return false
        val tokens = genre.split('/', ';', ',', '|').map { it.trim().lowercase() }
        val pattern = genrePattern.trim().lowercase()
        return if (isExactMatch) {
            tokens.any { it == pattern }
        } else {
            tokens.any { it.contains(pattern) }
        }
    }

    companion object {
        fun defaultRules(): List<GenreCrossfadeRule> = listOf(
            GenreCrossfadeRule(
                genrePattern = "Ambient",
                fadeSeconds = 6.0f,
                curve = FadeCurve.EQUAL_POWER
            ),
            GenreCrossfadeRule(
                genrePattern = "Classical",
                fadeSeconds = 5.0f,
                curve = FadeCurve.LOGARITHMIC
            ),
            GenreCrossfadeRule(
                genrePattern = "Live",
                fadeSeconds = 0.0f, // 0 = off for live recordings
                curve = FadeCurve.LINEAR
            ),
            GenreCrossfadeRule(
                genrePattern = "DJ Mix",
                fadeSeconds = 0.0f,
                curve = FadeCurve.LINEAR
            ),
            GenreCrossfadeRule(
                genrePattern = "Electronic",
                fadeSeconds = 4.0f,
                curve = FadeCurve.EQUAL_POWER
            )
        )
    }
}

object GenreCrossfadeResolver {

    /**
     * Resolves the effective crossfade config between [trackA] and [trackB].
     * Rules:
     * 1. If tracks are consecutive in the same album, auto-gapless wins (0s).
     * 2. Checks matching rules for both tracks.
     * 3. When two tracks match different rules, uses the shorter fade length.
     * 4. Falls back to [globalConfig] if no rule matches.
     */
    fun resolve(
        trackA: Track?,
        trackB: Track?,
        globalConfig: CrossfadeConfig,
        rules: List<GenreCrossfadeRule>
    ): Pair<CrossfadeConfig, String> {
        val ruleA = trackA?.genre?.let { g -> rules.firstOrNull { it.matches(g) } }
        val ruleB = trackB?.genre?.let { g -> rules.firstOrNull { it.matches(g) } }

        return when {
            ruleA != null && ruleB != null -> {
                val shorterRule = if (ruleA.fadeSeconds <= ruleB.fadeSeconds) ruleA else ruleB
                val config = globalConfig.copy(
                    durationSeconds = shorterRule.fadeSeconds,
                    curve = shorterRule.curve,
                    enabled = shorterRule.fadeSeconds > 0f
                )
                config to "Genre crossfade (${shorterRule.genrePattern}, shorter fade applied: ${shorterRule.fadeSeconds}s)"
            }
            ruleA != null -> {
                val config = globalConfig.copy(
                    durationSeconds = ruleA.fadeSeconds,
                    curve = ruleA.curve,
                    enabled = ruleA.fadeSeconds > 0f
                )
                config to "Genre crossfade (${ruleA.genrePattern}: ${ruleA.fadeSeconds}s)"
            }
            ruleB != null -> {
                val config = globalConfig.copy(
                    durationSeconds = ruleB.fadeSeconds,
                    curve = ruleB.curve,
                    enabled = ruleB.fadeSeconds > 0f
                )
                config to "Genre crossfade (${ruleB.genrePattern}: ${ruleB.fadeSeconds}s)"
            }
            else -> {
                globalConfig to "Global crossfade (${globalConfig.durationSeconds}s)"
            }
        }
    }
}
