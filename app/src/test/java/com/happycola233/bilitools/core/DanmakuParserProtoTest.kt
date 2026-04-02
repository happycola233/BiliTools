package com.happycola233.bilitools.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class DanmakuParserProtoTest {
    @Test
    fun parse_skipsLengthDelimitedFieldWithoutCorruptingFollowingData() {
        val elemBytes = ByteArrayOutputStream().apply {
            // A length-delimited field that we do not consume should not shift the reader.
            writeTag(1, 2)
            writeString("264235008hbad_payload")

            writeTag(2, 0)
            writeVarint(184_014)

            writeTag(3, 0)
            writeVarint(1)

            writeTag(4, 0)
            writeVarint(25)

            writeTag(5, 0)
            writeVarint(16_777_215)

            writeTag(6, 2)
            writeString("5b405ef3")

            writeTag(7, 2)
            writeString("danmaku-content")

            writeTag(8, 0)
            writeVarint(1_765_897_830L)

            writeTag(10, 0)
            writeVarint(0)
        }.toByteArray()

        val eventBytes = ByteArrayOutputStream().apply {
            writeTag(1, 2)
            writeVarint(elemBytes.size.toLong())
            write(elemBytes)
        }.toByteArray()

        val parsed = DanmakuParser.parse(eventBytes)

        assertEquals(1, parsed.size)
        assertEquals(184_014L, parsed[0].progressMs)
        assertEquals(1, parsed[0].mode)
        assertEquals(25, parsed[0].fontSize)
        assertEquals(16_777_215, parsed[0].color)
        assertEquals("5b405ef3", parsed[0].midHash)
        assertEquals("danmaku-content", parsed[0].content)
        assertEquals(1_765_897_830L, parsed[0].ctime)
        assertEquals(0, parsed[0].pool)
        assertEquals("", parsed[0].idStr)
    }

    private fun ByteArrayOutputStream.writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    private fun ByteArrayOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(bytes.size.toLong())
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeVarint(value: Long) {
        var current = value
        while (true) {
            if ((current and -128L) == 0L) {
                write(current.toInt() and 0x7F)
                return
            }
            write(((current and 0x7F) or 0x80).toInt())
            current = current ushr 7
        }
    }
}
