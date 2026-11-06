/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual fun RuntimeShader(shaderString: String): RuntimeShader {
    val effect = RuntimeEffect.makeForShader(shaderString)
    val builder = RuntimeShaderBuilder(effect)
    return SkikoRuntimeShader(builder)
}

actual fun RuntimeShader.asComposeShader(): Shader {
    return (this as SkikoRuntimeShader).shader.makeShader()
}

internal fun RuntimeShader.asSkikoRuntimeShader(): RuntimeShaderBuilder {
    return (this as SkikoRuntimeShader).shader
}

class SkikoRuntimeShader(val shader: RuntimeShaderBuilder) : RuntimeShader {

    override fun setFloatUniform(name: String, value: Float) {
        shader.uniform(name, value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        shader.uniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        shader.uniform(name, value1, value2, value3)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        shader.uniform(name, value1, value2, value3, value4)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        when (values.size) {
            1 -> shader.uniform(name, values[0])
            2 -> shader.uniform(name, values[0], values[1])
            3 -> shader.uniform(name, values[0], values[1], values[2])
            4 -> shader.uniform(name, values[0], values[1], values[2], values[3])
        }
    }

    override fun setIntUniform(name: String, value: Int) {
        shader.uniform(name, value)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int) {
        shader.uniform(name, value1, value2)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {
        shader.uniform(name, value1, value2, value3)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        shader.uniform(name, value1, value2, value3, value4)
    }

    override fun setIntUniform(name: String, values: IntArray) {
        when (values.size) {
            1 -> shader.uniform(name, values[0])
            2 -> shader.uniform(name, values[0], values[1])
            3 -> shader.uniform(name, values[0], values[1], values[2])
            4 -> shader.uniform(name, values[0], values[1], values[2], values[3])
        }
    }

    override fun setColorUniform(name: String, color: Color) {
        val a = color.alpha
        shader.uniform(name, color.red * a, color.green * a, color.blue * a, a)
    }
}
