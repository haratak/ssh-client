package com.harataku.sshclient.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.harataku.sshclient.terminal.TerminalSession
import com.harataku.sshclient.tmux.TmuxSessionInfo
import com.harataku.sshclient.ui.connect.ConnectionState

@Composable
fun TerminalScreen(
    terminalSession: TerminalSession,
    sessions: List<TmuxSessionInfo> = emptyList(),
    currentSessionName: String? = null,
    connectionState: ConnectionState = ConnectionState.Connected,
    onSessionTab: (String) -> Unit = {},
    onNewSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .systemBarsPadding()
            .imePadding()
    ) {
        AndroidView(
            factory = { context ->
                TerminalView(context).also { view ->
                    view.attachSession(terminalSession)
                    terminalSession.onRedraw = { view.triggerRedraw() }
                    view.post { view.showKeyboard() }
                    terminalView = view
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Session tab bar at the top
        if (sessions.isNotEmpty()) {
            SessionTabBar(
                sessions = sessions,
                currentSessionName = currentSessionName,
                onSessionTab = onSessionTab,
                onNewSession = onNewSession,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Reconnecting indicator
        if (connectionState is ConnectionState.Reconnecting) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Text("Reconnecting...", color = Color.White, fontSize = 14.sp)
            }
        }

        ModifierKeyBar(
            onShortcut = { action ->
                when (action) {
                    is ShortcutAction.SendByte -> terminalSession.writeByte(action.byte)
                    is ShortcutAction.SendText -> terminalSession.writeInput(action.text)
                }
            },
            onPaste = { terminalView?.pasteFromClipboard() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0xFF2D2D2D))
        )
    }
}

@Composable
private fun SessionTabBar(
    sessions: List<TmuxSessionInfo>,
    currentSessionName: String?,
    onSessionTab: (String) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D.toInt()))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEach { session ->
            val isActive = session.name == currentSessionName
            TextButton(
                onClick = { onSessionTab(session.name) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = session.name,
                    fontSize = 13.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else Color(0xFFAAAAAA.toInt())
                )
            }
        }
        TextButton(
            onClick = onNewSession,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text("+", fontSize = 16.sp, color = Color(0xFFAAAAAA.toInt()))
        }
    }
}
