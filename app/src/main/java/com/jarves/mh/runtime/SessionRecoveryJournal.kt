package com.jarves.mh.runtime

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

enum class SessionLifecycle { RUNNING, INTERRUPTED, COMPLETED, FAILED, CANCELLED }

data class SessionRecord(
    val sessionId: String,
    val projectId: String,
    val agentId: String,
    val state: SessionLifecycle,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * Persistent record of AI jobs. Records process state, not a resumable OS process.
 * Reconciliation happens once on a true cold app process start.
 */
class SessionRecoveryJournal(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val guard = Any()

    init {
        require(directory.isDirectory || directory.mkdirs()) { "Could not create session journal directory" }
    }

    fun start(sessionId: String, projectId: String, agentId: String): SessionRecord = synchronized(guard) {
        validateId(sessionId)
        validateId(projectId)
        validateId(agentId)
        require(!fileFor(sessionId).exists()) { "Session already exists: $sessionId" }
        val now = clock()
        SessionRecord(sessionId, projectId, agentId, SessionLifecycle.RUNNING, now, now)
            .also(::save)
    }

    fun finish(sessionId: String, outcome: SessionLifecycle): SessionRecord = synchronized(guard) {
        require(outcome in TERMINAL) { "Outcome must be terminal" }
        val previous = requireNotNull(read(sessionId)) { "Unknown session: $sessionId" }
        require(previous.state == SessionLifecycle.RUNNING) {
            "Cannot finish session in state ${previous.state}"
        }
        previous.copy(state = outcome, updatedAtMillis = clock()).also(::save)
    }

    fun find(sessionId: String): SessionRecord? = synchronized(guard) { read(sessionId) }

    fun all(): List<SessionRecord> = synchronized(guard) {
        directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "properties" && !Files.isSymbolicLink(it.toPath()) }
            .map { it.nameWithoutExtension }
            .mapNotNull(::read)
            .sortedByDescending { it.updatedAtMillis }
            .toList()
    }

    /** Never automatically replay an interrupted job with external side effects. */
    fun markInterruptedAfterColdStart(): List<SessionRecord> = synchronized(guard) {
        all().filter { it.state == SessionLifecycle.RUNNING }
            .map { old -> old.copy(state = SessionLifecycle.INTERRUPTED, updatedAtMillis = clock()).also(::save) }
    }

    private fun fileFor(sessionId: String): File {
        validateId(sessionId)
        return File(directory, "$sessionId.properties")
    }

    private fun read(sessionId: String): SessionRecord? {
        val file = fileFor(sessionId)
        if (!file.exists()) return null
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Invalid journal record" }
        val properties = Properties().apply { file.inputStream().use(::load) }
        val savedSessionId = requireNotNull(properties.getProperty("sessionId"))
        require(savedSessionId == sessionId) { "Journal session ID mismatch" }
        val projectId = requireNotNull(properties.getProperty("projectId")).also(::validateId)
        val agentId = requireNotNull(properties.getProperty("agentId")).also(::validateId)
        return SessionRecord(
            sessionId = savedSessionId,
            projectId = projectId,
            agentId = agentId,
            state = SessionLifecycle.valueOf(requireNotNull(properties.getProperty("state"))),
            startedAtMillis = requireNotNull(properties.getProperty("startedAtMillis")).toLong(),
            updatedAtMillis = requireNotNull(properties.getProperty("updatedAtMillis")).toLong(),
        )
    }

    private fun save(record: SessionRecord) {
        val target = fileFor(record.sessionId)
        val temporary = Files.createTempFile(directory.toPath(), ".journal-", ".tmp").toFile()
        val properties = Properties().apply {
            setProperty("sessionId", record.sessionId)
            setProperty("projectId", record.projectId)
            setProperty("agentId", record.agentId)
            setProperty("state", record.state.name)
            setProperty("startedAtMillis", record.startedAtMillis.toString())
            setProperty("updatedAtMillis", record.updatedAtMillis.toString())
        }
        try {
            FileOutputStream(temporary).use { stream ->
                properties.store(stream, "Mobile Harness session recovery journal")
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun validateId(id: String) {
        require(ID.matches(id)) { "Unsafe session/project/agent ID" }
    }

    private companion object {
        val ID = Regex("[a-zA-Z0-9_-]{1,128}")
        val TERMINAL = setOf(SessionLifecycle.COMPLETED, SessionLifecycle.FAILED, SessionLifecycle.CANCELLED)
    }
}
