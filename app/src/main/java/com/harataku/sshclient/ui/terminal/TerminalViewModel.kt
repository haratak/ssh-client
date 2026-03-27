package com.harataku.sshclient.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harataku.sshclient.ssh.SshSessionManager
import com.harataku.sshclient.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TerminalViewModel : ViewModel() {

    private val _terminalSession = MutableStateFlow<TerminalSession?>(null)
    val terminalSessionFlow: StateFlow<TerminalSession?> = _terminalSession

    val terminalSession: TerminalSession?
        get() = _terminalSession.value

    fun init(sshSessionManager: SshSessionManager) {
        if (_terminalSession.value != null) return
        _terminalSession.value = TerminalSession(sshSessionManager, viewModelScope)
    }

    fun reInit(sshSessionManager: SshSessionManager) {
        _terminalSession.value = TerminalSession(sshSessionManager, viewModelScope)
    }
}
