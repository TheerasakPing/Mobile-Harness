package com.jarves.mh.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date

/** User-facing recovery status; deliberately no automatic replay of effectful agent tasks. */
class SessionHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = runCatching {
            SessionRecoveryJournal(File(filesDir, "session-journal")).all()
        }
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("Agent Session Recovery", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(10.dp))
                    Text("Interrupted sessions are preserved as history. Review your project before manually resubmitting a task.")
                    Spacer(Modifier.height(12.dp))
                    result.onSuccess { sessions ->
                        if (sessions.isEmpty()) Text("No session history recorded yet.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sessions, key = { it.sessionId }) { entry ->
                                ElevatedCard {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(entry.state.name, style = MaterialTheme.typography.titleMedium)
                                        Text("Agent: ${entry.agentId} · Project: ${entry.projectId}")
                                        Text(DateFormat.getDateTimeInstance().format(Date(entry.updatedAtMillis)),
                                            style = MaterialTheme.typography.bodySmall)
                                        if (entry.state == SessionLifecycle.INTERRUPTED) {
                                            Text("Check filesystem changes before retrying. No action was automatically replayed.",
                                                color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { error ->
                        Text("Cannot read session journal: ${error.message}",
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
