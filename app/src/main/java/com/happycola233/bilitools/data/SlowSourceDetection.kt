package com.happycola233.bilitools.data

import kotlin.math.pow

/**
 * 判断「节点能用，但可能拖慢了下载」的参数。
 *
 * 同一条流在不同 CDN 节点上的速度可以相差几十倍（同一网络下实测约 2 MB/s 对 100 MB/s），
 * 只在出错时换源的话，落到慢节点上就会一直慢到下载结束。满足以下任一条件就去试一个还没用过的节点：
 * - 低于 [floorBytesPerSec]：在宽带和 5G 下明显偏慢，值得换个节点看看；
 * - 不到最近单条连接最快速度的 1/[relativeDivisor]：说明同一网络下别的节点快得多。
 *
 * 试探的代价很小：已下载的数据保留，换上的节点接着续传；每个节点每轮最多换上一次，
 * 换上的节点更慢时才回到之前快一倍以上的节点。网络本身就慢时，各节点都试过一遍就会停在当前节点。
 */
internal data class SlowSourcePolicy(
    /** 连接开始传输后先观察这么久再测速，避开握手和 TCP 慢启动。 */
    val graceMillis: Long = 4_000,
    /** 按这么长的窗口取平均速度，单次抖动不会触发换源。 */
    val windowMillis: Long = 8_000,
    val floorBytesPerSec: Long = 4L * 1024 * 1024,
    val relativeDivisor: Int = 8,
) {
    fun isSlow(windowSpeed: Long, referenceSpeed: Long): Boolean =
        windowSpeed < floorBytesPerSec || windowSpeed * relativeDivisor < referenceSpeed
}

/** 单个连接的滑动窗口测速：宽限期之后才开始采样，攒够一个完整窗口才给出平均速度。 */
internal class ConnectionSpeedWindow(
    private val startMillis: Long,
    private val policy: SlowSourcePolicy,
) {
    private val samples = ArrayDeque<Sample>()

    fun record(nowMillis: Long, transferredBytes: Long): Long? {
        if (nowMillis - startMillis < policy.graceMillis) return null
        samples.addLast(Sample(nowMillis, transferredBytes))
        // 只保留一个恰好覆盖整个窗口的最早样本作为起点。
        while (samples.size > 1 && nowMillis - samples[1].timeMillis >= policy.windowMillis) {
            samples.removeFirst()
        }
        val start = samples.first()
        val elapsed = nowMillis - start.timeMillis
        if (elapsed < policy.windowMillis) return null
        return (transferredBytes - start.bytes) * 1000 / elapsed
    }

    private data class Sample(val timeMillis: Long, val bytes: Long)
}

/**
 * 最近单条连接跑出的最快速度，作为「这个网络能跑多快」的参照，同一个下载器里的所有任务共用。
 * 参照值按半衰期衰减：从 Wi-Fi 换到移动数据后几分钟内就会降下来，不会长期把正常速度当成慢。
 */
internal class ThroughputReference(private val halfLifeMillis: Long = 120_000) {
    private var speed = 0.0
    private var updatedAtMillis = 0L

    /** 记录一个窗口速度，返回衰减后的参照值。 */
    @Synchronized
    fun observe(nowMillis: Long, windowSpeed: Long): Long {
        val elapsed = (nowMillis - updatedAtMillis).coerceAtLeast(0)
        speed = maxOf(speed * 0.5.pow(elapsed.toDouble() / halfLifeMillis), windowSpeed.toDouble())
        updatedAtMillis = nowMillis
        return speed.toLong()
    }
}
