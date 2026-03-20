package com.harataku.sshclient.ssh

import android.util.Log
import com.harataku.sshclient.tmux.TmuxSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.sftp.SFTPClient
import java.io.InputStream
import java.io.OutputStream

class SshSessionManager {

    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var sessionChannel: SessionChannel? = null
    private var _inputStream: InputStream? = null
    private var _outputStream: OutputStream? = null

    var connectionConfig: SshConnectionConfig? = null
        private set

    suspend fun connect(config: SshConnectionConfig) = withContext(Dispatchers.IO) {
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(config.host, config.port)
        client.authPassword(config.username, config.password)
        // Send keepalive every 15 seconds to detect dead connections
        client.connection.keepAlive.keepAliveInterval = 15
        sshClient = client
        connectionConfig = config
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
     * Kill (delete) a tmux session by name.
     */
    suspend fun killTmuxSession(sessionName: String) = withContext(Dispatchers.IO) {
        exec("tmux kill-session -t ${sessionName.replace("'", "'\\''")}")
    }

    /**
     * Attach to the given tmux session via exec channel with PTY.
     * No shell involved - tmux runs directly, no .bashrc, no $TMUX issues.
     */
    suspend fun startTmuxShell(sessionName: String) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        try { sess.setEnvVar("LANG", "en_US.UTF-8") } catch (_: Exception) {}
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val escapedName = sessionName.replace("'", "'\\''")
        val cmd = sess.exec("tmux -u attach-session -t '$escapedName'")
        session = sess
        sessionChannel = sess as SessionChannel
        _inputStream = cmd.inputStream
        _outputStream = cmd.outputStream
    }

    /**
     * Create a new tmux session via exec channel with PTY.
     */
    suspend fun startTmuxNewSession(sessionName: String? = null) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        try { sess.setEnvVar("LANG", "en_US.UTF-8") } catch (_: Exception) {}
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val tmuxCmd = if (sessionName != null) {
            "tmux -u new-session -s '${sessionName.replace("'", "'\\''")}'"
        } else {
            "tmux -u new-session"
        }
        val cmd = sess.exec(tmuxCmd)
        session = sess
        sessionChannel = sess as SessionChannel
        _inputStream = cmd.inputStream
        _outputStream = cmd.outputStream
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
        sessionChannel = sess as SessionChannel
        _inputStream = sh.inputStream
        _outputStream = sh.outputStream
    }

    fun getInputStream(): InputStream = _inputStream!!

    fun getOutputStream(): OutputStream = _outputStream!!

    fun resizePty(cols: Int, rows: Int) {
        try { sessionChannel?.changeWindowDimensions(cols, rows, 0, 0) } catch (_: Exception) {}
    }

    suspend fun closeShell() = withContext(Dispatchers.IO) {
        try { session?.close() } catch (_: Exception) {}
        sessionChannel = null
        session = null
        _inputStream = null
        _outputStream = null
    }

    /**
     * Get the current working directory of the active tmux pane.
     */
    suspend fun getTmuxPaneCwd(): String = withContext(Dispatchers.IO) {
        exec("tmux display-message -p '#{pane_current_path}'")
    }

    /**
     * Upload a file via SFTP to the specified remote directory.
     */
    suspend fun uploadFile(
        inputStream: InputStream,
        remotePath: String,
        fileName: String
    ) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sftp: SFTPClient = client.newSFTPClient()
        try {
            val fullPath = "$remotePath/$fileName"
            val remoteFile = sftp.open(fullPath,
                java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.WRITE,
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.TRUNC
                )
            )
            try {
                val out = remoteFile.RemoteFileOutputStream()
                inputStream.copyTo(out)
                out.flush()
                out.close()
            } finally {
                remoteFile.close()
            }
            Log.d("SSH", "Uploaded $fileName to $remotePath")
        } finally {
            sftp.close()
        }
    }

    fun isConnected(): Boolean = sshClient?.isConnected == true

    fun disconnect() {
        try { session?.close() } catch (_: Exception) {}
        try { sshClient?.disconnect() } catch (_: Exception) {}
        sessionChannel = null
        _inputStream = null
        _outputStream = null
        session = null
        sshClient = null
        Log.d("SSH", "Disconnected")
    }
}
