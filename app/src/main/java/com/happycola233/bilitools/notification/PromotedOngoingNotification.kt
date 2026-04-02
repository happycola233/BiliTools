package com.happycola233.bilitools.notification

import android.os.Build
import androidx.core.app.NotificationCompat

internal fun NotificationCompat.Builder.applyPromotedOngoing(
    shortCriticalText: String? = null,
): NotificationCompat.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        setRequestPromotedOngoing(true)
    }
    if (!shortCriticalText.isNullOrBlank() && Build.VERSION.SDK_INT >= 36) {
        setShortCriticalText(shortCriticalText)
    }
    return this
}
