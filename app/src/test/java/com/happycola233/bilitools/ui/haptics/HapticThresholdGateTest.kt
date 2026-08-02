package com.happycola233.bilitools.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Test

class HapticThresholdGateTest {
    @Test
    fun `同一次手势反复跨越阈值只触发一次`() {
        val gate = HapticThresholdGate()
        var activations = 0

        gate.reset()
        gate.update(false) { activations++ }
        gate.update(true) { activations++ }
        gate.update(false) { activations++ }
        gate.update(true) { activations++ }

        assertEquals(1, activations)
    }

    @Test
    fun `从阈值外开始不会在首次移动时误触发`() {
        val gate = HapticThresholdGate()
        var activations = 0

        gate.reset(passed = true)
        gate.update(true) { activations++ }
        gate.update(false) { activations++ }
        gate.update(true) { activations++ }

        assertEquals(0, activations)

        gate.reset()
        gate.update(true) { activations++ }
        assertEquals(1, activations)
    }
}
