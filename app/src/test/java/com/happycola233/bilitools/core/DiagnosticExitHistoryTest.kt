package com.happycola233.bilitools.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiagnosticExitHistoryTest {
    @Test fun ignoresOldExitsAndAcknowledgesOnlyNewProblems() {
        val context = RuntimeEnvironment.getApplication()
        val history = DiagnosticExitHistory(context)
        val manager = shadowOf(context.getSystemService(ActivityManager::class.java))
        fun add(reason: Int, timestamp: Long) {
            val exit = ReflectionHelpers.callConstructor(ApplicationExitInfo::class.java)
            ReflectionHelpers.setField(exit, "mReason", reason)
            ReflectionHelpers.setField(exit, "mTimestamp", timestamp)
            manager.addApplicationExitInfo(exit)
        }
        add(ApplicationExitInfo.REASON_CRASH, 1)
        history.initialize()
        assertNull(history.pendingProblem())
        val timestamp = System.currentTimeMillis() + 1000
        add(ApplicationExitInfo.REASON_LOW_MEMORY, timestamp)
        assertNull(history.pendingProblem())
        add(ApplicationExitInfo.REASON_ANR, timestamp + 1)
        val anr = history.pendingProblem()!!
        assertTrue(anr.reason.startsWith("ANR"))
        history.acknowledge(anr)
        assertNull(history.pendingProblem())
        add(ApplicationExitInfo.REASON_CRASH_NATIVE, timestamp + 2)
        assertTrue(history.pendingProblem()!!.reason.startsWith("CRASH_NATIVE"))
        // 同版本重启不会重置处理进度。
        history.initialize()
        assertNotNull(history.pendingProblem())
    }

    @Test @Config(sdk = [29])
    fun api29UsesSynchronousMarkerAndDoesNotRepeatAcknowledgedCrash() {
        val context = RuntimeEnvironment.getApplication()
        val history = DiagnosticExitHistory(context)
        history.initialize()
        context.getSharedPreferences("diagnostic-exits", Context.MODE_PRIVATE)
            .edit().putLong("handledThrough", 1).commit()
        history.recordUncaughtException()
        val pending = DiagnosticExitHistory(context).pendingProblem()!!
        assertEquals("CRASH", pending.reason)
        history.acknowledge(pending)
        assertNull(DiagnosticExitHistory(context).pendingProblem())
    }
}
