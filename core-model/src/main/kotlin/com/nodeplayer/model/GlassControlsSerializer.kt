package com.nodeplayer.model

/**
 * Pure Kotlin versioned JSON serializer for Liquid Glass controls configuration.
 * Format: NodePlayerGlassControls v1
 *
 * Provides serialization, deserialization with safe clamping, format validation,
 * corrupt payload fallback, and two-tier reset engine.
 */
object GlassControlsSerializer {

    const val FORMAT_NAME = "NodePlayerGlassControls"
    const val CURRENT_VERSION = 1
    const val MAX_JSON_LENGTH = 65536 // 64KB max to prevent memory exhaustion (T-04-02)

    fun toJson(config: GlassControlsConfig): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": \"$FORMAT_NAME\",\n")
        sb.append("  \"version\": $CURRENT_VERSION,\n")
        sb.append("  \"masterEnabled\": ${config.masterEnabled},\n")
        sb.append("  \"preset\": \"${config.preset.name}\",\n")
        sb.append("  \"global\": {\n")
        appendMaster(sb, config.global, "    ")
        sb.append("\n  },\n")
        sb.append("  \"components\": {\n")
        val types = GlassComponentType.values()
        types.forEachIndexed { index, type ->
            val comp = config.getComponent(type)
            sb.append("    \"${type.name}\": {\n")
            appendComponent(sb, comp, "      ")
            sb.append("\n    }")
            if (index < types.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  }\n")
        sb.append("}")
        return sb.toString()
    }

    fun fromJson(json: String): GlassControlsConfig {
        if (json.length > MAX_JSON_LENGTH || json.isBlank()) {
            return GlassControlsConfig.PRESET_BALANCED
        }
        return try {
            val format = extractString(json, "format", "")
            val version = extractInt(json, "version", 0)

            // Validate header (T-04-01)
            if (format != FORMAT_NAME || version > CURRENT_VERSION || version <= 0) {
                return GlassControlsConfig.PRESET_BALANCED
            }

            val masterEnabled = extractBool(json, "masterEnabled", true)
            val presetName = extractString(json, "preset", GlassControlsPreset.BALANCED.name)
            val preset = try {
                GlassControlsPreset.valueOf(presetName)
            } catch (e: Exception) {
                GlassControlsPreset.BALANCED
            }

            val globalBlock = extractObjectBlock(json, "global")
            val global = parseMaster(globalBlock)

            val componentsBlock = extractObjectBlock(json, "components")
            var config = GlassControlsConfig(
                masterEnabled = masterEnabled,
                preset = preset,
                global = global
            )

            if (componentsBlock != null) {
                for (type in GlassComponentType.values()) {
                    val compBlock = extractObjectBlock(componentsBlock, type.name)
                    if (compBlock != null) {
                        val parsedComp = parseComponent(compBlock)
                        config = config.updateComponent(type, parsedComp)
                    }
                }
            }

            config
        } catch (e: Exception) {
            GlassControlsConfig.PRESET_BALANCED
        }
    }

    /**
     * Resets a specific component override to inherit from global (D-15).
     * Sets useGlobal = true, forceLighterInLists = false, and matches values to global.
     */
    fun resetComponentToGlobal(config: GlassControlsConfig, type: GlassComponentType): GlassControlsConfig {
        val global = config.global
        val resetComp = ComponentGlassConfig(
            useGlobal = true,
            forceLighterInLists = false,
            blurDp = global.blurDp,
            lensHeightDp = global.lensHeightDp,
            lensAmountDp = global.lensAmountDp,
            vibrancy = global.vibrancy,
            tintOpacity = global.tintOpacity,
            highlightStrength = global.highlightStrength,
            shadowStrength = global.shadowStrength,
            chromaticAberration = global.chromaticAberration,
            cornerShapeDp = global.cornerShapeDp,
            pressScale = global.pressScale,
            springStiffness = global.springStiffness,
            springDamping = global.springDamping
        )
        return config.updateComponent(type, resetComp)
    }

    /**
     * Resets all configurations to factory default preset (D-15).
     */
    fun resetAllToFactoryDefaults(): GlassControlsConfig {
        return GlassControlsConfig.PRESET_BALANCED
    }

    private fun appendMaster(sb: StringBuilder, g: MasterGlassConfig, indent: String) {
        sb.append("${indent}\"blurDp\": ${g.blurDp},\n")
        sb.append("${indent}\"lensHeightDp\": ${g.lensHeightDp},\n")
        sb.append("${indent}\"lensAmountDp\": ${g.lensAmountDp},\n")
        sb.append("${indent}\"vibrancy\": ${g.vibrancy},\n")
        sb.append("${indent}\"tintOpacity\": ${g.tintOpacity},\n")
        sb.append("${indent}\"highlightStrength\": ${g.highlightStrength},\n")
        sb.append("${indent}\"shadowStrength\": ${g.shadowStrength},\n")
        sb.append("${indent}\"chromaticAberration\": ${g.chromaticAberration},\n")
        sb.append("${indent}\"cornerShapeDp\": ${g.cornerShapeDp},\n")
        sb.append("${indent}\"pressScale\": ${g.pressScale},\n")
        sb.append("${indent}\"springStiffness\": ${g.springStiffness},\n")
        sb.append("${indent}\"springDamping\": ${g.springDamping}")
    }

    private fun appendComponent(sb: StringBuilder, c: ComponentGlassConfig, indent: String) {
        sb.append("${indent}\"useGlobal\": ${c.useGlobal},\n")
        sb.append("${indent}\"forceLighterInLists\": ${c.forceLighterInLists},\n")
        sb.append("${indent}\"blurDp\": ${c.blurDp},\n")
        sb.append("${indent}\"lensHeightDp\": ${c.lensHeightDp},\n")
        sb.append("${indent}\"lensAmountDp\": ${c.lensAmountDp},\n")
        sb.append("${indent}\"vibrancy\": ${c.vibrancy},\n")
        sb.append("${indent}\"tintOpacity\": ${c.tintOpacity},\n")
        sb.append("${indent}\"highlightStrength\": ${c.highlightStrength},\n")
        sb.append("${indent}\"shadowStrength\": ${c.shadowStrength},\n")
        sb.append("${indent}\"chromaticAberration\": ${c.chromaticAberration},\n")
        sb.append("${indent}\"cornerShapeDp\": ${c.cornerShapeDp},\n")
        sb.append("${indent}\"pressScale\": ${c.pressScale},\n")
        sb.append("${indent}\"springStiffness\": ${c.springStiffness},\n")
        sb.append("${indent}\"springDamping\": ${c.springDamping}")
    }

    private fun parseMaster(block: String?): MasterGlassConfig {
        val default = MasterGlassConfig()
        if (block == null) return default
        return MasterGlassConfig(
            blurDp = extractFloat(block, "blurDp", default.blurDp, 0f, 64f),
            lensHeightDp = extractFloat(block, "lensHeightDp", default.lensHeightDp, 0f, 64f),
            lensAmountDp = extractFloat(block, "lensAmountDp", default.lensAmountDp, 0f, 64f),
            vibrancy = extractFloat(block, "vibrancy", default.vibrancy, 0f, 2f),
            tintOpacity = extractFloat(block, "tintOpacity", default.tintOpacity, 0f, 1f),
            highlightStrength = extractFloat(block, "highlightStrength", default.highlightStrength, 0f, 1f),
            shadowStrength = extractFloat(block, "shadowStrength", default.shadowStrength, 0f, 1f),
            chromaticAberration = extractFloat(block, "chromaticAberration", default.chromaticAberration, 0f, 1f),
            cornerShapeDp = extractFloat(block, "cornerShapeDp", default.cornerShapeDp, 0f, 64f),
            pressScale = extractFloat(block, "pressScale", default.pressScale, 0.5f, 1f),
            springStiffness = extractFloat(block, "springStiffness", default.springStiffness, 50f, 2000f),
            springDamping = extractFloat(block, "springDamping", default.springDamping, 0.1f, 2f)
        )
    }

    private fun parseComponent(block: String?): ComponentGlassConfig {
        val default = ComponentGlassConfig()
        if (block == null) return default
        return ComponentGlassConfig(
            useGlobal = extractBool(block, "useGlobal", default.useGlobal),
            forceLighterInLists = extractBool(block, "forceLighterInLists", default.forceLighterInLists),
            blurDp = extractFloat(block, "blurDp", default.blurDp, 0f, 64f),
            lensHeightDp = extractFloat(block, "lensHeightDp", default.lensHeightDp, 0f, 64f),
            lensAmountDp = extractFloat(block, "lensAmountDp", default.lensAmountDp, 0f, 64f),
            vibrancy = extractFloat(block, "vibrancy", default.vibrancy, 0f, 2f),
            tintOpacity = extractFloat(block, "tintOpacity", default.tintOpacity, 0f, 1f),
            highlightStrength = extractFloat(block, "highlightStrength", default.highlightStrength, 0f, 1f),
            shadowStrength = extractFloat(block, "shadowStrength", default.shadowStrength, 0f, 1f),
            chromaticAberration = extractFloat(block, "chromaticAberration", default.chromaticAberration, 0f, 1f),
            cornerShapeDp = extractFloat(block, "cornerShapeDp", default.cornerShapeDp, 0f, 64f),
            pressScale = extractFloat(block, "pressScale", default.pressScale, 0.5f, 1f),
            springStiffness = extractFloat(block, "springStiffness", default.springStiffness, 50f, 2000f),
            springDamping = extractFloat(block, "springDamping", default.springDamping, 0.1f, 2f)
        )
    }

    private fun extractObjectBlock(source: String, key: String): String? {
        val keyPattern = Regex(""""$key"\s*:\s*\{""")
        val match = keyPattern.find(source) ?: return null
        val startIndex = match.range.last // this is '{'
        var depth = 0
        for (i in startIndex until source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) {
                    return source.substring(startIndex + 1, i)
                }
            }
        }
        return null
    }

    private fun extractBool(block: String, key: String, default: Boolean): Boolean {
        val m = Regex(""""$key"\s*:\s*(true|false)""").find(block)
        return m?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: default
    }

    private fun extractFloat(block: String, key: String, default: Float, min: Float, max: Float): Float {
        val m = Regex(""""$key"\s*:\s*(-?[\d.]+(?:[eE][-+]?\d+)?)""").find(block)
        val raw = m?.groupValues?.get(1)?.toFloatOrNull()
        return if (raw != null && !raw.isNaN() && !raw.isInfinite()) {
            raw.coerceIn(min, max)
        } else {
            default
        }
    }

    private fun extractString(source: String, key: String, default: String): String {
        val m = Regex(""""$key"\s*:\s*"([^"]*)"""").find(source)
        return m?.groupValues?.get(1) ?: default
    }

    private fun extractInt(source: String, key: String, default: Int): Int {
        val m = Regex(""""$key"\s*:\s*(\d+)""").find(source)
        return m?.groupValues?.get(1)?.toIntOrNull() ?: default
    }
}
