package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadSource
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSourceCandidatesTest {
    @Test fun prioritizesMirrorsWithoutDroppingOrRewritingOtherSignedUrls() {
        val pcdn = "https://xy.example.mcdn.bilivideo.cn:8082/file?os=mcdn&sig=original"
        val bcache = "https://cn-sccd-ct-02-01.bilivideo.com/file?os=bcache&sig=bcache"
        val upos = "https://upos-sz.bilivideo.com/file?os=upos&sig=upos"
        val mirror = "https://upos-sz-mirrorhw.bilivideo.com/file?os=hwbv&sig=a%2Bb"
        val source = DownloadSource(pcdn, listOf(bcache, mirror, upos, pcdn, mirror))

        assertEquals(listOf(mirror, upos, bcache, pcdn), source.orderedUrls())
    }

    @Test fun preservesApiOrderWithinTheSamePriorityAndIgnoresMalformedUrls() {
        val primary = "https://cdn.example.com/media?sig=first"
        val backup = "https://cdn.example.org/media?sig=second"
        assertEquals(
            listOf(primary, backup),
            DownloadSource(primary, listOf("", "invalid", backup)).orderedUrls(),
        )
    }
}
