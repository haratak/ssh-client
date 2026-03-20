package com.harataku.sshclient.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.harataku.sshclient.session.Session
import com.harataku.sshclient.session.SessionState
import com.harataku.sshclient.session.SessionStore
import com.harataku.sshclient.ssh.ConnectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionListViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application)
    private val connectionStore = ConnectionStore(application)

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    init {
        migrateFromConnectionStore()
        reload()
    }

    private fun migrateFromConnectionStore() {
        val saved = connectionStore.load() ?: return
        val existing = sessionStore.loadAll()
        if (existing.any { it.host == saved.host && it.username == saved.username }) return
        val session = Session(
            name = "${saved.username}@${saved.host}",
            host = saved.host,
            port = saved.port,
            username = saved.username,
            password = saved.password
        )
        sessionStore.save(session)
    }

    fun reload() {
        _sessions.value = sessionStore.loadAll().sortedWith(
            compareByDescending<Session> { it.pinned }
                .thenByDescending { it.state == SessionState.ACTIVE }
                .thenByDescending { it.lastUpdated }
        )
    }

    fun createSession(session: Session) {
        sessionStore.save(session)
        reload()
    }

    fun deleteSession(id: String) {
        sessionStore.delete(id)
        reload()
    }

    fun togglePin(id: String) {
        val session = _sessions.value.find { it.id == id } ?: return
        sessionStore.save(session.copy(pinned = !session.pinned))
        reload()
    }

    fun updateSessionState(id: String, state: SessionState) {
        val session = _sessions.value.find { it.id == id } ?: return
        sessionStore.save(session.copy(state = state, lastUpdated = System.currentTimeMillis()))
        reload()
    }

    fun updateSession(session: Session) {
        sessionStore.save(session)
        reload()
    }
}
