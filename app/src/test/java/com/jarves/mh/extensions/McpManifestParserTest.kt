package com.jarves.mh.extensions

import org.junit.Assert.*
import org.junit.Test

class McpManifestParserTest {
    @Test fun acceptsDeclarativeStdioServerWithoutExecutingIt() {
        val json = """
            {"id":"mcp_reader","version":"1","command":"node","args":["server.mjs"],
             "capabilities":["MCP_INVOKE","PROCESS_EXEC","WORKSPACE_READ"]}
        """.trimIndent().toByteArray()
        val result = McpManifestParser.parse(json)
        assertEquals(ExtensionKind.MCP_SERVER, result.kind)
        assertEquals("mcp_reader", result.id)
        assertEquals("local://document/mcp_reader", result.source)
        assertEquals(setOf(ExtensionCapability.MCP_INVOKE, ExtensionCapability.PROCESS_EXEC,
            ExtensionCapability.WORKSPACE_READ), result.capabilities)
        assertEquals(64, result.sha256.length)
    }

    @Test fun rejectsUnrecognizedCapabilitiesAndUnsafeCommands() {
        for (json in listOf(
            """{"id":"reader","version":"1","command":"sh -c rm -rf /","capabilities":["PROCESS_EXEC"]}""",
            """{"id":"reader","version":"1","command":"node","capabilities":["ROOT"]}""",
            """{"id":"../escape","version":"1","command":"node","capabilities":[]}""",
            """{"id":"reader","version":"1","command":"curl","capabilities":[]}""",
        )) assertThrows(IllegalArgumentException::class.java) {
            McpManifestParser.parse(json.toByteArray())
        }
    }

    @Test fun rejectsEmptyAndOversizedManifests() {
        assertThrows(IllegalArgumentException::class.java) { McpManifestParser.parse(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { McpManifestParser.parse(ByteArray(129 * 1024)) }
    }
}
