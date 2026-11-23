package com.nodeplayer.desktop.converter

import com.nodeplayer.model.converter.TranscodeJob
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DesktopFFmpegTranscoderTest {

    @Test
    fun testBuildArgumentsArgumentVectorIntegrity() {
        val job = TranscodeJob(
            input = File("C:/Music/test track with spaces.flac"),
            output = File("C:/Music/Converted/test track with spaces.opus"),
            bitrateKbps = 160,
            vbr = true,
            application = "audio",
            compressionLevel = 10,
            keepTags = true,
            keepCover = true
        )

        val args = DesktopFFmpegTranscoder.buildArguments(job, "ffmpeg.exe")

        assertEquals("ffmpeg.exe", args[0])
        assertTrue(args.contains("-hide_banner"))
        assertTrue(args.contains("-nostdin"))
        assertTrue(args.contains("-y"))
        assertTrue(args.contains(job.input.absolutePath))
        assertTrue(args.contains("-c:a"))
        assertTrue(args.contains("libopus"))
        assertTrue(args.contains("-b:a"))
        assertTrue(args.contains("160k"))
        assertTrue(args.contains("-vbr"))
        assertTrue(args.contains("on"))
        assertTrue(args.contains("-compression_level"))
        assertTrue(args.contains("10"))
        assertTrue(args.contains("-application"))
        assertTrue(args.contains("audio"))
        assertTrue(args.contains("-map_metadata"))
        assertTrue(args.contains("0"))
        assertTrue(args.contains("-progress"))
        assertTrue(args.contains("pipe:1"))
        assertEquals(job.output.absolutePath, args.last())
    }

    @Test
    fun testBuildArgumentsWithoutCoverOrTags() {
        val job = TranscodeJob(
            input = File("input.flac"),
            output = File("output.opus"),
            bitrateKbps = 128,
            vbr = false,
            application = "voip",
            compressionLevel = 5,
            keepTags = false,
            keepCover = false
        )

        val args = DesktopFFmpegTranscoder.buildArguments(job, "ffmpeg")

        assertFalse(args.contains("-map_metadata"))
        assertFalse(args.contains("attached_pic"))
        assertTrue(args.contains("-vbr"))
        assertTrue(args.contains("off"))
        assertTrue(args.contains("-application"))
        assertTrue(args.contains("voip"))
        assertTrue(args.contains("-compression_level"))
        assertTrue(args.contains("5"))
    }

    @Test
    fun testResolveFFmpegBinaryDefaultsToFfmpeg() {
        val binary = DesktopFFmpegTranscoder.resolveFFmpegBinary("")
        assertTrue(binary == "ffmpeg" || binary.endsWith("ffmpeg.exe") || binary.endsWith("ffmpeg"))
    }
}
