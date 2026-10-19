package com.jarves.mh.extensions

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Local verified inventory only. Importing a skill never executes it or starts an MCP server. */
class ExtensionManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)?.takeIf {
            it.matches(Regex("[a-zA-Z0-9_-]{1,128}"))
        }
        val catalog = ExtensionCatalog(File(filesDir, "extensions"))
        setContent {
            MaterialTheme { ExtensionManagerScreen(catalog, projectId, onClose = ::finish) }
        }
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        fun open(context: android.content.Context, projectId: String?): Intent =
            Intent(context, ExtensionManagerActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionManagerScreen(
    catalog: ExtensionCatalog,
    projectId: String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var inventory by remember { mutableStateOf(catalog.list(projectId ?: "global")) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ExtensionSummary?>(null) }
    var chosen by remember { mutableStateOf(setOf<ExtensionCapability>()) }
    fun refresh() { inventory = catalog.list(projectId ?: "global") }
    val importSkill = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                    null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty()
                require(name.endsWith(".md", true) || name.endsWith(".txt", true)) {
                    "Choose a Markdown or plain-text skill"
                }
                val data = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    while (true) {
                        val n = stream.read(buffer)
                        if (n == -1) break
                        data.write(buffer, 0, n)
                        require(data.size() <= 128 * 1024) { "Skill exceeds 128 KiB" }
                    }
                } ?: error("Could not read selected document")
                val bytes = data.toByteArray()
                require(bytes.isNotEmpty()) { "Empty skill" }
                val id = "local_" + UUID.randomUUID().toString().replace("-", "")
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                }
                catalog.install(ExtensionManifest(id, "1", ExtensionKind.SKILL,
                    "local://document/$id", sha, emptySet()), bytes)
                refresh()
                message = "Imported as a read-only document. No code was executed."
            }.onFailure { message = it.message ?: "Import failed" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP & Skills Manager") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back") } },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(16.dp)) {
            Text("Verified inventory · SHA-256 · project-scoped permissions",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (projectId == null) {
                Text("Select a project before granting permissions.",
                    color = MaterialTheme.colorScheme.error)
            } else {
                Text("Project: $projectId", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { importSkill.launch("*/*") }) { Text("Import local Skill (.md/.txt)") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(inventory, key = { it.manifest.id }) { item ->
                    ElevatedCard {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(item.manifest.id, style = MaterialTheme.typography.titleMedium)
                            Text("${item.manifest.kind} · v${item.manifest.version}")
                            Text(item.manifest.source, style = MaterialTheme.typography.bodySmall)
                            Text("SHA-256: " + item.manifest.sha256.take(16) + "…",
                                style = MaterialTheme.typography.labelSmall)
                            if (item.manifest.capabilities.isEmpty()) {
                                Text("Document only · no executable permissions",
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Declared: " + item.manifest.capabilities.joinToString())
                                Text("Granted: " + item.granted.joinToString().ifBlank { "None" })
                                Row {
                                    OutlinedButton(onClick = {
                                        chosen = item.granted
                                        pending = item
                                    }, enabled = projectId != null) { Text("Permissions") }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        runCatching { catalog.disable(projectId!!, item.manifest.id); refresh() }
                                            .onFailure { message = it.message }
                                    }, enabled = projectId != null && item.granted.isNotEmpty()) {
                                        Text("Revoke")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { entry ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Approve capabilities for ${entry.manifest.id}?") },
            text = {
                Column {
                    Text("Only enable trusted extensions. MCP servers and scripts will not start from this screen.")
                    entry.manifest.capabilities.sortedBy { it.name }.forEach { cap ->
                        Row {
                            Checkbox(checked = cap in chosen, onCheckedChange = { checked ->
                                chosen = if (checked) chosen + cap else chosen - cap
                            })
                            Text(cap.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { catalog.enable(projectId!!, entry.manifest.id, chosen); refresh() }
                        .onFailure { message = it.message }
                    pending = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
