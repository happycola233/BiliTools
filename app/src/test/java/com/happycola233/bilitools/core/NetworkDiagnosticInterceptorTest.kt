package com.happycola233.bilitools.core

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NetworkDiagnosticInterceptorTest {
    @Test fun businessFailureIsWarningAndBodyRemainsReadable() {
        MockWebServer().use { server ->
            server.start()
            val body = """{"code":-352,"message":"risk control","data":"${"x".repeat(8000)}"}"""
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build())
            val logs = mutableListOf<Pair<Int, String>>()
            val client = OkHttpClient.Builder().addInterceptor(NetworkDiagnosticInterceptor("test", write = { level, _, message -> logs += level to message })).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { response ->
                assertEquals(body, response.body.string())
            }
            assertEquals(1, logs.size)
            assertEquals(5, logs.single().first)
            assertTrue(logs.single().second.contains("code=-352 message=risk control"))
        }
    }

    @Test fun binaryBodyIsNeverReadByLogger() {
        var reads = 0
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "video/mp4").body("binary-data").build())
            val client = OkHttpClient.Builder()
                .addInterceptor(NetworkDiagnosticInterceptor("test", write = { _, _, _ -> }))
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val original = response.body
                    val source = object : ForwardingSource(original.source()) {
                        override fun read(sink: Buffer, byteCount: Long): Long { reads++; return super.read(sink, byteCount) }
                    }.buffer()
                    response.newBuilder().body(object : ResponseBody() {
                        override fun contentType() = original.contentType()
                        override fun contentLength() = original.contentLength()
                        override fun source(): BufferedSource = source
                    }).build()
                }.build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { response ->
                assertEquals(0, reads)
                assertEquals("binary-data", response.body.string())
            }
        }
    }

    @Test fun ioExceptionIsLoggedAndRethrownUnchanged() {
        val failure = IOException("connection to 192.0.2.1 failed\nretry unavailable")
        val logs = mutableListOf<String>()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .addInterceptor(NetworkDiagnosticInterceptor("test", write = { _, _, message -> logs += message }))
            .addInterceptor { throw failure }.build()
        try {
            client.newCall(Request.Builder().url("https://example.com/").build()).execute()
            fail("Expected IOException")
        } catch (error: IOException) { assertSame(failure, error) }
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("IOException"))
        assertFalse(logs.single().contains("192.0.2.1"))
        assertFalse(logs.single().contains('\n'))
    }
}
