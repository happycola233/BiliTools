package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream

internal fun selectRetryVideoStream(
    streams: List<VideoStream>,
    resolutionId: Int,
    codec: VideoCodec?,
): VideoStream? {
    return streams.filter { stream ->
        if (stream.id != resolutionId) return@filter false
        if (stream.format == StreamFormat.Dash) {
            // DASH 必须明确匹配编码，不能把缺失或尚未支持的 codecid 当成 AVC。
            codec != null && stream.codec == codec
        } else {
            // 旧 MP4 / FLV 响应没有 codecid，按这两种格式的 AVC 编码处理。
            (stream.codec ?: VideoCodec.Avc) == (codec ?: VideoCodec.Avc)
        }
    }.maxByOrNull { it.bandwidth ?: 0L }
}

internal fun selectRetryAudioStream(streams: List<AudioStream>, qualityId: Int): AudioStream? =
    streams.filter { it.id == qualityId }.maxByOrNull { it.bandwidth ?: 0L }
