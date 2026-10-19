package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ScopedTaskCancellationTest {
    @Test fun refusesConcurrentSessionsOnTheSameAgentRuntime() {
        val registry = TaskCancellationRegistry()
        assertTrue(registry.register("session-a", "project-a", "claude-code") {})
        assertFalse(registry.register("session-b", "project-b", "claude-code") {})
        assertTrue(registry.register("session-c", "project-b", "deepseek-harness") {})
    }

    @Test fun oneAgentRuntimeBecomesAvailableAfterOwnerReleases() {
        val registry = TaskCancellationRegistry()
        assertTrue(registry.register("old", "project-a", "claude-code") {})
        assertTrue(registry.release("old"))
        assertTrue(registry.register("new", "project-b", "claude-code") {})
        assertEquals("new", registry.activeTaskFor("project-b"))
    }

    @Test fun staleStopCannotInterruptNextSession() {
        val registry = TaskCancellationRegistry()
        val first = AtomicInteger()
        val second = AtomicInteger()
        assertTrue(registry.register("old", "project", "claude-code") { first.incrementAndGet() })
        assertTrue(registry.release("old"))
        assertTrue(registry.register("new", "project", "claude-code") { second.incrementAndGet() })
        assertFalse(registry.requestStop("old"))
        assertTrue(registry.requestStop("new"))
        assertFalse(registry.requestStop("new"))
        assertEquals(0, first.get())
        assertEquals(1, second.get())
    }
}
