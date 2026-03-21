package com.harataku.sshclient.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harataku.sshclient.updater.AppUpdater
import com.harataku.sshclient.updater.UpdateInfo
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.harataku.sshclient.ui.connect.ConnectScreen
import com.harataku.sshclient.ui.connect.ConnectViewModel
import com.harataku.sshclient.ui.connect.ConnectionState
import com.harataku.sshclient.ui.session.SessionDetailScreen
import com.harataku.sshclient.ui.session.SessionListScreen
import com.harataku.sshclient.ui.terminal.TerminalViewModel
import java.io.File

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val connectViewModel: ConnectViewModel = viewModel()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Update check
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        updateInfo = AppUpdater.checkForUpdate(context)
    }
    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { if (!updating) updateInfo = null },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("v${updateInfo!!.version} is available. Update now?")
                    if (updating) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Downloading... ${downloadProgress}%", fontSize = 13.sp)
                    }
                    if (updateError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateError!!,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            updating = true
                            updateError = null
                            downloadProgress = 0
                            try {
                                AppUpdater.downloadAndInstall(context, updateInfo!!) { progress ->
                                    downloadProgress = progress
                                }
                            } catch (e: Exception) {
                                updateError = e.message ?: "Download failed"
                            } finally {
                                updating = false
                            }
                        }
                    },
                    enabled = !updating
                ) {
                    Text(if (updating) "${downloadProgress}%" else "Update")
                }
            },
            dismissButton = {
                if (!updating) {
                    TextButton(onClick = { updateInfo = null; updateError = null }) { Text("Later") }
                }
            }
        )
    }

    // Show crash log if exists
    var crashLog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val file = File(context.filesDir, "crash.log")
        if (file.exists()) {
            crashLog = file.readText().takeLast(3000)
            file.delete()
        }
    }
    if (crashLog != null) {
        AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("Crash Log") },
            text = {
                Text(
                    text = crashLog!!,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { crashLog = null }) { Text("OK") }
            }
        )
    }

    // Track which tmux session was selected and its info
    var activeTmuxSession by remember { mutableStateOf<String?>(null) }
    var activeSessionCwd by remember { mutableStateOf("") }
    var activeSessionBranch by remember { mutableStateOf("") }

    NavHost(navController = navController, startDestination = "connect") {
        // Login screen (SSH connection)
        composable("connect") {
            val connectionState by connectViewModel.connectionState.collectAsState()

            LaunchedEffect(connectionState) {
                if (connectionState is ConnectionState.Connected) {
                    navController.navigate("sessions_list") {
                        popUpTo("connect") { inclusive = true }
                    }
                }
            }

            ConnectScreen(
                onConnected = {
                    navController.navigate("sessions_list") {
                        popUpTo("connect") { inclusive = true }
                    }
                },
                viewModel = connectViewModel
            )
        }

        // Session list = tmux sessions
        composable("sessions_list") {
            val tmuxSessions by connectViewModel.tmuxSessions.collectAsState()
            val isLoading by connectViewModel.sessionsLoading.collectAsState()

            SessionListScreen(
                tmuxSessions = tmuxSessions,
                isLoading = isLoading,
                onSessionClick = { sessionName ->
                    activeTmuxSession = sessionName
                    val info = tmuxSessions.find { it.name == sessionName }
                    activeSessionCwd = info?.cwd ?: ""
                    activeSessionBranch = info?.gitBranch ?: ""
                    connectViewModel.attachTmuxSession(sessionName) {
                        navController.navigate("session_detail")
                    }
                },
                onNewSession = {
                    connectViewModel.createTmuxSession {
                        activeTmuxSession = connectViewModel.currentSessionName.value
                        navController.navigate("session_detail")
                    }
                },
                onDeleteSession = { sessionName ->
                    connectViewModel.deleteTmuxSession(sessionName)
                },
                onDisconnect = {
                    connectViewModel.disconnect()
                    navController.navigate("connect") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Session detail: 1 tmux session with Agent/Diff/Files/Logs
        composable("session_detail") {
            val sessionName = activeTmuxSession ?: return@composable
            val connectionState by connectViewModel.connectionState.collectAsState()
            val terminalViewModel: TerminalViewModel = viewModel()
            val sshSessionManager = remember { connectViewModel.sshSessionManager }
            val termSession by terminalViewModel.terminalSessionFlow.collectAsState()

            // Init terminal once shell is ready
            LaunchedEffect(Unit) {
                if (terminalViewModel.terminalSession == null) {
                    terminalViewModel.init(sshSessionManager)
                }
            }

            termSession?.let { ts ->
                // Wire up auto-reconnect
                LaunchedEffect(ts) {
                    ts.onDisconnected = {
                        connectViewModel.reconnect {
                            ts.reconnect()
                        }
                    }
                }

                SessionDetailScreen(
                    sessionName = sessionName,
                    host = "${connectViewModel.config.value.username}@${connectViewModel.config.value.host}",
                    terminalSession = ts,
                    sshSessionManager = sshSessionManager,
                    connectionState = connectionState,
                    cwd = activeSessionCwd,
                    gitBranch = activeSessionBranch,
                    onReconnect = {
                        connectViewModel.reconnect {
                            ts.reconnect()
                        }
                    },
                    onDisconnect = {
                        connectViewModel.disconnect()
                        navController.navigate("connect") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = {
                        // Detach from tmux session, go back to session list
                        ts.suppressDisconnect = true
                        connectViewModel.switchToSessions {
                            navController.popBackStack()
                        }
                    }
                )
            } ?: run {
                // Loading
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Attaching session...")
                    }
                }
            }
        }
    }
}
