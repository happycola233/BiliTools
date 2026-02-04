package com.happycola233.bilitools.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

class BiliHttpClient(private val cookieStore: CookieStore) {
    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val cookie = cookieStore.cookieHeader()
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.bilibili.com/")
                    .header("Origin", "https://www.bilibili.com")
                    .apply {
                        if (cookie.isNotBlank()) {
                            header("Cookie", cookie)
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                cookieStore.updateFromHeaders(response.headers)
                response
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()
    }

    suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).get().build())
    }

    suspend fun getBytes(url: HttpUrl): ByteArray = withContext(Dispatchers.IO) {
        executeBytes(Request.Builder().url(url).get().build())
    }

    suspend fun resolveUrl(url: HttpUrl): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            ensureSuccess(response)
            response.request.url.toString()
        }
    }

    suspend fun postForm(url: HttpUrl, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                params.forEach { (key, value) ->
                    add(key, value)
                }
            }.build()
            execute(Request.Builder().url(url).post(body).build())
        }

    suspend fun postEmpty(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().build()
        execute(Request.Builder().url(url).post(body).build())
    }

    fun <T> adapter(clazz: Class<T>) = moshi.adapter(clazz)

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            ensureSuccess(response)
            return response.body?.string().orEmpty()
        }
    }

    private fun executeBytes(request: Request): ByteArray {
        client.newCall(request).execute().use { response ->
            ensureSuccess(response)
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun ensureSuccess(response: Response) {
        if (!response.isSuccessful) {
            throw BiliHttpException("HTTP ${response.code}", response.code)
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    }
}

class BiliHttpException(message: String, val code: Int) : RuntimeException(message)
