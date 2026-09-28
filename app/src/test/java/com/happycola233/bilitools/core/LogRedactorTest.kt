package com.happycola233.bilitools.core

import org.junit.Assert.*
import org.junit.Test

class LogRedactorTest {
    private val redactor = LogRedactor { "987654321" }

    @Test fun cdnExamplesKeepOnlyHostAndFileName() {
        // 路径来自 bilibili-API-collect/docs/video/videostream_url.md；凭据和 IP 参数使用虚构值。
        listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/98/24/872498/872498-1-15.flv",
            "https://upos-sz-mirror08c.bilivideo.com/upgcxcode/65/46/244954665/244954665_f9-1-100113.m4s",
            "https://example.mcdn.bilivideo.cn:4483/upgcxcode/171776208/171776208_nb2-1-64.flv",
            "https://example.akamaized.net/upgcxcode/171776208_nb2-1-64.flv",
        ).forEach { url ->
            val result = redactor.describeUrl("$url?oi=3221225985&mid=987654321&trid=test-trace&upsig=test-signature")
            assertFalse(result.contains('?'))
            assertFalse(result.contains("upgcxcode"))
            assertTrue(result.endsWith(url.substringAfterLast('/')))
        }
    }

    @Test fun apiAllowlistAndShareTracking() {
        val result = redactor.describeUrl("https://api.bilibili.com/x/player/wbi/playurl?bvid=BV1test&aid=123&cid=456&qn=127&fnval=4048&w_rid=test-signature&wts=1234567890&csrf=test-csrf")
        assertEquals("api.bilibili.com/x/player/wbi/playurl?bvid=BV1test&aid=123&cid=456&qn=127&fnval=4048&w_rid=…&wts=…&csrf=…", result)
        assertEquals("www.bilibili.com/video/BV1test?vd_source=…", redactor.describeUrl("https://www.bilibili.com/video/BV1test?vd_source=test-tracking"))
        assertEquals("example.com/a", redactor.describeUrl("https://example.com/a?token=test-token#test-fragment"))
        assertEquals("passport.bilibili.com/login?cid=…&tel=…&code=…", redactor.describeUrl("https://passport.bilibili.com/login?cid=86&tel=test-phone&code=123456"))
    }

    @Test fun hidesAllCredentialFormsAndSelfUidWithoutGuessingPhoneNumbers() {
        val names = listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid", "buvid3", "buvid4", "refresh_token", "access_key", "access_token", "csrf", "validate", "seccode", "qrcode_key")
        names.forEach { name ->
            listOf("$name=test-secret;", "\"$name\":\"test-secret\"", "$name='test-secret'").forEach { text ->
                assertFalse("$text was not redacted", redactor.redact(text).contains("test-secret"))
            }
        }
        assertEquals("Cookie: …\nSet-Cookie: …\nAuthorization: …", redactor.redact("Cookie: a=test-secret; b=other\nSet-Cookie: sid=test-secret\nAuthorization: Bearer test-secret"))
        assertFalse(redactor.redact("""{"Authorization":"Bearer test-secret","Cookie":"test-cookie"}""").contains("test-secret"))
        assertEquals("account=<self-uid> aid=13800138000 cid=456", redactor.redact("account=987654321 aid=13800138000 cid=456"))
        assertEquals("12:34:56.123 W Test [main] address=<ip> v6=<ip>", redactor.redact("12:34:56.123 W Test [main] address=192.0.2.1 v6=2001:db8::1"))
    }
}
