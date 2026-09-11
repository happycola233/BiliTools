package com.happycola233.bilitools.data

data class DownloadMetadataSettings(
    val embedCover: Boolean = true,
    val embedLyrics: Boolean = true,
    val subtitleLyrics: SubtitleLyricsMode = SubtitleLyricsMode.Off,
    val useUploaderAsArtist: Boolean = true,
    val useCollectionAsAlbum: Boolean = true,
)

enum class SubtitleLyricsMode(val value: String) {
    Off("off"),
    ManualOnly("manual"),
    PreferManual("prefer_manual"),
    ;

    companion object {
        fun fromValue(value: String?): SubtitleLyricsMode =
            entries.firstOrNull { it.value == value } ?: Off
    }
}
