package com.harataku.sshclient.ui.connect

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harataku.sshclient.ssh.ConnectionStore
import com.harataku.sshclient.ssh.SshConnectionConfig
import com.harataku.sshclient.ssh.SshConnectionService
import com.harataku.sshclient.ssh.SshSessionManager
import com.harataku.sshclient.tmux.TmuxSessionInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val connectionStore = ConnectionStore(application)

    private val _config = MutableStateFlow(SshConnectionConfig())
    val config: StateFlow<SshConnectionConfig> = _config

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _tmuxSessions = MutableStateFlow<List<TmuxSessionInfo>>(emptyList())
    val tmuxSessions: StateFlow<List<TmuxSessionInfo>> = _tmuxSessions

    private val _currentSessionName = MutableStateFlow<String?>(null)
    val currentSessionName: StateFlow<String?> = _currentSessionName

    private val _sessionsLoading = MutableStateFlow(false)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading

    val sshSessionManager = SshSessionManager()

    private var _reconnecting = false

    init {
        val saved = connectionStore.load()
        if (saved != null) {
            _config.value = saved
            autoConnect(saved)
        }
    }

    private fun autoConnect(config: SshConnectionConfig) {
        _connectionState.value = ConnectionState.Connecting
        viewModelScope.launch {
            try {
                sshSessionManager.connect(config)
                _connectionState.value = ConnectionState.Connected
                startForegroundService()
                loadTmuxSessions()
            } catch (e: Exception) {
                Log.e("SSH", "Auto-connect failed", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Auto-connect failed")
            }
        }
    }

    private fun startForegroundService() {
        try {
            val app = getApplication<Application>()
            // Start service regardless of notification permission.
            // Service keeps connection alive; notification is just visible if permission granted.
            val intent = Intent(app, SshConnectionService::class.java)
            app.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("SSH", "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, SshConnectionService::class.java)
            app.stopService(intent)
        } catch (e: Exception) {
            Log.e("SSH", "Failed to stop foreground service", e)
        }
    }

    /**
     * Attempt to reconnect SSH and re-attach to the current tmux session.
     * Called when TerminalSession detects a disconnection.
     */
    fun reconnect(onReconnected: (() -> Unit)? = null) {
        if (_reconnecting) return
        _reconnecting = true
        val config = sshSessionManager.connectionConfig ?: _config.value
        val sessionName = _currentSessionName.value

        _connectionState.value = ConnectionState.Reconnecting

        viewModelScope.launch {
            var retries = 0
            val maxRetries = 5
            var connected = false

            while (retries < maxRetries && !connected) {
                retries++
                Log.d("SSH", "Reconnect attempt $retries/$maxRetries")
                try {
                    sshSessionManager.disconnect()
                    sshSessionManager.connect(config)
                    // Re-attach to tmux session if we had one
                    if (sessionName != null) {
                        sshSessionManager.startTmuxShell(sessionName)
                    } else {
                        sshSessionManager.startShell()
                    }
                    connected = true
                    _connectionState.value = ConnectionState.Connected
                    Log.d("SSH", "Reconnected successfully")
                    onReconnected?.invoke()
                } catch (e: Exception) {
                    Log.e("SSH", "Reconnect attempt $retries failed: ${e.message}")
                    if (retries < maxRetries) {
                        // Backoff: 2s, 4s, 8s, 16s
                        delay(minOf(2000L * (1L shl (retries - 1)), 16000L))
                    }
                }
            }

            if (!connected) {
                _connectionState.value = ConnectionState.Disconnected
                stopForegroundService()
            }
            _reconnecting = false
        }
    }

    fun loadTmuxSessions() {
        _sessionsLoading.value = true
        viewModelScope.launch {
            try {
                _tmuxSessions.value = sshSessionManager.listTmuxSessions()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to list tmux sessions", e)
            } finally {
                _sessionsLoading.value = false
            }
        }
    }

    fun switchToSessions(onReady: () -> Unit) {
        _sessionsLoading.value = true
        viewModelScope.launch {
            try {
                sshSessionManager.closeShell()
                _tmuxSessions.value = sshSessionManager.listTmuxSessions()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to switch sessions", e)
            } finally {
                _sessionsLoading.value = false
                onReady()
            }
        }
    }

    fun attachTmuxSession(sessionName: String, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                sshSessionManager.startTmuxShell(sessionName)
                _currentSessionName.value = sessionName
                onReady()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to attach tmux session", e)
            }
        }
    }

    fun createTmuxSession(onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                sshSessionManager.startTmuxNewSession()
                // Refresh sessions to get the new session name
                _tmuxSessions.value = sshSessionManager.listTmuxSessions()
                _currentSessionName.value = _tmuxSessions.value.lastOrNull()?.name
                onReady()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to create tmux session", e)
            }
        }
    }

    fun switchTmuxSession(sessionName: String, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                sshSessionManager.closeShell()
                sshSessionManager.startTmuxShell(sessionName)
                _currentSessionName.value = sessionName
                onReady()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to switch tmux session", e)
            }
        }
    }

    fun createAndSwitchTmuxSession(onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                sshSessionManager.closeShell()
                sshSessionManager.startTmuxNewSession()
                _tmuxSessions.value = sshSessionManager.listTmuxSessions()
                _currentSessionName.value = _tmuxSessions.value.lastOrNull()?.name
                onReady()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to create tmux session", e)
            }
        }
    }

    fun disconnect() {
        sshSessionManager.disconnect()
        stopForegroundService()
        _connectionState.value = ConnectionState.Idle
        _tmuxSessions.value = emptyList()
        _currentSessionName.value = null
        _reconnecting = false
    }

    fun deleteTmuxSession(sessionName: String) {
        viewModelScope.launch {
            try {
                sshSessionManager.killTmuxSession(sessionName)
                _tmuxSessions.value = sshSessionManager.listTmuxSessions()
            } catch (e: Exception) {
                Log.e("SSH", "Failed to delete tmux session", e)
            }
        }
    }

    fun updateHost(host: String) {
        _config.value = _config.value.copy(host = host)
    }

    fun updatePort(port: String) {
        _config.value = _config.value.copy(port = port.toIntOrNull() ?: 22)
    }

    fun updateUsername(username: String) {
        _config.value = _config.value.copy(username = username)
    }

    fun updatePassword(password: String) {
        _config.value = _config.value.copy(password = password)
    }

    fun connect() {
        val c = _config.value
        if (c.host.isBlank() || c.username.isBlank()) return

        _connectionState.value = ConnectionState.Connecting

        viewModelScope.launch {
            try {
                sshSessionManager.connect(c)
                connectionStore.save(c)
                _connectionState.value = ConnectionState.Connected
                startForegroundService()
                loadTmuxSessions()
            } catch (e: Exception) {
                Log.e("SSH", "Connection failed", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundService()
        sshSessionManager.disconnect()
    }
}

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Reconnecting : ConnectionState()
    data object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
