package com.happycola233.bilitools.data

enum class LiveUpdateIcon(val value: String) {
    BiliTools("bilitools"),
    Download("download"),
    ;

    companion object {
        fun fromValue(value: String?): LiveUpdateIcon =
            entries.firstOrNull { it.value == value } ?: BiliTools
    }
}
