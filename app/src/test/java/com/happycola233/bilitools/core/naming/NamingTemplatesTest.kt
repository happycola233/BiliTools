package com.happycola233.bilitools.core.naming

import com.happycola233.bilitools.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamingTemplatesTest {

    @Test
    fun `每种形态的默认文件名模板都能扛住空变量`() {
        val emptyContext = NamingContext(taskType = "音视频", title = "标题")
        NamingShape.entries
            .filter { NamingTemplateScope.File in it.supportedScopes }
            .forEach { shape ->
                val rendered = NamingRenderer.renderComponent(
                    template = NamingTemplates.default(shape, NamingTemplateScope.File),
                    context = emptyContext,
                )
                assertEquals("音视频 - 标题", rendered)
            }
    }

    @Test
    fun `顶层文件夹模板在没有合集时不留下空连接符`() {
        val template = NamingTemplates.default(NamingShape.Listing, NamingTemplateScope.TopFolder)
        val context = NamingContext(container = "稍后再看", downTimeEpochSeconds = 1_719_331_200L)
        val rendered = NamingRenderer.renderComponent(template, context)
        assertTrue(rendered, rendered.matches(Regex("""稍后再看 \(\d{4}-\d{2}-\d{2}\)""")))

        val withCollection = NamingRenderer.renderComponent(
            template = template,
            context = context.copy(container = "收藏夹", collection = "学习资料"),
        )
        assertTrue(
            withCollection,
            withCollection.matches(Regex("""收藏夹 - 学习资料 \(\d{4}-\d{2}-\d{2}\)""")),
        )
    }

    @Test
    fun `未覆盖时走内置默认`() {
        assertEquals(
            NamingTemplates.default(NamingShape.Episode, NamingTemplateScope.ItemFolder),
            NamingTemplates.resolve(emptyMap(), NamingShape.Episode, NamingTemplateScope.ItemFolder),
        )
        assertEquals(
            NamingTemplateSource.Default,
            NamingTemplates.sourceOf(emptyMap(), NamingShape.Episode, NamingTemplateScope.File),
        )
    }

    @Test
    fun `自定义只影响被改的那一格`() {
        val overrides = mapOf(
            NamingShape.Episode to NamingTemplateSet(file = "EP{ep}"),
        )
        assertEquals(
            "EP{ep}",
            NamingTemplates.resolve(overrides, NamingShape.Episode, NamingTemplateScope.File),
        )
        assertEquals(
            NamingTemplateSource.Custom,
            NamingTemplates.sourceOf(overrides, NamingShape.Episode, NamingTemplateScope.File),
        )
        // 同形态的其它层级不受影响
        assertEquals(
            NamingTemplates.default(NamingShape.Episode, NamingTemplateScope.ItemFolder),
            NamingTemplates.resolve(overrides, NamingShape.Episode, NamingTemplateScope.ItemFolder),
        )
        // 别的形态更不受影响
        assertEquals(
            NamingTemplates.default(NamingShape.Video, NamingTemplateScope.File),
            NamingTemplates.resolve(overrides, NamingShape.Video, NamingTemplateScope.File),
        )
        assertEquals(
            NamingTemplateSource.Default,
            NamingTemplates.sourceOf(overrides, NamingShape.Video, NamingTemplateScope.File),
        )
    }

    @Test
    fun `列表入口只决定顶层文件夹`() {
        assertEquals(
            setOf(NamingTemplateScope.TopFolder),
            NamingShape.Listing.supportedScopes,
        )
        assertEquals(NamingShape.Listing, NamingShape.ofEntry(MediaType.Favorite))
        // 收藏夹里的条目本身仍是普通稿件，套的是稿件视频那套命名
        assertEquals(NamingShape.Video, NamingShape.ofItem(MediaType.Video))
    }

    @Test
    fun `入口与条目形态的映射覆盖全部媒体类型`() {
        MediaType.entries.forEach { type ->
            NamingShape.ofEntry(type)
            NamingShape.ofItem(type)
        }
    }
}
