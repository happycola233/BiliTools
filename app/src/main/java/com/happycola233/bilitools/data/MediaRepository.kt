package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.CookieStore
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
) {
    suspend fun parseInput(input: String, allowRaw: Boolean): ParsedInput {
        val raw = input.trim()
        if (raw.isBlank()) {
            throw IllegalArgumentException("Invalid input")
        }
        if (DIRECT_ID_REGEX.matches(raw)) {
            val prefix = raw.substring(0, 2).lowercase()
            val type = when (prefix) {
                "av", "bv" -> MediaType.Video
                "ep", "ss", "md" -> MediaType.Bangumi
                "au" -> MediaType.Music
                "am" -> MediaType.MusicList
                "cv" -> MediaType.Opus
                "rl" -> MediaType.OpusList
                else -> null
            }
            return ParsedInput(raw, type)
        }

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
            throw IllegalArgumentException("Invalid input")
        }

        val host = url.host.lowercase()
        if (!host.endsWith("bilibili.com") && host != "b23.tv") {
            throw IllegalArgumentException("Invalid input")
        }

        if (host == "b23.tv") {
            val resolved = httpClient.resolveUrl(url)
            return parseInput(resolved, allowRaw)
        }

        val segments = url.pathSegments.filter { it.isNotBlank() }

        if (host == "space.bilibili.com") {
            val mid = segments.getOrNull(0) ?: throw IllegalArgumentException("Invalid input")
            val type = segments.getOrNull(1)
            if (type == "favlist") {
                val fid = url.queryParameter("fid")?.toLongOrNull()
                return ParsedInput(mid, MediaType.Favorite, fid)
            }
            if (segments.getOrNull(2) == "video" || type == "lists" || segments.size == 1) {
                val listId = Regex("/lists/(\\d+)").find(url.encodedPath)?.groupValues?.getOrNull(1)
                return ParsedInput(mid, MediaType.UserVideo, listId?.toLongOrNull())
            }
            if (segments.getOrNull(2) == "opus" || type == "article") {
                return ParsedInput(mid, MediaType.UserOpus)
            }
            if (segments.getOrNull(2) == "audio" || type == "audio") {
                return ParsedInput(mid, MediaType.UserAudio)
            }
            throw IllegalArgumentException("Invalid input")
        }

        val root = segments.getOrNull(0)
        val second = segments.getOrNull(1)
        if (!second.isNullOrBlank()) {
            if (BV_REGEX.matches(second) || AV_REGEX.matches(second)) {
                return ParsedInput(second, MediaType.Video)
            }
            if (AU_REGEX.matches(second)) {
                return ParsedInput(second, MediaType.Music)
            }
            if (AM_REGEX.matches(second)) {
                return ParsedInput(second, MediaType.MusicList)
            }
        }

        if (!second.isNullOrBlank()) {
            if (CV_REGEX.matches(second) || root == "opus") {
                return ParsedInput(second, MediaType.Opus)
            }
        }

        if (root == "watchlater") {
            return ParsedInput("", MediaType.WatchLater)
        }

        val third = segments.getOrNull(2)
        if (!third.isNullOrBlank() && (EP_REGEX.matches(third) || SS_REGEX.matches(third) || MD_REGEX.matches(third))) {
            return when (root) {
                "bangumi" -> ParsedInput(third, MediaType.Bangumi)
                "cheese" -> ParsedInput(third, MediaType.Lesson)
                else -> throw IllegalArgumentException("Invalid input")
            }
        }

        if (!third.isNullOrBlank() && RL_REGEX.matches(third)) {
            return ParsedInput(third, MediaType.OpusList)
        }

        if (second == "watchlater") {
            val id = url.queryParameter("aid")
                ?: url.queryParameter("oid")
                ?: url.queryParameter("bvid")
            if (id != null) {
                return ParsedInput(id, MediaType.Video)
            }
        }

        if (allowRaw) {
            return ParsedInput(raw)
        }
        throw IllegalArgumentException("Invalid input")
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
            else -> throw IllegalArgumentException("Unsupported media type: $type")
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
            ),
        )

        var list = baseList
        var sections: MediaSections? = null

        val ugcSeason = data.ugcSeason
        val hasCollection = ugcSeason != null
        if (ugcSeason != null && ugcSeason.sections.isNotEmpty()) {
            val allEpisodes = ugcSeason.sections.flatMap { it.episodes }
            val targetEpisodeId = options.target?.takeIf { t -> allEpisodes.any { it.id == t } }
            val mapEpisode: (Int, UgcEpisodeInfo) -> MediaItem = { index, ep ->
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
                )
            }
            val targetEpisode = allEpisodes.firstOrNull { ep ->
                options.target?.let { t -> ep.id == t } ?: (ep.aid == data.aid)
            }
            if (targetEpisode != null) {
                if (options.collection) {
                    list = allEpisodes.mapIndexed(mapEpisode)
                    sections = null
                } else if ((data.pages?.size ?: 0) > 1) {
                    val section = ugcSeason.sections.firstOrNull { it.id == targetEpisode.sectionId }
                    if (section != null) {
                        list = targetEpisode.pages.mapIndexed { index, page ->
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
                            )
                        }
                        val targetId =
                            options.target?.takeIf { t -> section.episodes.any { it.id == t } }
                                ?: targetEpisode.id
                        sections = MediaSections(
                            target = targetId,
                            tabs = section.episodes.map { MediaTab(it.id, it.title) },
                        )
                    }
                } else {
                    // Default to current video info for single-page videos in a collection.
                    // Collection list is only shown when collection mode is enabled.
                }
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
                upper = data.owner?.let { MediaUpper(it.name, it.mid, it.face) },
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
            )
        }
        val tabs = buildList {
            add(MediaTab(data.positive.id, data.positive.title))
            data.section?.forEach { add(MediaTab(it.id, it.title)) }
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
                upper = data.upInfo?.let { MediaUpper(it.uname, it.mid, it.avatar) },
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
                upper = data.upInfo?.let { MediaUpper(it.uname, it.mid, it.avatar) },
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
                upper = upper?.let {
                    MediaUpper(it.uname, it.uid, it.avater.takeIf { v -> v.isNotBlank() } ?: it.avatar)
                },
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
                isTarget = index == 0,
                index = index,
            )
        }
        return MediaInfo(
            type = MediaType.MusicList,
            id = id,
            paged = true,
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
                upper = MediaUpper(data.uname, data.uid, null),
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
                isTarget = index == 0,
                index = baseIndex + index,
            )
        }
        return MediaInfo(
            type = MediaType.WatchLater,
            id = "",
            paged = true,
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

        val target = options.target ?: folderList?.firstOrNull()?.id?.toLong()
        val targetId = target ?: idNum.toLongOrNull()
            ?: throw BiliHttpException("No favorite id provided", -1)
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
                MediaStat(
                    play = cnt.play,
                    danmaku = cnt.danmaku,
                    reply = cnt.reply,
                    like = cnt.thumbUp ?: cnt.like,
                    coin = cnt.coin,
                    favorite = cnt.collect,
                    share = cnt.share,
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
                isTarget = index == 0,
                index = baseIndex + index,
                fid = data.info.id,
            )
        }
        val resolvedInfo = data.info
        val sections = folderList?.let { folders ->
            MediaSections(
                target = targetId,
                tabs = folders.map { MediaTab(it.id.toLong(), it.title) },
            )
        }
        return MediaInfo(
            type = MediaType.Favorite,
            id = id,
            paged = true,
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
                upper = resolvedInfo.upper?.let { MediaUpper(it.name, it.mid, it.face) },
            ),
            list = list,
            sections = sections,
        )
    }

    private suspend fun fetchOpusInfo(id: String): MediaInfo {
        val idType = id.take(2).lowercase()
        val previewId = if (idType == "cv") {
            val resolved = httpClient.resolveUrl("https://www.bilibili.com/read/$id".toHttpUrl())
            OPUS_ID_REGEX.find(resolved)?.groupValues?.getOrNull(1)
                ?: throw BiliHttpException("Opus id not found", -1)
        } else {
            id
        }
        val body = httpClient.get(
            buildUrl(
                "https://api.bilibili.com/x/polymer/web-dynamic/v1/forward/preview",
                mapOf("id" to previewId),
            ),
        )
        val adapter = httpClient.adapter(OpusPreviewResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty opus response", -1)
        val item = resp.data?.item
        if (resp.code != 0 || item == null) {
            throw BiliHttpException(resp.message ?: "Opus error", resp.code)
        }
        val details = runCatching { fetchOpusDetails(id) }.getOrNull()
        val titleFromCard =
            item.commonCard.nodes.orEmpty().firstOrNull {
                it.type == "RICH_TEXT_NODE_TYPE_TEXT"
            }?.text
        val title = titleFromCard?.takeIf { it.isNotBlank() }
            ?: details?.title?.takeIf { it.isNotBlank() }
            ?: throw BiliHttpException("Opus title missing", -1)
        val upper = fetchUserUpper(item.user.mid)
            ?: MediaUpper(item.user.name.orEmpty(), item.user.mid, null)
        val opusUrl = "https://www.bilibili.com/opus/${item.id}"
        val stat = details?.stat
        val thumbs = buildList {
            item.commonCard.cover?.takeIf { it.isNotBlank() }?.let {
                add(MediaThumb("cover", normalizeCoverUrl(it)))
            }
        }
        val list = listOf(
            MediaItem(
                title = title,
                coverUrl = normalizeCoverUrl(item.commonCard.cover.orEmpty()),
                description = title,
                url = opusUrl,
                duration = 0,
                pubTime = details?.author?.pubTs ?: 0,
                type = MediaType.Opus,
                isTarget = true,
                index = 0,
                opid = item.id,
            ),
        )
        return MediaInfo(
            type = MediaType.Opus,
            id = id,
            nfo = MediaNfo(
                showTitle = title,
                intro = title,
                tags = emptyList(),
                url = opusUrl,
                stat = MediaStat(
                    coin = stat?.coin,
                    reply = stat?.comment,
                    favorite = stat?.favorite,
                    share = stat?.forward,
                    like = stat?.like,
                ),
                thumbs = thumbs,
                upper = upper,
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
            MediaItem(
                title = article.title,
                coverUrl = normalizeCoverUrl(article.imageUrls.firstOrNull().orEmpty()),
                description = article.summary,
                url = "https://www.bilibili.com/opus/${article.dynId}",
                duration = 0,
                pubTime = article.publishTime,
                type = MediaType.Opus,
                isTarget = index == 0,
                index = index,
                opid = article.dynId,
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
                upper = author?.let { MediaUpper(it.name, it.mid, it.face) },
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

            val (archives, meta, sections) = if (useSeason) {
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
                Triple(
                    listResp.data.archives.orEmpty(),
                    resolvedMeta,
                    MediaSections(
                        target = resolvedTarget,
                        tabs = seasons.mapNotNull { item ->
                            val metaItem = item.meta ?: return@mapNotNull null
                            MediaTab(metaItem.seasonId, metaItem.name)
                        },
                    ),
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
                Triple(
                    listResp.data.archives.orEmpty(),
                    resolvedMeta,
                    MediaSections(
                        target = resolvedTarget,
                        tabs = series.mapNotNull { item ->
                            val metaItem = item.meta ?: return@mapNotNull null
                            MediaTab(metaItem.seriesId, metaItem.name)
                        },
                    ),
                )
            }

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
                )
            }
            return MediaInfo(
                type = MediaType.UserVideo,
                id = id,
                paged = true,
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
            )
        }
        return MediaInfo(
            type = MediaType.UserVideo,
            id = id,
            paged = true,
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
            "page" to "1",
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
        val cover = data.items.firstOrNull()?.cover?.url
        val thumbs = cover?.takeIf { it.isNotBlank() }?.let {
            listOf(MediaThumb("cover", normalizeCoverUrl(it)))
        }.orEmpty()
        val list = data.items.mapIndexed { index, item ->
            MediaItem(
                title = item.content,
                coverUrl = normalizeCoverUrl(item.cover?.url.orEmpty()),
                description = item.content,
                url = url,
                duration = 0,
                pubTime = 0,
                type = MediaType.Opus,
                isTarget = index == 0,
                index = index,
                opid = item.opusId,
            )
        }
        return MediaInfo(
            type = MediaType.UserOpus,
            id = id,
            paged = true,
            offset = data.offset,
            nfo = MediaNfo(
                tags = emptyList(),
                stat = MediaStat(),
                upper = upper,
                url = url,
                thumbs = thumbs,
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
            )
        }
        return MediaInfo(
            type = MediaType.UserAudio,
            id = id,
            paged = true,
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
        return MediaUpper(name, resolvedMid, data.face)
    }

    private suspend fun fetchOpusDetails(id: String): OpusDetails? {
        val prefix = if (id.startsWith("cv", ignoreCase = true)) "read" else "opus"
        val html = httpClient.get("https://www.bilibili.com/$prefix/$id".toHttpUrl())
        val json = OPUS_INITIAL_STATE_REGEX.find(html)?.groupValues?.getOrNull(1) ?: return null
        val root = JSONObject(json)
        val detail = root.optJSONObject("detail") ?: return null
        val modules = detail.optJSONArray("modules") ?: return null
        var title: String? = null
        var author: OpusAuthor? = null
        var stat: OpusStat? = null
        for (index in 0 until modules.length()) {
            val module = modules.optJSONObject(index) ?: continue
            when (module.optString("module_type")) {
                "MODULE_TYPE_TITLE" -> {
                    title = module.optJSONObject("module_title")?.optString("text")
                }
                "MODULE_TYPE_AUTHOR" -> {
                    val authorObj = module.optJSONObject("module_author") ?: continue
                    val authorMid = authorObj.optLongOrNull("mid") ?: continue
                    author = OpusAuthor(
                        mid = authorMid,
                        name = authorObj.optString("name").takeIf { it.isNotBlank() },
                        pubTs = authorObj.optLongOrNull("pub_ts"),
                    )
                }
                "MODULE_TYPE_STAT" -> {
                    val statObj = module.optJSONObject("module_stat") ?: continue
                    stat = OpusStat(
                        coin = statObj.optJSONObject("coin")?.optLongOrNull("count"),
                        comment = statObj.optJSONObject("comment")?.optLongOrNull("count"),
                        favorite = statObj.optJSONObject("favorite")?.optLongOrNull("count"),
                        forward = statObj.optJSONObject("forward")?.optLongOrNull("count"),
                        like = statObj.optJSONObject("like")?.optLongOrNull("count"),
                    )
                }
            }
        }
        return OpusDetails(title, author, stat)
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
            else -> Unit
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

        val durls = data.durls ?: emptyList()
        if (durls.isNotEmpty()) {
            val resolvedFormat = StreamFormat.Mp4
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
            val resolvedFormat = if (data.acceptFormat?.contains("flv") == true) {
                StreamFormat.Flv
            } else {
                StreamFormat.Mp4
            }
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
        throw BiliHttpException("No playable stream", -1)
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
            )
        }
    }

    private suspend fun fetchMusicPlayUrl(item: MediaItem): PlayUrlInfo {
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
        val data = resp.data
        val id = MUSIC_QUALITY_MAP[data.type] ?: -1
        val audio = listOf(
            AudioStream(
                id = id,
                url = data.cdns.firstOrNull().orEmpty(),
                backupUrls = data.cdns,
            ),
        )
        return PlayUrlInfo(
            format = StreamFormat.Dash,
            video = emptyList(),
            audio = audio,
            acceptQuality = emptyList(),
            acceptDescription = emptyList(),
        )
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

    private fun mapFavoriteType(raw: Int): MediaType {
        return when (raw) {
            2 -> MediaType.Video
            12 -> MediaType.Music
            24 -> MediaType.Bangumi
            else -> MediaType.Video
        }
    }

    companion object {
        private val DIRECT_ID_REGEX =
            Regex("^(av\\d+|BV[0-9A-Za-z]{10}|ep\\d+|ss\\d+|md\\d+|au\\d+|am\\d+|cv\\d+|rl\\d+)$", RegexOption.IGNORE_CASE)
        private val URL_CANDIDATE_REGEX =
            Regex("^(?:https?://)?(?:[\\w-]+\\.)*(?:bilibili\\.com|b23\\.tv)/.+$", RegexOption.IGNORE_CASE)
        private val URL_SAFE_REGEX =
            Regex("^[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$")
        private val DOMAIN_ONLY_REGEX =
            Regex("\\.(bilibili\\.com|b23\\.tv)$", RegexOption.IGNORE_CASE)

        private val BV_REGEX = Regex("^BV[0-9A-Za-z]{10}$", RegexOption.IGNORE_CASE)
        private val AV_REGEX = Regex("^av\\d+$", RegexOption.IGNORE_CASE)
        private val AU_REGEX = Regex("^au\\d+$", RegexOption.IGNORE_CASE)
        private val AM_REGEX = Regex("^am\\d+$", RegexOption.IGNORE_CASE)
        private val CV_REGEX = Regex("^cv\\d+$", RegexOption.IGNORE_CASE)
        private val EP_REGEX = Regex("^ep\\d+$", RegexOption.IGNORE_CASE)
        private val SS_REGEX = Regex("^ss\\d+$", RegexOption.IGNORE_CASE)
        private val MD_REGEX = Regex("^md\\d+$", RegexOption.IGNORE_CASE)
        private val RL_REGEX = Regex("^rl\\d+$", RegexOption.IGNORE_CASE)
        private val OPUS_ID_REGEX = Regex("/opus/(\\d+)")
        private val OPUS_INITIAL_STATE_REGEX = Regex(
            "window\\.__INITIAL_STATE__\\s*=\\s*([\\s\\S]*?)(?=;\\(\\s*function)",
            RegexOption.IGNORE_CASE,
        )

        private val MUSIC_QUALITY_MAP = mapOf(
            0 to 30228,
            1 to 30280,
            2 to 30380,
            3 to 30252,
        )
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
    @Json(name = "avater") val avater: String,
    @Json(name = "avatar") val avatar: String,
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
    @Json(name = "data") val data: List<MusicInfoData>,
)

private data class WatchLaterResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: WatchLaterData?,
)

private data class WatchLaterData(
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

private data class OpusPreviewResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: OpusPreviewData?,
)

private data class OpusPreviewData(
    @Json(name = "item") val item: OpusPreviewItem?,
)

private data class OpusPreviewItem(
    @Json(name = "id") val id: String,
    @Json(name = "user") val user: OpusPreviewUser,
    @Json(name = "common_card") val commonCard: OpusCommonCard,
)

private data class OpusPreviewUser(
    @Json(name = "mid") val mid: Long,
    @Json(name = "name") val name: String?,
)

private data class OpusCommonCard(
    @Json(name = "cover") val cover: String?,
    @Json(name = "nodes") val nodes: List<OpusCommonNode>?,
)

private data class OpusCommonNode(
    @Json(name = "type") val type: String?,
    @Json(name = "text") val text: String?,
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
    @Json(name = "title") val title: String,
    @Json(name = "publish_time") val publishTime: Long,
    @Json(name = "image_urls") val imageUrls: List<String>,
    @Json(name = "summary") val summary: String,
    @Json(name = "dyn_id_str") val dynId: String,
    @Json(name = "categories") val categories: List<OpusListCategory>?,
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

private data class UploadsArchivesResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UploadsArchivesData?,
)

private data class UploadsArchivesData(
    @Json(name = "archives") val archives: List<UploadsArchive>?,
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
)

private data class UserOpusItem(
    @Json(name = "content") val content: String,
    @Json(name = "cover") val cover: UserOpusCover?,
    @Json(name = "opus_id") val opusId: String,
)

private data class UserOpusCover(
    @Json(name = "url") val url: String?,
)

private data class UserAudioResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UserAudioData?,
)

private data class UserAudioData(
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

private data class OpusDetails(
    val title: String?,
    val author: OpusAuthor?,
    val stat: OpusStat?,
)

private data class OpusAuthor(
    val mid: Long,
    val name: String?,
    val pubTs: Long?,
)

private data class OpusStat(
    val coin: Long?,
    val comment: Long?,
    val favorite: Long?,
    val forward: Long?,
    val like: Long?,
)

private data class MusicPlayUrlResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String?,
    @Json(name = "data") val data: MusicPlayUrlData?,
)

private data class MusicPlayUrlData(
    @Json(name = "type") val type: Int,
    @Json(name = "cdns") val cdns: List<String>,
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
