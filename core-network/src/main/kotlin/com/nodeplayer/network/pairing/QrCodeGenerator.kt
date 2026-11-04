package com.nodeplayer.network.pairing

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin Self-Contained QR Code (ISO/IEC 18004) Generator.
 * Zero external libraries, zero native dependencies.
 * Generates standard Byte Mode QR Codes with Reed-Solomon Error Correction.
 */
class QrCodeMatrix(val size: Int) {
    private val modules = Array(size) { BooleanArray(size) }
    private val isFunction = Array(size) { BooleanArray(size) }

    fun get(x: Int, y: Int): Boolean = modules[y][x]

    fun set(x: Int, y: Int, value: Boolean) {
        modules[y][x] = value
        isFunction[y][x] = true
    }

    fun isReserved(x: Int, y: Int): Boolean = isFunction[y][x]

    fun setDirect(x: Int, y: Int, value: Boolean) {
        modules[y][x] = value
    }
}

object QrCodeGenerator {

    // Reed-Solomon Galois Field GF(256) with primitive poly 0x11D (285)
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = (x shl 1) xor if (x >= 128) 0x11D else 0
        }
        LOG[0] = 0
    }

    private fun gfMul(x: Int, y: Int): Int = if (x == 0 || y == 0) 0 else EXP[LOG[x] + LOG[y]]

    private fun rsCompute(data: ByteArray, eccCount: Int): ByteArray {
        var gen = intArrayOf(1)
        for (i in 0 until eccCount) {
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gfMul(gen[j], EXP[i])
                nextGen[j + 1] = nextGen[j + 1] xor gen[j]
            }
            gen = nextGen
        }

        val res = IntArray(eccCount)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor res[0]
            System.arraycopy(res, 1, res, 0, eccCount - 1)
            res[eccCount - 1] = 0
            for (j in 0 until eccCount) {
                res[j] = res[j] xor gfMul(gen[j], factor)
            }
        }
        val out = ByteArray(eccCount)
        for (i in 0 until eccCount) out[i] = res[i].toByte()
        return out
    }

    // Version definitions (Version 1 to 10, ECC Level M)
    // format: (dataCapacityBytes, eccCodewordsPerBlock, blocks)
    private val VERSION_CAPACITY = arrayOf(
        intArrayOf(0, 0, 0),         // Dummy
        intArrayOf(14, 10, 1),        // V1: 21x21, 14 bytes
        intArrayOf(26, 16, 1),        // V2: 25x25, 26 bytes
        intArrayOf(42, 26, 1),        // V3: 29x29, 42 bytes
        intArrayOf(62, 18, 2),        // V4: 33x33, 62 bytes (2 blocks of 31 + 18 ecc)
        intArrayOf(84, 24, 2),        // V5: 37x37, 84 bytes (2 blocks of 42 + 24 ecc)
        intArrayOf(108, 16, 4),       // V6: 41x41, 108 bytes (4 blocks of 27 + 16 ecc)
        intArrayOf(124, 18, 4),       // V7: 45x45, 124 bytes (4 blocks of 31 + 18 ecc)
        intArrayOf(152, 22, 4),       // V8: 49x49, 152 bytes (4 blocks of 38 + 22 ecc)
        intArrayOf(180, 22, 4),       // V9: 53x53, 180 bytes (4 blocks of 45 + 22 ecc)
        intArrayOf(216, 26, 4)        // V10: 57x57, 216 bytes (4 blocks of 54 + 26 ecc)
    )

    private val ALIGNMENT_PATTERN_POS = arrayOf(
        intArrayOf(),
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50)
    )

    /**
     * Generates a 2D QR Code boolean matrix for the given text payload.
     */
    fun encode(text: String): QrCodeMatrix {
        val rawBytes = text.toByteArray(StandardCharsets.UTF_8)

        // Select minimal fitting version
        var version = 1
        while (version <= 10 && VERSION_CAPACITY[version][0] < rawBytes.size + 3) {
            version++
        }
        if (version > 10) {
            version = 10 // Fallback clamp
        }

        val totalDataCap = VERSION_CAPACITY[version][0]
        val eccPerBlock = VERSION_CAPACITY[version][1]
        val numBlocks = VERSION_CAPACITY[version][2]
        val size = 17 + 4 * version
        val matrix = QrCodeMatrix(size)

        // 1. Pack bit stream: Mode=Byte(0100) + Count(8 bits) + Data + Terminator(0000)
        val bitBuffer = mutableListOf<Int>()
        fun appendBits(value: Int, numBits: Int) {
            for (i in numBits - 1 downTo 0) {
                bitBuffer.add((value ushr i) and 1)
            }
        }

        appendBits(0b0100, 4) // Byte mode
        appendBits(rawBytes.size, 8)
        for (b in rawBytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        // Terminator
        val remainingBits = totalDataCap * 8 - bitBuffer.size
        val termLen = remainingBits.coerceIn(0, 4)
        for (i in 0 until termLen) bitBuffer.add(0)

        // Byte align
        while (bitBuffer.size % 8 != 0) bitBuffer.add(0)

        // Pad bytes (0xEC, 0x11)
        val pad = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.size < totalDataCap * 8) {
            appendBits(pad[padIdx % 2], 8)
            padIdx++
        }

        // Convert bits to data bytes
        val dataBytes = ByteArray(totalDataCap)
        for (i in 0 until totalDataCap) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bitBuffer[i * 8 + j]
            }
            dataBytes[i] = b.toByte()
        }

        // 2. Compute ECC per block
        val blockSize = totalDataCap / numBlocks
        val dataBlocks = Array(numBlocks) { ByteArray(blockSize) }
        val eccBlocks = Array(numBlocks) { ByteArray(eccPerBlock) }

        for (blk in 0 until numBlocks) {
            System.arraycopy(dataBytes, blk * blockSize, dataBlocks[blk], 0, blockSize)
            eccBlocks[blk] = rsCompute(dataBlocks[blk], eccPerBlock)
        }

        // Interleave data codewords then ECC codewords
        val finalCodewords = ByteArrayOutputStream()
        for (i in 0 until blockSize) {
            for (blk in 0 until numBlocks) {
                finalCodewords.write(dataBlocks[blk][i].toInt())
            }
        }
        for (i in 0 until eccPerBlock) {
            for (blk in 0 until numBlocks) {
                finalCodewords.write(eccBlocks[blk][i].toInt())
            }
        }
        val allCodewords = finalCodewords.toByteArray()

        // 3. Place Function Patterns into Matrix
        // Finder patterns (top-left, top-right, bottom-left)
        fun placeFinderPattern(originX: Int, originY: Int) {
            for (y in -1..7) {
                for (x in -1..7) {
                    val mx = originX + x
                    val my = originY + y
                    if (mx in 0 until size && my in 0 until size) {
                        val isBlack = (x in 0..6 && (y == 0 || y == 6)) ||
                                (y in 0..6 && (x == 0 || x == 6)) ||
                                (x in 2..4 && y in 2..4)
                        matrix.set(mx, my, isBlack)
                    }
                }
            }
        }

        placeFinderPattern(0, 0)
        placeFinderPattern(size - 7, 0)
        placeFinderPattern(0, size - 7)

        // Alignment patterns
        val alignPos = ALIGNMENT_PATTERN_POS[version]
        for (ay in alignPos) {
            for (ax in alignPos) {
                if (matrix.isReserved(ax, ay)) continue
                for (y in -2..2) {
                    for (x in -2..2) {
                        val isBlack = Math.max(Math.abs(x), Math.abs(y)) != 1
                        matrix.set(ax + x, ay + y, isBlack)
                    }
                }
            }
        }

        // Timing patterns
        for (i in 0 until size) {
            if (!matrix.isReserved(i, 6)) matrix.set(i, 6, i % 2 == 0)
            if (!matrix.isReserved(6, i)) matrix.set(6, i, i % 2 == 0)
        }

        // Dark module
        matrix.set(8, 4 * version + 9, true)

        // Reserve Format Information areas
        for (i in 0..8) {
            if (!matrix.isReserved(i, 8)) matrix.set(i, 8, false)
            if (!matrix.isReserved(8, i)) matrix.set(8, i, false)
        }
        for (i in size - 8 until size) {
            if (!matrix.isReserved(i, 8)) matrix.set(i, 8, false)
            if (!matrix.isReserved(8, i)) matrix.set(8, i, false)
        }

        // 4. Fill Data with Mask Pattern 0: (x + y) % 2 == 0
        var bitIdx = 0
        val totalBits = allCodewords.size * 8
        var right = size - 1

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing pattern
            val colList = listOf(right, right - 1)
            val rows = if (((right + 1) / 2) % 2 == 1) (size - 1 downTo 0) else (0 until size)

            for (row in rows) {
                for (col in colList) {
                    if (!matrix.isReserved(col, row)) {
                        var bit = false
                        if (bitIdx < totalBits) {
                            val byteVal = allCodewords[bitIdx / 8].toInt() and 0xFF
                            val shift = 7 - (bitIdx % 8)
                            bit = ((byteVal ushr shift) and 1) == 1
                            bitIdx++
                        }
                        // Mask 0 invert condition
                        if ((col + row) % 2 == 0) {
                            bit = !bit
                        }
                        matrix.setDirect(col, row, bit)
                    }
                }
            }
            right -= 2
        }

        // 5. Write Format Information (ECC Level M = 00, Mask 0 = 000 -> 0x5412 XOR masked)
        val formatBits = 0b101010000010010 // Standard format code for M-Mask0
        fun setFormatBit(idx: Int, bit: Boolean) {
            // Around top-left
            when {
                idx <= 5 -> matrix.setDirect(idx, 8, bit)
                idx == 6 -> matrix.setDirect(7, 8, bit)
                idx == 7 -> matrix.setDirect(8, 8, bit)
                idx == 8 -> matrix.setDirect(8, 7, bit)
                else -> matrix.setDirect(8, 14 - idx, bit)
            }
            // Around top-right and bottom-left
            if (idx < 8) {
                matrix.setDirect(size - 1 - idx, 8, bit)
            } else {
                matrix.setDirect(8, size - 15 + idx, bit)
            }
        }

        for (i in 0 until 15) {
            val bit = ((formatBits ushr (14 - i)) and 1) == 1
            setFormatBit(i, bit)
        }

        return matrix
    }
}
