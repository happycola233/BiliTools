package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.ParsedInput
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaInputClassifierTest {
    @Test
    fun parseDirectId_recognizesUidPrefixCaseInsensitively() {
        listOf("uid123456", "UID123456", "UiD123456").forEach { input ->
            assertEquals(
                ParsedInput(input, MediaType.UserVideo),
                MediaInputClassifier.parseDirectId(input),
            )
        }
    }

    @Test
    fun parseDirectId_rejectsUidWithoutNumericId() {
        assertNull(MediaInputClassifier.parseDirectId("uid"))
        assertNull(MediaInputClassifier.parseDirectId("uidabc"))
    }

    @Test
    fun parseSpaceUrl_recognizesMobileUserVideoPaths() {
        val cases = mapOf(
            "https://m.bilibili.com/space/123456" to ParsedInput("123456", MediaType.UserVideo),
            "https://m.bilibili.com/space/123456/video" to ParsedInput("123456", MediaType.UserVideo),
            "https://m.bilibili.com/space/123456/lists" to ParsedInput("123456", MediaType.UserVideo),
            "https://m.bilibili.com/space/123456/lists/3333" to
                ParsedInput("123456", MediaType.UserVideo, 3333),
        )

        cases.forEach { (urlString, expected) ->
            val url = urlString.toHttpUrl()
            assertEquals(
                expected,
                MediaInputClassifier.parseSpaceUrl(url),
            )
        }
    }

    @Test
    fun parseSpaceUrl_keepsDesktopAndMobileRoutingConsistent() {
        val desktopUrl = "https://space.bilibili.com/123456/upload/audio".toHttpUrl()
        val mobileUrl = "https://m.bilibili.com/space/123456/upload/audio".toHttpUrl()

        val desktop = MediaInputClassifier.parseSpaceUrl(desktopUrl)
        val mobile = MediaInputClassifier.parseSpaceUrl(mobileUrl)

        assertEquals(ParsedInput("123456", MediaType.UserAudio), desktop)
        assertEquals(desktop, mobile)
    }

    @Test
    fun parseSpaceUrl_doesNotTreatOtherMobileSpacePathsAsUserVideo() {
        val url = "https://m.bilibili.com/space/123456/dynamic".toHttpUrl()

        assertThrows(IllegalArgumentException::class.java) {
            MediaInputClassifier.parseSpaceUrl(url)
        }
    }
}
