package com.nodeplayer.common

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {

    @Test
    fun verifyCoreModulesDoNotImportAndroidFramework() {
        val rootDir = findProjectRoot() ?: return // Skip if running in isolated runner without project root
        val coreDirs = listOf("core-common", "core-model", "core-dsp", "core-library")

        val violations = mutableListOf<String>()

        for (coreDir in coreDirs) {
            val dir = File(rootDir, "$coreDir/src/main/kotlin")
            if (!dir.exists()) continue

            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import android.")) {
                            violations.add("${file.relativeTo(rootDir)}:${index + 1}: imports Android framework: $trimmed")
                        }
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Core modules must remain pure Kotlin and never import Android classes:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun verifyFeaturesDoNotImportOtherFeaturesInternals() {
        val rootDir = findProjectRoot() ?: return
        val featuresDir = File(rootDir, "app-android/src/main/kotlin/com/nodeplayer/music")
        if (!featuresDir.exists()) return

        val violations = mutableListOf<String>()

        featuresDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val relative = file.relativeTo(featuresDir).path.replace('\\', '/')
            val currentFeature = relative.substringBefore('/')

            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import com.nodeplayer.music.") && trimmed.contains(".internal.")) {
                        val importedFeature = trimmed.removePrefix("import com.nodeplayer.music.").substringBefore('.')
                        if (importedFeature != currentFeature) {
                            violations.add("${file.relativeTo(rootDir)}:${index + 1}: cross-feature internal import: $trimmed")
                        }
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Features must never import another feature's internal classes:\n" + violations.joinToString("\n")
        )
    }

    private fun findProjectRoot(): File? {
        var current = File(".").canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists() && File(current, "app-android").exists()) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}
