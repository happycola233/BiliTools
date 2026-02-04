package com.happycola233.bilitools.core

import android.content.Context

class StringProvider(private val context: Context) {
    fun get(resId: Int, vararg args: Any): String {
        return if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args)
        }
    }
}
