package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.DownloadMetadataSettings
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

internal data class EmbeddedCover(val file: File, val width: Int, val height: Int)

internal fun DownloadEmbeddedMetadata.tagValues(
    settings: DownloadMetadataSettings,
    lyrics: String? = null,
    lyricsSource: String? = null,
): Map<String, String> = buildMap {
    fun add(key: String, value: String?) {
        value?.trim()?.takeIf(String::isNotBlank)?.let { put(key, it) }
    }
    add("title", title)
    add("artist", artist.takeUnless { artistIsUploader && !settings.useUploaderAsArtist })
    val albumEnabled = !albumIsCollection || settings.useCollectionAsAlbum
    add("album", album.takeIf { albumEnabled })
    if (albumEnabled && album != null && trackNumber != null) {
        add("track", trackNumber.toString() + (trackTotal?.let { "/$it" } ?: ""))
    }
    add("lyrics", lyrics)
    // 来源链接是内容页，不冒充唱片发行官网；普通 TAG 也不冒充音乐流派。
    add("comment", buildList {
        comment?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        uploader?.let { add("UP 主：$it") }
        publishedDate?.let { add("B站发布时间：$it") }
        if (tags.isNotEmpty()) add("标签：${tags.joinToString("、")}")
        lyricsSource?.let { add("歌词来源：$it") }
        originalUrl?.let { add("来源：$it") }
    }.joinToString("\n"))
}

/** 原生 ID3 / Vorbis 字段。FFmpeg 6 的 MP3 muxer 会把 lyrics 写成 TXXX，播放器不认作 USLT。 */
internal object EmbeddedAudioTagWriter {
    fun write(file: File, values: Map<String, String>, cover: EmbeddedCover?) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        val fields = mapOf(
            "title" to FieldKey.TITLE,
            "artist" to FieldKey.ARTIST,
            "album" to FieldKey.ALBUM,
            "comment" to FieldKey.COMMENT,
            "lyrics" to FieldKey.LYRICS,
        )
        fields.forEach { (name, key) ->
            values[name]?.let { value ->
                val field = tag.createField(key, value)
                if (field is AbstractID3v2Frame) {
                    // 这两个帧要求三字母语言码；来源没声明歌词语言，不猜英文或中文。
                    when (val body = field.body) {
                        is FrameBodyUSLT -> body.language = "und"
                        is FrameBodyCOMM -> body.language = "und"
                    }
                }
                tag.deleteField(key)
                tag.setField(field)
            }
        }
        values["track"]?.let { track ->
            tag.deleteField(FieldKey.TRACK)
            tag.deleteField(FieldKey.TRACK_TOTAL)
            tag.setField(FieldKey.TRACK, track.substringBefore('/'))
            if ('/' in track) tag.setField(FieldKey.TRACK_TOTAL, track.substringAfter('/'))
        }
        if (cover != null) {
            val bytes = cover.file.readBytes()
            tag.deleteArtworkField()
            if (tag is FlacTag) {
                // Android 没有 AWT / ImageIO，不能让 jaudiotagger 自行读取图片尺寸。
                tag.setField(tag.createArtworkField(bytes, 3, "image/jpeg", "Cover", cover.width, cover.height, 24, 0))
            } else {
                tag.setField(AndroidArtwork().apply {
                    binaryData = bytes
                    mimeType = "image/jpeg"
                    pictureType = 3
                    description = "Cover"
                    width = cover.width
                    height = cover.height
                })
            }
        }
        audioFile.commit()
    }
}
