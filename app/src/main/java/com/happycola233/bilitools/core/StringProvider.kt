package com.happycola233.bilitools.core

import android.content.Context

class StringProvider(private val context: Context) {
    fun get(resId: Int, vararg args: Any): String {
        // 每次读取当前语言，避免长生命周期的仓库、下载任务持有切换前的资源配置。
        val localizedContext = context.localizedContext()
        return if (args.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *args)
        }
    }
}
