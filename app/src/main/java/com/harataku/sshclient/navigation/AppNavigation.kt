package com.harataku.sshclient.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.harataku.sshclient.ui.connect.ConnectScreen
import com.harataku.sshclient.ui.connect.ConnectViewModel
import com.harataku.sshclient.ui.connect.ConnectionState
import com.harataku.sshclient.ui.terminal.TerminalScreen
import com.harataku.sshclient.ui.terminal.TerminalViewModel
import com.harataku.sshclient.ui.tmux.TmuxSessionListScreen
import java.io.File

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val connectViewModel: ConnectViewModel = viewModel()

    // Show crash log if exists
    val context = LocalContext.current
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

    NavHost(navController = navController, startDestination = "connect") {
        composable("connect") {
            val connectionState by connectViewModel.connectionState.collectAsState()

            // Auto-navigate to session list when connected
            LaunchedEffect(connectionState) {
                if (connectionState is ConnectionState.Connected) {
                    navController.navigate("sessions") {
                        popUpTo("connect") { inclusive = true }
                    }
                }
            }

            ConnectScreen(
                onConnected = {
                    navController.navigate("sessions") {
                        popUpTo("connect") { inclusive = true }
                    }
                },
                viewModel = connectViewModel
            )
        }

        composable("sessions") {
            val sessions by connectViewModel.tmuxSessions.collectAsState()
            val isLoading by connectViewModel.sessionsLoading.collectAsState()
            var hasLoaded by remember { mutableStateOf(false) }

            // Track when a load completes
            LaunchedEffect(isLoading) {
                if (!isLoading) hasLoaded = true
            }

            // If done loading and no sessions exist, auto-create and go to terminal
            LaunchedEffect(hasLoaded, sessions) {
                if (hasLoaded && !isLoading && sessions.isEmpty()) {
                    connectViewModel.createTmuxSession {
                        navController.navigate("terminal") {
                            popUpTo("sessions") { inclusive = true }
                        }
                    }
                }
            }

            TmuxSessionListScreen(
                sessions = sessions,
                isLoading = isLoading,
                onSessionSelected = { sessionName ->
                    connectViewModel.attachTmuxSession(sessionName) {
                        navController.navigate("terminal") {
                            popUpTo("sessions") { inclusive = true }
                        }
                    }
                },
                onNewSession = {
                    connectViewModel.createTmuxSession {
                        navController.navigate("terminal") {
                            popUpTo("sessions") { inclusive = true }
                        }
                    }
                },
                onDeleteSession = { sessionName ->
                    connectViewModel.deleteTmuxSession(sessionName)
                }
            )
        }

        composable("terminal") {
            val sessions by connectViewModel.tmuxSessions.collectAsState()
            val currentSessionName by connectViewModel.currentSessionName.collectAsState()
            val terminalViewModel: TerminalViewModel = viewModel()
            val sshSessionManager = remember { connectViewModel.sshSessionManager }
            terminalViewModel.init(sshSessionManager, useTmux = false)

            val connectionState by connectViewModel.connectionState.collectAsState()

            terminalViewModel.terminalSession?.let { session ->
                // Wire up auto-reconnect on disconnection
                LaunchedEffect(session) {
                    session.onDisconnected = {
                        connectViewModel.reconnect {
                            session.reconnect()
                        }
                    }
                }

                TerminalScreen(
                    terminalSession = session,
                    sshSessionManager = sshSessionManager,
                    sessions = sessions,
                    currentSessionName = currentSessionName,
                    connectionState = connectionState,
                    onSessionTab = { sessionName ->
                        if (sessionName != currentSessionName) {
                            session.suppressDisconnect = true
                            connectViewModel.switchTmuxSession(sessionName) {
                                session.switchSession()
                            }
                        }
                    },
                    onNewSession = {
                        session.suppressDisconnect = true
                        connectViewModel.createAndSwitchTmuxSession {
                            session.switchSession()
                        }
                    },
                    onReconnect = {
                        connectViewModel.reconnect {
                            session.reconnect()
                        }
                    }
                )
            }
        }
    }
}
