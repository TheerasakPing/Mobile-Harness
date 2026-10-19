package com.jarves.mh.extensions

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

enum class ExtensionKind { SKILL, MCP_SERVER }
enum class ExtensionCapability { WORKSPACE_READ, WORKSPACE_WRITE, NETWORK, PROCESS_EXEC, PACKAGE_INSTALL, GIT_PUSH, MCP_INVOKE }

data class ExtensionManifest(
    val id: String,
    val version: String,
    val kind: ExtensionKind,
    val source: String,
    val sha256: String,
    val capabilities: Set<ExtensionCapability>,
)

data class ExtensionSummary(
    val manifest: ExtensionManifest,
    val granted: Set<ExtensionCapability>,
)

/** Metadata-only inventory. Nothing here executes an MCP process or skill instruction. */
class ExtensionCatalog(private val directory: File) {
    private val lock = Any()

    init { require(directory.isDirectory || directory.mkdirs()) { "Cannot create extension directory" } }

    fun install(manifest: ExtensionManifest, payload: ByteArray): Unit = synchronized(lock) {
        validId(manifest.id)
        require(manifest.version.isNotBlank() && manifest.version.length <= 64) { "Invalid version" }
        require((manifest.source.startsWith("https://") || manifest.source.startsWith("local://")) &&
            manifest.source.length <= 2048) { "Only HTTPS and local document sources are supported" }
        require(manifest.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256" }
        require(sha256(payload) == manifest.sha256) { "Payload checksum mismatch" }
        require(payload.isNotEmpty()) { "Empty extension payload" }
        val existing = readProperties(manifest.id)
        val keepGrants = existing != null && existing.getProperty("sha256") == manifest.sha256
        val data = Properties().apply {
            setProperty("id", manifest.id)
            setProperty("version", manifest.version)
            setProperty("kind", manifest.kind.name)
            setProperty("source", manifest.source)
            setProperty("sha256", manifest.sha256)
            setProperty("capabilities", manifest.capabilities.joinToString(",") { it.name })
            if (keepGrants) existing!!.stringPropertyNames().filter { it.startsWith("grant.") }
                .forEach { setProperty(it, existing.getProperty(it)) }
        }
        atomicWrite(payloadFile(manifest.id), payload)
        saveProperties(manifest.id, data)
    }

    fun enable(projectId: String, extensionId: String, capabilities: Set<ExtensionCapability>): Unit = synchronized(lock) {
        validId(projectId)
        validId(extensionId)
        val data = requireNotNull(verified(extensionId)) { "Extension missing or checksum invalid" }
        val declared = parseCapabilities(data.getProperty("capabilities"))
        require(declared.containsAll(capabilities)) { "Capability not declared by extension" }
        data.setProperty("grant.$projectId", capabilities.joinToString(",") { it.name })
        saveProperties(extensionId, data)
    }

    fun disable(projectId: String, extensionId: String): Unit = synchronized(lock) {
        validId(projectId)
        validId(extensionId)
        val data = readProperties(extensionId) ?: return@synchronized
        data.remove("grant.$projectId")
        saveProperties(extensionId, data)
    }

    /** Fail closed if metadata or payload changes after approval. */
    fun isAllowed(projectId: String, extensionId: String, capability: ExtensionCapability): Boolean = synchronized(lock) {
        if (!safeId(projectId) || !safeId(extensionId)) return@synchronized false
        val data = verified(extensionId) ?: return@synchronized false
        val granted = data.getProperty("grant.$projectId") ?: return@synchronized false
        parseCapabilities(granted).contains(capability) &&
            parseCapabilities(data.getProperty("capabilities")).contains(capability)
    }

    /** Only healthy, hash-verified catalog entries appear. Never exposes payload bytes or secrets. */
    fun list(projectId: String): List<ExtensionSummary> = synchronized(lock) {
        validId(projectId)
        directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "properties" && !Files.isSymbolicLink(it.toPath()) }
            .map { it.nameWithoutExtension }
            .filter(::safeId)
            .mapNotNull { id ->
                val data = verified(id) ?: return@mapNotNull null
                runCatching {
                    val caps = parseCapabilities(data.getProperty("capabilities"))
                    val grant = parseCapabilities(data.getProperty("grant.$projectId"))
                    ExtensionSummary(
                        manifest = ExtensionManifest(
                            id = id,
                            version = requireNotNull(data.getProperty("version")),
                            kind = ExtensionKind.valueOf(requireNotNull(data.getProperty("kind"))),
                            source = requireNotNull(data.getProperty("source")),
                            sha256 = requireNotNull(data.getProperty("sha256")),
                            capabilities = caps,
                        ),
                        granted = grant.intersect(caps),
                    )
                }.getOrNull()
            }
            .sortedBy { it.manifest.id }
            .toList()
    }

    private fun verified(id: String): Properties? = runCatching {
        val data = readProperties(id) ?: return null
        val file = payloadFile(id)
        if (!file.isFile || Files.isSymbolicLink(file.toPath())) return null
        if (data.getProperty("id") != id || data.getProperty("sha256") != sha256(file.readBytes())) return null
        data
    }.getOrNull()

    private fun readProperties(id: String): Properties? {
        val file = metadataFile(id)
        if (!file.exists()) return null
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Invalid metadata file" }
        return Properties().apply { file.inputStream().use(::load) }
    }

    private fun saveProperties(id: String, data: Properties) {
        val temp = Files.createTempFile(directory.toPath(), ".extensions-", ".tmp").toFile()
        try {
            FileOutputStream(temp).use { stream -> data.store(stream, "Mobile Harness extension inventory"); stream.fd.sync() }
            Files.move(temp.toPath(), metadataFile(id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temp = Files.createTempFile(directory.toPath(), ".payload-", ".tmp").toFile()
        try {
            FileOutputStream(temp).use { stream -> stream.write(bytes); stream.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    private fun metadataFile(id: String) = File(directory, "$id.properties")
    private fun payloadFile(id: String) = File(directory, "$id.payload")
    private fun validId(id: String) { require(safeId(id)) { "Unsafe extension/project ID" } }
    private fun safeId(id: String) = id.matches(Regex("[a-zA-Z0-9_-]{1,128}"))
    private fun parseCapabilities(value: String?): Set<ExtensionCapability> =
        value.orEmpty().split(',').filter { it.isNotBlank() }.map { ExtensionCapability.valueOf(it) }.toSet()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
