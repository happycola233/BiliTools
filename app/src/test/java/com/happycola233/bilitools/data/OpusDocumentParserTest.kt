package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.OpusBlock
import com.happycola233.bilitools.data.model.OpusNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusDocumentParserTest {
    @Test
    fun completeFixture_parsesAllParagraphsRichNodesAndAuthorAssets() {
        val response = checkNotNull(javaClass.getResource("/opus/detail_complete.json"))
            .readText(Charsets.UTF_8)

        val document = parseOpusDocumentResponse(
            json = response,
            requestedId = "987654321",
            cvid = 4815593,
        )

        assertEquals("987654321", document.id)
        assertEquals(4815593L, document.cvid)
        assertEquals("详情标题", document.title)
        assertEquals("夹具作者", document.author?.name)
        assertEquals(123L, document.author?.mid)
        assertEquals("https://i0.hdslb.com/bfs/face/avatar.jpg", document.author?.avatar)
        assertEquals(1_700_000_000L, document.publishedAt)
        assertEquals(2L, document.stat.coin)
        assertEquals(3L, document.stat.reply)
        assertEquals(4L, document.stat.favorite)
        assertEquals(5L, document.stat.share)
        assertEquals(6L, document.stat.like)
        assertEquals(listOf("标签一", "话题标签"), document.tags)
        assertEquals(
            listOf(
                "https://i0.hdslb.com/bfs/new_dyn/top.jpg",
                "https://i0.hdslb.com/bfs/new_dyn/view.png",
                "https://i0.hdslb.com/bfs/new_dyn/body.webp",
            ),
            document.images.map { it.url },
        )

        assertTrue(document.blocks[0] is OpusBlock.Paragraph)
        assertEquals(2, (document.blocks[0] as OpusBlock.Paragraph).headingLevel)
        assertTrue(document.blocks[1] is OpusBlock.Pictures)
        assertTrue(document.blocks[2] is OpusBlock.Divider)
        assertTrue(document.blocks[3] is OpusBlock.Quote)
        assertTrue(document.blocks[4] is OpusBlock.ListBlock)
        assertTrue((document.blocks[4] as OpusBlock.ListBlock).ordered)
        assertEquals(
            OpusBlock.LinkCard("卡片商品", "https://example.com/card"),
            document.blocks[5],
        )
        assertEquals(
            OpusBlock.Code("language-kotlin", "println(&quot;ok&quot;)"),
            document.blocks[6],
        )
        assertTrue(document.blocks[7] is OpusBlock.Paragraph)

        val nodes = (document.blocks[0] as OpusBlock.Paragraph).nodes
        assertEquals(7, nodes.filterIsInstance<OpusNode.Link>().size)
        assertTrue(nodes.filterIsInstance<OpusNode.Text>().any { it.text == "投票" })
        assertTrue(nodes.filterIsInstance<OpusNode.Text>().any { it.text == "[笑]" })
        assertTrue(nodes.filterIsInstance<OpusNode.Text>().any { it.text == "未知节点文字" })
        assertEquals("x^2", nodes.filterIsInstance<OpusNode.Formula>().single().latex)
        assertEquals("查看图片", nodes.filterIsInstance<OpusNode.Picture>().single().text)
    }

    @Test
    fun missingTitle_usesFirstNonBlankBodyTextThenStableFallback() {
        val bodyTitleResponse = """
            {"code":0,"data":{"item":{"id_str":"11","basic":{"title":"","uid":1},"modules":[
              {"module_content":{"paragraphs":[{"para_type":1,"text":{"nodes":[{"word":{"words":"  正文标题候选  "}}]}}]}}
            ]}}}
        """.trimIndent()
        val emptyResponse = """
            {"code":0,"data":{"item":{"id_str":"12","basic":{"title":"","uid":1},"modules":[]}}}
        """.trimIndent()

        assertEquals("正文标题候选", parseOpusDocumentResponse(bodyTitleResponse, "11").title)
        assertEquals("图文_12", parseOpusDocumentResponse(emptyResponse, "12").title)
    }

    @Test
    fun pictureOnlyBody_doesNotUseViewPictureLabelAsTitle() {
        val response = """
            {"code":0,"data":{"item":{"id_str":"13","basic":{"title":"","uid":1},"modules":[
              {"module_content":{"paragraphs":[{"para_type":1,"text":{"nodes":[
                {"rich":{"type":"RICH_TEXT_NODE_TYPE_VIEW_PICTURE","text":"查看图片","pics":[{"url":"//i0.hdslb.com/image.jpg"}]}}
              ]}}]}}
            ]}}}
        """.trimIndent()

        val document = parseOpusDocumentResponse(response, "13")

        assertEquals("图文_13", document.title)
        assertEquals("", document.summary)
        assertEquals(listOf("https://i0.hdslb.com/image.jpg"), document.images.map { it.url })
    }

    @Test
    fun successfulResponseWithoutItem_isReportedAsMissingContent() {
        val error = assertThrows(OpusException::class.java) {
            parseOpusDocumentResponse("""{"code":0,"data":{"item":null}}""", "14")
        }

        assertEquals(OpusFailure.NotFound, error.failure)
    }
}
