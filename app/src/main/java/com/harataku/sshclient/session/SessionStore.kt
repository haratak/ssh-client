package com.harataku.sshclient.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SessionStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "sessions", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
    }

    fun saveAll(sessions: List<Session>) {
        val array = JSONArray()
        sessions.forEach { s ->
            array.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("host", s.host)
                put("port", s.port)
                put("username", s.username)
                put("password", s.password)
                put("tmuxSessionName", s.tmuxSessionName ?: "")
                put("state", s.state.name)
                put("lastUpdated", s.lastUpdated)
                put("pinned", s.pinned)
            })
        }
        prefs.edit().putString("sessions", array.toString()).apply()
    }

    fun loadAll(): List<Session> {
        val json = prefs.getString("sessions", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Session(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    host = obj.getString("host"),
                    port = obj.optInt("port", 22),
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    tmuxSessionName = obj.optString("tmuxSessionName", "").ifEmpty { null },
                    state = try {
                        SessionState.valueOf(obj.getString("state"))
                    } catch (_: Exception) {
                        SessionState.DISCONNECTED
                    },
                    lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                    pinned = obj.optBoolean("pinned", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(session: Session) {
        val sessions = loadAll().toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = session
        } else {
            sessions.add(session)
        }
        saveAll(sessions)
    }

    fun delete(id: String) {
        saveAll(loadAll().filter { it.id != id })
    }
}
