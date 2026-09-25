package com.happycola233.bilitools.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Test

class HapticThresholdGateTest {
    @Test
    fun `同一次手势进入和退出阈值分别反馈且区内移动不重复`() {
        val gate = HapticThresholdGate()
        val changes = mutableListOf<Boolean>()

        gate.reset()
        listOf(false, false, true, true, false, false, true, true).forEach { passed ->
            gate.update(passed) { changes += it }
        }

        assertEquals(listOf(true, false, true), changes)
    }

    @Test
    fun `从阈值外开始不会在首次移动时误触发`() {
        val gate = HapticThresholdGate()
        val changes = mutableListOf<Boolean>()

        gate.reset(passed = true)
        gate.update(true) { changes += it }
        assertEquals(emptyList<Boolean>(), changes)
        gate.update(false) { changes += it }
        gate.update(true) { changes += it }
        assertEquals(listOf(false, true), changes)

        gate.reset()
        gate.update(false) { changes += it }
        gate.update(true) { changes += it }
        assertEquals(listOf(false, true, true), changes)
    }
}
