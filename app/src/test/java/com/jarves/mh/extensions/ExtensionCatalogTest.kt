package com.jarves.mh.extensions

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ExtensionCatalogTest {
    private fun hash(payload: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(payload).joinToString("") { "%02x".format(it) }

    @Test fun installChecksChecksumBeforeSaving() {
        val root = Files.createTempDirectory("mh-extension-test").toFile()
        try {
            val bytes = "safe".toByteArray()
            val catalog = ExtensionCatalog(root)
            val manifest = ExtensionManifest("safe", "1", ExtensionKind.SKILL, "https://example.org/safe", "0".repeat(64), setOf(ExtensionCapability.WORKSPACE_READ))
            assertThrows(IllegalArgumentException::class.java) { catalog.install(manifest, bytes) }
            assertFalse(root.resolve("safe.properties").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun enabledCapsAreProjectScopedAndDefaultDeny() {
        val root = Files.createTempDirectory("mh-extension-test").toFile()
        try {
            val bytes = "read-only".toByteArray()
            val catalog = ExtensionCatalog(root)
            catalog.install(ExtensionManifest("reader", "1", ExtensionKind.SKILL, "https://example.org/reader", hash(bytes), setOf(ExtensionCapability.WORKSPACE_READ)), bytes)
            assertFalse(catalog.isAllowed("a", "reader", ExtensionCapability.WORKSPACE_READ))
            catalog.enable("a", "reader", setOf(ExtensionCapability.WORKSPACE_READ))
            assertTrue(catalog.isAllowed("a", "reader", ExtensionCapability.WORKSPACE_READ))
            assertFalse(catalog.isAllowed("b", "reader", ExtensionCapability.WORKSPACE_READ))
            assertFalse(catalog.isAllowed("a", "reader", ExtensionCapability.PROCESS_EXEC))
            assertThrows(IllegalArgumentException::class.java) { catalog.enable("a", "reader", setOf(ExtensionCapability.NETWORK)) }
        } finally { root.deleteRecursively() }
    }

    @Test fun storedGrantInvalidAfterPayloadTampering() {
        val root = Files.createTempDirectory("mh-extension-test").toFile()
        try {
            val bytes = "read-only".toByteArray()
            val catalog = ExtensionCatalog(root)
            catalog.install(ExtensionManifest("reader", "1", ExtensionKind.MCP_SERVER, "https://example.org/reader", hash(bytes), setOf(ExtensionCapability.WORKSPACE_READ)), bytes)
            catalog.enable("a", "reader", setOf(ExtensionCapability.WORKSPACE_READ))
            root.resolve("reader.payload").writeText("replaced")
            assertFalse(ExtensionCatalog(root).isAllowed("a", "reader", ExtensionCapability.WORKSPACE_READ))
        } finally { root.deleteRecursively() }
    }

    @Test fun newDigestRevokesOldApprovals() {
        val root = Files.createTempDirectory("mh-extension-test").toFile()
        try {
            val catalog = ExtensionCatalog(root)
            val first = "v1".toByteArray()
            val manifest = ExtensionManifest("reader", "1", ExtensionKind.SKILL, "https://example.org/reader", hash(first), setOf(ExtensionCapability.WORKSPACE_READ))
            catalog.install(manifest, first)
            catalog.enable("a", "reader", setOf(ExtensionCapability.WORKSPACE_READ))
            val updated = "v2".toByteArray()
            catalog.install(manifest.copy(version = "2", sha256 = hash(updated)), updated)
            assertFalse(catalog.isAllowed("a", "reader", ExtensionCapability.WORKSPACE_READ))
        } finally { root.deleteRecursively() }
    }

    @Test fun disableRevokesPersistedGrant() {
        val root = Files.createTempDirectory("mh-extension-test").toFile()
        try {
            val bytes = "read-only".toByteArray()
            val catalog = ExtensionCatalog(root)
            catalog.install(ExtensionManifest("reader", "1", ExtensionKind.SKILL, "https://example.org/reader", hash(bytes), setOf(ExtensionCapability.WORKSPACE_READ)), bytes)
            catalog.enable("a", "reader", setOf(ExtensionCapability.WORKSPACE_READ))
            catalog.disable("a", "reader")
            assertFalse(ExtensionCatalog(root).isAllowed("a", "reader", ExtensionCapability.WORKSPACE_READ))
        } finally { root.deleteRecursively() }
    }
}
