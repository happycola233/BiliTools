package com.happycola233.bilitools

import android.content.Context
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.ExtrasRepository
import com.happycola233.bilitools.data.MediaRepository
import com.happycola233.bilitools.data.OpusRepository
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.ui.history.HistoryViewModel
import com.happycola233.bilitools.ui.parse.ParseViewModel
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.fail
import org.robolectric.RuntimeEnvironment

/** 所有 HTTP 请求都在内存中响应，回归测试不访问真实账号或上游。 */
class PaginationTestEnvironment {
    private val context = RuntimeEnvironment.getApplication()
    val requests = CopyOnWriteArrayList<HttpUrl>()
    @Volatile var respond: (HttpUrl) -> String = { """{"code":-404,"message":"测试资源不存在"}""" }
    val settings = SettingsRepository(context)
    val cookies = CookieStore(context).apply {
        clear()
        updateFromHeaders(Headers.headersOf("Set-Cookie", "SESSDATA=pagination-test-only"))
    }
    private val transport = OkHttpClient.Builder().addInterceptor { chain ->
        val url = chain.request().url
        requests += url
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(respond(url).toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val http = BiliHttpClient(cookies, settings, transport)
    private val signer = WbiSigner(http)
    private val opus = OpusRepository(http, cookies)
    val media = MediaRepository(http, signer, cookies, opus)
    private val extras = ExtrasRepository(http, signer)
    private val auth = AuthRepository(http, cookies, signer)
    private val strings = StringProvider(context)

    fun history() = HistoryViewModel(auth, extras, strings)
    fun parse(): ParseViewModel {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return ParseViewModel(media, opus, extras, AppContainer(context).downloadRepository, settings, auth, strings)
    }

    fun historyPages() = requests.filter { it.encodedPath.endsWith("/history/search") }
    fun favoritePages() = requests.filter { it.encodedPath.endsWith("/fav/resource/list") }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.awaitPagination(condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
        runCurrent()
        if (condition()) return
        if (System.nanoTime() > deadline) fail("分页操作未在 10 秒内完成")
        // HTTP 在真实 IO dispatcher 执行，让出测试线程后再推进主线程调度器。
        withContext(Dispatchers.IO) { Thread.sleep(5) }
    }
}

fun historyPage(page: Int, vararg ids: Int, more: Boolean = true, total: Int = 80): String {
    val items = ids.joinToString { id ->
        """{"title":"记录$id","view_at":$id,"history":{"oid":$id,"business":"archive"}}"""
    }
    return """{"code":0,"data":{"page":{"pn":$page,"total":$total},"has_more":$more,"list":[$items]}}"""
}

val historyTabs = """{"code":0,"data":{"cursor":{"business":"live"},"tab":[{"type":"live","name":"直播"},{"type":"archive","name":"视频"}]}}"""
val favoriteFolders = """{"code":0,"data":{"list":[{"id":42,"title":"测试收藏夹"}]}}"""

fun favoritePage(vararg ids: Int, more: Boolean = true, nullItems: Boolean = false): String {
    val items = if (nullItems) "null" else ids.joinToString(prefix = "[", postfix = "]") { id ->
        """{"id":$id,"type":2,"title":"视频$id","cover":"","duration":60,"pubtime":1}"""
    }
    return """{"code":0,"data":{"info":{"id":42,"title":"测试收藏夹","cover":"","intro":"","upper":null,"cnt_info":{"collect":0,"play":0,"thumb_up":0,"share":0},"ctime":1,"media_count":144},"medias":$items,"has_more":$more}}"""
}
