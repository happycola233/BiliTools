package com.happycola233.bilitools

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.error
import coil3.request.fallback
import coil3.size.Size
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BiliToolsImageLoaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var imageLoader: ImageLoader
    private lateinit var server: HttpServer
    private val requestCount = AtomicInteger()
    private val context get() = RuntimeEnvironment.getApplication() as BiliToolsApp

    @Before fun setUp() {
        imageLoader = context.newImageLoader(context).newBuilder()
            .diskCache(DiskCache.Builder().directory(temporaryFolder.newFolder("images").toOkioPath()).build())
            .build()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @After fun tearDown() {
        imageLoader.shutdown()
        server.stop(0)
    }

    @Test fun networkImageIsReusedFromDiskCache() = runBlocking {
        // 当前 Robolectric 不支持 ImageDecoder 的原生文件解码，使用 SVG 验证真实磁盘缓存。
        val url = serveImage(svg(), "image/svg+xml", "public, max-age=3600")

        assertEquals(DataSource.NETWORK, loadImage(url).dataSource)
        assertEquals(DataSource.DISK, loadImage(url).dataSource)
        assertEquals(1, requestCount.get())
    }

    @Test fun noStoreImagesAreFetchedAgain() = runBlocking {
        val url = serveImage(png(), "image/png", "no-store")

        repeat(2) { assertEquals(DataSource.NETWORK, loadImage(url).dataSource) }
        assertEquals(2, requestCount.get())
    }

    @Test fun networkSvgUsesAutomaticallyRegisteredDecoder() = runBlocking {
        val result = loadImage(serveImage(svg(), "image/svg+xml", "no-store"))

        assertEquals(8, result.image.width)
        assertEquals(4, result.image.height)
    }

    @Test fun missingAndInvalidImagesKeepAvatarPlaceholder() = runBlocking {
        for (data in listOf(null, byteArrayOf(0, 1, 2))) {
            val request = ImageRequest.Builder(context)
                .data(data)
                .fallback(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .build()
            val result = imageLoader.execute(request) as ErrorResult
            assertNotNull(result.image)
        }
    }

    private suspend fun loadImage(url: String): SuccessResult {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size.ORIGINAL)
            // 绕过内存缓存，确保第二次请求真正验证 HTTP 磁盘缓存规则。
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        return when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result
            is ErrorResult -> throw AssertionError("图片加载失败", result.throwable)
        }
    }

    private fun serveImage(body: ByteArray, contentType: String, cacheControl: String): String {
        server.createContext("/image") { exchange ->
            requestCount.incrementAndGet()
            exchange.responseHeaders.set("Content-Type", contentType)
            exchange.responseHeaders.set("Cache-Control", cacheControl)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        return "http://127.0.0.1:${server.address.port}/image"
    }

    private fun svg(): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" width="8" height="4"><rect width="8" height="4" fill="red"/></svg>"""
            .toByteArray()

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
