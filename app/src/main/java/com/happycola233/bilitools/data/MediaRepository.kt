package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaQueryOptions
import com.happycola233.bilitools.data.model.MediaSections
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaTab
import com.happycola233.bilitools.data.model.MediaThumb
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.MediaUpper
import com.happycola233.bilitools.data.model.ParsedInput
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import com.squareup.moshi.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

class MediaRepository(
    private val httpClient: BiliHttpClient,
    private val wbiSigner: WbiSigner,
    private val cookieStore: CookieStore,
    private val opusRepository: OpusRepository,
) {
    suspend fun parseInput(input: String, allowRaw: Boolean): ParsedInput {
        val raw = input.trim()
        if (raw.isBlank()) {
            throw InvalidMediaInputException()
        }
        MediaInputClassifier.parseDirectId(raw)?.let { return it }

        val picked = raw.split(Regex("\\s+")).firstOrNull { token ->
            URL_CANDIDATE_REGEX.matches(token) && URL_SAFE_REGEX.matches(token)
        }
        val urlString = when {
            picked != null -> if (picked.startsWith("http", ignoreCase = true)) picked else "https://$picked"
            DOMAIN_ONLY_REGEX.containsMatchIn(raw) -> "https://$raw"
            else -> raw
        }
        val url = urlString.toHttpUrlOrNull()
        if (url == null) {
            if (allowRaw) {
                return ParsedInput(raw)
            }
            throw InvalidMediaInputException()
        }

        val host = url.host.lowercase()
        if (host != "bilibili.com" && !host.endsWith(".bilibili.com") && host != "b23.tv") {
            throw InvalidMediaInputException()
        }

        if (host == "b23.tv") {
            val resolved = httpClient.resolveUrl(url)
            return parseInput(resolved, allowRaw)
        }

        MediaInputClassifier.parseSpaceUrl(url)?.let { return it }

        val segments = url.pathSegments.filter { it.isNotBlank() }
        val root = segments.getOrNull(0)
        val second = segments.getOrNull(1)
        if (!second.isNullOrBlank()) {
            val parsedSecond = MediaInputClassifier.parseDirectId(second)
            when (parsedSecond?.type) {
                MediaType.Video,
                MediaType.Music,
                MediaType.MusicList,
                MediaType.Opus,
                -> return parsedSecond
                else -> Unit
            }
            if (root.equals("opus", ignoreCase = true)) {
                return ParsedInput(second, MediaType.Opus)
            }
        }

        if (root.equals("watchlater", ignoreCase = true)) {
            return ParsedInput("", MediaType.WatchLater)
        }

        val third = segments.getOrNull(2)
        if (!third.isNullOrBlank()) {
            val parsedThird = MediaInputClassifier.parseDirectId(third)
            when (parsedThird?.type) {
                MediaType.Bangumi -> return when {
                    root.equals("bangumi", ignoreCase = true) -> parsedThird
                    root.equals("cheese", ignoreCase = true) ->
                        ParsedInput(parsedThird.id, MediaType.Lesson)
                    else -> throw InvalidMediaInputException()
                }
                MediaType.OpusList -> return parsedThird
                else -> Unit
            }
        }

        if (second.equals("watchlater", ignoreCase = true)) {
            val id = url.queryParameter("aid")
                ?: url.queryParameter("oid")
                ?: url.queryParameter("bvid")
            if (id != null) {
                val parsedQueryId = MediaInputClassifier.parseDirectId(id)
                if (parsedQueryId?.type == MediaType.Video) {
                    return parsedQueryId
                }
                return ParsedInput(id, MediaType.Video)
            }
        }

        if (allowRaw) {
            return ParsedInput(raw)
        }
        throw InvalidMediaInputException()
    }

    suspend fun getMediaInfo(
        id: String,
        type: MediaType,
        options: MediaQueryOptions = MediaQueryOptions(),
    ): MediaInfo {
        return when (type) {
            MediaType.Video -> fetchVideoInfo(id, options)
            MediaType.Bangumi -> fetchBangumiInfo(id, options)
            MediaType.Lesson -> fetchLessonInfo(id, options)
            MediaType.Music -> fetchMusicInfo(id)
            MediaType.MusicList -> fetchMusicListInfo(id, options)
            MediaType.WatchLater -> fetchWatchLaterInfo(options)
            MediaType.Favorite -> fetchFavoriteInfo(id, options)
            MediaType.Opus -> fetchOpusInfo(id)
            MediaType.OpusList -> fetchOpusListInfo(id)
            MediaType.UserVideo -> fetchUserVideoInfo(id, options)
            MediaType.UserOpus -> fetchUserOpusInfo(id, options)
            MediaType.UserAudio -> fetchUserAudioInfo(id, options)
        }
    }

    suspend fun getPlayUrlInfo(
        item: MediaItem,
        type: MediaType,
        format: StreamFormat,
    ): PlayUrlInfo {
        val resolved = if (type == MediaType.Video) ensureVideoCid(item) else item
        return when (type) {
            MediaType.Video,
            MediaType.Bangumi,
            MediaType.Lesson,
            -> fetchVideoPlayUrl(resolved, type, format)
            MediaType.Music -> fetchMusicPlayUrl(resolved)
            else -> throw IllegalArgumentException("Unsupported media type: $type")
        }
    }

    suspend fun resolveItemForPlay(item: MediaItem, type: MediaType): MediaItem {
        return if (type == MediaType.Video) ensureVideoCid(item) else item
    }

    private suspend fun fetchVideoInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val params = if (id.lowercase().startsWith("bv")) {
            mapOf("bvid" to id)
        } else {
            mapOf("aid" to idNum)
        }
        val url = buildUrl("https://api.bilibili.com/x/web-interface/view", params)
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(VideoViewResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty view response", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.message ?: "View error", resp.code)
        }
        val data = resp.data
        val listLink = "https://www.bilibili.com/video/"

        val ugcSeason = data.ugcSeason
        val hasCollection = ugcSeason != null
        val allEpisodes = ugcSeason?.sections?.flatMap { it.episodes }.orEmpty()
        val targetEpisode = allEpisodes.firstOrNull { ep ->
            options.target?.let { t -> ep.id == t } ?: (ep.aid == data.aid)
        }
        val sectionOfTarget = targetEpisode?.let { ep ->
            ugcSeason?.sections?.firstOrNull { it.id == ep.sectionId }
        }
        // 合集里的单集标题和稿件标题可以不同，命名用的是合集里展示的那个。
        val workTitle = targetEpisode?.title?.takeIf { it.isNotBlank() } ?: data.title
        val basePageCount = data.pages?.size ?: 1

        val baseList = data.pages?.mapIndexed { index, page ->
            MediaItem(
                title = page.part?.takeIf { it.isNotBlank() } ?: data.title,
                coverUrl = normalizeCoverUrl(data.pic),
                description = data.desc,
                url = listLink + data.bvid,
                aid = data.aid,
                bvid = data.bvid,
                cid = page.cid,
                duration = page.duration,
                pubTime = page.ctime ?: data.pubdate,
                type = MediaType.Video,
                isTarget = index == 0,
                index = index,
                page = page.page,
                pageCount = basePageCount,
                workTitle = workTitle,
                sectionTitle = sectionOfTarget?.title,
            )
        } ?: listOf(
            MediaItem(
                title = data.title,
                coverUrl = normalizeCoverUrl(data.pic),
                description = data.desc,
                url = listLink + data.bvid,
                aid = data.aid,
                bvid = data.bvid,
                cid = data.cid,
                duration = data.duration,
                pubTime = data.pubdate,
                type = MediaType.Video,
                isTarget = true,
                index = 0,
                page = 1,
                pageCount = 1,
                workTitle = workTitle,
                sectionTitle = sectionOfTarget?.title,
            ),
        )

        var list = baseList
        var sections: MediaSections? = null

        if (targetEpisode != null) {
            if (options.collection) {
                val targetEpisodeId = options.target?.takeIf { t -> allEpisodes.any { it.id == t } }
                list = allEpisodes.mapIndexed { index, ep ->
                    MediaItem(
                        title = ep.title,
                        coverUrl = normalizeCoverUrl(ep.arc.pic),
                        description = ep.arc.desc,
                        url = listLink + ep.bvid,
                        aid = ep.aid,
                        bvid = ep.bvid,
                        cid = ep.cid,
                        duration = ep.page.duration,
                        pubTime = ep.arc.pubdate,
                        type = MediaType.Video,
                        isTarget = targetEpisodeId?.let { ep.id == it } ?: (ep.aid == data.aid),
                        index = index,
                        pageCount = ep.pages.size.takeIf { it > 0 },
                        workTitle = ep.title,
                        sectionTitle = ugcSeason?.sections
                            ?.firstOrNull { it.id == ep.sectionId }
                            ?.title,
                    )
                }
            } else if (basePageCount > 1 && sectionOfTarget != null) {
                list = targetEpisode.pages.mapIndexed { index, page ->
                    val pageNumber = page.page ?: (index + 1)
                    MediaItem(
                        title = page.part?.takeIf { it.isNotBlank() } ?: targetEpisode.title,
                        coverUrl = normalizeCoverUrl(targetEpisode.arc.pic),
                        description = targetEpisode.arc.desc,
                        url = listLink + targetEpisode.bvid,
                        aid = targetEpisode.aid,
                        bvid = targetEpisode.bvid,
                        cid = page.cid,
                        duration = page.duration,
                        pubTime = targetEpisode.arc.pubdate,
                        type = MediaType.Video,
                        isTarget = index == 0,
                        index = index,
                        page = pageNumber,
                        pageCount = targetEpisode.pages.size,
                        workTitle = targetEpisode.title,
                        sectionTitle = sectionOfTarget.title,
                    )
                }
                val targetId = options.target
                    ?.takeIf { t -> sectionOfTarget.episodes.any { it.id == t } }
                    ?: targetEpisode.id
                sections = MediaSections(
                    target = targetId,
                    tabs = sectionOfTarget.episodes.map { MediaTab(it.id, it.title) },
                )
            }
        }

        val tags = runCatching {
            val tagBody = httpClient.get(buildUrl("https://api.bilibili.com/x/tag/archive/tags", params))
            val tagAdapter = httpClient.adapter(VideoTagsResponse::class.java)
            val tagResp = tagAdapter.fromJson(tagBody)
            tagResp?.data?.mapNotNull { it.tagName }.orEmpty()
        }.getOrDefault(emptyList())

        val thumbs = buildList {
            add(MediaThumb("cover", normalizeCoverUrl(data.pic)))
            ugcSeason?.cover?.takeIf { it.isNotBlank() }?.let { add(MediaThumb("ugc", normalizeCoverUrl(it))) }
        }

        return MediaInfo(
            type = MediaType.Video,
            id = id,
            nfo = MediaNfo(
                showTitle = ugcSeason?.title ?: data.title,
                intro = ugcSeason?.intro?.takeIf { it.isNotBlank() } ?: data.desc,
                tags = tags,
                url = listLink + data.bvid,
                stat = MediaStat(
                    play = data.stat?.view?.toLong(),
                    danmaku = data.stat?.danmaku?.toLong(),
                    reply = data.stat?.reply?.toLong(),
                    like = data.stat?.like?.toLong(),
                    coin = data.stat?.coin?.toLong(),
                    favorite = data.stat?.favorite?.toLong(),
                    share = data.stat?.share?.toLong(),
                ),
                thumbs = thumbs,
                premiered = data.pubdate,
                upper = data.owner?.let { createMediaUpper(it.name, it.mid, it.face) },
            ),
            list = list,
            sections = sections,
            collection = hasCollection,
        )
    }

    private suspend fun fetchBangumiInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idType = id.take(2).lowercase()
        val idNum = id.filter { it.isDigit() }
        val params = if (idType == "md") {
            val reviewBody = httpClient.get(
                buildUrl(
                    "https://api.bilibili.com/pgc/review/user",
                    mapOf("media_id" to idNum),
                ),
            )
            val reviewAdapter = httpClient.adapter(BangumiMediaResponse::class.java)
            val reviewResp = reviewAdapter.fromJson(reviewBody)
                ?: throw BiliHttpException("Empty bangumi media response", -1)
            mapOf("season_id" to reviewResp.result?.media?.seasonId?.toString().orEmpty())
        } else if (idType == "ss") {
            mapOf("season_id" to idNum)
        } else {
            mapOf("ep_id" to idNum)
        }
        val url = buildUrl("https://api.bilibili.com/pgc/view/web/season", params)
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(BangumiResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty bangumi response", -1)
        if (resp.code != 0 || resp.result == null) {
            throw BiliHttpException(resp.message ?: "Bangumi error", resp.code)
        }
        val data = resp.result
        val season = data.seasons.firstOrNull { it.seasonId == data.seasonId }
        val inputEpisodeId = idNum.toLongOrNull()
        val autoSectionId = if (idType == "ep" && inputEpisodeId != null) {
            data.section
                ?.firstOrNull { section ->
                    section.episodes.any { ep ->
                        ep.epId == inputEpisodeId || ep.id == inputEpisodeId
                    }
                }
                ?.id
        } else {
            null
        }
        val targetSectionId = options.target ?: autoSectionId ?: data.positive.id
        val sectionEpisodes = data.section?.firstOrNull { it.id == targetSectionId }?.episodes
        val listSource = when {
            !sectionEpisodes.isNullOrEmpty() -> sectionEpisodes
            data.episodes.isNotEmpty() -> data.episodes
            !data.section.isNullOrEmpty() -> data.section.firstOrNull()?.episodes.orEmpty()
            else -> emptyList()
        }
        val tabs = buildList {
            add(MediaTab(data.positive.id, data.positive.title))
            data.section?.forEach { add(MediaTab(it.id, it.title)) }
        }
        val sectionTitle = tabs.firstOrNull { it.id == targetSectionId }?.name
        val list = listSource.mapIndexed { index, ep ->
            val isTargetEpisode = if (idType == "ep" && inputEpisodeId != null) {
                ep.epId == inputEpisodeId || ep.id == inputEpisodeId
            } else {
                false
            }
            val title = ep.showTitle?.takeIf { it.isNotBlank() }
                ?: ep.title?.takeIf { it.isNotBlank() }
                ?: "EP${index + 1}"
            MediaItem(
                title = title,
                coverUrl = normalizeCoverUrl(ep.cover ?: data.cover),
                description = data.evaluate,
                url = ep.shareUrl ?: data.shareUrl,
                aid = ep.aid,
                bvid = ep.bvid,
                cid = ep.cid,
                epid = ep.epId ?: ep.id,
                ssid = data.seasonId,
                duration = ((ep.duration ?: 0) / 1000),
                pubTime = ep.pubTime ?: 0L,
                type = MediaType.Bangumi,
                isTarget = isTargetEpisode,
                index = index,
                workTitle = data.seasonTitle,
                episode = ep.title,
                longTitle = ep.longTitle,
                sectionTitle = sectionTitle,
            )
        }
        val resolvedTargetId = tabs.firstOrNull { it.id == targetSectionId }?.id ?: data.positive.id
        val thumbs = runCatching {
            val root = JSONObject(body)
            val resultJson = root.optJSONObject("result")
            val seasonsJson = resultJson?.optJSONArray("seasons")
            val seasonJson = if (seasonsJson != null) {
                var matched: JSONObject? = null
                for (index in 0 until seasonsJson.length()) {
                    val item = seasonsJson.optJSONObject(index) ?: continue
                    if (item.optLong("season_id") == data.seasonId) {
                        matched = item
                        break
                    }
                }
                matched ?: seasonsJson.optJSONObject(0)
            } else {
                null
            }
            val images = buildList {
                addAll(collectPublicImages(resultJson))
                addAll(collectPublicImages(seasonJson, "season"))
            }
            images.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: buildList {
            fun addThumb(id: String, url: String?) {
                if (!url.isNullOrBlank()) {
                    add(MediaThumb(id, normalizeCoverUrl(url)))
                }
            }
            addThumb("cover", data.cover)
            addThumb("square_cover", data.squareCover)
            addThumb("season_cover", season?.cover)
            addThumb("season_horizontal_cover_1610", season?.horizontalCover1610)
            addThumb("season_horizontal_cover_169", season?.horizontalCover169)
        }
        return MediaInfo(
            type = MediaType.Bangumi,
            id = id,
            nfo = MediaNfo(
                showTitle = data.seasonTitle,
                intro = data.evaluate,
                tags = data.styles,
                url = data.shareUrl,
                stat = MediaStat(
                    play = data.stat.views.toLong(),
                    danmaku = data.stat.danmakus.toLong(),
                    reply = data.stat.reply.toLong(),
                    like = data.stat.likes.toLong(),
                    coin = data.stat.coins.toLong(),
                    favorite = data.stat.favorite.toLong(),
                    share = data.stat.share.toLong(),
                ),
                thumbs = thumbs,
                premiered = data.episodes.firstOrNull()?.pubTime,
                upper = data.upInfo?.let { createMediaUpper(it.uname, it.mid, it.avatar) },
            ),
            list = list,
            sections = if (tabs.isNotEmpty()) MediaSections(resolvedTargetId, tabs) else null,
        )
    }

    private suspend fun fetchLessonInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val params = if (id.startsWith("ss", ignoreCase = true)) {
            mapOf("season_id" to idNum)
        } else {
            mapOf("ep_id" to idNum)
        }
        val url = buildUrl("https://api.bilibili.com/pugv/view/web/season", params)
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(LessonResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty lesson response", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.message ?: "Lesson error", resp.code)
        }
        val data = resp.data
        val list = data.episodes.mapIndexed { index, ep ->
            MediaItem(
                title = ep.title,
                coverUrl = normalizeCoverUrl(ep.cover),
                description = data.subtitle,
                url = data.shareUrl,
                aid = ep.aid,
                cid = ep.cid,
                epid = ep.id,
                ssid = data.seasonId,
                duration = ep.duration,
                pubTime = ep.releaseDate,
                type = MediaType.Lesson,
                isTarget = index == 0,
                index = index,
                workTitle = data.title,
                episode = (ep.index ?: (index + 1)).toString(),
            )
        }
        val intro = listOfNotNull(
            data.subtitle.takeIf { it.isNotBlank() },
            data.faq?.title?.takeIf { it.isNotBlank() },
            data.faq?.content?.takeIf { it.isNotBlank() },
        ).joinToString("\n")
        val thumbs = runCatching {
            val root = JSONObject(body)
            val dataJson = root.optJSONObject("data")
            if (dataJson == null) return@runCatching null
            val images = buildList {
                addAll(collectPublicImages(dataJson))
                val brief = dataJson.optJSONObject("brief")?.optJSONArray("img")
                if (brief != null) {
                    for (index in 0 until brief.length()) {
                        val item = brief.optJSONObject(index) ?: continue
                        val url = item.optString("url")
                        if (url.isNotBlank()) {
                            add(MediaThumb("brief-${index + 1}", normalizeCoverUrl(url)))
                        }
                    }
                }
            }
            images.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: buildList {
            add(MediaThumb("cover", normalizeCoverUrl(data.cover)))
            data.brief?.img?.forEachIndexed { idx, image ->
                add(MediaThumb("brief-${idx + 1}", normalizeCoverUrl(image.url)))
            }
        }
        return MediaInfo(
            type = MediaType.Lesson,
            id = id,
            nfo = MediaNfo(
                showTitle = data.title,
                intro = intro,
                tags = emptyList(),
                url = data.shareUrl,
                stat = MediaStat(play = data.stat.play.toLong()),
                thumbs = thumbs,
                premiered = data.episodes.firstOrNull()?.releaseDate,
                upper = data.upInfo?.let { createMediaUpper(it.uname, it.mid, it.avatar) },
            ),
            list = list,
        )
    }

    private suspend fun fetchMusicInfo(id: String): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val url = buildUrl(
            "https://www.bilibili.com/audio/music-service-c/web/song/info",
            mapOf("sid" to idNum),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(MusicInfoResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty music response", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.msg ?: "Music error", resp.code)
        }
        val data = resp.data
        val tags = runCatching {
            val tagBody = httpClient.get(
                buildUrl(
                    "https://www.bilibili.com/audio/music-service-c/web/tag/song",
                    mapOf("sid" to idNum),
                ),
            )
            val tagAdapter = httpClient.adapter(MusicTagsResponse::class.java)
            val tagResp = tagAdapter.fromJson(tagBody)
            tagResp?.data?.map { it.info }.orEmpty()
        }.getOrDefault(emptyList())
        val upper = runCatching {
            val upperBody = httpClient.get(
                buildUrl(
                    "https://www.bilibili.com/audio/music-service-c/web/user/info",
                    mapOf("uid" to data.uid.toString()),
                ),
            )
            val upperAdapter = httpClient.adapter(MusicUpperResponse::class.java)
            upperAdapter.fromJson(upperBody)?.data
        }.getOrNull()
        val link = "https://www.bilibili.com/audio/au${data.id}"
        val list = listOf(
            MediaItem(
                title = data.title,
                coverUrl = normalizeCoverUrl(data.cover),
                description = data.intro,
                url = link,
                aid = data.aid,
                bvid = data.bvid,
                cid = data.cid,
                sid = data.id,
                duration = data.duration,
                pubTime = data.passtime,
                type = MediaType.Music,
                isTarget = true,
                index = 0,
                workTitle = data.title,
                artist = data.author,
            ),
        )
        return MediaInfo(
            type = MediaType.Music,
            id = id,
            nfo = MediaNfo(
                showTitle = data.title,
                intro = data.intro,
                tags = tags,
                url = link,
                stat = MediaStat(
                    play = data.statistic.play.toLong(),
                    reply = data.statistic.comment.toLong(),
                    favorite = data.statistic.collect.toLong(),
                    share = data.statistic.share.toLong(),
                ),
                thumbs = listOf(MediaThumb("cover", normalizeCoverUrl(data.cover))),
                premiered = data.passtime,
                upper = createMediaUpper(
                    name = upper?.uname ?: data.uname,
                    mid = upper?.uid ?: data.uid,
                    avatar = upper?.avater?.takeIf { it.isNotBlank() } ?: upper?.avatar,
                ),
            ),
            list = list,
        )
    }

    private suspend fun fetchMusicListInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val url = buildUrl(
            "https://www.bilibili.com/audio/music-service-c/web/menu/info",
            mapOf("sid" to idNum),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(MusicListResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty music list", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.msg ?: "Music list error", resp.code)
        }
        val data = resp.data
        val listBody = httpClient.get(
            buildUrl(
                "https://www.bilibili.com/audio/music-service-c/web/song/of-menu",
                mapOf(
                    "pn" to options.page.toString(),
                    "ps" to "20",
                    "sid" to data.menuId.toString(),
                ),
            ),
        )
        val listAdapter = httpClient.adapter(MusicListDetailResponse::class.java)
        val listResp = listAdapter.fromJson(listBody)
            ?: throw BiliHttpException("Empty music list detail", -1)
        if (listResp.code != 0 || listResp.data == null) {
            throw BiliHttpException(listResp.msg ?: "Music list error", listResp.code)
        }
        val link = "https://www.bilibili.com/audio/"
        val list = listResp.data.data.mapIndexed { index, item ->
            MediaItem(
                title = item.title,
                coverUrl = normalizeCoverUrl(item.cover),
                description = data.intro,
                url = "${link}au${item.id}",
                aid = item.aid,
                bvid = item.bvid,
                cid = item.cid,
                sid = item.id,
                duration = item.duration,
                pubTime = item.passtime,
                type = MediaType.Music,
                upper = createMediaUpper(item.uname, item.uid, null),
                isTarget = index == 0,
                index = index,
                workTitle = item.title,
                artist = item.author,
                amid = data.menuId,
            )
        }
        return MediaInfo(
            type = MediaType.MusicList,
            id = id,
            paged = true,
            // 歌单接口直接返回总页数
            totalPages = listResp.data.pageCount,
            nfo = MediaNfo(
                showTitle = data.title,
                intro = data.intro,
                tags = emptyList(),
                url = "${link}am${data.menuId}",
                stat = MediaStat(
                    play = data.statistic.play.toLong(),
                    reply = data.statistic.comment.toLong(),
                    favorite = data.statistic.collect.toLong(),
                    share = data.statistic.share.toLong(),
                ),
                thumbs = listOf(MediaThumb("cover", normalizeCoverUrl(data.cover))),
                premiered = data.ctime * 1000,
                upper = createMediaUpper(data.uname, data.uid, null),
            ),
            list = list,
        )
    }

    private suspend fun fetchWatchLaterInfo(
        options: MediaQueryOptions,
    ): MediaInfo {
        val pageSize = 20
        val adapter = httpClient.adapter(WatchLaterResponse::class.java)
        val page = options.page.coerceAtLeast(1)
        val url = buildUrl(
            "https://api.bilibili.com/x/v2/history/toview/web",
            mapOf("ps" to pageSize.toString(), "pn" to page.toString()),
        )
        val body = httpClient.get(url)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty watch later", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.message ?: "Watch later error", resp.code)
        }
        val baseIndex = (page - 1) * pageSize
        val list = resp.data.list.mapIndexed { index, item ->
            MediaItem(
                title = item.title,
                coverUrl = normalizeCoverUrl(item.pic),
                description = item.desc,
                url = "https://www.bilibili.com/video/${item.bvid}",
                aid = item.aid,
                bvid = item.bvid,
                duration = item.duration,
                pubTime = item.pubdate,
                type = MediaType.Video,
                upper = item.owner?.let { createMediaUpper(it.name, it.mid, it.face) },
                isTarget = index == 0,
                index = baseIndex + index,
                workTitle = item.title,
            )
        }
        return MediaInfo(
            type = MediaType.WatchLater,
            id = "",
            paged = true,
            totalPages = totalPagesFromItemCount(resp.data.count, pageSize),
            nfo = MediaNfo(
                tags = emptyList(),
                stat = MediaStat(),
                url = "https://www.bilibili.com/watchlater/list",
                thumbs = list.firstOrNull()?.let { listOf(MediaThumb("cover", it.coverUrl)) }.orEmpty(),
            ),
            list = list,
        )
    }

    private suspend fun fetchFavoriteInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val folderList = runCatching {
            val listBody = httpClient.get(
                buildUrl(
                    "https://api.bilibili.com/x/v3/fav/folder/created/list-all",
                    mapOf("up_mid" to idNum),
                ),
            )
            val listAdapter = httpClient.adapter(FavoriteListResponse::class.java)
            val listResp = listAdapter.fromJson(listBody)
                ?: throw BiliHttpException("Empty favorite list", -1)
            if (listResp.code != 0 || listResp.data == null) {
                throw BiliHttpException(listResp.message ?: "Favorite list error", listResp.code)
            }
            listResp.data.list
        }.getOrNull()

        val target = options.target ?: folderList?.firstOrNull()?.id
        val targetId = target ?: idNum.toLongOrNull()
            ?: throw BiliHttpException("No favorite id provided", -1)
        // 请求页容量；服务端按这个宽度切窗口，medias 实际条数可以更少
        val pageSize = 36
        val listAdapter = httpClient.adapter(FavoriteResourceResponse::class.java)
        val page = options.page.coerceAtLeast(1)
        val listBody = httpClient.get(
            buildUrl(
                "https://api.bilibili.com/x/v3/fav/resource/list",
                mapOf(
                    "media_id" to targetId.toString(),
                    "pn" to page.toString(),
                    "ps" to pageSize.toString(),
                    "platform" to "web",
                ),
            ),
        )
        val listResp = listAdapter.fromJson(listBody)
            ?: throw BiliHttpException("Empty favorite list", -1)
        if (listResp.code != 0 || listResp.data == null) {
            throw BiliHttpException(listResp.message ?: "Favorite list error", listResp.code)
        }
        val data = listResp.data
        val baseIndex = (page - 1) * pageSize
        val list = data.medias.mapIndexed { index, item ->
            val itemStat = item.cntInfo?.let { cnt ->
                // 收藏夹列表项的 cnt_info 只有基础计数，完整视频统计需要再查视频详情。
                MediaStat(
                    play = cnt.play,
                    danmaku = cnt.danmaku,
                    favorite = cnt.collect,
                )
            } ?: MediaStat()
            MediaItem(
                title = item.title,
                coverUrl = normalizeCoverUrl(item.cover),
                description = item.intro?.takeIf { it.isNotBlank() } ?: "",
                stat = itemStat,
                url = "https://www.bilibili.com/video/${item.bvid}",
                aid = item.id,
                bvid = item.bvid,
                duration = item.duration,
                pubTime = item.pubtime,
                type = mapFavoriteType(item.type),
                upper = item.upper?.let { createMediaUpper(it.name, it.mid, it.face) },
                isTarget = index == 0,
                index = baseIndex + index,
                workTitle = item.title,
                fid = data.info.id,
            )
        }
        val resolvedInfo = data.info
        val sections = folderList?.let { folders ->
            MediaSections(
                target = targetId,
                tabs = folders.map { MediaTab(it.id, it.title) },
            )
        }
        return MediaInfo(
            type = MediaType.Favorite,
            id = id,
            paged = true,
            totalPages = totalPagesFromItemCount(resolvedInfo.mediaCount, pageSize),
            nfo = MediaNfo(
                showTitle = resolvedInfo.title,
                intro = resolvedInfo.intro,
                tags = emptyList(),
                url = resolvedInfo.upper?.let { "https://space.bilibili.com/${it.mid}/favlist" },
                stat = MediaStat(
                    play = resolvedInfo.cntInfo.play.toLong(),
                    like = resolvedInfo.cntInfo.thumbUp.toLong(),
                    favorite = resolvedInfo.cntInfo.collect.toLong(),
                    share = resolvedInfo.cntInfo.share.toLong(),
                ),
                thumbs = listOf(MediaThumb("cover", normalizeCoverUrl(resolvedInfo.cover))),
                premiered = resolvedInfo.ctime * 1000,
                upper = resolvedInfo.upper?.let { createMediaUpper(it.name, it.mid, it.face) },
            ),
            list = list,
            sections = sections,
        )
    }

    private suspend fun fetchOpusInfo(id: String): MediaInfo {
        val cvid = id.takeIf { it.startsWith("cv", ignoreCase = true) }
            ?.drop(2)
            ?.toLongOrNull()
        val document = opusRepository.getDocument(id, cvid)
        val coverUrl = document.images.firstOrNull()?.url.orEmpty()
        val list = listOf(
            MediaItem(
                title = document.title,
                coverUrl = coverUrl,
                description = document.summary,
                stat = document.stat,
                url = document.sourceUrl,
                duration = 0,
                pubTime = document.publishedAt ?: 0,
                type = MediaType.Opus,
                isTarget = true,
                index = 0,
                workTitle = document.title,
                opid = document.id,
                cvid = document.cvid,
            ),
        )
        return MediaInfo(
            type = MediaType.Opus,
            id = id,
            nfo = MediaNfo(
                showTitle = document.title,
                intro = document.summary,
                tags = document.tags,
                url = document.sourceUrl,
                stat = document.stat,
                thumbs = document.images.firstOrNull()?.let {
                    listOf(MediaThumb("cover", it.url))
                }.orEmpty(),
                premiered = document.publishedAt?.times(1000),
                upper = document.author,
            ),
            list = list,
        )
    }

    private suspend fun fetchOpusListInfo(id: String): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val body = httpClient.get(
            buildUrl(
                "https://api.bilibili.com/x/article/list/web/articles",
                mapOf("id" to idNum),
            ),
        )
        val adapter = httpClient.adapter(OpusListResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty opus list", -1)
        val data = resp.data
        val listInfo = data?.list
        if (resp.code != 0 || listInfo == null) {
            throw BiliHttpException(resp.message ?: "Opus list error", resp.code)
        }
        val articles = data.articles.orEmpty()
        val author = data.author
        val url = "https://www.bilibili.com/read/readlist/rl${listInfo.id}"
        val tags = articles.firstOrNull()?.categories?.mapNotNull { it.name }.orEmpty()
        val thumbs = listInfo.imageUrl?.takeIf { it.isNotBlank() }?.let {
            listOf(MediaThumb("cover", normalizeCoverUrl(it)))
        }.orEmpty()
        val list = articles.mapIndexed { index, article ->
            val articleUrl = article.dynId?.takeIf { it.isNotBlank() }
                ?.let { "https://www.bilibili.com/opus/$it" }
                ?: "https://www.bilibili.com/read/cv${article.id}"
            MediaItem(
                title = article.title,
                coverUrl = normalizeCoverUrl(article.imageUrls.firstOrNull().orEmpty()),
                description = article.summary,
                stat = article.stats?.toMediaStat(),
                url = articleUrl,
                duration = 0,
                pubTime = article.publishTime,
                type = MediaType.Opus,
                isTarget = index == 0,
                index = index,
                workTitle = article.title,
                opid = article.dynId,
                cvid = article.id,
                rlid = listInfo.id,
            )
        }
        return MediaInfo(
            type = MediaType.OpusList,
            id = id,
            nfo = MediaNfo(
                showTitle = listInfo.name,
                intro = listInfo.summary,
                tags = tags,
                url = url,
                stat = MediaStat(play = listInfo.read?.toLong()),
                thumbs = thumbs,
                upper = author?.let { createMediaUpper(it.name, it.mid, it.face) },
            ),
            list = list,
        )
    }

    private suspend fun fetchUserVideoInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val fallbackMid = idNum.toLongOrNull() ?: 0L
        val upper = fetchUserUpper(fallbackMid)
        val upperMid = upper?.mid ?: fallbackMid
        val target = options.target

        if (target != null) {
            val params = mapOf(
                "mid" to idNum,
                "page_size" to "10",
                "page_num" to options.page.toString(),
            )
            val body = httpClient.get(
                buildUrl(
                    "https://api.bilibili.com/x/polymer/web-space/home/seasons_series",
                    params,
                ),
            )
            val adapter = httpClient.adapter(UploadsSeriesResponse::class.java)
            val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty uploads response", -1)
            val itemsLists = resp.data?.itemsLists
            if (resp.code != 0 || itemsLists == null) {
                throw BiliHttpException(resp.message ?: "Uploads error", resp.code)
            }
            val seasons = itemsLists.seasonsList.orEmpty()
            val series = itemsLists.seriesList.orEmpty()
            val matchedSeason = seasons.firstOrNull { it.meta?.seasonId == target }?.meta
            val matchedSeries = series.firstOrNull { it.meta?.seriesId == target }?.meta
            val useSeason = when {
                matchedSeason != null -> true
                matchedSeries != null -> false
                seasons.isNotEmpty() && series.isEmpty() -> true
                series.isNotEmpty() && seasons.isEmpty() -> false
                else -> throw BiliHttpException("No list found for target $target", -1)
            }

            // 合集/系列列表请求固定 page_size=10，与上方 params 保持一致
            val listPageSize = 10
            val selection = if (useSeason) {
                val resolvedTarget = matchedSeason?.seasonId ?: target
                val listBody = httpClient.get(
                    buildUrl(
                        "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list",
                        params + mapOf("season_id" to resolvedTarget.toString()),
                    ),
                )
                val listAdapter = httpClient.adapter(UploadsArchivesResponse::class.java)
                val listResp = listAdapter.fromJson(listBody)
                    ?: throw BiliHttpException("Empty uploads list", -1)
                if (listResp.code != 0 || listResp.data == null) {
                    throw BiliHttpException(listResp.message ?: "Uploads list error", listResp.code)
                }
                val resolvedMeta = seasons.firstOrNull { it.meta?.seasonId == resolvedTarget }?.meta
                UploadsListSelection(
                    archives = listResp.data.archives.orEmpty(),
                    meta = resolvedMeta,
                    sections = MediaSections(
                        target = resolvedTarget,
                        tabs = seasons.mapNotNull { item ->
                            val metaItem = item.meta ?: return@mapNotNull null
                            MediaTab(metaItem.seasonId, metaItem.name)
                        },
                    ),
                    totalItems = listResp.data.page?.total,
                )
            } else {
                val resolvedTarget = matchedSeries?.seriesId ?: target
                val listBody = httpClient.get(
                    buildUrl(
                        "https://api.bilibili.com/x/series/archives",
                        params + mapOf("series_id" to resolvedTarget.toString()),
                    ),
                )
                val listAdapter = httpClient.adapter(UploadsArchivesResponse::class.java)
                val listResp = listAdapter.fromJson(listBody)
                    ?: throw BiliHttpException("Empty uploads list", -1)
                if (listResp.code != 0 || listResp.data == null) {
                    throw BiliHttpException(listResp.message ?: "Uploads list error", listResp.code)
                }
                val resolvedMeta = series.firstOrNull { it.meta?.seriesId == resolvedTarget }?.meta
                UploadsListSelection(
                    archives = listResp.data.archives.orEmpty(),
                    meta = resolvedMeta,
                    sections = MediaSections(
                        target = resolvedTarget,
                        tabs = series.mapNotNull { item ->
                            val metaItem = item.meta ?: return@mapNotNull null
                            MediaTab(metaItem.seriesId, metaItem.name)
                        },
                    ),
                    totalItems = listResp.data.page?.total,
                )
            }

            val (archives, meta, sections, listTotalItems) = selection
            val resolvedMeta = meta ?: throw BiliHttpException("No meta found for uploads", -1)
            val list = archives.mapIndexed { index, item ->
                MediaItem(
                    title = item.title,
                    coverUrl = normalizeCoverUrl(item.pic),
                    description = resolvedMeta.description,
                    url = "https://www.bilibili.com/video/${item.bvid}",
                    aid = item.aid,
                    bvid = item.bvid,
                    duration = item.duration,
                    pubTime = item.pubdate,
                    type = MediaType.Video,
                    isTarget = index == 0,
                    index = index,
                    workTitle = item.title,
                )
            }
            return MediaInfo(
                type = MediaType.UserVideo,
                id = id,
                paged = true,
                totalPages = totalPagesFromItemCount(listTotalItems, listPageSize),
                nfo = MediaNfo(
                    showTitle = resolvedMeta.name,
                    intro = resolvedMeta.description,
                    tags = emptyList(),
                    url = "https://space.bilibili.com/$upperMid/lists/${sections.target}",
                    stat = MediaStat(),
                    thumbs = listOfNotNull(
                        resolvedMeta.cover.takeIf { it.isNotBlank() }?.let {
                            MediaThumb("cover", normalizeCoverUrl(it))
                        },
                    ),
                    premiered = resolvedMeta.ptime,
                    upper = upper,
                ),
                sections = sections,
                list = list,
            )
        }

        val listUrl = wbiSigner.signedUrl(
            "https://api.bilibili.com/x/space/wbi/arc/search",
            mapOf(
                "mid" to idNum,
                "ps" to "25",
                "pn" to options.page.toString(),
            ),
        )
        val listBody = httpClient.get(listUrl)
        val listAdapter = httpClient.adapter(UploadsSearchResponse::class.java)
        val listResp = listAdapter.fromJson(listBody)
            ?: throw BiliHttpException("Empty uploads list", -1)
        if (listResp.code != 0 || listResp.data?.list == null) {
            throw BiliHttpException(listResp.message ?: "Uploads list error", listResp.code)
        }
        val vlist = listResp.data.list.vlist.orEmpty()
        val searchPage = listResp.data.page
        val searchTotalPages = totalPagesFromItemCount(
            searchPage?.count,
            searchPage?.ps?.takeIf { it > 0 } ?: 25,
        )
        val list = vlist.mapIndexed { index, item ->
            MediaItem(
                title = item.title,
                coverUrl = normalizeCoverUrl(item.pic),
                description = item.description,
                url = "https://www.bilibili.com/video/${item.bvid}",
                aid = item.aid,
                bvid = item.bvid,
                duration = parseDurationText(item.length),
                pubTime = item.created,
                type = MediaType.Video,
                isTarget = index == 0,
                index = index,
                workTitle = item.title,
            )
        }
        return MediaInfo(
            type = MediaType.UserVideo,
            id = id,
            paged = true,
            totalPages = searchTotalPages,
            nfo = MediaNfo(
                tags = emptyList(),
                stat = MediaStat(),
                url = "https://space.bilibili.com/$upperMid/upload/video",
                upper = upper,
                thumbs = if (vlist.isNotEmpty()) {
                    listOf(MediaThumb("pic", normalizeCoverUrl(vlist[0].pic)))
                } else {
                    emptyList()
                },
            ),
            list = list,
        )
    }

    private suspend fun fetchUserOpusInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val params = mapOf(
            "host_mid" to idNum,
            "page" to options.page.toString(),
            "offset" to (options.offset ?: ""),
            "type" to "all",
        )
        val body = httpClient.get(
            buildUrl(
                "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/feed/space",
                params,
            ),
        )
        val adapter = httpClient.adapter(UserOpusResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty user opus response", -1)
        val data = resp.data
        if (resp.code != 0 || data == null) {
            throw BiliHttpException(resp.message ?: "User opus error", resp.code)
        }
        val fallbackMid = idNum.toLongOrNull() ?: 0L
        val upper = fetchUserUpper(fallbackMid)
        val upperMid = upper?.mid ?: fallbackMid
        val url = "https://space.bilibili.com/$upperMid/upload/opus"
        val list = data.items.mapIndexed { index, item ->
            val itemUrl = item.jumpUrl?.takeIf { it.isNotBlank() }
                ?.let(::normalizeCoverUrl)
                ?: "https://www.bilibili.com/opus/${item.opusId}"
            MediaItem(
                title = item.content.trim().takeIf { it.isNotBlank() }
                    ?: "图文_${item.opusId}",
                coverUrl = normalizeCoverUrl(item.cover?.url.orEmpty()),
                description = item.content,
                stat = MediaStat(
                    play = item.stat?.view?.toLongOrNull(),
                    like = item.stat?.like?.toLongOrNull(),
                ),
                url = itemUrl,
                duration = 0,
                pubTime = 0,
                type = MediaType.Opus,
                isTarget = index == 0,
                index = index,
                workTitle = item.content.trim().takeIf { it.isNotBlank() },
                opid = item.opusId,
            )
        }
        return MediaInfo(
            type = MediaType.UserOpus,
            id = id,
            paged = true,
            offset = data.offset,
            hasMore = data.hasMore,
            nfo = MediaNfo(
                showTitle = upper?.name?.takeIf { it.isNotBlank() },
                tags = emptyList(),
                stat = MediaStat(),
                upper = upper,
                url = url,
                thumbs = emptyList(),
            ),
            list = list,
        )
    }

    private suspend fun fetchUserAudioInfo(
        id: String,
        options: MediaQueryOptions,
    ): MediaInfo {
        val idNum = id.filter { it.isDigit() }
        val body = httpClient.get(
            buildUrl(
                "https://www.bilibili.com/audio/music-service/web/song/upper",
                mapOf(
                    "uid" to idNum,
                    "ps" to "42",
                    "pn" to options.page.toString(),
                ),
            ),
        )
        val adapter = httpClient.adapter(UserAudioResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty user audio response", -1)
        val data = resp.data?.data.orEmpty()
        if (resp.code != 0) {
            throw BiliHttpException(resp.message ?: "User audio error", resp.code)
        }
        val fallbackMid = idNum.toLongOrNull() ?: 0L
        val upper = fetchUserUpper(fallbackMid)
        val upperMid = upper?.mid ?: fallbackMid
        val list = data.mapIndexed { index, item ->
            MediaItem(
                title = item.title,
                coverUrl = normalizeCoverUrl(item.cover),
                description = item.title,
                url = "https://www.bilibili.com/audio/au${item.id}",
                aid = item.aid,
                bvid = item.bvid,
                cid = item.cid,
                sid = item.id,
                duration = item.duration,
                pubTime = item.passtime,
                type = MediaType.Music,
                isTarget = index == 0,
                index = index,
                workTitle = item.title,
                artist = item.author,
            )
        }
        return MediaInfo(
            type = MediaType.UserAudio,
            id = id,
            paged = true,
            // 用户音频接口直接返回总页数
            totalPages = resp.data?.pageCount,
            nfo = MediaNfo(
                tags = emptyList(),
                stat = MediaStat(),
                url = "https://space.bilibili.com/$upperMid/upload/audio",
                upper = upper,
                thumbs = data.firstOrNull()?.let {
                    listOf(MediaThumb("cover", normalizeCoverUrl(it.cover)))
                }.orEmpty(),
            ),
            list = list,
        )
    }

    private suspend fun fetchUserUpper(mid: Long): MediaUpper? {
        if (mid <= 0L) return null
        val url = wbiSigner.signedUrl(
            "https://api.bilibili.com/x/space/wbi/acc/info",
            mapOf("mid" to mid.toString()),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(SpaceUserInfoResponse::class.java)
        val resp = adapter.fromJson(body) ?: return null
        if (resp.code != 0 || resp.data == null) return null
        val data = resp.data
        val name = data.name.orEmpty()
        val resolvedMid = data.mid ?: mid
        return createMediaUpper(name, resolvedMid, data.face)
    }

    private fun parseDurationText(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val parts = text.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0
        var total = 0
        for (part in parts) {
            total = total * 60 + part
        }
        return total
    }

    private fun collectPublicImages(
        json: JSONObject?,
        prefix: String? = null,
    ): List<MediaThumb> {
        if (json == null) return emptyList()
        val images = mutableListOf<MediaThumb>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)
            if (value is String && isPublicImageUrl(value)) {
                val id = if (prefix.isNullOrBlank()) key else "${prefix}_${key}"
                images.add(MediaThumb(id, normalizeCoverUrl(value)))
            }
        }
        return images
    }

    private fun isPublicImageUrl(url: String): Boolean {
        return url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".gif")
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }

    private suspend fun fetchVideoPlayUrl(
        item: MediaItem,
        type: MediaType,
        format: StreamFormat,
    ): PlayUrlInfo {
        val params = mutableMapOf(
            "qn" to if (cookieStore.isLoggedIn()) "127" else "64",
            "fnver" to "0",
            "fnval" to "16",
            "fourk" to "1",
        )
        params["fnval"] = when (format) {
            StreamFormat.Flv -> "0"
            StreamFormat.Mp4 -> "1"
            StreamFormat.Dash -> if (cookieStore.isLoggedIn()) "4048" else "16"
        }
        val baseUrl = when (type) {
            MediaType.Video -> "https://api.bilibili.com/x/player/wbi/playurl"
            MediaType.Bangumi -> "https://api.bilibili.com/pgc/player/web/v2/playurl"
            MediaType.Lesson -> "https://api.bilibili.com/pugv/player/web/playurl"
            else -> throw IllegalArgumentException("Unsupported media type: $type")
        }
        when (type) {
            MediaType.Video -> {
                item.aid?.let { params["avid"] = it.toString() }
                item.cid?.let { params["cid"] = it.toString() }
            }
            MediaType.Bangumi -> {
                item.epid?.let { params["ep_id"] = it.toString() }
                item.ssid?.let { params["season_id"] = it.toString() }
            }
            MediaType.Lesson -> {
                item.aid?.let { params["avid"] = it.toString() }
                item.cid?.let { params["cid"] = it.toString() }
                item.epid?.let { params["ep_id"] = it.toString() }
                item.ssid?.let { params["season_id"] = it.toString() }
            }
        }
        val cleaned = params.filterValues { it.isNotBlank() }
        val body = httpClient.get(wbiSigner.signedUrl(baseUrl, cleaned))
        val adapter = httpClient.adapter(PlayUrlResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty playurl response", -1)
        if (resp.code != 0) {
            throw BiliHttpException(resp.message ?: "Playurl error", resp.code)
        }
        val altAdapter = httpClient.adapter(PlayUrlVideoInfoResponse::class.java)
        val alt = altAdapter.fromJson(body)
        if (alt != null && alt.code != 0) {
            throw BiliHttpException(alt.message ?: "Playurl error", alt.code)
        }
        val data = resolvePlayUrlData(resp, alt)
            ?: throw BiliHttpException("No playable stream", -1)
        val acceptQuality = data.acceptQuality ?: emptyList()
        val acceptDescription = data.acceptDescription ?: emptyList()

        if (format == StreamFormat.Dash && data.dash != null) {
            val dash = data.dash
            val video = dash.video?.mapNotNull { it.toVideoStream(StreamFormat.Dash) } ?: emptyList()
            val audio = buildList {
                addAll(dash.audio?.mapNotNull { it.toAudioStream() }.orEmpty())
                dash.dolby?.audio?.firstOrNull()?.toAudioStream()?.let { add(it) }
                dash.flac?.audio?.toAudioStream()?.let { add(it) }
            }
            return PlayUrlInfo(
                format = StreamFormat.Dash,
                video = video,
                audio = audio,
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }

        val durls = data.durls ?: emptyList()
        if (durls.isNotEmpty()) {
            val resolvedFormat = resolveLegacyStreamFormat(data, format)
            val video = durls.mapNotNull { it.toVideoStream(resolvedFormat) }
            return PlayUrlInfo(
                format = resolvedFormat,
                video = video,
                audio = emptyList(),
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }

        val durlList = data.durl ?: emptyList()
        if (durlList.isNotEmpty()) {
            val resolvedFormat = resolveLegacyStreamFormat(data, format)
            val video = if (acceptQuality.isNotEmpty()) {
                acceptQuality.mapNotNull { qn ->
                    val info = if (data.quality == qn) {
                        data
                    } else {
                        val paramsQn = cleaned.toMutableMap()
                        paramsQn["qn"] = qn.toString()
                        val qnBody = httpClient.get(wbiSigner.signedUrl(baseUrl, paramsQn))
                        val qnResp = adapter.fromJson(qnBody) ?: return@mapNotNull null
                        val qnAlt = altAdapter.fromJson(qnBody)
                        resolvePlayUrlData(qnResp, qnAlt)
                    }
                    val durl = info?.durl?.firstOrNull()
                    durl?.toVideoStream(resolvedFormat, qn)
                }
            } else {
                durlList.mapNotNull { it.toVideoStream(resolvedFormat, data.quality ?: 0) }
            }
            return PlayUrlInfo(
                format = resolvedFormat,
                video = video,
                audio = emptyList(),
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }
        if (data.dash != null) {
            val dash = data.dash
            val video = dash.video?.mapNotNull { it.toVideoStream(StreamFormat.Dash) } ?: emptyList()
            val audio = buildList {
                addAll(dash.audio?.mapNotNull { it.toAudioStream() }.orEmpty())
                dash.dolby?.audio?.firstOrNull()?.toAudioStream()?.let { add(it) }
                dash.flac?.audio?.toAudioStream()?.let { add(it) }
            }
            return PlayUrlInfo(
                format = StreamFormat.Dash,
                video = video,
                audio = audio,
                acceptQuality = acceptQuality,
                acceptDescription = acceptDescription,
            )
        }
        throw BiliHttpException("No playable stream", -1)
    }

    private fun resolveLegacyStreamFormat(
        data: PlayUrlData,
        requestedFormat: StreamFormat,
    ): StreamFormat {
        parseResponseFormat(data.format)?.let { return it }
        val accepted = parseAcceptFormats(data.acceptFormat)
        return when {
            requestedFormat == StreamFormat.Flv && StreamFormat.Flv in accepted -> StreamFormat.Flv
            requestedFormat == StreamFormat.Mp4 && StreamFormat.Mp4 in accepted -> StreamFormat.Mp4
            StreamFormat.Mp4 in accepted && StreamFormat.Flv !in accepted -> StreamFormat.Mp4
            StreamFormat.Flv in accepted && StreamFormat.Mp4 !in accepted -> StreamFormat.Flv
            requestedFormat != StreamFormat.Dash -> requestedFormat
            else -> StreamFormat.Mp4
        }
    }

    private fun parseResponseFormat(format: String?): StreamFormat? {
        val normalized = format?.trim()?.lowercase().orEmpty()
        return when {
            normalized.isBlank() -> null
            "flv" in normalized -> StreamFormat.Flv
            "mp4" in normalized -> StreamFormat.Mp4
            else -> null
        }
    }

    private fun parseAcceptFormats(acceptFormat: String?): Set<StreamFormat> {
        if (acceptFormat.isNullOrBlank()) return emptySet()
        return buildSet {
            acceptFormat
                .split(',')
                .map { it.trim().lowercase() }
                .forEach { token ->
                    when {
                        "mp4" in token -> add(StreamFormat.Mp4)
                        "flv" in token -> add(StreamFormat.Flv)
                    }
                }
        }
    }

    private fun resolvePlayUrlData(
        resp: PlayUrlResponse?,
        alt: PlayUrlVideoInfoResponse?,
    ): PlayUrlData? {
        var data = resp?.data
        if (!hasPlayableStreams(data)) {
            data = resp?.result
        }
        if (!hasPlayableStreams(data)) {
            data = alt?.result?.videoInfo
        }
        return if (hasPlayableStreams(data)) data else null
    }

    private fun hasPlayableStreams(data: PlayUrlData?): Boolean {
        return data != null && (data.dash != null || !data.durls.isNullOrEmpty() || !data.durl.isNullOrEmpty())
    }

    private suspend fun ensureVideoCid(item: MediaItem): MediaItem {
        if (item.cid != null) return item
        val params = buildMap<String, String> {
            item.aid?.let { put("aid", it.toString()) }
            if (isEmpty()) {
                item.bvid?.let { put("bvid", it) }
            }
        }
        if (params.isEmpty()) return item
        val body = httpClient.get(buildUrl("https://api.bilibili.com/x/player/pagelist", params))
        val adapter = httpClient.adapter(VideoPageListResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty pagelist response", -1)
        if (resp.code != 0 || resp.data.isNullOrEmpty()) {
            throw BiliHttpException(resp.message ?: "Pagelist error", resp.code)
        }
        val cid = resp.data.firstOrNull()?.cid
            ?: throw BiliHttpException("No cid found", -1)
        return item.copy(cid = cid)
    }

    suspend fun getVideoPages(item: MediaItem): List<MediaItem> {
        val params = buildMap<String, String> {
            item.aid?.let { put("aid", it.toString()) }
            if (isEmpty()) {
                item.bvid?.let { put("bvid", it) }
            }
        }
        if (params.isEmpty()) return listOf(item)
        val body = httpClient.get(buildUrl("https://api.bilibili.com/x/player/pagelist", params))
        val adapter = httpClient.adapter(VideoPageListResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty pagelist response", -1)
        if (resp.code != 0 || resp.data.isNullOrEmpty()) {
            throw BiliHttpException(resp.message ?: "Pagelist error", resp.code)
        }
        return resp.data.mapIndexed { index, page ->
            val pageIndex = (page.page - 1).coerceAtLeast(0)
            val title = page.part?.takeIf { it.isNotBlank() } ?: item.title
            item.copy(
                title = title,
                cid = page.cid,
                duration = page.duration,
                pubTime = page.ctime ?: item.pubTime,
                isTarget = index == 0,
                index = pageIndex,
                page = page.page,
                pageCount = resp.data.size,
                workTitle = item.workTitle ?: item.title,
            )
        }
    }

    private suspend fun fetchMusicPlayUrl(item: MediaItem): PlayUrlInfo {
        return runCatching { fetchMusicPlayUrlViaAppApi(item) }
            .getOrElse { fetchMusicPlayUrlViaWebApi(item) }
    }

    private suspend fun fetchMusicPlayUrlViaAppApi(item: MediaItem): PlayUrlInfo {
        val sid = item.sid ?: throw BiliHttpException("Missing sid", -1)
        val mid = cookieStore.getCookie("DedeUserID")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        var initial: MusicPlayUrlData? = null
        for (quality in listOf(3, 2, 1, 0)) {
            val data = runCatching { requestMusicPlayUrlApi(sid, quality, mid) }.getOrNull()
            if (data != null) {
                initial = data
                break
            }
        }
        initial = initial ?: throw BiliHttpException("Empty music playurl", -1)
        val qualityInfoByType = initial.qualities.orEmpty()
            .mapNotNull { info -> info.type?.let { type -> type to info } }
            .toMap()
        val requestedTypes = initial.qualities.orEmpty()
            .mapNotNull { it.type }
            .ifEmpty {
                listOfNotNull(initial.type.takeIf { it >= 0 })
            }
            .distinct()
            .sortedDescending()
        val streams = LinkedHashMap<Int, AudioStream>()
        for (qualityType in requestedTypes) {
            val data = if (initial.type == qualityType) {
                initial
            } else {
                runCatching { requestMusicPlayUrlApi(sid, qualityType, mid) }.getOrNull()
                    ?: continue
            }
            if (data.type != qualityType && data.type != -1) {
                continue
            }
            val stream = buildMusicAudioStream(
                data = data,
                bandwidthHint = qualityInfoByType[qualityType]?.bps?.let(::parseMusicQualityBps),
            ) ?: continue
            streams.putIfAbsent(stream.id, stream)
        }
        if (streams.isEmpty()) {
            buildMusicAudioStream(initial)?.let { stream ->
                streams[stream.id] = stream
            }
        }
        if (streams.isEmpty()) {
            throw BiliHttpException("No playable stream", -1)
        }
        return PlayUrlInfo(
            format = StreamFormat.Dash,
            video = emptyList(),
            audio = AudioQualities.sortDescending(streams.keys).mapNotNull { streams[it] },
            acceptQuality = emptyList(),
            acceptDescription = emptyList(),
        )
    }

    private suspend fun fetchMusicPlayUrlViaWebApi(item: MediaItem): PlayUrlInfo {
        val sid = item.sid ?: throw BiliHttpException("Missing sid", -1)
        val params = mapOf(
            "sid" to sid.toString(),
            "privilege" to "2",
            "quality" to "0",
        )
        val baseUrl = "https://www.bilibili.com/audio/music-service-c/web/url"
        val body = httpClient.get(wbiSigner.signedUrl(baseUrl, params))
        val adapter = httpClient.adapter(MusicPlayUrlResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty music playurl", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.msg ?: "Music playurl error", resp.code)
        }
        val audio = buildMusicAudioStream(resp.data)?.let(::listOf)
            ?: throw BiliHttpException("No playable stream", -1)
        return PlayUrlInfo(
            format = StreamFormat.Dash,
            video = emptyList(),
            audio = audio,
            acceptQuality = emptyList(),
            acceptDescription = emptyList(),
        )
    }

    private suspend fun requestMusicPlayUrlApi(
        sid: Long,
        quality: Int,
        mid: Long,
    ): MusicPlayUrlData {
        val url = buildUrl(
            "https://api.bilibili.com/audio/music-service-c/url",
            mapOf(
                "songid" to sid.toString(),
                "quality" to quality.toString(),
                "privilege" to "2",
                "mid" to mid.toString(),
                "platform" to "android",
            ),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(MusicPlayUrlResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty music playurl", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.msg ?: "Music playurl error", resp.code)
        }
        return resp.data
    }

    private fun buildMusicAudioStream(
        data: MusicPlayUrlData,
        bandwidthHint: Long? = null,
    ): AudioStream? {
        val streamId = AudioQualities.musicApiTypeToStreamId(data.type) ?: return null
        val urls = data.cdns.filter { it.isNotBlank() }
        if (urls.isEmpty()) return null
        val fallbackBandwidth = (AudioQualities.allIds.indexOf(streamId) + 1).toLong()
        return AudioStream(
            id = streamId,
            bandwidth = bandwidthHint ?: fallbackBandwidth,
            url = urls.first(),
            backupUrls = urls,
        )
    }

    private fun parseMusicQualityBps(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val number = Regex("(\\d+)").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
        return when {
            value.contains("mbit", ignoreCase = true) -> number * 1_000_000L
            value.contains("kbit", ignoreCase = true) -> number * 1_000L
            else -> number
        }
    }

    /**
     * 用接口给出的总条数和本次请求的页容量换算总页数（向上取整）。
     *
     * 页容量是请求参数 `ps` / `page_size`，即服务端切分页窗口的宽度，不是「这一页列表一定有这么多条」。
     * 收藏夹尤其明显：`info.media_count` 按窗口切页，窗口内失效、无权限等稿件仍占名额，但不会出现在 `medias` 里，
     * 所以中间页实际条数可以小于 `ps`。总页数必须用总数 ÷ 请求页容量，不能用当前页返回条数去除。
     */
    private fun totalPagesFromItemCount(itemCount: Int?, pageSize: Int): Int? {
        if (itemCount == null || pageSize <= 0) return null
        return (itemCount + pageSize - 1) / pageSize
    }

    private fun buildUrl(base: String, params: Map<String, String>): HttpUrl {
        val builder = base.toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value.isNotBlank()) {
                builder.addQueryParameter(key, value)
            }
        }
        return builder.build()
    }

    private fun normalizeCoverUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            else -> url
        }
    }

    private fun createMediaUpper(name: String, mid: Long, avatar: String?): MediaUpper {
        val normalizedAvatar = avatar
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeCoverUrl)
        return MediaUpper(name = name, mid = mid, avatar = normalizedAvatar)
    }

    private fun mapFavoriteType(raw: Int): MediaType {
        return when (raw) {
            2 -> MediaType.Video
            12 -> MediaType.Music
            24 -> MediaType.Bangumi
            else -> MediaType.Video
        }
    }

    private fun OpusListArticleStats.toMediaStat(): MediaStat = MediaStat(
        play = view,
        reply = reply,
        like = like,
        coin = coin,
        favorite = favorite,
        share = share,
    )

    companion object {
        private val URL_CANDIDATE_REGEX =
            Regex("^(?:https?://)?(?:[\\w-]+\\.)*(?:bilibili\\.com|b23\\.tv)/.+$", RegexOption.IGNORE_CASE)
        private val URL_SAFE_REGEX =
            Regex("^[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$")
        private val DOMAIN_ONLY_REGEX =
            Regex("\\.(bilibili\\.com|b23\\.tv)$", RegexOption.IGNORE_CASE)

    }
}

private data class VideoViewResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: VideoViewData?,
)

private data class VideoPageListResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: List<VideoPageInfo>?,
)

private data class VideoViewData(
    @Json(name = "bvid") val bvid: String,
    @Json(name = "aid") val aid: Long,
    @Json(name = "title") val title: String,
    @Json(name = "desc") val desc: String,
    @Json(name = "pic") val pic: String,
    @Json(name = "cid") val cid: Long,
    @Json(name = "duration") val duration: Int,
    @Json(name = "pubdate") val pubdate: Long,
    @Json(name = "owner") val owner: VideoOwner?,
    @Json(name = "pages") val pages: List<VideoPageInfo>?,
    @Json(name = "ugc_season") val ugcSeason: UgcSeasonInfo?,
    @Json(name = "rights") val rights: VideoRights?,
    @Json(name = "stat") val stat: VideoStat?,
)

private data class VideoOwner(
    @Json(name = "name") val name: String,
    @Json(name = "mid") val mid: Long,
    @Json(name = "face") val face: String,
)

private data class VideoRights(
    @Json(name = "is_stein_gate") val isSteinGate: Int,
)

private data class VideoStat(
    @Json(name = "view") val view: Int,
    @Json(name = "danmaku") val danmaku: Int,
    @Json(name = "reply") val reply: Int,
    @Json(name = "favorite") val favorite: Int,
    @Json(name = "coin") val coin: Int,
    @Json(name = "share") val share: Int,
    @Json(name = "like") val like: Int,
)

private data class VideoPageInfo(
    @Json(name = "cid") val cid: Long,
    @Json(name = "page") val page: Int,
    @Json(name = "part") val part: String?,
    @Json(name = "duration") val duration: Int,
    @Json(name = "ctime") val ctime: Long?,
)

private data class UgcSeasonInfo(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "cover") val cover: String,
    @Json(name = "intro") val intro: String,
    @Json(name = "sections") val sections: List<UgcSectionInfo>,
)

private data class UgcSectionInfo(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "episodes") val episodes: List<UgcEpisodeInfo>,
)

private data class UgcEpisodeInfo(
    @Json(name = "section_id") val sectionId: Long,
    @Json(name = "id") val id: Long,
    @Json(name = "aid") val aid: Long,
    @Json(name = "cid") val cid: Long,
    @Json(name = "title") val title: String,
    @Json(name = "arc") val arc: UgcEpisodeArc,
    @Json(name = "page") val page: UgcEpisodePage,
    @Json(name = "pages") val pages: List<UgcEpisodePage>,
    @Json(name = "bvid") val bvid: String,
)

private data class UgcEpisodeArc(
    @Json(name = "pic") val pic: String,
    @Json(name = "desc") val desc: String,
    @Json(name = "pubdate") val pubdate: Long,
)

private data class UgcEpisodePage(
    @Json(name = "cid") val cid: Long,
    @Json(name = "page") val page: Int? = null,
    @Json(name = "part") val part: String?,
    @Json(name = "duration") val duration: Int,
)

private data class VideoTagsResponse(
    @Json(name = "data") val data: List<VideoTagItem>?,
)

private data class VideoTagItem(
    @Json(name = "tag_name") val tagName: String,
)

private data class BangumiMediaResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "result") val result: BangumiMediaResult?,
)

private data class BangumiMediaResult(
    @Json(name = "media") val media: BangumiMediaSeason?,
)

private data class BangumiMediaSeason(
    @Json(name = "season_id") val seasonId: Long,
)

private data class BangumiResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "result") val result: BangumiResult?,
)

private data class BangumiResult(
    @Json(name = "cover") val cover: String,
    @Json(name = "square_cover") val squareCover: String?,
    @Json(name = "episodes") val episodes: List<BangumiEpisode>,
    @Json(name = "evaluate") val evaluate: String,
    @Json(name = "positive") val positive: BangumiPositive,
    @Json(name = "season_id") val seasonId: Long,
    @Json(name = "season_title") val seasonTitle: String,
    @Json(name = "seasons") val seasons: List<BangumiSeasonInfo>,
    @Json(name = "section") val section: List<BangumiSection>?,
    @Json(name = "share_url") val shareUrl: String,
    @Json(name = "staff") val staff: String,
    @Json(name = "actors") val actors: String,
    @Json(name = "stat") val stat: BangumiStat,
    @Json(name = "styles") val styles: List<String>,
    @Json(name = "up_info") val upInfo: BangumiUpInfo?,
)

private data class BangumiPositive(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
)

private data class BangumiSeasonInfo(
    @Json(name = "season_id") val seasonId: Long,
    @Json(name = "cover") val cover: String,
    @Json(name = "horizontal_cover_1610") val horizontalCover1610: String?,
    @Json(name = "horizontal_cover_169") val horizontalCover169: String?,
)

private data class BangumiSection(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "episodes") val episodes: List<BangumiEpisode>,
)

private data class BangumiEpisode(
    @Json(name = "aid") val aid: Long? = null,
    @Json(name = "bvid") val bvid: String? = null,
    @Json(name = "cid") val cid: Long? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "ep_id") val epId: Long? = null,
    @Json(name = "id") val id: Long? = null,
    @Json(name = "pub_time") val pubTime: Long? = null,
    @Json(name = "share_url") val shareUrl: String? = null,
    @Json(name = "show_title") val showTitle: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "long_title") val longTitle: String? = null,
)

private data class BangumiStat(
    @Json(name = "coins") val coins: Int,
    @Json(name = "danmakus") val danmakus: Int,
    @Json(name = "favorite") val favorite: Int,
    @Json(name = "likes") val likes: Int,
    @Json(name = "reply") val reply: Int,
    @Json(name = "share") val share: Int,
    @Json(name = "views") val views: Int,
)

private data class BangumiUpInfo(
    @Json(name = "avatar") val avatar: String,
    @Json(name = "mid") val mid: Long,
    @Json(name = "uname") val uname: String,
)

private data class LessonResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: LessonData?,
)

private data class LessonData(
    @Json(name = "brief") val brief: LessonBrief?,
    @Json(name = "cover") val cover: String,
    @Json(name = "episodes") val episodes: List<LessonEpisode>,
    @Json(name = "faq") val faq: LessonFaq?,
    @Json(name = "season_id") val seasonId: Long,
    @Json(name = "share_url") val shareUrl: String,
    @Json(name = "stat") val stat: LessonStat,
    @Json(name = "subtitle") val subtitle: String,
    @Json(name = "title") val title: String,
    @Json(name = "up_info") val upInfo: LessonUpInfo?,
)

private data class LessonBrief(
    @Json(name = "img") val img: List<LessonImage>,
)

private data class LessonImage(
    @Json(name = "url") val url: String,
)

private data class LessonEpisode(
    @Json(name = "aid") val aid: Long,
    @Json(name = "cid") val cid: Long,
    @Json(name = "cover") val cover: String,
    @Json(name = "duration") val duration: Int,
    @Json(name = "id") val id: Long,
    @Json(name = "index") val index: Int? = null,
    @Json(name = "release_date") val releaseDate: Long,
    @Json(name = "title") val title: String,
)

private data class LessonFaq(
    @Json(name = "content") val content: String,
    @Json(name = "title") val title: String,
)

private data class LessonStat(
    @Json(name = "play") val play: Int,
)

private data class LessonUpInfo(
    @Json(name = "avatar") val avatar: String,
    @Json(name = "mid") val mid: Long,
    @Json(name = "uname") val uname: String,
)

private data class MusicInfoResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String?,
    @Json(name = "data") val data: MusicInfoData?,
)

private data class MusicInfoData(
    @Json(name = "id") val id: Long,
    @Json(name = "uid") val uid: Long,
    @Json(name = "uname") val uname: String,
    @Json(name = "author") val author: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "cover") val cover: String,
    @Json(name = "intro") val intro: String,
    @Json(name = "duration") val duration: Int,
    @Json(name = "passtime") val passtime: Long,
    @Json(name = "aid") val aid: Long,
    @Json(name = "bvid") val bvid: String,
    @Json(name = "cid") val cid: Long,
    @Json(name = "statistic") val statistic: MusicStat,
)

private data class MusicStat(
    @Json(name = "play") val play: Int,
    @Json(name = "collect") val collect: Int,
    @Json(name = "comment") val comment: Int,
    @Json(name = "share") val share: Int,
)

private data class MusicTagsResponse(
    @Json(name = "data") val data: List<MusicTag>?,
)

private data class MusicTag(
    @Json(name = "info") val info: String,
)

private data class MusicUpperResponse(
    @Json(name = "data") val data: MusicUpperData?,
)

private data class MusicUpperData(
    @Json(name = "uid") val uid: Long,
    @Json(name = "uname") val uname: String,
    // 当前接口使用拼写错误的 avater；同时兼容部分历史响应中的 avatar。
    @Json(name = "avater") val avater: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
)

private data class MusicListResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String?,
    @Json(name = "data") val data: MusicListData?,
)

private data class MusicListData(
    @Json(name = "menuId") val menuId: Long,
    @Json(name = "uid") val uid: Long,
    @Json(name = "uname") val uname: String,
    @Json(name = "title") val title: String,
    @Json(name = "cover") val cover: String,
    @Json(name = "intro") val intro: String,
    @Json(name = "ctime") val ctime: Long,
    @Json(name = "statistic") val statistic: MusicStat,
)

private data class MusicListDetailResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String?,
    @Json(name = "data") val data: MusicListDetailData?,
)

private data class MusicListDetailData(
    @Json(name = "pageCount") val pageCount: Int? = null,
    @Json(name = "data") val data: List<MusicInfoData>,
)

private data class WatchLaterResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: WatchLaterData?,
)

private data class WatchLaterData(
    @Json(name = "count") val count: Int? = null,
    @Json(name = "list") val list: List<WatchLaterItem>,
)

private data class WatchLaterItem(
    @Json(name = "title") val title: String,
    @Json(name = "pic") val pic: String,
    @Json(name = "desc") val desc: String,
    @Json(name = "bvid") val bvid: String,
    @Json(name = "aid") val aid: Long,
    @Json(name = "duration") val duration: Int,
    @Json(name = "pubdate") val pubdate: Long,
    @Json(name = "owner") val owner: VideoOwner? = null,
)

private data class FavoriteListResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: FavoriteListData?,
)

private data class FavoriteListData(
    @Json(name = "list") val list: List<FavoriteFolder>,
)

private data class FavoriteFolder(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
)

private data class FavoriteResourceResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: FavoriteResourceData?,
)

private data class FavoriteResourceData(
    @Json(name = "info") val info: FavoriteInfo,
    // 当前窗口内能展示的稿件；失效/无权限等仍占分页名额，所以条数可以小于请求的 ps
    @Json(name = "medias") val medias: List<FavoriteMedia>,
    @Json(name = "has_more") val hasMore: Boolean? = null,
)

private data class FavoriteInfo(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "cover") val cover: String,
    @Json(name = "intro") val intro: String,
    @Json(name = "upper") val upper: FavoriteUpper?,
    @Json(name = "cnt_info") val cntInfo: FavoriteCntInfo,
    @Json(name = "ctime") val ctime: Long,
    // 收藏夹内容总数（含 medias 里被过滤掉的条目），总页数按它 ÷ 请求 ps 向上取整
    @Json(name = "media_count") val mediaCount: Int? = null,
)

private data class FavoriteUpper(
    @Json(name = "mid") val mid: Long,
    @Json(name = "name") val name: String,
    @Json(name = "face") val face: String,
)

private data class FavoriteCntInfo(
    @Json(name = "collect") val collect: Int,
    @Json(name = "play") val play: Int,
    @Json(name = "thumb_up") val thumbUp: Int,
    @Json(name = "share") val share: Int,
)

private data class FavoriteMedia(
    @Json(name = "id") val id: Long,
    @Json(name = "bvid") val bvid: String,
    @Json(name = "type") val type: Int,
    @Json(name = "title") val title: String,
    @Json(name = "cover") val cover: String,
    @Json(name = "intro") val intro: String? = null,
    @Json(name = "cnt_info") val cntInfo: FavoriteMediaCntInfo? = null,
    @Json(name = "duration") val duration: Int,
    @Json(name = "pubtime") val pubtime: Long,
    @Json(name = "upper") val upper: FavoriteUpper? = null,
)

private data class FavoriteMediaCntInfo(
    @Json(name = "collect") val collect: Long? = null,
    @Json(name = "play") val play: Long? = null,
    @Json(name = "danmaku") val danmaku: Long? = null,
    @Json(name = "thumb_up") val thumbUp: Long? = null,
    @Json(name = "share") val share: Long? = null,
    @Json(name = "reply") val reply: Long? = null,
    @Json(name = "coin") val coin: Long? = null,
    @Json(name = "like") val like: Long? = null,
)

private data class OpusListResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: OpusListData?,
)

private data class OpusListData(
    @Json(name = "list") val list: OpusListInfo?,
    @Json(name = "articles") val articles: List<OpusListArticle>?,
    @Json(name = "author") val author: OpusListAuthor?,
)

private data class OpusListInfo(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "image_url") val imageUrl: String?,
    @Json(name = "summary") val summary: String,
    @Json(name = "read") val read: Int?,
)

private data class OpusListArticle(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "publish_time") val publishTime: Long,
    @Json(name = "image_urls") val imageUrls: List<String>,
    @Json(name = "summary") val summary: String,
    @Json(name = "dyn_id_str") val dynId: String? = null,
    @Json(name = "categories") val categories: List<OpusListCategory>?,
    @Json(name = "stats") val stats: OpusListArticleStats? = null,
)

private data class OpusListArticleStats(
    @Json(name = "view") val view: Long? = null,
    @Json(name = "favorite") val favorite: Long? = null,
    @Json(name = "like") val like: Long? = null,
    @Json(name = "reply") val reply: Long? = null,
    @Json(name = "share") val share: Long? = null,
    @Json(name = "coin") val coin: Long? = null,
)

private data class OpusListCategory(
    @Json(name = "name") val name: String?,
)

private data class OpusListAuthor(
    @Json(name = "mid") val mid: Long,
    @Json(name = "name") val name: String,
    @Json(name = "face") val face: String?,
)

private data class UploadsSeriesResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UploadsSeriesData?,
)

private data class UploadsSeriesData(
    @Json(name = "items_lists") val itemsLists: UploadsSeriesItems?,
)

private data class UploadsSeriesItems(
    @Json(name = "seasons_list") val seasonsList: List<UploadsSeriesItem>?,
    @Json(name = "series_list") val seriesList: List<UploadsSeriesItem>?,
)

private data class UploadsSeriesItem(
    @Json(name = "archives") val archives: List<UploadsArchive>?,
    @Json(name = "meta") val meta: UploadsMeta?,
)

// 合集/系列分支解析结果：条目列表、元数据、分节 tab 与列表内总条目数
private data class UploadsListSelection(
    val archives: List<UploadsArchive>,
    val meta: UploadsMeta?,
    val sections: MediaSections,
    val totalItems: Int?,
)

private data class UploadsArchivesResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UploadsArchivesData?,
)

private data class UploadsArchivesData(
    @Json(name = "archives") val archives: List<UploadsArchive>?,
    @Json(name = "page") val page: UploadsArchivesPage? = null,
)

// seasons_archives_list 与 series/archives 的 page 字段名不同，但 total 一致
private data class UploadsArchivesPage(
    @Json(name = "total") val total: Int? = null,
)

private data class UploadsArchive(
    @Json(name = "aid") val aid: Long,
    @Json(name = "bvid") val bvid: String,
    @Json(name = "duration") val duration: Int,
    @Json(name = "pic") val pic: String,
    @Json(name = "pubdate") val pubdate: Long,
    @Json(name = "title") val title: String,
)

private data class UploadsMeta(
    @Json(name = "cover") val cover: String,
    @Json(name = "description") val description: String,
    @Json(name = "mid") val mid: Long,
    @Json(name = "name") val name: String,
    @Json(name = "ptime") val ptime: Long,
    @Json(name = "season_id") val seasonId: Long,
    @Json(name = "series_id") val seriesId: Long,
)

private data class UploadsSearchResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UploadsSearchData?,
)

private data class UploadsSearchData(
    @Json(name = "list") val list: UploadsSearchList?,
    @Json(name = "page") val page: UploadsSearchPage? = null,
)

private data class UploadsSearchPage(
    @Json(name = "count") val count: Int? = null,
    @Json(name = "ps") val ps: Int? = null,
)

private data class UploadsSearchList(
    @Json(name = "vlist") val vlist: List<UploadsVlistItem>?,
)

private data class UploadsVlistItem(
    @Json(name = "aid") val aid: Long,
    @Json(name = "bvid") val bvid: String,
    @Json(name = "pic") val pic: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "length") val length: String,
    @Json(name = "created") val created: Long,
)

private data class UserOpusResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UserOpusData?,
)

private data class UserOpusData(
    @Json(name = "items") val items: List<UserOpusItem>,
    @Json(name = "offset") val offset: String?,
    @Json(name = "has_more") val hasMore: Boolean? = null,
)

private data class UserOpusItem(
    @Json(name = "content") val content: String,
    @Json(name = "cover") val cover: UserOpusCover?,
    @Json(name = "opus_id") val opusId: String,
    @Json(name = "jump_url") val jumpUrl: String? = null,
    @Json(name = "stat") val stat: UserOpusStat? = null,
)

private data class UserOpusCover(
    @Json(name = "url") val url: String?,
)

private data class UserOpusStat(
    @Json(name = "like") val like: String? = null,
    @Json(name = "view") val view: String? = null,
)

private data class UserAudioResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UserAudioData?,
)

private data class UserAudioData(
    @Json(name = "pageCount") val pageCount: Int? = null,
    @Json(name = "data") val data: List<MusicInfoData>?,
)

private data class SpaceUserInfoResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: SpaceUserInfoData?,
)

private data class SpaceUserInfoData(
    @Json(name = "mid") val mid: Long?,
    @Json(name = "name") val name: String?,
    @Json(name = "face") val face: String?,
)

private data class MusicPlayUrlResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String?,
    @Json(name = "data") val data: MusicPlayUrlData?,
)

private data class MusicPlayUrlData(
    @Json(name = "type") val type: Int,
    @Json(name = "cdns") val cdns: List<String>,
    @Json(name = "qualities") val qualities: List<MusicPlayUrlQualityInfo>?,
)

private data class MusicPlayUrlQualityInfo(
    @Json(name = "type") val type: Int?,
    @Json(name = "bps") val bps: String?,
)

private data class PlayUrlResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: PlayUrlData?,
    @Json(name = "result") val result: PlayUrlData?,
)

private data class PlayUrlVideoInfoResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "result") val result: PlayUrlVideoInfoResult?,
)

private data class PlayUrlVideoInfoResult(
    @Json(name = "video_info") val videoInfo: PlayUrlData?,
)

private data class PlayUrlData(
    @Json(name = "quality") val quality: Int?,
    @Json(name = "format") val format: String?,
    @Json(name = "accept_quality") val acceptQuality: List<Int>?,
    @Json(name = "accept_description") val acceptDescription: List<String>?,
    @Json(name = "accept_format") val acceptFormat: String?,
    @Json(name = "durls") val durls: List<DurlGroup>?,
    @Json(name = "durl") val durl: List<Durl>?,
    @Json(name = "dash") val dash: Dash?,
)

private data class DurlGroup(
    @Json(name = "quality") val quality: Int,
    @Json(name = "durl") val durl: List<Durl>?,
)

private data class Durl(
    @Json(name = "url") val url: String,
    @Json(name = "backup_url") val backupUrl: List<String>?,
    @Json(name = "size") val size: Long?,
)

private data class Dash(
    @Json(name = "video") val video: List<DashItem>?,
    @Json(name = "audio") val audio: List<DashItem>?,
    @Json(name = "dolby") val dolby: DashDolby?,
    @Json(name = "flac") val flac: DashFlac?,
)

private data class DashItem(
    @Json(name = "id") val id: Int,
    @Json(name = "baseUrl") val baseUrl: String?,
    @Json(name = "base_url") val baseUrlAlt: String?,
    @Json(name = "backupUrl") val backupUrl: List<String>?,
    @Json(name = "backup_url") val backupUrlAlt: List<String>?,
    @Json(name = "bandwidth") val bandwidth: Long?,
    @Json(name = "codecid") val codecid: Int?,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?,
    @Json(name = "frameRate") val frameRate: String?,
)

private data class DashDolby(
    @Json(name = "audio") val audio: List<DashItem>?,
)

private data class DashFlac(
    @Json(name = "audio") val audio: DashItem?,
)

private fun DashItem.toVideoStream(format: StreamFormat): VideoStream? {
    val url = baseUrl ?: baseUrlAlt ?: return null
    return VideoStream(
        id = id,
        format = format,
        width = width,
        height = height,
        bandwidth = bandwidth,
        frameRate = frameRate,
        codec = when (codecid) {
            7 -> VideoCodec.Avc
            12 -> VideoCodec.Hevc
            13 -> VideoCodec.Av1
            else -> null
        },
        url = url,
        backupUrls = backupUrl ?: backupUrlAlt.orEmpty(),
    )
}

private fun DashItem.toAudioStream(): AudioStream? {
    val url = baseUrl ?: baseUrlAlt ?: return null
    return AudioStream(
        id = id,
        bandwidth = bandwidth,
        url = url,
        backupUrls = backupUrl ?: backupUrlAlt.orEmpty(),
    )
}

private fun Durl.toVideoStream(format: StreamFormat, quality: Int): VideoStream {
    return VideoStream(
        id = quality,
        format = format,
        url = url,
        backupUrls = backupUrl.orEmpty(),
        size = size,
    )
}

private fun DurlGroup.toVideoStream(format: StreamFormat): VideoStream? {
    val primary = durl?.firstOrNull() ?: return null
    return VideoStream(
        id = quality,
        format = format,
        url = primary.url,
        backupUrls = primary.backupUrl.orEmpty(),
        size = primary.size,
    )
}
