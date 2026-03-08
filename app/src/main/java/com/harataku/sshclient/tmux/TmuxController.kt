package com.harataku.sshclient.tmux

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream

data class TmuxSessionInfo(
    val id: String,
    val name: String,
    val windows: Int,
    val attached: Boolean
)

class TmuxController(
    private val outputStream: OutputStream,
    private val scope: CoroutineScope
) {
    private val _sessions = MutableStateFlow<List<TmuxSessionInfo>>(emptyList())
    val sessions: StateFlow<List<TmuxSessionInfo>> = _sessions

    private val _currentSession = MutableStateFlow<String?>(null)
    val currentSession: StateFlow<String?> = _currentSession

    private var pendingCommand: String? = null

    fun sendCommand(cmd: String) {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream.write("$cmd\n".toByteArray())
                outputStream.flush()
            } catch (e: Exception) {
                Log.e("TmuxController", "Failed to send command: $cmd", e)
            }
        }
    }

    fun listSessions() {
        pendingCommand = "list-sessions"
        sendCommand("list-sessions -F \"#{session_id}:#{session_name}:#{session_windows}:#{session_attached}\"")
    }

    fun switchSession(name: String) {
        sendCommand("switch-client -t $name")
    }

    fun newSession(name: String? = null) {
        if (name != null) {
            sendCommand("new-session -d -s $name")
        } else {
            sendCommand("new-session -d")
        }
        // Refresh session list after creation
        listSessions()
    }

    fun killSession(name: String) {
        sendCommand("kill-session -t $name")
        listSessions()
    }

    fun handleEvent(event: TmuxEvent) {
        when (event) {
            is TmuxEvent.SessionChanged -> {
                _currentSession.value = event.name
                listSessions()
            }
            is TmuxEvent.SessionsChanged -> {
                listSessions()
            }
            is TmuxEvent.CommandResponse -> {
                if (pendingCommand == "list-sessions") {
                    parseSessionList(event.lines)
                    pendingCommand = null
                }
            }
            is TmuxEvent.Exit -> {
                Log.d("TmuxController", "tmux exited: ${event.reason}")
            }
            else -> {}
        }
    }

    private fun parseSessionList(lines: List<String>) {
        val sessions = lines.mapNotNull { line ->
            // Format: $id:name:windows:attached
            val parts = line.split(":", limit = 4)
            if (parts.size >= 4) {
                TmuxSessionInfo(
                    id = parts[0],
                    name = parts[1],
                    windows = parts[2].toIntOrNull() ?: 0,
                    attached = parts[3] == "1"
                )
            } else null
        }
        _sessions.value = sessions
    }
}
