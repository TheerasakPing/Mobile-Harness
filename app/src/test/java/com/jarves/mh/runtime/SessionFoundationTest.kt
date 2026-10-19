package com.jarves.mh.runtime

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SessionFoundationTest {
    @Test fun journalPersistsAndReconcilesUnfinishedJobs() {
        val directory = Files.createTempDirectory("mh-recovery-test").toFile()
        try {
            val store = SessionRecoveryJournal(directory)
            store.start("s1", "p1", "claude-code")
            store.start("s2", "p2", "antigravity")
            store.finish("s2", SessionLifecycle.COMPLETED)
            val reopened = SessionRecoveryJournal(directory)
            assertEquals(listOf("s1"), reopened.markInterruptedAfterColdStart().map { it.sessionId })
            assertEquals(SessionLifecycle.INTERRUPTED, reopened.find("s1")!!.state)
            assertEquals(SessionLifecycle.COMPLETED, reopened.find("s2")!!.state)
            assertTrue(reopened.markInterruptedAfterColdStart().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun journalRejectsUnsafeIdsAndIllegalTransitions() {
        val directory = Files.createTempDirectory("mh-recovery-test").toFile()
        try {
            val store = SessionRecoveryJournal(directory)
            assertThrows(IllegalArgumentException::class.java) {
                store.start("../escape", "p1", "claude-code")
            }
            store.start("s1", "p1", "claude-code")
            assertThrows(IllegalArgumentException::class.java) {
                store.start("s1", "p1", "claude-code")
            }
            store.finish("s1", SessionLifecycle.FAILED)
            assertThrows(IllegalArgumentException::class.java) {
                store.finish("s1", SessionLifecycle.COMPLETED)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancellationNeverTargetsUnrelatedProject() {
        val registry = TaskCancellationRegistry()
        val first = AtomicInteger()
        val second = AtomicInteger()
        assertTrue(registry.register("s1", "p1") { first.incrementAndGet() })
        assertTrue(registry.register("s2", "p2") { second.incrementAndGet() })
        assertFalse(registry.register("s3", "p1") { })
        assertTrue(registry.requestStop("s1"))
        assertFalse(registry.requestStop("s1"))
        assertFalse(registry.requestStop("missing"))
        assertEquals(1, first.get())
        assertEquals(0, second.get())
    }

    @Test fun releasedTaskCannotCancelNewLease() {
        val registry = TaskCancellationRegistry()
        val stopped = AtomicInteger()
        assertTrue(registry.register("previous", "project") { stopped.incrementAndGet() })
        assertTrue(registry.release("previous"))
        assertTrue(registry.register("next", "project") { stopped.incrementAndGet() })
        assertFalse(registry.requestStop("previous"))
        assertEquals(0, stopped.get())
        assertTrue(registry.requestStop("next"))
        assertEquals(1, stopped.get())
    }

    @Test fun parallelStartsAdmitOnlyOneLeaseForProject() {
        val registry = TaskCancellationRegistry()
        val start = CountDownLatch(1)
        val accepted = AtomicInteger()
        val threads = (1..16).map { index ->
            Thread {
                start.await()
                if (registry.register("task-$index", "shared") {}) accepted.incrementAndGet()
            }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, accepted.get())
    }
}
