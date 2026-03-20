package com.harataku.sshclient.ui.session

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harataku.sshclient.session.Session
import com.harataku.sshclient.session.SessionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    sessions: List<Session>,
    onSessionClick: (Session) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = context.packageManager
        .getPackageInfo(context.packageName, 0).versionName ?: ""

    var sessionToDelete by remember { mutableStateOf<Session?>(null) }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session") },
            text = { Text("Delete session \"${sessionToDelete!!.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(sessionToDelete!!.id)
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
            TopAppBar(title = { Text("Sessions") })
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
            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "No sessions yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNewSession) {
                        Text("Add Connection")
                    }
                }
            } else {
                val pinned = sessions.filter { it.pinned }
                val active = sessions.filter { !it.pinned && it.state == SessionState.ACTIVE }
                val recent = sessions.filter { !it.pinned && it.state != SessionState.ACTIVE }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pinned.isNotEmpty()) {
                        item {
                            Text(
                                "Pinned",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(pinned, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onSessionClick(session) },
                                onLongClick = { sessionToDelete = session },
                                onTogglePin = { onTogglePin(session.id) }
                            )
                        }
                    }

                    if (active.isNotEmpty()) {
                        item {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (pinned.isNotEmpty()) 12.dp else 0.dp, bottom = 4.dp)
                            )
                        }
                        items(active, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onSessionClick(session) },
                                onLongClick = { sessionToDelete = session },
                                onTogglePin = { onTogglePin(session.id) }
                            )
                        }
                    }

                    if (recent.isNotEmpty()) {
                        item {
                            Text(
                                "Recent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = if (pinned.isNotEmpty() || active.isNotEmpty()) 12.dp else 0.dp,
                                    bottom = 4.dp
                                )
                            )
                        }
                        items(recent, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onSessionClick(session) },
                                onLongClick = { sessionToDelete = session },
                                onTogglePin = { onTogglePin(session.id) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${session.username}@${session.host}:${session.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (session.tmuxSessionName != null) {
                    Text(
                        text = "tmux: ${session.tmuxSessionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SessionStateBadge(session.state)
                if (session.pinned) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStateBadge(state: SessionState) {
    val (label, color) = when (state) {
        SessionState.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
        SessionState.IDLE -> "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.RUNNING -> "Running" to MaterialTheme.colorScheme.tertiary
        SessionState.NEEDS_REVIEW -> "Review" to MaterialTheme.colorScheme.error
        SessionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.outline
        SessionState.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = {
            Text(label, fontSize = 11.sp)
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = color
        ),
        modifier = Modifier.height(28.dp)
    )
}
