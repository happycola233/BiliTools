package com.happycola233.bilitools.notification

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

internal fun Context.isLiveUpdateSupported(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return false
    }
    return NotificationManagerCompat.from(applicationContext).canPostPromotedNotifications()
}
