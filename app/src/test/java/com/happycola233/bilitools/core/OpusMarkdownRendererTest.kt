package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaUpper
import com.happycola233.bilitools.data.model.OpusBlock
import com.happycola233.bilitools.data.model.OpusDocument
import com.happycola233.bilitools.data.model.OpusImage
import com.happycola233.bilitools.data.model.OpusListItem
import com.happycola233.bilitools.data.model.OpusNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class OpusMarkdownRendererTest {
    @Test
    fun normalizeImageUrl_keepsOriginalBiliAssetAndForcesHttps() {
        assertEquals(
            "https://i0.hdslb.com/bfs/new_dyn/example.webp",
            OpusAssetPlanner.normalizeImageUrl(
                "http://i0.hdslb.com/bfs/new_dyn/example.webp@672w_378h_1c_!web-home-common-cover?foo=bar#part",
            ),
        )
        assertEquals(
            "https://example.com/image.png?token=1",
            OpusAssetPlanner.normalizeImageUrl("https://example.com/image.png?token=1#part"),
        )
        assertEquals(
            "https://album.biliimg.com/bfs/new_dyn/example.png",
            OpusAssetPlanner.normalizeImageUrl(
                "http://album.biliimg.com/bfs/new_dyn/example.png@1048w_!web-dynamic.webp",
            ),
        )
    }

    @Test
    fun plan_usesStableOrderNumberingAndWhitelistedExtensions() {
        val document = document(
            images = listOf(
                OpusImage("https://i0.hdslb.com/a.webp"),
                OpusImage("https://i0.hdslb.com/b.avif"),
                OpusImage("https://i0.hdslb.com/c.unsupported"),
            ),
        )

        val assets = OpusAssetPlanner.plan(document, "示例 - 图文图片.jpg")

        assertEquals(
            listOf(
                "示例 - 图文图片 - 01.webp",
                "示例 - 图文图片 - 02.avif",
                "示例 - 图文图片 - 03.jpg",
            ),
            assets.map { it.fileName },
        )
        assertEquals(listOf("image/webp", "image/avif", "image/jpeg"), assets.map { it.mimeType })
    }

    @Test
    fun render_withImageTasksUsesEncodedRelativeLinksAndCompleteBlocks() {
        val topImage = OpusImage("https://i0.hdslb.com/top.jpg", "顶部 图片")
        val bodyImage = OpusImage("https://i0.hdslb.com/body.png", "正文图片")
        val document = document(
            topImages = listOf(topImage),
            images = listOf(topImage, bodyImage),
            blocks = listOf(
                OpusBlock.Paragraph(
                    nodes = listOf(
                        OpusNode.Text("粗体", bold = true),
                        OpusNode.Text("与"),
                        OpusNode.Link("网页", "https://example.com/a?b=1"),
                        OpusNode.Text("，公式 "),
                        OpusNode.Formula("x^2+y^2"),
                    ),
                    headingLevel = 2,
                ),
                OpusBlock.Pictures(listOf(bodyImage)),
                OpusBlock.Quote(listOf(OpusNode.Text("第一行\n第二行"))),
                OpusBlock.ListBlock(
                    ordered = true,
                    items = listOf(
                        OpusListItem(1, 1, listOf(OpusNode.Text("项目一"))),
                        OpusListItem(2, 2, listOf(OpusNode.Text("项目二"))),
                    ),
                ),
                OpusBlock.LinkCard("关联图文", "https://www.bilibili.com/opus/2"),
                OpusBlock.Code("language-kotlin", "println(&quot;ok&quot;)"),
                OpusBlock.Divider,
            ),
        )
        val assets = OpusAssetPlanner.plan(document, "示例 图文图片.jpg")

        val markdown = OpusMarkdownRenderer.render(document, assets)

        assertTrue(markdown.contains("# 示例\\[标题\\]"))
        assertTrue(markdown.contains("- 作者：测试 UP"))
        assertTrue(markdown.contains("- 数据：阅读 10 · 点赞 2 · 评论 1"))
        assertTrue(markdown.contains("## **粗体**与[网页](https://example.com/a?b=1)，公式 \$x^2+y^2\$"))
        assertTrue(markdown.contains("> 第一行\n> 第二行"))
        assertTrue(markdown.contains("1. 项目一\n    2. 项目二"))
        assertTrue(markdown.contains("[关联图文](https://www.bilibili.com/opus/2)"))
        assertTrue(markdown.contains("```kotlin\nprintln(\"ok\")\n```"))
        assets.forEach { asset ->
            val encodedName = URI(null, null, asset.fileName, null).rawPath
            assertTrue(markdown.contains(encodedName))
        }
        assertFalse(markdown.contains("](https://i0.hdslb.com/top.jpg)"))
        assertEquals(1, Regex("顶部 图片").findAll(markdown).count())
    }

    @Test
    fun render_withoutImageTasksKeepsOriginalHttpsLinks() {
        val image = OpusImage("https://i0.hdslb.com/original.jpg", "原图")
        val document = document(
            topImages = listOf(image),
            images = listOf(image),
        )

        val markdown = OpusMarkdownRenderer.render(document)

        assertTrue(markdown.contains("![原图](https://i0.hdslb.com/original.jpg)"))
    }

    private fun document(
        topImages: List<OpusImage> = emptyList(),
        images: List<OpusImage> = emptyList(),
        blocks: List<OpusBlock> = emptyList(),
    ) = OpusDocument(
        id = "1",
        title = "示例[标题]",
        summary = "摘要",
        sourceUrl = "https://www.bilibili.com/opus/1",
        author = MediaUpper("测试 UP", 123L, null),
        publishedAt = 1_700_000_000L,
        stat = MediaStat(play = 10, like = 2, reply = 1),
        topImages = topImages,
        images = images,
        blocks = blocks,
    )
}
