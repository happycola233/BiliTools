package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRetryStreamSelectionTest {
    private val avc1080 = video(80, VideoCodec.Avc)
    private val hevc1080 = video(80, VideoCodec.Hevc)
    private val avc4k = video(120, VideoCodec.Avc)

    @Test fun refreshKeepsTheOriginalQualityAndCodec() {
        assertEquals(avc1080, selectRetryVideoStream(listOf(avc4k, hevc1080, avc1080), 80, VideoCodec.Avc))
        assertNull(selectRetryVideoStream(listOf(avc4k, hevc1080), 80, VideoCodec.Avc))
    }

    @Test fun missingAudioQualityDoesNotSubstituteADifferentTrack() {
        val requested = AudioStream(30280, url = "https://cdn.example.com/audio")
        val other = AudioStream(30216, url = "https://cdn.example.com/other")
        assertEquals(requested, selectRetryAudioStream(listOf(other, requested), 30280))
        assertNull(selectRetryAudioStream(listOf(other), 30280))
    }

    @Test fun legacyAvcStreamsWithoutCodecIdRemainSelectable() {
        val legacy = VideoStream(80, StreamFormat.Mp4, url = "https://cdn.example.com/video.mp4")
        assertEquals(legacy, selectRetryVideoStream(listOf(legacy), 80, VideoCodec.Avc))
    }

    @Test fun unknownDashCodecCannotBeAssumedToBeAvc() {
        val unknown = VideoStream(80, StreamFormat.Dash, url = "https://cdn.example.com/unknown")
        assertNull(selectRetryVideoStream(listOf(unknown), 80, VideoCodec.Avc))
        assertNull(selectRetryVideoStream(listOf(avc1080), 80, null))
    }

    private fun video(id: Int, codec: VideoCodec) = VideoStream(
        id, StreamFormat.Dash, codec = codec, url = "https://cdn.example.com/$id/$codec",
    )
}
