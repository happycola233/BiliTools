package com.happycola233.bilitools.core

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class Mp4DolbyConfigurationTest {
    private val atmos = byteArrayOf(0x14, 0, 0x20, 0x0f, 0, 1, 0x10)
    private val regular = byteArrayOf(0x14, 0, 0x20, 0x0f, 0, 0)

    @Test
    fun restoresAtmosConfigurationWithoutMovingMediaDataAndCanBeReadAgain() {
        val source = file("source.mp4", mp4(atmos))
        val output = file("output.mp4", mp4(regular))
        val before = output.readBytes()
        val mdatOffset = find(before, "mdat") - 4
        Mp4DolbyConfiguration.preserve(source, output)
        val after = output.readBytes()
        assertArrayEquals(atmos, Mp4DolbyConfiguration.readConfigurations(output).single())
        assertTrue(Mp4DolbyConfiguration.hasAtmosExtension(atmos))
        assertEquals(mdatOffset, find(after, "mdat") - 4)
        assertArrayEquals(before.copyOfRange(mdatOffset, before.size), after.copyOfRange(mdatOffset, before.size))
        assertEquals("free", after.copyOfRange(20, 24).toString(Charsets.ISO_8859_1))
        // 所有父级大小都正确：重新按边界遍历能找到新的 dec3；第二次执行不继续追加 moov。
        Mp4DolbyConfiguration.preserve(source, output)
        assertArrayEquals(after, output.readBytes())
    }

    @Test
    fun multipleAudioTracksKeepTheirOwnConfigurationAndAncestorSizes() {
        val alternate = atmos.copyOf().also { it[it.lastIndex] = 12 }
        val source = file("multi-source.mp4", mp4(atmos, alternate))
        val output = file("multi-output.mp4", mp4(regular, regular))
        Mp4DolbyConfiguration.preserve(source, output)
        val configs = Mp4DolbyConfiguration.readConfigurations(output)
        assertEquals(2, configs.size)
        assertArrayEquals(atmos, configs[0])
        assertArrayEquals(alternate, configs[1])
    }

    @Test
    fun fragmentedSourceConfigurationIsReadFromInitMoov() {
        val ordinary = mp4(atmos)
        val moovSize = ByteBuffer.wrap(ordinary).getInt(16)
        val initMoov = ordinary.copyOfRange(16, 16 + moovSize)
        // DASH 将配置保留在 init moov，音频包位于后续 moof/mdat；读取配置不依赖样本表。
        val source = file("fragmented-source.m4s", box("ftyp", ByteArray(8)) +
            box("sidx", ByteArray(32)) + initMoov +
            box("moof", box("mfhd", ByteArray(8)) + box("traf", box("tfhd", ByteArray(16)))) +
            box("mdat", "fragment 1".toByteArray()) +
            box("moof", box("mfhd", ByteArray(8))) + box("mdat", "fragment 2".toByteArray()))
        val before = source.readBytes()
        val output = file("fragmented-output.m4a", mp4(regular))
        Mp4DolbyConfiguration.preserve(source, output)
        assertArrayEquals(atmos, Mp4DolbyConfiguration.readConfigurations(output).single())
        assertArrayEquals(before, source.readBytes())
    }

    @Test
    fun extendedSizeMoovAndAudioEntriesKeepValidParentBoundaries() {
        val source = file("extended-source.mp4", mp4(atmos, atmos, extendedSize = true))
        val output = file("extended-output.mp4", mp4(regular, regular, extendedSize = true))
        val original = output.readBytes()
        val mdatOffset = find(original, "mdat") - 4
        Mp4DolbyConfiguration.preserve(source, output)
        val result = output.readBytes()
        assertEquals(mdatOffset, find(result, "mdat") - 4)
        assertArrayEquals(original.copyOfRange(mdatOffset, original.size), result.copyOfRange(mdatOffset, original.size))
        val configs = Mp4DolbyConfiguration.readConfigurations(output)
        assertEquals(2, configs.size)
        configs.forEach { assertArrayEquals(atmos, it) }
        val appendedMoov = ByteBuffer.wrap(result, original.size, result.size - original.size).slice()
        assertEquals(1, appendedMoov.int)
        appendedMoov.position(8)
        assertEquals((result.size - original.size).toLong(), appendedMoov.long)
    }

    @Test
    fun regularEac3AndAlreadyCorrectOutputAreNotRewritten() {
        val source = file("plain-source.mp4", mp4(regular))
        val output = file("plain-output.mp4", mp4(regular))
        val original = output.readBytes()
        Mp4DolbyConfiguration.preserve(source, output)
        assertArrayEquals(original, output.readBytes())
        assertFalse(Mp4DolbyConfiguration.hasAtmosExtension(regular))
        val correct = file("correct.mp4", mp4(atmos))
        val correctBefore = correct.readBytes()
        Mp4DolbyConfiguration.preserve(correct, correct)
        assertArrayEquals(correctBefore, correct.readBytes())
    }

    @Test
    fun missingOutputEac3AfterExplicitTranscodeDoesNotInsertOldConfiguration() {
        val source = file("transcode-source.mp4", mp4(atmos))
        val output = file("aac-output.mp4", box("ftyp", ByteArray(8)) + box("moov", box("mvhd", ByteArray(8))))
        val before = output.readBytes()
        Mp4DolbyConfiguration.preserve(source, output)
        assertArrayEquals(before, output.readBytes())
    }

    @Test
    fun malformedSourceBoxFailsBeforeChangingOutput() {
        val source = file("malformed-source.mp4", mp4(atmos).also { ByteBuffer.wrap(it).putInt(16, Int.MAX_VALUE) })
        val output = file("protected-output.mp4", mp4(regular))
        val before = output.readBytes()
        assertThrows(IOException::class.java) { Mp4DolbyConfiguration.preserve(source, output) }
        assertArrayEquals(before, output.readBytes())
    }

    @Test
    fun truncatedAtmosExtensionFailsBeforeChangingOutput() {
        val source = file("truncated-source.mp4", mp4(atmos.copyOf(6)))
        val output = file("truncated-output.mp4", mp4(regular))
        val before = output.readBytes()
        assertThrows(IOException::class.java) { Mp4DolbyConfiguration.preserve(source, output) }
        assertArrayEquals(before, output.readBytes())
    }

    @Test
    fun mismatchedTrackCountFailsBeforeChangingOutput() {
        val source = file("mismatch-source.mp4", mp4(atmos, atmos))
        val output = file("mismatch-output.mp4", mp4(regular))
        val before = output.readBytes()
        assertThrows(IOException::class.java) { Mp4DolbyConfiguration.preserve(source, output) }
        assertArrayEquals(before, output.readBytes())
    }

    private fun file(name: String, bytes: ByteArray): File =
        File("../.tmp/media-output-tests/boxes/$name").apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun mp4(vararg configs: ByteArray, extendedSize: Boolean = false): ByteArray {
        val tracks = configs.map { config ->
            var nested = box("ec-3", ByteArray(28) + box("dec3", config), extendedSize)
            nested = box("stsd", ByteArray(4) + ByteBuffer.allocate(4).putInt(1).array() + nested)
            for (container in listOf("stbl", "minf", "mdia", "trak")) nested = box(container, nested)
            nested
        }.fold(ByteArray(0)) { all, track -> all + track }
        return box("ftyp", ByteArray(8)) + box("moov", tracks, extendedSize) + box("mdat", "unchanged media data".toByteArray())
    }

    private fun box(type: String, content: ByteArray, extended: Boolean = false): ByteArray =
        if (extended) ByteBuffer.allocate(16 + content.size).putInt(1).put(type.toByteArray())
            .putLong(16L + content.size).put(content).array()
        else ByteBuffer.allocate(8 + content.size).putInt(8 + content.size).put(type.toByteArray()).put(content).array()

    private fun find(bytes: ByteArray, type: String): Int = bytes.toString(Charsets.ISO_8859_1).indexOf(type)
}
