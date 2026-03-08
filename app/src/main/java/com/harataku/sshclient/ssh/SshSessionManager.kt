package com.harataku.sshclient.ssh

import android.util.Log
import com.harataku.sshclient.tmux.TmuxSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.InputStream
import java.io.OutputStream

class SshSessionManager {

    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var _inputStream: InputStream? = null
    private var _outputStream: OutputStream? = null

    suspend fun connect(config: SshConnectionConfig) = withContext(Dispatchers.IO) {
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(config.host, config.port)
        client.authPassword(config.username, config.password)
        sshClient = client
        Log.d("SSH", "Connected to ${config.host}:${config.port}")
    }

    /**
     * Run a command via exec channel and return stdout as a string.
     */
    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        try {
            val cmd = sess.exec(command)
            val output = cmd.inputStream.bufferedReader().readText()
            cmd.join()
            output.trim()
        } finally {
            sess.close()
        }
    }

    /**
     * List tmux sessions via exec. Returns empty list if tmux is not running.
     */
    suspend fun listTmuxSessions(): List<TmuxSessionInfo> = withContext(Dispatchers.IO) {
        try {
            val output = exec("tmux list-sessions -F '#{session_id}:#{session_name}:#{session_windows}:#{session_attached}'")
            if (output.isBlank()) return@withContext emptyList()
            output.lines().mapNotNull { line ->
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
        } catch (e: Exception) {
            Log.d("SSH", "No tmux sessions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Open a shell and attach to the given tmux session.
     */
    suspend fun startTmuxShell(sessionName: String) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val sh = sess.startShell()
        sh.outputStream.write("tmux attach-session -t ${sessionName.replace("'", "'\\''")}\n".toByteArray())
        sh.outputStream.flush()
        session = sess
        shell = sh
        _inputStream = sh.inputStream
        _outputStream = sh.outputStream
    }

    /**
     * Open a shell and create a new tmux session.
     */
    suspend fun startTmuxNewSession(sessionName: String? = null) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val sh = sess.startShell()
        val tmuxCmd = if (sessionName != null) {
            "tmux new-session -s ${sessionName.replace("'", "'\\''")}\n"
        } else {
            "tmux new-session\n"
        }
        sh.outputStream.write(tmuxCmd.toByteArray())
        sh.outputStream.flush()
        session = sess
        shell = sh
        _inputStream = sh.inputStream
        _outputStream = sh.outputStream
    }

    /**
     * Open a plain shell (no tmux).
     */
    suspend fun startShell() = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val sh = sess.startShell()
        session = sess
        shell = sh
        _inputStream = sh.inputStream
        _outputStream = sh.outputStream
    }

    fun getInputStream(): InputStream = _inputStream!!

    fun getOutputStream(): OutputStream = _outputStream!!

    fun resizePty(cols: Int, rows: Int) {
        try { shell?.changeWindowDimensions(cols, rows, 0, 0) } catch (_: Exception) {}
    }

    suspend fun closeShell() = withContext(Dispatchers.IO) {
        try { shell?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        shell = null
        session = null
        _inputStream = null
        _outputStream = null
    }

    fun isConnected(): Boolean = sshClient?.isConnected == true

    fun disconnect() {
        try { shell?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { sshClient?.disconnect() } catch (_: Exception) {}
        shell = null
        _inputStream = null
        _outputStream = null
        session = null
        sshClient = null
        Log.d("SSH", "Disconnected")
    }
}
