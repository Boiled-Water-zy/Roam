package com.roami.app

data class CapsuleTask(val session: String, val title: String, val body: String,
                       val state: TaskNoticeState, val actions: List<String> = emptyList())

class TaskCapsuleState {
    private val waiting = linkedMapOf<String, CapsuleTask>()
    private val resolved = mutableMapOf<String, Long>()
    private val recentEvents = linkedSetOf<Long>()
    private var running: CapsuleTask? = null
    private var flash: CapsuleTask? = null
    private var flashUntil = 0L
    private val queued = ArrayDeque<CapsuleTask>()

    fun reset() {
        waiting.clear(); resolved.clear(); recentEvents.clear()
        running = null; flash = null; flashUntil = 0L; queued.clear()
    }

    fun onEvent(task: CapsuleTask, eventId: Long, now: Long) {
        if (eventId > 0 && !recentEvents.add(eventId)) return
        while (recentEvents.size > 128) recentEvents.remove(recentEvents.first())
        when (task.state) {
            TaskNoticeState.WAITING -> {
                resolved.remove(task.session)
                waiting[task.session] = task
                if (running?.session == task.session) running = null
                if (flash != null) flashUntil = 0L
            }
            TaskNoticeState.FINISHED, TaskNoticeState.FAILED -> {
                waiting.remove(task.session)
                if (running?.session == task.session) running = null
                if (flash == null) {
                    flash = task
                    flashUntil = if (waiting.isEmpty()) now + FLASH_MS else 0L
                } else queued.addLast(task)
            }
            TaskNoticeState.RUNNING -> running = task
        }
    }

    fun onSnapshot(runningItems: List<CapsuleTask>, waitingItems: List<CapsuleTask>, now: Long) {
        resolved.entries.removeAll { now >= it.value }
        val seen = waitingItems.map { it.session }.toSet()
        waiting.keys.retainAll(seen)
        waitingItems.forEach { task ->
            if (task.session !in resolved) waiting.putIfAbsent(task.session, task)
        }
        running = runningItems.firstOrNull { it.session !in resolved && it.session !in waiting }
    }

    fun resolve(session: String, now: Long) {
        waiting.remove(session)
        resolved[session] = now + RESOLVED_MS
    }

    fun current(now: Long): CapsuleTask? {
        waiting.values.firstOrNull()?.let { return it }
        if (flash != null && flashUntil == 0L) flashUntil = now + FLASH_MS
        if (now < flashUntil) return flash
        flash = null
        if (queued.isNotEmpty()) {
            flash = queued.removeFirst()
            flashUntil = now + FLASH_MS
            return flash
        }
        return running
    }

    fun nextChangeDelay(now: Long): Long? = if (waiting.isEmpty() && flash != null && now < flashUntil) flashUntil - now else null

    companion object {
        const val FLASH_MS = 15_000L
        private const val RESOLVED_MS = 30_000L
    }
}
