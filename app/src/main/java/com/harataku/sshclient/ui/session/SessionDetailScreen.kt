package com.harataku.sshclient.ui.session

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.harataku.sshclient.ssh.SshSessionManager
import com.harataku.sshclient.terminal.TerminalSession
import com.harataku.sshclient.ui.connect.ConnectionState
import com.harataku.sshclient.ui.terminal.ModifierKeyBar
import com.harataku.sshclient.ui.terminal.ShortcutAction
import com.harataku.sshclient.ui.terminal.TerminalView
import kotlinx.coroutines.launch

enum class SessionTab(val label: String) {
    AGENT("Agent"),
    DIFF("Diff"),
    FILES("Files"),
    LOGS("Logs")
}

@Composable
private fun GestureHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gestures") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GestureHelpRow("Tap", "Mouse click + keyboard")
                GestureHelpRow("Double tap", "Enter")
                GestureHelpRow("Swipe horizontal", "Cursor left/right")
                GestureHelpRow("Swipe vertical", "Scroll")
                GestureHelpRow("Long press + swipe", "Arrow keys")
                GestureHelpRow("2-finger tap", "Text selection")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun GestureHelpRow(gesture: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(gesture, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        Text(action, fontSize = 13.sp, color = Color(0xFF888888))
    }
}

@Composable
fun SessionDetailScreen(
    sessionName: String,
    host: String,
    terminalSession: TerminalSession?,
    sshSessionManager: SshSessionManager,
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    cwd: String = "",
    gitBranch: String = "",
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SessionTab.AGENT) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var composingText by remember { mutableStateOf("") }
    var showGestureHelp by remember { mutableStateOf(false) }

    if (showGestureHelp) {
        GestureHelpDialog(onDismiss = { showGestureHelp = false })
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "upload_${System.currentTimeMillis()}"

                val cwd = sshSessionManager.getTmuxPaneCwd()
                val remotePath = "$cwd/uploads"
                sshSessionManager.exec("mkdir -p '$remotePath'")

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    sshSessionManager.uploadFile(inputStream, remotePath, fileName)
                }

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
        // Session header
        SessionHeader(
            sessionName = sessionName,
            host = host,
            connectionState = connectionState,
            onBack = onBack,
            cwd = cwd,
            gitBranch = gitBranch,
            onHelp = { showGestureHelp = true }
        )

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Agent view (always rendered at z=0, other tabs overlay on top at z=1)
            if (terminalSession != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx).also { view ->
                                view.attachSession(terminalSession)
                                terminalSession.onRedraw = { view.triggerRedraw() }
                                view.onComposingTextChanged = { composingText = it }
                                view.post { view.showKeyboard() }
                                terminalView = view
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Composing text preview
                    if (composingText.isNotEmpty() && selectedTab == SessionTab.AGENT) {
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
                    if (selectedTab == SessionTab.AGENT) {
                        ConnectionOverlay(
                            connectionState = connectionState,
                            onReconnect = onReconnect,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Other tab content (overlays terminal at z=1)
            when (selectedTab) {
                SessionTab.DIFF -> DiffTab(
                    sshSessionManager = sshSessionManager,
                    modifier = Modifier.fillMaxSize().zIndex(1f)
                )
                SessionTab.FILES -> FilesTab(
                    sshSessionManager = sshSessionManager,
                    modifier = Modifier.fillMaxSize().zIndex(1f)
                )
                SessionTab.LOGS -> LogsTab(
                    sshSessionManager = sshSessionManager,
                    modifier = Modifier.fillMaxSize().zIndex(1f)
                )
                SessionTab.AGENT -> {}
            }
        }

        // Upload progress
        if (uploading && selectedTab == SessionTab.AGENT) {
            UploadingBar()
        }

        // ModifierKeyBar (always visible to keep terminal size stable across tabs)
        if (terminalSession != null) {
            ModifierKeyBar(
                onShortcut = { action ->
                    when (action) {
                        is ShortcutAction.SendByte -> terminalSession.writeByte(action.byte)
                        is ShortcutAction.SendText -> terminalSession.writeInput(action.text)
                    }
                },
                onPaste = { terminalView?.pasteFromClipboard() },
                onUpload = { filePickerLauncher.launch("*/*") },
                onDisconnect = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
            )
        }

        // View switcher (bottom navigation)
        ViewSwitcher(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}

@Composable
private fun SessionHeader(
    sessionName: String,
    host: String,
    connectionState: ConnectionState,
    onBack: () -> Unit,
    cwd: String = "",
    gitBranch: String = "",
    onHelp: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sessionName,
                color = Color.White,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = host,
                    color = Color(0xFF888888),
                    fontSize = 11.sp
                )
                if (gitBranch.isNotEmpty()) {
                    Text(
                        text = gitBranch,
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp
                    )
                }
            }
            if (cwd.isNotEmpty()) {
                val shortCwd = cwd.split("/").takeLast(2).joinToString("/")
                Text(
                    text = shortCwd,
                    color = Color(0xFF666666),
                    fontSize = 10.sp
                )
            }
        }
        TextButton(
            onClick = onHelp,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.size(32.dp)
        ) {
            Text("?", color = Color(0xFF888888), fontSize = 14.sp)
        }
        ConnectionBadge(connectionState)
    }
}

@Composable
private fun ConnectionBadge(connectionState: ConnectionState) {
    val (label, color) = when (connectionState) {
        is ConnectionState.Connected -> "Connected" to Color(0xFF4CAF50)
        is ConnectionState.Connecting -> "Connecting" to Color(0xFFFFC107)
        is ConnectionState.Reconnecting -> "Reconnecting" to Color(0xFFFFC107)
        is ConnectionState.Disconnected -> "Disconnected" to Color(0xFFF44336)
        is ConnectionState.Error -> "Error" to Color(0xFFF44336)
        is ConnectionState.Idle -> "Idle" to Color(0xFF888888)
    }
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier.padding(end = 12.dp)
    )
}

@Composable
private fun ConnectionOverlay(
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (connectionState) {
        is ConnectionState.Reconnecting -> {
            Row(
                modifier = modifier
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
                modifier = modifier
                    .background(Color(0xCC000000))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Disconnected", color = Color.White, fontSize = 16.sp)
                Text(
                    "tmux session preserved",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
                TextButton(onClick = onReconnect) {
                    Text("Reconnect", color = Color(0xFF64B5F6), fontSize = 14.sp)
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun UploadingBar() {
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

@Composable
private fun ViewSwitcher(
    selectedTab: SessionTab,
    onTabSelected: (SessionTab) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF252525),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.height(56.dp)
    ) {
        SessionTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {},
                label = {
                    Text(
                        tab.label,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = Color(0xFF888888),
                    indicatorColor = Color(0xFF333333)
                )
            )
        }
    }
}
