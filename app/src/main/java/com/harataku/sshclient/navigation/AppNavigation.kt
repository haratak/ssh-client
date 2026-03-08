package com.harataku.sshclient.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val connectViewModel: ConnectViewModel = viewModel()

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
                }
            )
        }

        composable("terminal") {
            val sessions by connectViewModel.tmuxSessions.collectAsState()
            val currentSessionName by connectViewModel.currentSessionName.collectAsState()
            val terminalViewModel: TerminalViewModel = viewModel()
            val sshSessionManager = remember { connectViewModel.sshSessionManager }
            terminalViewModel.init(sshSessionManager, useTmux = false)

            terminalViewModel.terminalSession?.let { session ->
                TerminalScreen(
                    terminalSession = session,
                    sessions = sessions,
                    currentSessionName = currentSessionName,
                    onSessionTab = { sessionName ->
                        if (sessionName != currentSessionName) {
                            connectViewModel.switchTmuxSession(sessionName) {
                                navController.navigate("terminal") {
                                    popUpTo("terminal") { inclusive = true }
                                }
                            }
                        }
                    },
                    onNewSession = {
                        connectViewModel.createAndSwitchTmuxSession {
                            navController.navigate("terminal") {
                                popUpTo("terminal") { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}
