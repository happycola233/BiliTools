package com.happycola233.bilitools.core.naming

data class NamingTemplateSet(
    val topFolder: String? = null,
    val itemFolder: String? = null,
    val file: String? = null,
) {
    val isEmpty: Boolean
        get() = topFolder == null && itemFolder == null && file == null

    operator fun get(scope: NamingTemplateScope): String? = when (scope) {
        NamingTemplateScope.TopFolder -> topFolder
        NamingTemplateScope.ItemFolder -> itemFolder
        NamingTemplateScope.File -> file
    }

    fun with(scope: NamingTemplateScope, template: String?): NamingTemplateSet = when (scope) {
        NamingTemplateScope.TopFolder -> copy(topFolder = template)
        NamingTemplateScope.ItemFolder -> copy(itemFolder = template)
        NamingTemplateScope.File -> copy(file = template)
    }
}

/** 一条模板当前的来源，设置页据此显示「默认 / 跟随通用 / 已自定义」。 */
enum class NamingTemplateSource {
    Default,
    Common,
    Custom,
}

/**
 * 各形态的内置默认模板。默认值必须能扛住变量为空的情况：
 * 凡是「某些形态才有」的变量，一律包进可选片段，避免留下 `(P)`、`EP` 这类残渣。
 */
object NamingTemplates {
    private const val TOP_FOLDER = "{container}{? - {collection}} ({downtime:YYYY-MM-DD})"

    private val defaults: Map<NamingShape, NamingTemplateSet> = mapOf(
        NamingShape.Common to NamingTemplateSet(
            topFolder = TOP_FOLDER,
            itemFolder = "{mediaType} - {id} - {work}",
            file = "{taskType} - {?(P{p}) }{title}{? - {res}}",
        ),
        NamingShape.Video to NamingTemplateSet(
            topFolder = TOP_FOLDER,
            itemFolder = "{id} - {work}",
            file = "{taskType} - {?(P{p}) }{title}{? - {res}}",
        ),
        // 一季多集共用同一个项目文件夹，下载整季时就是一个季度文件夹装满单集。
        NamingShape.Episode to NamingTemplateSet(
            topFolder = TOP_FOLDER,
            itemFolder = "{work}",
            file = "{taskType} - {?EP{ep} }{title}{? - {res}}",
        ),
        NamingShape.Track to NamingTemplateSet(
            topFolder = TOP_FOLDER,
            itemFolder = "{id} - {title}",
            file = "{taskType} - {title}{? - {abr}}",
        ),
        NamingShape.Opus to NamingTemplateSet(
            topFolder = TOP_FOLDER,
            itemFolder = "{id} - {work}",
            file = "{taskType} - {title}{? - {img}}",
        ),
        NamingShape.Listing to NamingTemplateSet(
            topFolder = TOP_FOLDER,
        ),
    )

    fun default(shape: NamingShape, scope: NamingTemplateScope): String {
        return defaults[shape]?.get(scope)
            ?: defaults.getValue(NamingShape.Common).get(scope).orEmpty()
    }

    fun resolve(
        overrides: Map<NamingShape, NamingTemplateSet>,
        shape: NamingShape,
        scope: NamingTemplateScope,
    ): String {
        overrides[shape]?.get(scope)?.let { return it }
        if (shape != NamingShape.Common) {
            overrides[NamingShape.Common]?.get(scope)?.let { return it }
        }
        return default(shape, scope)
    }

    fun sourceOf(
        overrides: Map<NamingShape, NamingTemplateSet>,
        shape: NamingShape,
        scope: NamingTemplateScope,
    ): NamingTemplateSource {
        return when {
            overrides[shape]?.get(scope) != null -> NamingTemplateSource.Custom
            shape != NamingShape.Common && overrides[NamingShape.Common]?.get(scope) != null ->
                NamingTemplateSource.Common
            else -> NamingTemplateSource.Default
        }
    }
}
