package com.jarves.mh.runtime

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RuntimeTaskLifecycleTest {
    @Test fun coldStartMarksOnlyStaleRunningJobsInterrupted() {
        val folder = Files.createTempDirectory("mh-lifecycle").toFile()
        try {
            val first = RuntimeTaskLifecycle(folder)
            assertTrue(first.register("a", "project-a", "claude-code") {})
            assertTrue(first.register("b", "project-b", "dsh") {})
            first.finish("b", SessionLifecycle.COMPLETED)
            val second = RuntimeTaskLifecycle(folder)
            assertEquals(listOf("a"), second.reconcileOnColdStart().map { it.sessionId })
            assertEquals(SessionLifecycle.INTERRUPTED, second.record("a")!!.state)
            assertEquals(SessionLifecycle.COMPLETED, second.record("b")!!.state)
            assertTrue(second.reconcileOnColdStart().isEmpty())
        } finally { folder.deleteRecursively() }
    }

    @Test fun cancellationCannotInterruptAnotherProjectOrRuntime() {
        val folder = Files.createTempDirectory("mh-lifecycle").toFile()
        try {
            val lifecycle = RuntimeTaskLifecycle(folder)
            var stoppedA = 0
            var stoppedB = 0
            assertTrue(lifecycle.register("a", "project-a", "claude-code") { stoppedA++ })
            assertFalse(lifecycle.register("c", "project-c", "claude-code") {})
            assertTrue(lifecycle.register("b", "project-b", "dsh") { stoppedB++ })
            assertTrue(lifecycle.requestStop("a"))
            assertFalse(lifecycle.requestStop("a"))
            assertEquals(1, stoppedA)
            assertEquals(0, stoppedB)
            lifecycle.finish("a", SessionLifecycle.CANCELLED)
            assertFalse(lifecycle.requestStop("a"))
            assertEquals(SessionLifecycle.CANCELLED, lifecycle.record("a")!!.state)
        } finally { folder.deleteRecursively() }
    }

    @Test fun failureToWriteJournalDoesNotLeaveLeaseHeld() {
        val folder = Files.createTempDirectory("mh-lifecycle").toFile()
        try {
            val lifecycle = RuntimeTaskLifecycle(folder)
            lifecycle.register("session", "one", "claude-code") {}
            lifecycle.finish("session", SessionLifecycle.FAILED)
            assertFalse(lifecycle.register("session", "one", "claude-code") {})
            assertTrue(lifecycle.register("another", "one", "claude-code") {})
        } finally { folder.deleteRecursively() }
    }

    @Test fun terminalResultDoesNotAutoReplaySideEffects() {
        val folder = Files.createTempDirectory("mh-lifecycle").toFile()
        try {
            val lifecycle = RuntimeTaskLifecycle(folder)
            var executionCount = 0
            lifecycle.register("a", "project-a", "dsh") { executionCount++ }
            assertEquals(listOf("a"), RuntimeTaskLifecycle(folder).reconcileOnColdStart().map { it.sessionId })
            assertEquals(0, executionCount)
        } finally { folder.deleteRecursively() }
    }
}
