package com.happycola233.bilitools.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 取消请求发出后仍等待原生完成回调，调用者此后才能清理它正在读写的临时文件。 */
internal suspend fun <T> awaitNativeOperation(start: ((T) -> Unit) -> (() -> Unit)): T {
    currentCoroutineContext().ensureActive()
    val completed = CompletableDeferred<T>()
    val cancel = start { completed.complete(it) }
    return try {
        completed.await()
    } catch (error: CancellationException) {
        try {
            cancel()
        } finally {
            withContext(NonCancellable) { completed.await() }
        }
        throw error
    }
}
