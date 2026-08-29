package com.happycola233.bilitools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskConcurrencyQueueTest {
    @Test
    fun `starts tasks in fifo order up to the limit`() {
        val queue = TaskConcurrencyQueue(initialLimit = 2)
        queue.enqueue(1)
        queue.enqueue(2)
        queue.enqueue(3)

        val firstSlots = queue.takeReady()
        assertEquals(listOf(1L, 2L), firstSlots.map { it.taskId })
        assertTrue(queue.isRunning(1))
        assertTrue(queue.isRunning(2))
        assertEquals(emptyList<Long>(), queue.takeReady().map { it.taskId })

        assertTrue(queue.finish(firstSlots.first()))
        assertEquals(listOf(3L), queue.takeReady().map { it.taskId })
    }

    @Test
    fun `paused pending task never consumes a slot`() {
        val queue = TaskConcurrencyQueue(initialLimit = 1)
        queue.enqueue(1)
        queue.enqueue(2)
        val runningSlot = queue.takeReady().single()
        assertEquals(1L, runningSlot.taskId)

        assertTrue(queue.pausePending(2))
        assertTrue(queue.finish(runningSlot))
        assertEquals(emptyList<Long>(), queue.takeReady().map { it.taskId })
        assertFalse(queue.isRunning(2))
    }

    @Test
    fun `raising limit starts more tasks and lowering it does not interrupt running tasks`() {
        val queue = TaskConcurrencyQueue(initialLimit = 1)
        (1L..4L).forEach(queue::enqueue)
        val firstSlot = queue.takeReady().single()
        assertEquals(1L, firstSlot.taskId)

        queue.updateLimit(3)
        val addedSlots = queue.takeReady()
        assertEquals(listOf(2L, 3L), addedSlots.map { it.taskId })

        queue.updateLimit(1)
        assertTrue(queue.finish(firstSlot))
        assertEquals(emptyList<Long>(), queue.takeReady().map { it.taskId })
        assertTrue(queue.finish(addedSlots[0]))
        assertEquals(emptyList<Long>(), queue.takeReady().map { it.taskId })
        assertTrue(queue.finish(addedSlots[1]))
        assertEquals(listOf(4L), queue.takeReady().map { it.taskId })
    }

    @Test
    fun `enqueue is idempotent while pending or running`() {
        val queue = TaskConcurrencyQueue(initialLimit = 1)
        queue.enqueue(7)
        queue.enqueue(7)
        val runningSlot = queue.takeReady().single()
        assertEquals(7L, runningSlot.taskId)

        queue.enqueue(7)
        assertEquals(emptyList<Long>(), queue.takeReady().map { it.taskId })
        assertTrue(queue.finish(runningSlot))
    }

    @Test
    fun `stale execution cannot release a newer slot for the same task`() {
        val queue = TaskConcurrencyQueue(initialLimit = 1)
        queue.enqueue(9)
        val oldSlot = queue.takeReady().single()

        assertTrue(queue.finishCurrent(9))
        queue.enqueue(9)
        val newSlot = queue.takeReady().single()

        assertFalse(queue.finish(oldSlot))
        assertTrue(queue.isCurrent(newSlot))
        assertTrue(queue.finish(newSlot))
    }
}
