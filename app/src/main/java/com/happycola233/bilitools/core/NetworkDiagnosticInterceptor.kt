package com.happycola233.bilitools.core

import com.squareup.moshi.JsonReader
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一次应用层请求（含重定向）只记一行，绝不记录请求体、响应体或请求头。 */
class NetworkDiagnosticInterceptor(
    private val tag: String,
    private val redactor: LogRedactor = LogRedactor(),
    private val write: (Int, String, String) -> Unit = { level, logTag, message ->
        if (level == 5) AppLog.w(logTag, message) else AppLog.d(logTag, message)
    },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.nanoTime()
        val description = "method=${request.method} url=${redactor.describeUrl(request.url.toString())}"
        try {
            val response = chain.proceed(request)
            val body = response.body
            val subtype = body.contentType()?.subtype
            val business = try {
                if (subtype == "json" || subtype?.endsWith("+json") == true) readBusinessResult(response) else null
            } catch (error: IOException) {
                response.close()
                throw error
            }
            val warning = !response.isSuccessful || (business?.first != null && business.first != 0L)
            val message = buildString {
                append("[http] $description status=${response.code} durationMs=${elapsed(start)} bytes=${body.contentLength()}")
                if (response.request.url != request.url) {
                    append(" final=${redactor.describeUrl(response.request.url.newBuilder().query(null).build().toString())}")
                }
                business?.let { (code, message) ->
                    append(" code=$code")
                    // 登录接口的 message 可能回显手机号或账号，只记录结果码。
                    if (request.url.host != "passport.bilibili.com") append(" message=$message")
                }
            }
            write(if (warning) 5 else 3, tag, singleLine(message))
            return response
        } catch (error: IOException) {
            write(5, tag, singleLine("[http] $description durationMs=${elapsed(start)} error=${error.javaClass.simpleName} message=${error.message}"))
            throw error
        }
    }

    private fun readBusinessResult(response: Response): Pair<Long?, String?>? {
        var code: Long? = null
        var message: String? = null
        // 网络 I/O 失败必须交给外层原样记录并抛出；下面仅容忍窥读副本的 JSON 解析错误。
        val peeked = response.peekBody(4096)
        // 流式读取顶层字段：data 大于窥读上限时，仍保留此前读到的 code/message。
        try {
            JsonReader.of(peeked.source()).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "code" -> code = reader.nextLong()
                        "message", "msg" -> if (reader.peek() == JsonReader.Token.STRING) message = reader.nextString() else reader.skipValue()
                        else -> reader.skipValue()
                    }
                }
            }
        } catch (_: IOException) {
            // 窥读上限可能截断 JSON，解析完整响应的职责仍属于调用方。
        } catch (_: com.squareup.moshi.JsonDataException) {
            // 外部响应不一定符合 B 站信封结构。
        }
        return if (code != null || message != null) code to message else null
    }

    private fun elapsed(start: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

    private fun singleLine(message: String) = redactor.redact(message).replace("\r", "\\r").replace("\n", "\\n")
}
