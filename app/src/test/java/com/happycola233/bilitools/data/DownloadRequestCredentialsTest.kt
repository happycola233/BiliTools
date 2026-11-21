package com.happycola233.bilitools.data

import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.Headers.Companion.headersOf
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** 下载地址可能指向第三方 PCDN 节点，账号凭据既不能发出去，也不能被它们改写。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadRequestCredentialsTest {
    @Test fun mediaRequestsKeepBrowserHeadersButNeverExchangeAccountCookies() {
        val context = RuntimeEnvironment.getApplication()
        val cookies = CookieStore(context).apply {
            updateFromHeaders(headersOf("Set-Cookie", "SESSDATA=account-secret; Path=/"))
        }
        val settings = SettingsRepository(context)
        val bili = BiliHttpClient(cookies)
        val signer = WbiSigner(bili)
        val repository = DownloadRepository(
            context, settings, MediaRepository(bili, signer, cookies, OpusRepository(bili, cookies)),
            ExtrasRepository(bili, signer), ExportRepository(context, settings),
        )
        ReflectionHelpers.getField<CoroutineScope>(repository, "scope").cancel()
        val client = ReflectionHelpers.getField<Lazy<Call.Factory>>(repository, "httpClient\$delegate").value

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "SESSDATA=; Path=/")
                    .body("m4s")
                    .build(),
            )
            server.start()
            client.newCall(Request.Builder().url(server.url("/video.m4s")).build()).execute().use {
                assertEquals("m4s", it.body.string())
            }
            val request = server.takeRequest()
            assertNull(request.headers["Cookie"])
            assertEquals("https://www.bilibili.com/", request.headers["Referer"])
            assertEquals(BiliHttpClient.USER_AGENT, request.headers["User-Agent"])
        }
        assertEquals("account-secret", cookies.getCookie("SESSDATA"))
    }
}
