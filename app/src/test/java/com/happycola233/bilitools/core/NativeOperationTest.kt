package com.happycola233.bilitools.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeOperationTest {
    @Test fun cancellationWaitsForNativeCompletionBeforeCleanup() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancellation = CompletableDeferred<Unit>()
        lateinit var finish: (String) -> Unit
        var cleaned = false
        val task = launch {
            try {
                awaitNativeOperation<String> { callback ->
                    finish = callback
                    started.complete(Unit)
                    val cancel: () -> Unit = { cancellation.complete(Unit) }
                    cancel
                }
            } finally { cleaned = true }
        }
        started.await()
        task.cancel()
        cancellation.await()
        assertFalse(task.isCompleted)
        assertFalse(cleaned)
        finish("cancelled")
        task.join()
        assertTrue(cleaned)
    }

    @Test fun synchronousCompletionIsReturnedWithoutCancellation() = runBlocking {
        var cancelled = false
        val result = awaitNativeOperation<String> { finish ->
            finish("done")
            val cancel: () -> Unit = { cancelled = true }
            cancel
        }
        assertEquals("done", result)
        assertFalse(cancelled)
    }
}
