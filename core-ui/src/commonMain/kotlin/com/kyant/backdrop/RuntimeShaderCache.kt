package com.kyant.backdrop

import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
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
            try {
                Shared.obtainRuntimeShader("lens", RoundedRectRefractionShaderString)
            } catch (_: Throwable) {
                // Ignore warmup failure on unsupported platforms
            }
        }
    }
}
