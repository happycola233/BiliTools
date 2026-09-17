package com.happycola233.bilitools.data

/** 「添加元数据」的细项。字幕与歌词不是元数据，改为在解析页逐次选择，见 [com.happycola233.bilitools.data.model.DownloadEmbedding]。 */
data class DownloadMetadataSettings(
    val embedCover: Boolean = true,
    val useUploaderAsArtist: Boolean = true,
    val useCollectionAsAlbum: Boolean = true,
)
