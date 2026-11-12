package com.kyant.backdrop

import com.kyant.backdrop.internal.AmbientHighlightShaderString
import com.kyant.backdrop.internal.DefaultHighlightShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader

    companion object {
        fun warmUp() {
            RuntimeShaderCacheImpl.warmUp()
        }
    }
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        runtimeShaders.clear()
    }

    companion object {
        val Shared = RuntimeShaderCacheImpl()

        fun warmUp() {
            val shaders = listOf(
                "Refraction" to RoundedRectRefractionShaderString,
                "RefractionWithDispersion" to RoundedRectRefractionWithDispersionShaderString,
                "Default" to DefaultHighlightShaderString,
                "Ambient" to AmbientHighlightShaderString
            )
            for ((key, shaderString) in shaders) {
                try {
                    Shared.obtainRuntimeShader(key, shaderString)
                } catch (_: Throwable) {
                    // Ignore warmup failure on unsupported platforms
                }
            }
        }
    }
}
