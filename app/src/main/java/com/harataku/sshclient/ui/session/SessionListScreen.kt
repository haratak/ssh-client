package com.harataku.sshclient.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harataku.sshclient.tmux.TmuxSessionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    tmuxSessions: List<TmuxSessionInfo>,
    isLoading: Boolean,
    onSessionClick: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = context.packageManager
        .getPackageInfo(context.packageName, 0).versionName ?: ""

    var sessionToDelete by remember { mutableStateOf<String?>(null) }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session") },
            text = { Text("Kill tmux session \"$sessionToDelete\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(sessionToDelete!!)
                    sessionToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    TextButton(onClick = onDisconnect) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewSession) {
                Icon(Icons.Default.Add, contentDescription = "New Session")
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                tmuxSessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "No sessions",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onNewSession) {
                            Text("New Session")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tmuxSessions, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onSessionClick(session.name) },
                                onDelete = { sessionToDelete = session.name }
                            )
                        }
                    }
                }
            }

            Text(
                text = "v$versionName",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: TmuxSessionInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Session name
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium
                )
                // cwd (shortened to last 2 segments)
                if (session.cwd.isNotEmpty()) {
                    val shortCwd = session.cwd.split("/").takeLast(2).joinToString("/")
                    Text(
                        text = shortCwd,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Branch + command info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (session.gitBranch.isNotEmpty()) {
                        Text(
                            text = session.gitBranch,
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50),
                            maxLines = 1
                        )
                    }
                    if (session.currentCommand.isNotEmpty() && session.currentCommand != "bash" && session.currentCommand != "zsh" && session.currentCommand != "fish") {
                        Text(
                            text = session.currentCommand,
                            fontSize = 11.sp,
                            color = Color(0xFFFFC107),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${session.windows} win",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Status badge
                if (session.attached) {
                    Text(
                        text = "attached",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (session.currentCommand.isNotEmpty() && session.currentCommand != "bash" && session.currentCommand != "zsh" && session.currentCommand != "fish") {
                    Text(
                        text = "Running",
                        fontSize = 10.sp,
                        color = Color(0xFFFFC107)
                    )
                } else {
                    Text(
                        text = "Idle",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
