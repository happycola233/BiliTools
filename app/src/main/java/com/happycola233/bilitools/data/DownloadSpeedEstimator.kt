package com.happycola233.bilitools.data

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToLong

internal data class DownloadTransferEstimate(
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long? = null,
)

/** 每次传输尝试独立采样，续传前的字节和暂停时间不参与测速。时间使用单调时钟。 */
internal class DownloadSpeedEstimator(startTimeMillis: Long, initialDownloadedBytes: Long) {
    private var sampleTimeMillis = startTimeMillis
    private var sampleDownloadedBytes = initialDownloadedBytes
    private var lastObservedBytes = initialDownloadedBytes
    private var lastProgressTimeMillis = startTimeMillis
    private var smoothedSpeed: Double? = null
    private var estimate = DownloadTransferEstimate()

    fun update(nowMillis: Long, downloadedBytes: Long, totalBytes: Long): DownloadTransferEstimate {
        if (downloadedBytes > lastObservedBytes) {
            lastProgressTimeMillis = nowMillis
            lastObservedBytes = downloadedBytes
        }
        if (nowMillis - lastProgressTimeMillis >= STALL_TIMEOUT_MILLIS) {
            // 断流时不能一直展示旧速度，恢复后重新积累样本，避免 ETA 突然膨胀。
            sampleTimeMillis = nowMillis
            sampleDownloadedBytes = downloadedBytes
            smoothedSpeed = null
            estimate = DownloadTransferEstimate()
            return estimate
        }

        val elapsed = nowMillis - sampleTimeMillis
        val sampleInterval = if (smoothedSpeed == null) WARM_UP_MILLIS else SAMPLE_INTERVAL_MILLIS
        if (elapsed < sampleInterval) return estimate

        val instantSpeed = (downloadedBytes - sampleDownloadedBytes) * 1000.0 / elapsed
        sampleTimeMillis = nowMillis
        sampleDownloadedBytes = downloadedBytes

        // 按实际时间间隔加权的 EMA，调度延迟不会改变平滑窗口的含义。
        val weight = 1.0 - exp(-elapsed / SMOOTHING_TIME_MILLIS)
        val speed = smoothedSpeed?.let { it + weight * (instantSpeed - it) } ?: instantSpeed
        smoothedSpeed = speed.takeIf { it > 0.0 }
        val speedBytesPerSec = speed.roundToLong()
        estimate = DownloadTransferEstimate(
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = if (speedBytesPerSec > 0 && totalBytes > downloadedBytes) {
                ceil((totalBytes - downloadedBytes).toDouble() / speedBytesPerSec).toLong()
            } else null,
        )
        return estimate
    }

    private companion object {
        const val SAMPLE_INTERVAL_MILLIS = 1_000L
        const val WARM_UP_MILLIS = 2_000L
        const val STALL_TIMEOUT_MILLIS = 4_000L
        const val SMOOTHING_TIME_MILLIS = 4_000.0
    }
}
