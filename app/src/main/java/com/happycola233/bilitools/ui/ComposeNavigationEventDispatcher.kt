package com.happycola233.bilitools.ui

import android.util.Log
import androidx.compose.ui.platform.ComposeView

internal fun attachNavigationEventDispatcherOwner(
    composeView: ComposeView,
    host: Any,
    logTag: String,
) {
    runCatching {
        val ownerClass = Class.forName("androidx.navigationevent.NavigationEventDispatcherOwner")
        if (!ownerClass.isInstance(host)) return

        val helperClass = Class.forName("androidx.navigationevent.ViewTreeNavigationEventDispatcherOwner")
        val setMethod = helperClass.getMethod("set", android.view.View::class.java, ownerClass)
        setMethod.invoke(null, composeView, host)
    }.onFailure { throwable ->
        Log.w(logTag, "Unable to attach NavigationEventDispatcherOwner to ComposeView", throwable)
    }
}
