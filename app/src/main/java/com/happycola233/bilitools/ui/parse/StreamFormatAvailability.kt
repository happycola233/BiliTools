package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.capabilities

/** 只记成功取流提供的证据；请求失败不代表格式不受支持。 */
data class StreamFormatEvidence(
    val available: Set<StreamFormat> = emptySet(),
    val unavailable: Set<StreamFormat> = emptySet(),
) {
    fun withResponse(requested: StreamFormat, actual: StreamFormat): StreamFormatEvidence = copy(
        available = (if (requested == actual) available else available - requested) + actual,
        unavailable = (if (requested == actual) unavailable else unavailable + requested) - actual,
    )
}

internal fun MediaItem.streamFormatKey(): String = "$type:${aid ?: bvid ?: epid ?: sid}:${cid ?: epid ?: sid}"

internal fun MediaItem.unavailableStreamFormats(evidence: StreamFormatEvidence?): Set<StreamFormat> {
    // 普通稿件的 web playurl 已下线 fnval=0；番剧、课程使用不同接口，不能一并推断。
    // accept_format 中 flv720 等也可能是 DASH 的历史画质名，不能据此宣称容器可用。
    val retired = if (type == MediaType.Video) setOf(StreamFormat.Flv) else emptySet()
    return (retired + evidence?.unavailable.orEmpty()) - evidence?.available.orEmpty()
}

/** 批量只禁用已有明确限制的格式；其余条目保持未知，不为枚举格式发起额外请求。 */
internal val ParseUiState.unavailableStreamFormats: Set<StreamFormat>
    get() = selectedItemIndices.mapNotNull { items.getOrNull(it) }
        .filter { it.type.capabilities.supportsPlaybackStream }
        .flatMap { item -> item.unavailableStreamFormats(streamFormatEvidence[item.streamFormatKey()]) }
        .toSet()

internal val ParseUiState.availableStreamFormat: StreamFormat
    get() {
        val unavailable = unavailableStreamFormats
        if (format !in unavailable) return format
        return listOf(StreamFormat.Dash, StreamFormat.Mp4, StreamFormat.Flv)
            .firstOrNull { it !in unavailable } ?: format
    }

internal val ParseUiState.hasCommonStreamFormat: Boolean
    get() = !unavailableStreamFormats.containsAll(StreamFormat.entries)
