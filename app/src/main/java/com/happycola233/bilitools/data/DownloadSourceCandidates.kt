package com.happycola233.bilitools.data

import com.happycola233.bilitools.data.model.DownloadSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun DownloadSource.orderedUrls(): List<String> =
    (listOf(url) + backupUrls)
        .distinct()
        .mapNotNull { value -> value.toHttpUrlOrNull()?.let { value to it } }
        .sortedBy { (_, url) -> url.downloadPriority() }
        .map { (value, _) -> value }

/**
 * 一次下载里的候选地址轮换。
 *
 * 出错的节点本轮不再使用；因为太慢被换下的节点仍然可用，会记下它测得的速度。按速度换源只换到
 * 其他主机：主地址和备用地址常常指向同一主机，换过去还是同一个慢节点。所有主机都试过以后，
 * 如果之前某个节点至少快一倍就回到那里，然后不再按速度换源，避免来回切换；其余节点都出错时，
 * 宁可退回慢节点继续下载，也不直接失败。
 */
internal class DownloadSourceRotation(candidates: List<String>) {
    private val untried = ArrayDeque(candidates)
    private val slowSpeeds = LinkedHashMap<String, Long>()
    private var speedSwitchTarget: String? = null
    private var settled = false

    fun first(): String? = untried.removeFirstOrNull()

    /** 当前节点测得偏慢时调用。返回 true 表示应断开当前连接，改用 [takeSpeedSwitchTarget]。 */
    fun leaveForSpeed(url: String, speedBytesPerSec: Long): Boolean {
        if (settled) return false
        slowSpeeds[url] = speedBytesPerSec
        val slowNodes = slowSpeeds.keys.mapTo(mutableSetOf(), ::nodeOf)
        // 同主机的候选留在队列里，出错时仍可作为备用。
        val otherNode = untried.firstOrNull { nodeOf(it) !in slowNodes }
        speedSwitchTarget = otherNode?.also(untried::remove) ?: run {
            settled = true
            slowSpeeds.filterKeys { it != url }.maxByOrNull { it.value }
                ?.takeIf { it.value >= speedBytesPerSec * 2 }
                ?.key
        }
        return speedSwitchTarget != null
    }

    fun takeSpeedSwitchTarget(): String = speedSwitchTarget!!.also { speedSwitchTarget = null }

    /** 当前节点出错时调用，返回下一个要尝试的地址；没有可用地址时返回 null。 */
    fun afterFailure(url: String): String? {
        slowSpeeds.remove(url)
        return untried.removeFirstOrNull() ?: slowSpeeds.maxByOrNull { it.value }?.key
    }

    /** 主机加端口才对应一台服务器，PCDN 节点常用非标准端口区分。 */
    private fun nodeOf(url: String): String = url.toHttpUrl().let { "${it.host}:${it.port}" }
}

private fun HttpUrl.downloadPriority(): Int {
    val biliCdn = host.endsWith(".bilivideo.com") || host.endsWith(".bilivideo.cn")
    if (!biliCdn) return 3
    val storage = queryParameter("os").orEmpty()
    return when {
        host.contains("mirror") && storage.endsWith("bv") -> 0
        storage == "upos" -> 1
        host.startsWith("cn-") && storage == "bcache" -> 2
        else -> 3
    }
}
