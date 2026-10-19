package com.jarves.mh.runtime

/** Process-local dispatch guard. Each Stop command must target an explicit task ID. */
class TaskCancellationRegistry {
    private data class Task(val projectId: String, val cancel: () -> Unit, var stopping: Boolean = false)
    private val tasks = mutableMapOf<String, Task>()
    private val projectOwners = mutableMapOf<String, String>()
    private val lock = Any()

    fun register(taskId: String, projectId: String, cancel: () -> Unit): Boolean = synchronized(lock) {
        require(taskId.isNotBlank() && projectId.isNotBlank())
        if (tasks.containsKey(taskId) || projectOwners.containsKey(projectId)) return@synchronized false
        tasks[taskId] = Task(projectId, cancel)
        projectOwners[projectId] = taskId
        true
    }

    /** Returns false on unknown/already stopping task; never cancels unrelated work. */
    fun requestStop(taskId: String): Boolean {
        val cancel = synchronized(lock) {
            val task = tasks[taskId] ?: return false
            if (task.stopping) return false
            task.stopping = true
            task.cancel
        }
        cancel()
        return true
    }

    /** Stale completion cannot release a newer task's lease. */
    fun release(taskId: String): Boolean = synchronized(lock) {
        val task = tasks.remove(taskId) ?: return@synchronized false
        if (projectOwners[task.projectId] == taskId) projectOwners.remove(task.projectId)
        true
    }

    fun activeTaskFor(projectId: String): String? = synchronized(lock) { projectOwners[projectId] }
    fun activeCount(): Int = synchronized(lock) { tasks.size }
}
