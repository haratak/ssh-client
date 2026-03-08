package com.harataku.sshclient.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harataku.sshclient.ssh.SshSessionManager
import com.harataku.sshclient.terminal.TerminalSession

class TerminalViewModel : ViewModel() {

    var terminalSession: TerminalSession? = null
        private set

    fun init(sshSessionManager: SshSessionManager, useTmux: Boolean = false) {
        if (terminalSession != null) return
        terminalSession = TerminalSession(sshSessionManager, viewModelScope, useTmux)
    }
}
