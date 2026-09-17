package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.model.DownloadEmbedding
import com.happycola233.bilitools.data.model.LyricsEmbedding
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.SubtitleTrackEmbedding
import com.happycola233.bilitools.data.model.capabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 全部是明确的选择；空的指定集合表示尚未选好，不能悄悄退回全部。 */
sealed interface SubtitleLanguageSelection {
    data object All : SubtitleLanguageSelection
    data class Languages(val languages: Set<String>) : SubtitleLanguageSelection
}

internal fun SubtitleLanguageSelection.toRequest(): SubtitleTrackEmbedding = when (this) {
    SubtitleLanguageSelection.All -> SubtitleTrackEmbedding()
    is SubtitleLanguageSelection.Languages -> SubtitleTrackEmbedding(languages.toList())
}

internal fun selectSubtitles(
    subtitles: List<SubtitleInfo>,
    selection: SubtitleLanguageSelection,
): List<SubtitleInfo> = when (selection) {
    SubtitleLanguageSelection.All -> subtitles
    is SubtitleLanguageSelection.Languages -> subtitles.filter { it.lan in selection.languages }
}

internal fun SubtitleLanguageSelection.toggle(
    language: String,
    available: List<SubtitleInfo>,
): SubtitleLanguageSelection {
    val current = when (this) {
        SubtitleLanguageSelection.All -> available.map { it.lan }.toSet()
        is SubtitleLanguageSelection.Languages -> languages
    }
    return SubtitleLanguageSelection.Languages(if (language in current) current - language else current + language)
}

internal val SubtitleLanguageSelection.isEmpty: Boolean
    get() = this is SubtitleLanguageSelection.Languages && languages.isEmpty()

/** 请求保留用户意图；容器调整和缺失资源的反馈由下载阶段负责。 */
internal fun ParseUiState.downloadEmbedding(): DownloadEmbedding? {
    val subtitles = if (embedSubtitlesEnabled && embedSubtitlesApplicable) {
        embedSubtitleSelection.toRequest()
    } else null
    val lyrics = if (embedLyricsEnabled && embedLyricsApplicable) LyricsEmbedding(embedLyricsLanguage) else null
    return if (subtitles == null && lyrics == null) null else DownloadEmbedding(subtitles, lyrics)
}

internal val ParseUiState.needsLyricsLanguage: Boolean
    get() = embedLyricsEnabled && embedLyricsApplicable && embedLyricsLanguage == null &&
        selectedItemIndices.any { items.getOrNull(it)?.type?.capabilities?.supportsSubtitleExport == true }

internal val ParseUiState.hasIncompleteSubtitleSelection: Boolean
    get() = subtitleEnabled && subtitleLanguageSelection.isEmpty ||
        embedSubtitlesEnabled && embedSubtitlesApplicable && embedSubtitleSelection.isEmpty || needsLyricsLanguage

internal data class SubtitleTarget(val aid: Long, val cid: Long)

internal data class SubtitleCatalogSourceKey(
    val type: MediaType,
    val aid: Long?,
    val bvid: String?,
    val cid: Long?,
    val epid: Long?,
    val expandPages: Boolean,
)

internal class SubtitleCatalogSource(val item: MediaItem, val expandPages: Boolean = false) {
    /** 展示信息的补全不会让同一稿件重复请求目录。 */
    val key = SubtitleCatalogSourceKey(item.type, item.aid, item.bvid, item.cid, item.epid, expandPages)

    override fun equals(other: Any?): Boolean = other is SubtitleCatalogSource && key == other.key
    override fun hashCode(): Int = key.hashCode()
}

/** 单条始终预览；批量仅在启用字幕或歌词后查询，避免勾选整个列表就请求所有条目。 */
internal fun ParseUiState.subtitleCatalogTargets(): List<SubtitleCatalogSource> {
    if (loading || collectionModeLoading) return emptyList()
    val info = mediaInfo ?: return emptyList()
    val expandPages = collectionMode && info.type == MediaType.Video
    val needsCatalog = subtitleEnabled || embedSubtitlesEnabled && embedSubtitlesApplicable ||
        embedLyricsEnabled && embedLyricsApplicable
    if ((isMultiSelect || expandPages) && !needsCatalog) return emptyList()
    return selectedItemIndices.mapNotNull { items.getOrNull(it) }
        .filter { it.type.capabilities.supportsSubtitleExport }
        .map { SubtitleCatalogSource(it, expandPages) }
        .distinctBy { it.key }
}

internal data class SubtitleCatalog(
    val subtitles: List<SubtitleInfo> = emptyList(),
    val availableCounts: Map<String, Int> = emptyMap(),
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
)

internal fun ParseUiState.withSubtitleCatalog(catalog: SubtitleCatalog): ParseUiState {
    val complete = catalog.completed == catalog.total
    return copy(
        subtitleList = catalog.subtitles,
        subtitleKnownLanguages = (subtitleKnownLanguages + catalog.subtitles).distinctBy { it.lan },
        subtitleAvailableCounts = catalog.availableCounts,
        subtitleTargetCount = catalog.total,
        subtitleCompletedCount = catalog.completed,
        subtitleFailedCount = catalog.failed,
        subtitleLoadStatus = when {
            !complete -> SubtitleLoadStatus.Loading
            catalog.failed > 0 -> SubtitleLoadStatus.Failed
            else -> SubtitleLoadStatus.Ready
        },
        // 只有完整目录确认唯一语言时才能省去选择；失败或部分加载不能猜测用户需要哪一种。
        embedLyricsLanguage = embedLyricsLanguage ?: catalog.subtitles.singleOrNull()?.lan
            ?.takeIf { complete && catalog.failed == 0 },
    )
}

/** 新解析清除语言与目录，功能开关由下载偏好初始化，当前批次变化则保留语言选择。 */
internal fun ParseUiState.resetSubtitleChoices(): ParseUiState = copy(
    subtitleList = emptyList(),
    subtitleKnownLanguages = emptyList(),
    subtitleLanguageSelection = SubtitleLanguageSelection.All,
    embedSubtitleSelection = SubtitleLanguageSelection.All,
    embedLyricsLanguage = null,
    subtitleAvailableCounts = emptyMap(),
    subtitleTargetCount = 0,
    subtitleCompletedCount = 0,
    subtitleFailedCount = 0,
    subtitleLoadStatus = SubtitleLoadStatus.Loading,
    subtitleRefreshRevision = subtitleRefreshRevision + 1,
)

/** 同一轮解析缓存成功结果，有限并发汇总批量语言；失败不缓存，重试只请求缺失的结果。 */
internal class SubtitleCatalogLoader(
    private val resolve: suspend (MediaItem) -> MediaItem,
    private val expand: suspend (MediaItem) -> List<MediaItem>,
    private val fetch: suspend (SubtitleTarget) -> List<SubtitleInfo>,
) {
    private val lock = Any()
    private val cache = mutableMapOf<SubtitleTarget, List<SubtitleInfo>>()
    private val resolvedSources = mutableMapOf<SubtitleCatalogSourceKey, List<SubtitleTarget>>()
    private var generation = 0

    fun clear() = synchronized(lock) {
        generation += 1
        cache.clear()
        resolvedSources.clear()
    }

    suspend fun load(sources: List<SubtitleCatalogSource>, publish: (SubtitleCatalog) -> Unit) = coroutineScope {
        val loadGeneration = synchronized(lock) { generation }
        val permits = Semaphore(3)
        fun publishCurrent(catalog: SubtitleCatalog) = synchronized(lock) {
            if (generation == loadGeneration) publish(catalog)
        }
        publishCurrent(SubtitleCatalog(total = sources.size))
        val resolved = sources.map { source ->
            async {
                permits.withPermit {
                    try {
                        val cached = synchronized(lock) { resolvedSources[source.key] }
                        cached ?: run {
                            val pages = if (source.expandPages) expand(source.item) else listOf(source.item)
                            val targets = pages.map { page ->
                                val item = resolve(page)
                                SubtitleTarget(
                                    aid = requireNotNull(item.aid) { "Subtitle source is missing aid" },
                                    cid = requireNotNull(item.cid) { "Subtitle source is missing cid" },
                                )
                            }.distinct()
                            currentCoroutineContext().ensureActive()
                            synchronized(lock) {
                                if (generation == loadGeneration) resolvedSources[source.key] = targets
                            }
                            targets
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        null
                    }
                }
            }
        }.awaitAll()
        currentCoroutineContext().ensureActive()
        val targets = resolved.filterNotNull().flatten().distinct()
        // 解析失败的稿件还不知道有多少分 P，按一项未完成计入，不伪装成“没有字幕”。
        val unresolvedCount = resolved.count { it == null }
        val results = linkedMapOf<SubtitleTarget, List<SubtitleInfo>>()
        val failed = mutableSetOf<SubtitleTarget>()
        fun report() {
            val ordered = targets.mapNotNull(results::get)
            publishCurrent(SubtitleCatalog(
                subtitles = ordered.flatten().distinctBy { it.lan },
                availableCounts = ordered.flatMap { list -> list.map { it.lan }.distinct() }
                    .groupingBy { it }.eachCount(),
                total = targets.size + unresolvedCount,
                completed = results.size + failed.size + unresolvedCount,
                failed = failed.size + unresolvedCount,
            ))
        }
        synchronized(lock) {
            targets.forEach { target -> cache[target]?.let { results[target] = it } }
            report()
        }
        targets.filterNot(results::containsKey).map { target ->
            async {
                permits.withPermit {
                    try {
                        val subtitles = fetch(target)
                        currentCoroutineContext().ensureActive()
                        synchronized(lock) {
                            if (generation == loadGeneration) cache[target] = subtitles
                            results[target] = subtitles
                            report()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        synchronized(lock) {
                            failed += target
                            report()
                        }
                    }
                }
            }
        }.awaitAll()
    }
}
