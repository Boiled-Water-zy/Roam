package com.roami.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskCapsuleStateTest {
    private fun task(session: String, state: TaskNoticeState) = CapsuleTask(session, session, "detail", state)

    @Test fun waitingPreemptsCompletionThenCompletionExpires() {
        val state = TaskCapsuleState()
        state.onSnapshot(listOf(task("running", TaskNoticeState.RUNNING)), emptyList(), 1)
        state.onEvent(task("done", TaskNoticeState.FINISHED), 1, 2)
        state.onEvent(task("approval", TaskNoticeState.WAITING), 2, 3)
        assertEquals("approval", state.current(3)?.session)
        state.resolve("approval", 4)
        assertEquals("done", state.current(4)?.session)
        assertEquals("running", state.current(4 + TaskCapsuleState.FLASH_MS)?.session)
    }

    @Test fun resolvedWaitingDoesNotReturnFromStaleSnapshot() {
        val state = TaskCapsuleState()
        val waiting = listOf(task("approval", TaskNoticeState.WAITING))
        state.onSnapshot(emptyList(), waiting, 1)
        state.resolve("approval", 2)
        state.onSnapshot(emptyList(), waiting, 3)
        assertNull(state.current(3))
    }

    @Test fun repeatedEventDoesNotRestartCompletionPill() {
        val state = TaskCapsuleState()
        val done = task("done", TaskNoticeState.FINISHED)
        state.onEvent(done, 42, 1)
        state.onEvent(done, 42, 10_000)
        assertNull(state.current(1 + TaskCapsuleState.FLASH_MS))
    }

    @Test fun completionsQueueBehindApproval() {
        val state = TaskCapsuleState()
        state.onEvent(task("approval", TaskNoticeState.WAITING), 1, 1)
        state.onEvent(task("first", TaskNoticeState.FINISHED), 2, 2)
        state.onEvent(task("second", TaskNoticeState.FINISHED), 3, 3)
        assertEquals("approval", state.current(3)?.session)
        state.resolve("approval", 4)
        assertEquals("first", state.current(4)?.session)
        assertEquals("second", state.current(4 + TaskCapsuleState.FLASH_MS)?.session)
    }
}
