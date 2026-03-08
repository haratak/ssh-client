package com.harataku.sshclient.tmux

sealed class TmuxEvent {
    data class SessionChanged(val sessionId: String, val name: String) : TmuxEvent()
    data object SessionsChanged : TmuxEvent()
    data class WindowAdd(val windowId: String) : TmuxEvent()
    data class WindowClose(val windowId: String) : TmuxEvent()
    data class WindowRenamed(val windowId: String, val name: String) : TmuxEvent()
    data class Output(val paneId: String, val data: ByteArray) : TmuxEvent() {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }
    data class LayoutChange(val windowId: String, val layout: String) : TmuxEvent()
    data class Begin(val timestamp: Long, val commandNum: Int) : TmuxEvent()
    data class End(val timestamp: Long, val commandNum: Int) : TmuxEvent()
    data class Error(val timestamp: Long, val commandNum: Int) : TmuxEvent()
    data class Exit(val reason: String?) : TmuxEvent()
    data class CommandResponse(val lines: List<String>) : TmuxEvent()
    data class Unknown(val line: String) : TmuxEvent()
}
