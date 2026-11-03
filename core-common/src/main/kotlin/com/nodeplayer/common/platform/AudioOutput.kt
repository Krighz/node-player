package com.nodeplayer.common.platform

data class AudioFormatConfig(
    val sampleRate: Int,
    val channelCount: Int = 2,
    val isFloat32: Boolean = true,
    val isExclusiveMode: Boolean = false
)

data class AudioDeviceSpec(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val maxSampleRate: Int = 192000,
    val minSampleRate: Int = 44100,
    val preferredSampleRate: Int = 48000,
    val supportedBits: List<Int> = listOf(16, 24, 32)
)

interface AudioOutput {
    val isOpened: Boolean
    val currentDeviceSpec: AudioDeviceSpec?
    val currentSampleRate: Int
    val currentChannels: Int

    fun open(config: AudioFormatConfig): Boolean
    fun write(buffer: FloatArray, offset: Int, count: Int): Int
    fun pause()
    fun resume()
    fun flush()
    fun close()
    fun getPlaybackPositionMs(): Long
}
