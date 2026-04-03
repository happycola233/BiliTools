package com.happycola233.bilitools.core

import com.happycola233.bilitools.R

object AudioQualities {
    const val BITRATE_64 = 30216
    const val BITRATE_128 = 30228
    const val BITRATE_132 = 30232
    const val BITRATE_192 = 30280
    const val DOLBY_ATMOS = 30250
    const val BITRATE_320 = 30380
    const val HI_RES_LOSSLESS = 30251
    const val LOSSLESS_FLAC = 30252

    private val order = listOf(
        BITRATE_64,
        BITRATE_128,
        BITRATE_132,
        BITRATE_192,
        DOLBY_ATMOS,
        BITRATE_320,
        HI_RES_LOSSLESS,
        LOSSLESS_FLAC,
    )

    private val rankById = order.withIndex().associate { it.value to it.index }

    val allIds: List<Int> = order

    fun labelRes(id: Int): Int {
        return when (id) {
            BITRATE_64 -> R.string.parse_bitrate_64
            BITRATE_128 -> R.string.parse_bitrate_128
            BITRATE_132 -> R.string.parse_bitrate_132
            BITRATE_192 -> R.string.parse_bitrate_192
            DOLBY_ATMOS -> R.string.parse_bitrate_dolby
            BITRATE_320 -> R.string.parse_bitrate_320
            HI_RES_LOSSLESS -> R.string.parse_bitrate_hires
            LOSSLESS_FLAC -> R.string.parse_bitrate_flac
            else -> R.string.parse_bitrate_other
        }
    }

    fun sortDescending(ids: Iterable<Int>): List<Int> {
        return ids.distinct().sortedByDescending { rank(it) }
    }

    fun highest(ids: Iterable<Int>): Int? {
        return ids.distinct().maxByOrNull { rank(it) }
    }

    fun lowest(ids: Iterable<Int>): Int? {
        return ids.distinct().minByOrNull { rank(it) }
    }

    fun audioFileExtension(id: Int): String {
        return when (id) {
            DOLBY_ATMOS -> "eac3"
            HI_RES_LOSSLESS,
            LOSSLESS_FLAC,
            -> "flac"
            else -> "m4a"
        }
    }

    fun mergedContainerExtension(id: Int): String {
        return when (id) {
            HI_RES_LOSSLESS,
            LOSSLESS_FLAC,
            -> "mkv"
            else -> "mp4"
        }
    }

    fun musicApiTypeToStreamId(type: Int): Int? {
        return when (type) {
            -1 -> BITRATE_192
            0 -> BITRATE_128
            1 -> BITRATE_192
            2 -> BITRATE_320
            3 -> LOSSLESS_FLAC
            else -> null
        }
    }

    fun streamIdToMusicApiType(id: Int): Int? {
        return when (id) {
            BITRATE_128 -> 0
            BITRATE_192 -> 1
            BITRATE_320 -> 2
            LOSSLESS_FLAC -> 3
            else -> null
        }
    }

    private fun rank(id: Int): Int {
        return rankById[id] ?: Int.MIN_VALUE
    }
}
