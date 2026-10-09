package com.happycola233.bilitools.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用例取自历次发布说明里真实用过的写法，覆盖 GFM 语法与内嵌 HTML 的混排。 */
class GithubMarkdownParserTest {
    private val releasePage = "https://github.com/happycola233/BiliTools/releases/tag/v3.1"

    private fun parse(markdown: String) = GithubMarkdownParser.parse(markdown, releasePage).blocks

    @Test
    fun singleNewlineInsideParagraphIsLineBreakLikeGitHubReleaseNotes() {
        val paragraph = parse("第一行\n第二行") .single() as MarkdownBlock.Paragraph

        assertEquals("第一行\n第二行", paragraph.text.text)
    }

    @Test
    fun inlineStylesKeepExactRanges() {
        val text = (parse("**重要修复：** 使用 `.m4a` 与 ~~旧格式~~").single() as MarkdownBlock.Paragraph).text

        assertEquals("重要修复： 使用 .m4a 与 旧格式", text.text)
        assertEquals(
            listOf(
                MarkdownSpan(0, 5, MarkdownSpanStyle.Bold),
                MarkdownSpan(9, 13, MarkdownSpanStyle.Code),
                MarkdownSpan(16, 19, MarkdownSpanStyle.Strikethrough),
            ),
            text.spans,
        )
    }

    @Test
    fun gitHubReferencesBecomeLinksOutsideCodeAndExistingLinks() {
        val text = (
            parse("修复 #2，感谢 @happycola233。邮箱 a@b.com，`#3` 与 [#4](https://example.com) 保持原样")
                .single() as MarkdownBlock.Paragraph
            ).text
        val links = text.spans.mapNotNull { span ->
            (span.style as? MarkdownSpanStyle.Link)?.let { text.text.substring(span.start, span.end) to it.url }
        }

        assertEquals(
            listOf(
                "#2" to "https://github.com/happycola233/BiliTools/issues/2",
                "@happycola233" to "https://github.com/happycola233",
                "a@b.com" to "mailto:a@b.com",
                "#4" to "https://example.com",
            ),
            links,
        )
    }

    @Test
    fun documentsOutsideGitHubKeepReferencesAsPlainText() {
        val text = (
            GithubMarkdownParser.parse("感谢 @Painter 的第 #2 期投稿，[00:12](https://www.bilibili.com/video/BV17x411w7KC?t=12)")
                .blocks.single() as MarkdownBlock.Paragraph
            ).text
        val links = text.spans.mapNotNull { (it.style as? MarkdownSpanStyle.Link)?.url }

        assertEquals(listOf("https://www.bilibili.com/video/BV17x411w7KC?t=12"), links)
    }

    @Test
    fun pictureWithMediaSourcesBecomesStandaloneImage() {
        val markdown = """
            段落

            <picture>
              <source media="(max-width: 600px) and (prefers-color-scheme: dark)" srcset="https://example.com/dark-mobile.svg">
              <source media="(prefers-color-scheme: dark)" srcset="https://example.com/dark.svg">
              <img src="https://example.com/light.svg" width="100%" alt="色卡">
            </picture>

            - 列表
        """.trimIndent()

        val blocks = parse(markdown)
        val image = (blocks[1] as MarkdownBlock.Images).images.single()

        assertEquals(3, blocks.size)
        assertEquals("https://example.com/light.svg", image.url)
        assertEquals("色卡", image.alt)
        assertEquals(MarkdownLength.Percent(100f), image.width)
        assertEquals(
            listOf(
                MarkdownImageSource("(max-width: 600px) and (prefers-color-scheme: dark)", "https://example.com/dark-mobile.svg"),
                MarkdownImageSource("(prefers-color-scheme: dark)", "https://example.com/dark.svg"),
            ),
            image.sources,
        )
    }

    @Test
    fun alignedHtmlContainerCentersItsImages() {
        val block = parse("""<div align="center"> <img src="https://example.com/a.png" width="60%" alt="图标"> </div>""")
            .single() as MarkdownBlock.Images

        assertEquals(MarkdownAlign.Center, block.align)
        assertEquals(MarkdownLength.Percent(60f), block.images.single().width)
    }

    @Test
    fun htmlBreakAndImageAfterListItemTextBecomeStandaloneImage() {
        val markdown = """
            * 🫧 **液态玻璃**

              * 浮窗升级。
              * ⚠️ **兼容性提示：需 Android 13+。**  
              <br>
              <img src="https://example.com/glass.png" width="50%" />
        """.trimIndent()

        val outer = (parse(markdown).single() as MarkdownBlock.ListBlock).items.single()
        // CommonMark 把紧随其后的 HTML 行视为上一项正文的延续，图片仍单独成行显示。
        val last = (outer.blocks[1] as MarkdownBlock.ListBlock).items.last().blocks

        assertEquals("⚠️ 兼容性提示：需 Android 13+。", (last[0] as MarkdownBlock.Paragraph).text.text)
        assertEquals(MarkdownLength.Percent(50f), (last[1] as MarkdownBlock.Images).images.single().width)
    }

    @Test
    fun imageBetweenWordsStaysInline() {
        val text = (
            parse("""点击 <img src="https://example.com/icon.png" width="16" height="16"> 图标""")
                .single() as MarkdownBlock.Paragraph
            ).text

        assertEquals("点击 ${MarkdownText.INLINE_IMAGE_PLACEHOLDER} 图标", text.text)
        assertEquals(3, text.images.single().position)
        assertEquals(MarkdownLength.Px(16f), text.images.single().image.width)
    }

    @Test
    fun orderedNestedAndTaskListsKeepStructure() {
        val markdown = """
            3. 第三项
               - [x] 已完成
               - [ ] 未完成
            4. 第四项
        """.trimIndent()

        val list = parse(markdown).single() as MarkdownBlock.ListBlock
        val tasks = list.items.first().blocks[1] as MarkdownBlock.ListBlock

        assertTrue(list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(listOf(true, false), tasks.items.map { it.taskChecked })
        assertEquals("已完成", (tasks.items.first().blocks.single() as MarkdownBlock.Paragraph).text.text)
        assertNull(list.items.last().taskChecked)
    }

    @Test
    fun alertsUseLocalizedTitleUnlessCustomized() {
        val alert = parse("> [!WARNING]\n> 升级前请备份").single() as MarkdownBlock.Alert

        assertEquals(MarkdownAlertKind.Warning, alert.kind)
        assertNull(alert.title)
        assertEquals("升级前请备份", (alert.blocks.single() as MarkdownBlock.Paragraph).text.text)
    }

    @Test
    fun tablesKeepHeaderAndColumnAlignment() {
        val table = parse("| 格式 | 大小 |\n|:-:|-:|\n| MP4 | 12 MB |").single() as MarkdownBlock.Table

        assertEquals(listOf(true, false), table.rows.map { it.isHeader })
        assertEquals(listOf(MarkdownAlign.Center, MarkdownAlign.End), table.rows[1].cells.map { it.align })
        assertEquals("12 MB", table.rows[1].cells[1].text.text)
    }

    @Test
    fun detailsParseMarkdownInsideAndRememberOpenState() {
        val markdown = "<details open><summary>更多 **细节**</summary>\n\n- 隐藏内容\n\n</details>"

        val details = parse(markdown).single() as MarkdownBlock.Details

        assertEquals("更多 **细节**", details.summary?.text)
        assertTrue(details.initiallyExpanded)
        assertTrue(details.blocks.single() is MarkdownBlock.ListBlock)
    }

    @Test
    fun footnotesRenderAsSuperscriptWithoutBackReferences() {
        val blocks = parse("正文[^1]\n\n[^1]: 脚注内容")

        val body = (blocks.first() as MarkdownBlock.Paragraph).text
        assertEquals("正文1", body.text)
        assertTrue(body.spans.any { it.style == MarkdownSpanStyle.Superscript })
        assertTrue(body.spans.none { it.style is MarkdownSpanStyle.Link })
        assertEquals(MarkdownBlock.Divider, blocks[1])
        val note = (blocks[2] as MarkdownBlock.ListBlock).items.single().blocks.single() as MarkdownBlock.Paragraph
        assertEquals("脚注内容", note.text.text)
    }

    @Test
    fun unsupportedMarkupNeverLeaksAsSourceText() {
        val markdown = """
            <!-- 注释 -->
            <script>alert(1)</script>
            <p align="right"><span style="color:red">右对齐</span><kbd>Ctrl</kbd></p>
            <video src="https://example.com/a.mp4"></video>
        """.trimIndent()

        val paragraph = parse(markdown).single() as MarkdownBlock.Paragraph

        assertEquals("右对齐Ctrl", paragraph.text.text)
        assertEquals(MarkdownAlign.End, paragraph.align)
        assertFalse(paragraph.text.text.contains('<'))
    }

    @Test
    fun codeBlocksKeepWhitespaceAndLanguage() {
        val code = parse("```kotlin\nval a = 1\n    val b = \"<br>\"\n```").single() as MarkdownBlock.CodeBlock

        assertEquals("kotlin", code.language)
        assertEquals("val a = 1\n    val b = \"<br>\"", code.code)
    }

    @Test
    fun mediaQueriesFollowThemeAndViewportWidth() {
        val mobileDark = "(max-width: 600px) and (prefers-color-scheme: dark)"

        assertTrue(matchesMediaQuery(mobileDark, isDark = true, viewportWidthDp = 412f))
        assertFalse(matchesMediaQuery(mobileDark, isDark = false, viewportWidthDp = 412f))
        assertFalse(matchesMediaQuery(mobileDark, isDark = true, viewportWidthDp = 800f))
        assertTrue(matchesMediaQuery("print, (min-width: 40em)", isDark = false, viewportWidthDp = 700f))
        assertFalse(matchesMediaQuery("(orientation: landscape)", isDark = false, viewportWidthDp = 700f))
    }
}
