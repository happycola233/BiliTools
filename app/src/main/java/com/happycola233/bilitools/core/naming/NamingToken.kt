package com.happycola233.bilitools.core.naming

private val ALL_SCOPES = NamingTemplateScope.entries.toSet()

enum class NamingTokenGroup {
    General,
    Time,
    Ids,
    Stream,
}

/**
 * 命名变量。每个变量只对应 B 站资源层级上的一个概念，取不到就留空，
 * 不会用别的层级的值顶替——顶替会让预览和实际结果对不上。
 *
 * [shapes] 为 null 表示所有形态都能用；否则只在列出的形态里出现在设置界面上。
 * 这只影响「插入变量」的按钮，渲染时任何变量都合法，取不到值即为空。
 *
 * 各形态的变量表互相独立，不存在一个「什么都能用」的形态。
 */
enum class NamingToken(
    val key: String,
    val scopes: Set<NamingTemplateScope>,
    val group: NamingTokenGroup,
    val shapes: Set<NamingShape>? = null,
    val supportsPattern: Boolean = false,
) {
    Title(
        key = "title",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    Work(
        key = "work",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.General,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track, NamingShape.Opus),
    ),
    Collection(
        key = "collection",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.General,
    ),
    P(
        key = "p",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
        shapes = setOf(NamingShape.Video),
    ),
    Ep(
        key = "ep",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
        shapes = setOf(NamingShape.Episode),
    ),
    Section(
        key = "section",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.General,
        shapes = setOf(NamingShape.Video, NamingShape.Episode),
    ),
    Img(
        key = "img",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.General,
        shapes = setOf(NamingShape.Opus),
    ),
    Container(
        key = "container",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.General,
    ),
    MediaType(
        key = "mediaType",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    TaskType(
        key = "taskType",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    Index(
        key = "index",
        scopes = setOf(NamingTemplateScope.ItemFolder, NamingTemplateScope.File),
        group = NamingTokenGroup.General,
    ),
    PubTime(
        key = "pubtime",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Time,
        supportsPattern = true,
    ),
    DownTime(
        key = "downtime",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Time,
        supportsPattern = true,
    ),
    Id(
        key = "id",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track, NamingShape.Opus),
    ),
    Upper(
        key = "upper",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
    ),
    UpperId(
        key = "upperid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
    ),
    Artist(
        key = "artist",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Track),
    ),
    Aid(
        key = "aid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track),
    ),
    Bvid(
        key = "bvid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track),
    ),
    Cid(
        key = "cid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track),
    ),
    Epid(
        key = "epid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Episode),
    ),
    Ssid(
        key = "ssid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Episode),
    ),
    Sid(
        key = "sid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Track),
    ),
    Amid(
        key = "amid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Track),
    ),
    Fid(
        key = "fid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Video, NamingShape.Listing),
    ),
    Opid(
        key = "opid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Opus),
    ),
    Cvid(
        key = "cvid",
        scopes = ALL_SCOPES,
        group = NamingTokenGroup.Ids,
        shapes = setOf(NamingShape.Opus),
    ),
    Res(
        key = "res",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
        shapes = setOf(NamingShape.Video, NamingShape.Episode),
    ),
    Abr(
        key = "abr",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track),
    ),
    Enc(
        key = "enc",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
        shapes = setOf(NamingShape.Video, NamingShape.Episode),
    ),
    Fmt(
        key = "fmt",
        scopes = setOf(NamingTemplateScope.File),
        group = NamingTokenGroup.Stream,
        shapes = setOf(NamingShape.Video, NamingShape.Episode, NamingShape.Track),
    ),
    ;

    fun isVisibleIn(shape: NamingShape): Boolean = shapes == null || shape in shapes

    companion object {
        fun fromKey(key: String): NamingToken? = entries.firstOrNull { it.key == key }

        fun forEditor(shape: NamingShape, scope: NamingTemplateScope): List<NamingToken> {
            return entries.filter { scope in it.scopes && it.isVisibleIn(shape) }
        }
    }
}
