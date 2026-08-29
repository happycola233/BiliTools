package com.happycola233.bilitools.data

/**
 * 按入队顺序分配并发名额。等待队列与正在执行集合分开保存，调用方因此可以准确区分
 * “排队中”和“已经开始”，并只暂停尚未开始的附加任务。
 */
internal class TaskConcurrencyQueue(initialLimit: Int) {
    private val pendingTaskIds = linkedSetOf<Long>()
    private val runningSlots = mutableMapOf<Long, Long>()
    private var limit = initialLimit.coerceAtLeast(1)
    private var nextSlotToken = 0L

    class TaskSlot internal constructor(
        val taskId: Long,
        internal val token: Long,
    )

    @Synchronized
    fun enqueue(taskId: Long) {
        if (taskId !in runningSlots) {
            pendingTaskIds.add(taskId)
        }
    }

    @Synchronized
    fun takeReady(): List<TaskSlot> {
        val ready = mutableListOf<TaskSlot>()
        while (runningSlots.size < limit && pendingTaskIds.isNotEmpty()) {
            val taskId = pendingTaskIds.first()
            pendingTaskIds.remove(taskId)
            val token = ++nextSlotToken
            runningSlots[taskId] = token
            ready += TaskSlot(taskId, token)
        }
        return ready
    }

    @Synchronized
    fun pausePending(taskId: Long): Boolean = pendingTaskIds.remove(taskId)

    @Synchronized
    fun finish(slot: TaskSlot): Boolean {
        if (runningSlots[slot.taskId] != slot.token) return false
        runningSlots.remove(slot.taskId)
        return true
    }

    @Synchronized
    fun finishCurrent(taskId: Long): Boolean = runningSlots.remove(taskId) != null

    @Synchronized
    fun remove(taskId: Long): Boolean {
        val removedPending = pendingTaskIds.remove(taskId)
        val removedRunning = runningSlots.remove(taskId) != null
        return removedPending || removedRunning
    }

    @Synchronized
    fun clear() {
        pendingTaskIds.clear()
        runningSlots.clear()
    }

    @Synchronized
    fun updateLimit(value: Int) {
        limit = value.coerceAtLeast(1)
    }

    @Synchronized
    fun isRunning(taskId: Long): Boolean = taskId in runningSlots

    @Synchronized
    fun isCurrent(slot: TaskSlot): Boolean = runningSlots[slot.taskId] == slot.token

    @Synchronized
    fun currentSlot(taskId: Long): TaskSlot? = runningSlots[taskId]?.let { token ->
        TaskSlot(taskId, token)
    }
}
