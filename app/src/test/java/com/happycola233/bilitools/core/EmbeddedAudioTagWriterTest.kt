package com.happycola233.bilitools.core

import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.junit.Assert.*
import org.junit.Test

class EmbeddedAudioTagWriterTest {
    @Test fun mp3WritesRealLyricsFrameAndFrontCover() = roundTrip("mp3")
    @Test fun flacWritesLyricsAndPictureDimensionsWithoutAwt() = roundTrip("flac")

    private fun roundTrip(extension: String) {
        val directory = File("../.tmp/metadata/native-tag-tests").apply { mkdirs() }
        val file = File.createTempFile("tag-", ".$extension", directory)
        val cover = File.createTempFile("cover-", ".jpg", directory)
        try {
            javaClass.getResourceAsStream("/metadata/audio.$extension")!!.use { source ->
                file.outputStream().use { source.copyTo(it) }
            }
            javaClass.getResourceAsStream("/metadata/cover.jpg")!!.use { source ->
                cover.outputStream().use { source.copyTo(it) }
            }
            val before = AudioFileIO.read(file).audioHeader
            val lyrics = "[00:00.00]示例歌词\n[00:00.20]第二句"
            EmbeddedAudioTagWriter.write(
                file,
                mapOf("title" to "标题=测试", "artist" to "作者", "album" to "作品", "track" to "2/5", "lyrics" to lyrics),
                EmbeddedCover(cover, 64, 48),
            )
            val result = AudioFileIO.read(file)
            assertEquals(before.sampleRate, result.audioHeader.sampleRate)
            assertEquals(before.channels, result.audioHeader.channels)
            val tag = result.tag
            assertEquals("标题=测试", tag.getFirst(FieldKey.TITLE))
            assertEquals("作者", tag.getFirst(FieldKey.ARTIST))
            assertEquals("作品", tag.getFirst(FieldKey.ALBUM))
            assertEquals("2", tag.getFirst(FieldKey.TRACK))
            assertEquals("5", tag.getFirst(FieldKey.TRACK_TOTAL))
            assertEquals(lyrics, tag.getFirst(FieldKey.LYRICS))
            assertTrue(tag.getFirst(FieldKey.GENRE).isBlank())
            assertEquals(1, tag.artworkList.size)
            val artwork = tag.firstArtwork
            assertEquals("image/jpeg", artwork.mimeType)
            assertEquals(3, artwork.pictureType)
            assertArrayEquals(cover.readBytes(), artwork.binaryData)
            if (extension == "flac") {
                assertEquals(64, artwork.width)
                assertEquals(48, artwork.height)
            } else {
                assertTrue((tag as AbstractID3v2Tag).hasField("USLT"))
                val lyricsFrame = tag.getFirstField(FieldKey.LYRICS) as AbstractID3v2Frame
                assertEquals("und", (lyricsFrame.body as FrameBodyUSLT).language)
            }
            EmbeddedAudioTagWriter.write(file, mapOf("track" to "3"), null)
            val updated = AudioFileIO.read(file).tag
            assertEquals("3", updated.getFirst(FieldKey.TRACK))
            assertTrue("Unknown total must not inherit the old total", updated.getFirst(FieldKey.TRACK_TOTAL).isBlank())
            assertEquals(lyrics, updated.getFirst(FieldKey.LYRICS))
        } finally {
            file.delete()
            cover.delete()
        }
    }
}
