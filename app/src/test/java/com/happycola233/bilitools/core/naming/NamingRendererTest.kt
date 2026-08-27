package com.happycola233.bilitools.core.naming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamingRendererTest {

    private val videoContext = NamingContext(
        title = "分P标题",
        work = "稿件标题",
        collection = "合集标题",
        p = "2",
        container = "视频",
        mediaType = "视频",
        taskType = "音视频",
        res = "1080P",
    )

    @Test
    fun `renders plain tokens`() {
        assertEquals(
            "音视频 - 分P标题 - 1080P",
            NamingRenderer.render("{taskType} - {title} - {res}", videoContext),
        )
    }

    @Test
    fun `keeps optional segment when every token resolves`() {
        assertEquals(
            "音视频 - (P2) 分P标题 - 1080P",
            NamingRenderer.render(FILE_TEMPLATE, videoContext),
        )
    }

    @Test
    fun `drops whole optional segment when a token is missing`() {
        val bangumiLike = videoContext.copy(p = null)
        assertEquals(
            "音视频 - 分P标题 - 1080P",
            NamingRenderer.render(FILE_TEMPLATE, bangumiLike),
        )
    }

    @Test
    fun `drops trailing optional segment for attachment tasks`() {
        val subtitle = videoContext.copy(p = null, res = null, taskType = "字幕")
        assertEquals(
            "字幕 - 分P标题",
            NamingRenderer.renderComponent(FILE_TEMPLATE, subtitle),
        )
    }

    @Test
    fun `unknown token stays literal and is flagged in preview`() {
        assertEquals("a{nope}b", NamingRenderer.render("a{nope}b", videoContext))
        val kinds = NamingRenderer.previewSegments("a{nope}b").map { it.kind }
        assertEquals(
            listOf(
                NamingSegmentKind.Literal,
                NamingSegmentKind.Unknown,
                NamingSegmentKind.Literal,
            ),
            kinds,
        )
    }

    @Test
    fun `unknown token inside optional segment removes the segment`() {
        assertEquals("头", NamingRenderer.render("头{? - {nope}}", videoContext))
    }

    @Test
    fun `preview marks optional boundaries and inner tokens`() {
        val segments = NamingRenderer.previewSegments("{taskType}{? - {res}}")
        assertEquals(NamingSegmentKind.Token, segments.first().kind)
        assertEquals(NamingSegmentKind.OptionalStart, segments[1].kind)
        assertEquals(NamingSegmentKind.OptionalEnd, segments.last().kind)
        assertTrue(segments.drop(1).all { it.optional })
    }

    @Test
    fun `unbalanced brace is treated as literal`() {
        assertEquals("{title", NamingRenderer.render("{title", videoContext))
    }

    @Test
    fun `cleans separators left behind by empty tokens`() {
        val context = NamingContext(taskType = "音视频", title = "标题")
        assertEquals(
            "音视频 - 标题",
            NamingRenderer.renderComponent("{taskType} - {work} - {title} - {res}", context),
        )
    }

    @Test
    fun `keeps separators when cleaning is disabled`() {
        val context = NamingContext(taskType = "音视频", title = "标题")
        assertEquals(
            "音视频 -  - 标题 -",
            NamingRenderer.renderComponent(
                template = "{taskType} - {work} - {title} - {res}",
                context = context,
                cleanSeparators = false,
            ),
        )
    }

    @Test
    fun `sanitizes path separators and appends extension`() {
        assertEquals(
            "a_b_c.mp4",
            NamingRenderer.appendExtension(NamingRenderer.sanitizeComponent("a/b:c"), "mp4"),
        )
    }

    @Test
    fun `formats time tokens with dayjs style patterns`() {
        val context = NamingContext(downTimeEpochSeconds = 1_719_331_200L)
        assertEquals(
            "1719331200",
            NamingRenderer.render("{downtime:ts}", context),
        )
        assertTrue(
            NamingRenderer.render("{downtime:YYYY}", context).matches(Regex("""\d{4}""")),
        )
    }

    private companion object {
        const val FILE_TEMPLATE = "{taskType} - {?(P{p}) }{title}{? - {res}}"
    }
}
