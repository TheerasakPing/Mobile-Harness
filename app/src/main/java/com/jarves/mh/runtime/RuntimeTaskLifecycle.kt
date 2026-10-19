package com.jarves.mh.runtime

import java.io.File

/**
 * Process-scoped supervisor for tasks that can mutate a project.
 * Persistent state is a journal, never a promise that a killed process can resume.
 */
class RuntimeTaskLifecycle(directory: File) {
    private val journal = SessionRecoveryJournal(directory)
    private val registry = TaskCancellationRegistry()
    private val lock = Any()

    fun reconcileOnColdStart(): List<SessionRecord> = synchronized(lock) {
        journal.markInterruptedAfterColdStart()
    }

    fun register(taskId: String, projectId: String, runtimeId: String, cancel: () -> Unit): Boolean =
        synchronized(lock) {
            if (journal.find(taskId) != null) return@synchronized false
            if (!registry.register(taskId, projectId, runtimeId, cancel)) return@synchronized false
            try {
                journal.start(taskId, projectId, runtimeId)
                true
            } catch (error: Throwable) {
                registry.release(taskId)
                throw error
            }
        }

    fun requestStop(taskId: String): Boolean = registry.requestStop(taskId)

    fun finish(taskId: String, outcome: SessionLifecycle) = synchronized(lock) {
        try {
            journal.finish(taskId, outcome)
        } finally {
            registry.release(taskId)
        }
    }

    fun record(taskId: String): SessionRecord? = journal.find(taskId)
}
