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
import com.harataku.sshclient.session.Session
import com.harataku.sshclient.session.SessionState
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
import com.harataku.sshclient.ui.session.SessionListViewModel
import com.harataku.sshclient.ui.terminal.TerminalViewModel
import java.io.File

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val connectViewModel: ConnectViewModel = viewModel()
    val sessionListViewModel: SessionListViewModel = viewModel()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Update check
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        updateInfo = AppUpdater.checkForUpdate(context)
    }
    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { if (!updating) updateInfo = null },
            title = { Text("Update Available") },
            text = { Text("v${updateInfo!!.version} is available. Update now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            updating = true
                            try {
                                AppUpdater.downloadAndInstall(context, updateInfo!!)
                            } finally {
                                updating = false
                            }
                        }
                    },
                    enabled = !updating
                ) {
                    if (updating) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading...")
                        }
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                if (!updating) {
                    TextButton(onClick = { updateInfo = null }) { Text("Later") }
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

    // Track the active session for session detail
    var activeSession by remember { mutableStateOf<Session?>(null) }

    NavHost(navController = navController, startDestination = "sessions_list") {
        // Session list (new home screen)
        composable("sessions_list") {
            val sessions by sessionListViewModel.sessions.collectAsState()

            // Reset connection state when returning to list
            LaunchedEffect(Unit) {
                sessionListViewModel.reload()
            }

            SessionListScreen(
                sessions = sessions,
                onSessionClick = { session ->
                    activeSession = session
                    // Load saved credentials and connect
                    connectViewModel.updateHost(session.host)
                    connectViewModel.updatePort(session.port.toString())
                    connectViewModel.updateUsername(session.username)
                    connectViewModel.updatePassword(session.password)
                    connectViewModel.connect()
                    navController.navigate("session_detail")
                },
                onNewSession = {
                    navController.navigate("connect")
                },
                onDeleteSession = { id ->
                    sessionListViewModel.deleteSession(id)
                },
                onTogglePin = { id ->
                    sessionListViewModel.togglePin(id)
                }
            )
        }

        // Connect screen (session creation sub-flow)
        composable("connect") {
            val connectionState by connectViewModel.connectionState.collectAsState()

            LaunchedEffect(connectionState) {
                if (connectionState is ConnectionState.Connected) {
                    // Create session and navigate to detail
                    val config = connectViewModel.config.value
                    val session = Session(
                        name = "${config.username}@${config.host}",
                        host = config.host,
                        port = config.port,
                        username = config.username,
                        password = config.password,
                        state = SessionState.ACTIVE
                    )
                    sessionListViewModel.createSession(session)
                    activeSession = session
                    navController.navigate("session_detail") {
                        popUpTo("sessions_list")
                    }
                }
            }

            ConnectScreen(
                onConnected = {
                    // Handled by LaunchedEffect above
                },
                viewModel = connectViewModel
            )
        }

        // Session detail with Agent/Diff/Files/Logs tabs
        composable("session_detail") {
            val session = activeSession ?: return@composable
            val connectionState by connectViewModel.connectionState.collectAsState()
            val tmuxSessions by connectViewModel.tmuxSessions.collectAsState()
            val currentSessionName by connectViewModel.currentSessionName.collectAsState()
            val terminalViewModel: TerminalViewModel = viewModel()
            val sshSessionManager = remember { connectViewModel.sshSessionManager }

            // Wait for connection, then auto-attach tmux
            LaunchedEffect(connectionState) {
                if (connectionState is ConnectionState.Connected && terminalViewModel.terminalSession == null) {
                    // Auto-attach or create tmux session
                    val tmuxName = session.tmuxSessionName
                    if (tmuxName != null) {
                        connectViewModel.attachTmuxSession(tmuxName) {}
                    } else {
                        connectViewModel.createTmuxSession {}
                    }
                    terminalViewModel.init(sshSessionManager)

                    // Update session state
                    sessionListViewModel.updateSessionState(session.id, SessionState.ACTIVE)
                }
            }

            terminalViewModel.terminalSession?.let { termSession ->
                // Wire up auto-reconnect
                LaunchedEffect(termSession) {
                    termSession.onDisconnected = {
                        connectViewModel.reconnect {
                            termSession.reconnect()
                        }
                    }
                }

                SessionDetailScreen(
                    session = session,
                    terminalSession = termSession,
                    sshSessionManager = sshSessionManager,
                    tmuxSessions = tmuxSessions,
                    currentSessionName = currentSessionName,
                    connectionState = connectionState,
                    onSessionTab = { sessionName ->
                        if (sessionName != currentSessionName) {
                            termSession.suppressDisconnect = true
                            connectViewModel.switchTmuxSession(sessionName) {
                                termSession.switchSession()
                            }
                        }
                    },
                    onNewTmuxSession = {
                        termSession.suppressDisconnect = true
                        connectViewModel.createAndSwitchTmuxSession {
                            termSession.switchSession()
                        }
                    },
                    onReconnect = {
                        connectViewModel.reconnect {
                            termSession.reconnect()
                        }
                    },
                    onDisconnect = {
                        sessionListViewModel.updateSessionState(session.id, SessionState.DISCONNECTED)
                        connectViewModel.disconnect()
                        navController.navigate("sessions_list") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = {
                        sessionListViewModel.updateSessionState(session.id, SessionState.DISCONNECTED)
                        connectViewModel.disconnect()
                        navController.navigate("sessions_list") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            } ?: run {
                // Show loading while connecting
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            when (connectionState) {
                                is ConnectionState.Connecting -> "Connecting..."
                                is ConnectionState.Connected -> "Attaching session..."
                                is ConnectionState.Error -> (connectionState as ConnectionState.Error).message
                                else -> "Connecting..."
                            }
                        )
                        if (connectionState is ConnectionState.Error) {
                            TextButton(onClick = {
                                navController.navigate("sessions_list") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }) {
                                Text("Back to Sessions")
                            }
                        }
                    }
                }
            }
        }
    }
}
