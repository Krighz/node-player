package com.nodeplayer.network.cast

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class AudioPacket(
    val sequenceId: Long,
    val ptsMs: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPacket) return false
        if (sequenceId != other.sequenceId) return false
        if (ptsMs != other.ptsMs) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sequenceId.hashCode()
        result = 31 * result + ptsMs.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Dynamic Audio Jitter Buffer for Node Connect Cast Receiver.
 * Target buffer latency: 150ms to 300ms.
 * Supports instantaneous flush on seek/track change and micro-drift adjustment.
 */
class JitterBuffer(
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 2,
    val targetBufferMs: Int = 200,
    val minBufferMs: Int = 80,
    val maxBufferMs: Int = 400
) {
    private val bytesPerSample = (bitDepth / 8) * channels
    private val bytesPerMs = (sampleRate * bytesPerSample) / 1000

    private val lock = ReentrantLock()
    private val queue = ConcurrentLinkedQueue<AudioPacket>()
    private val activeSequenceId = AtomicLong(0L)

    private var currentByteCount = 0
    private var activePacket: AudioPacket? = null
    private var activePacketOffset = 0

    var underrunCount = 0
        private set
    var overrunCount = 0
        private set

    /**
     * Pushes a received PCM chunk into the jitter buffer.
     * Drops chunks with stale sequenceId.
     */
    fun push(sequenceId: Long, ptsMs: Long, data: ByteArray): Boolean {
        val currentSeq = activeSequenceId.get()
        if (sequenceId < currentSeq) {
            // Stale packet from previous track or pre-seek: drop immediately!
            return false
        }
        if (sequenceId > currentSeq) {
            // Newer sequence started without explicit flush: adopt new sequence
            flush(sequenceId)
        }

        lock.withLock {
            val maxBytes = maxBufferMs * bytesPerMs
            if (currentByteCount + data.size > maxBytes) {
                overrunCount++
                // Drop oldest packet to avoid runaway latency
                val dropped = queue.poll()
                if (dropped != null) {
                    currentByteCount -= dropped.data.size
                }
            }

            queue.offer(AudioPacket(sequenceId, ptsMs, data))
            currentByteCount += data.size
        }
        return true
    }

    /**
     * Pulls PCM bytes from the jitter buffer for playback by the hardware audio sink.
     * Returns the actual number of bytes read (zero if underrun / buffer starvation).
     */
    fun read(target: ByteArray, offset: Int, length: Int): Int {
        lock.withLock {
            var bytesRead = 0
            while (bytesRead < length) {
                var pkt = activePacket
                if (pkt == null || activePacketOffset >= pkt.data.size) {
                    pkt = queue.poll()
                    if (pkt == null) {
                        // Buffer underrun
                        activePacket = null
                        activePacketOffset = 0
                        if (bytesRead == 0) {
                            underrunCount++
                        }
                        break
                    }
                    activePacket = pkt
                    activePacketOffset = 0
                    currentByteCount -= pkt.data.size
                }

                val available = pkt.data.size - activePacketOffset
                val needed = length - bytesRead
                val toCopy = Math.min(available, needed)

                System.arraycopy(pkt.data, activePacketOffset, target, offset + bytesRead, toCopy)
                activePacketOffset += toCopy
                bytesRead += toCopy
            }
            return bytesRead
        }
    }

    /**
     * Flushes the jitter buffer immediately when a seek or track change occurs.
     * Zero old audio will be played.
     */
    fun flush(newSequenceId: Long) {
        lock.withLock {
            activeSequenceId.set(newSequenceId)
            queue.clear()
            activePacket = null
            activePacketOffset = 0
            currentByteCount = 0
        }
    }

    val bufferedMs: Long
        get() {
            if (bytesPerMs <= 0) return 0L
            return (currentByteCount / bytesPerMs).toLong()
        }

    val isReadyForPlayback: Boolean
        get() = bufferedMs >= minBufferMs
}
