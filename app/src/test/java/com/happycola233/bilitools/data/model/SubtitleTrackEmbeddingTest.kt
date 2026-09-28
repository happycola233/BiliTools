package com.happycola233.bilitools.data.model

import com.happycola233.bilitools.data.selectEmbeddedSubtitleTracks
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTrackEmbeddingTest {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val subtitles = listOf(
        SubtitleInfo("zh-Hans", "中文", "https://example.com/zh"),
        SubtitleInfo("en", "English", "https://example.com/en"),
    )

    @Test fun embeddingRoundTripPreservesAllSpecificAndEmptySelections() {
        val adapter = moshi.adapter(DownloadEmbedding::class.java)
        listOf(
            SubtitleTrackEmbedding() to subtitles,
            SubtitleTrackEmbedding(listOf("en")) to subtitles.takeLast(1),
            SubtitleTrackEmbedding(allLanguages = false) to emptyList(),
        ).forEach { (request, expected) ->
            val embedding = DownloadEmbedding(subtitles = request)
            val restored = requireNotNull(adapter.fromJson(adapter.toJson(embedding)))
            assertEquals(embedding, restored)
            assertEquals(expected, selectEmbeddedSubtitleTracks(subtitles, requireNotNull(restored.subtitles)))
        }
    }

    @Test fun legacyRequestsWithoutAllLanguagesKeepTheirOriginalMeaning() {
        val adapter = moshi.adapter(SubtitleTrackEmbedding::class.java)
        listOf(
            "{}" to subtitles,
            """{"languages":[]}""" to subtitles,
            """{"languages":["en"]}""" to subtitles.takeLast(1),
        ).forEach { (json, expected) ->
            val restored = requireNotNull(adapter.fromJson(json))
            assertEquals(expected, selectEmbeddedSubtitleTracks(subtitles, restored))
        }
    }
}
