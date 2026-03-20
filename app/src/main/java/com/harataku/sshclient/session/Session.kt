package com.harataku.sshclient.session

import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String,
    val tmuxSessionName: String? = null,
    val state: SessionState = SessionState.DISCONNECTED,
    val lastUpdated: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

enum class SessionState {
    ACTIVE,
    IDLE,
    RUNNING,
    NEEDS_REVIEW,
    DISCONNECTED,
    ERROR
}
