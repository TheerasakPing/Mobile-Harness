package com.jarves.mh.extensions

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Imports a declarative local MCP server description. Parsing never starts a process;
 * execution still requires explicit capability grants and a separate runtime broker.
 */
object McpManifestParser {
    fun parse(payload: ByteArray): ExtensionManifest {
        require(payload.isNotEmpty() && payload.size <= 128 * 1024) { "MCP manifest exceeds 128 KiB or is empty" }
        return try {
            val json = JSONObject(payload.toString(Charsets.UTF_8))
            val id = json.getString("id")
            require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Unsafe MCP ID" }
            val version = json.optString("version", "1")
            require(version.isNotBlank() && version.length <= 64) { "Invalid MCP version" }
            val command = json.getString("command")
            require(command in setOf("node", "python3")) { "Only local node/python3 stdio manifests are supported" }
            val args = json.optJSONArray("args")
            if (args != null) {
                require(args.length() <= 32) { "Too many arguments" }
                for (i in 0 until args.length()) {
                    val arg = args.getString(i)
                    require(arg.matches(Regex("[A-Za-z0-9_./:=+-]{1,256}")) &&
                        arg.split('/').none { it == ".." }) { "Unsafe MCP argument" }
                }
            }
            val declared = mutableSetOf<ExtensionCapability>()
            val capabilities = json.optJSONArray("capabilities")
            if (capabilities != null) {
                require(capabilities.length() <= ExtensionCapability.entries.size)
                for (i in 0 until capabilities.length()) {
                    val item = capabilities.getString(i)
                    require(ExtensionCapability.entries.any { it.name == item }) { "Unknown MCP capability" }
                    declared += ExtensionCapability.valueOf(item)
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload).joinToString("") { "%02x".format(it) }
            ExtensionManifest(id, version, ExtensionKind.MCP_SERVER,
                "local://document/$id", digest, declared)
        } catch (exception: Exception) {
            throw IllegalArgumentException("Invalid MCP manifest: ${exception.message}", exception)
        }
    }
}
