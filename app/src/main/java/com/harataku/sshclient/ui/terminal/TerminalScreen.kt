package com.harataku.sshclient.ui.terminal

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.harataku.sshclient.ssh.SshSessionManager
import com.harataku.sshclient.terminal.TerminalSession
import com.harataku.sshclient.tmux.TmuxSessionInfo
import com.harataku.sshclient.ui.connect.ConnectionState
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    terminalSession: TerminalSession,
    sshSessionManager: SshSessionManager? = null,
    sessions: List<TmuxSessionInfo> = emptyList(),
    currentSessionName: String? = null,
    connectionState: ConnectionState = ConnectionState.Connected,
    onSessionTab: (String) -> Unit = {},
    onNewSession: () -> Unit = {},
    onReconnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var composingText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null || sshSessionManager == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                // Get file name from URI
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "upload_${System.currentTimeMillis()}"

                // Get remote cwd + create uploads/ subdirectory
                val cwd = sshSessionManager.getTmuxPaneCwd()
                val remotePath = "$cwd/uploads"
                sshSessionManager.exec("mkdir -p '$remotePath'")

                // Upload via SFTP
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    sshSessionManager.uploadFile(inputStream, remotePath, fileName)
                }

                // Add uploads/ to .gitignore if not already there
                sshSessionManager.exec("grep -qxF 'uploads/' '$cwd/.gitignore' 2>/dev/null || echo 'uploads/' >> '$cwd/.gitignore'")

                Toast.makeText(context, "$fileName → uploads/", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                uploading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .systemBarsPadding()
            .imePadding()
    ) {
        // Session tab bar at the top
        if (sessions.isNotEmpty()) {
            SessionTabBar(
                sessions = sessions,
                currentSessionName = currentSessionName,
                onSessionTab = onSessionTab,
                onNewSession = onNewSession,
            )
        }

        // Terminal view fills remaining space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    TerminalView(context).also { view ->
                        view.attachSession(terminalSession)
                        terminalSession.onRedraw = { view.triggerRedraw() }
                        view.onComposingTextChanged = { composingText = it }
                        view.post { view.showKeyboard() }
                        terminalView = view
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Composing text preview (voice input)
            if (composingText.isNotEmpty()) {
                Text(
                    text = composingText,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Connection status overlay
            when (connectionState) {
                is ConnectionState.Reconnecting -> {
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
                is ConnectionState.Disconnected -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xCC000000))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Disconnected", color = Color.White, fontSize = 16.sp)
                        TextButton(onClick = onReconnect) {
                            Text("Retry", color = Color(0xFF64B5F6), fontSize = 14.sp)
                        }
                    }
                }
                else -> {}
            }
        }

        if (uploading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Text("Uploading...", color = Color.White, fontSize = 13.sp)
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
            onUpload = { filePickerLauncher.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
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
