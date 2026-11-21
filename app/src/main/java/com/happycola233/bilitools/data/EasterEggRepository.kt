package com.happycola233.bilitools.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * 彩蛋要记住的少量本地数据：启动次数、累计下载量，以及还没来得及展示的里程碑。
 * 这些只用于彩蛋，不属于用户设置，因此与 [SettingsRepository] 分开存放。
 */
class EasterEggRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val copies = ConcurrentHashMap<EggCopyLanguage, EasterEggCopy>()

    fun copy(language: EggCopyLanguage): EasterEggCopy = copies.getOrPut(language) {
        appContext.assets.open("easter_eggs/${language.assetName}.json").use { input ->
            EasterEggCopy.parse(input.readBytes().decodeToString())
        }
    }

    /** 每次冷启动调用一次；配置变化导致的重建不算。 */
    @Synchronized
    fun recordAppLaunch() {
        val count = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        val editor = prefs.edit().putInt(KEY_LAUNCH_COUNT, count)
        if (count in LAUNCH_MILESTONES) editor.putString(KEY_PENDING_MILESTONE, encode(EggMilestone.Launch(count)))
        editor.apply()
    }

    /** 一个音视频文件成功保存后调用；字幕、封面等附属文件不计入。 */
    @Synchronized
    fun recordMediaDownloaded(bytes: Long) {
        val count = prefs.getInt(KEY_DOWNLOAD_COUNT, 0) + 1
        val previousBytes = prefs.getLong(KEY_DOWNLOAD_BYTES, 0L)
        val totalBytes = previousBytes + bytes.coerceAtLeast(0L)
        val milestone = when {
            previousBytes < TERABYTE && totalBytes >= TERABYTE -> EggMilestone.Terabyte
            count in DOWNLOAD_MILESTONES -> EggMilestone.Downloads(count)
            else -> null
        }
        val editor = prefs.edit()
            .putInt(KEY_DOWNLOAD_COUNT, count)
            .putLong(KEY_DOWNLOAD_BYTES, totalBytes)
        milestone?.let { editor.putString(KEY_PENDING_MILESTONE, encode(it)) }
        editor.apply()
    }

    /** 取出待展示的里程碑，取出后即清除，每个里程碑只祝贺一次。 */
    @Synchronized
    fun takePendingMilestone(): EggMilestone? {
        val encoded = prefs.getString(KEY_PENDING_MILESTONE, null) ?: return null
        prefs.edit().remove(KEY_PENDING_MILESTONE).apply()
        return decode(encoded)
    }

    private fun encode(milestone: EggMilestone): String = when (milestone) {
        is EggMilestone.Launch -> "launch:${milestone.count}"
        is EggMilestone.Downloads -> "downloads:${milestone.count}"
        EggMilestone.Terabyte -> "terabyte"
    }

    private fun decode(value: String): EggMilestone? {
        val (kind, count) = value.split(':').let { it[0] to it.getOrNull(1)?.toIntOrNull() }
        return when (kind) {
            "launch" -> count?.let(EggMilestone::Launch)
            "downloads" -> count?.let(EggMilestone::Downloads)
            "terabyte" -> EggMilestone.Terabyte
            else -> null
        }
    }

    companion object {
        /** B 站数字梗：233 是笑，666 是厉害，2333 是笑得更大声 */
        val LAUNCH_MILESTONES = setOf(100, 233, 666, 1000, 2333)
        val DOWNLOAD_MILESTONES = setOf(1, 100, 1000)
        const val TERABYTE = 1L shl 40

        private const val PREFS_NAME = "easter_eggs"
        private const val KEY_LAUNCH_COUNT = "launch_count"
        private const val KEY_DOWNLOAD_COUNT = "media_download_count"
        private const val KEY_DOWNLOAD_BYTES = "media_download_bytes"
        private const val KEY_PENDING_MILESTONE = "pending_milestone"
    }
}
