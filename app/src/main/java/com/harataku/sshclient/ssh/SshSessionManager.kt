package com.harataku.sshclient.ssh

import android.util.Log
import com.harataku.sshclient.tmux.TmuxSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.sftp.SFTPClient
import java.io.InputStream
import java.io.OutputStream

class SshSessionManager {

    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
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
     * Open a shell and attach to the given tmux session.
     * Waits for shell to be ready before sending tmux command.
     */
    suspend fun startTmuxShell(sessionName: String) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val sh = sess.startShell()
        // Wait for shell to initialize before sending tmux command
        waitForShellReady(sh)
        val escapedName = sessionName.replace("'", "'\\''")
        sh.outputStream.write("tmux attach-session -t '$escapedName'\n".toByteArray())
        sh.outputStream.flush()
        // Only expose streams after tmux command is sent
        session = sess
        shell = sh
        _inputStream = sh.inputStream
        _outputStream = sh.outputStream
    }

    /**
     * Open a shell and create a new tmux session.
     * Waits for shell to be ready before sending tmux command.
     */
    suspend fun startTmuxNewSession(sessionName: String? = null) = withContext(Dispatchers.IO) {
        val client = sshClient ?: throw IllegalStateException("Not connected")
        val sess = client.startSession()
        sess.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
        val sh = sess.startShell()
        // Wait for shell to initialize before sending tmux command
        waitForShellReady(sh)
        val tmuxCmd = if (sessionName != null) {
            "tmux new-session -s '${sessionName.replace("'", "'\\''")}'\n"
        } else {
            "tmux new-session\n"
        }
        sh.outputStream.write(tmuxCmd.toByteArray())
        sh.outputStream.flush()
        // Only expose streams after tmux command is sent
        session = sess
        shell = sh
        _inputStream = sh.inputStream
        _outputStream = sh.outputStream
    }

    /**
     * Wait for shell to be ready by draining initial output (motd, prompt).
     * Reads all available data until the stream is quiet for a short period.
     */
    private suspend fun waitForShellReady(sh: Session.Shell) {
        val input = sh.inputStream
        val buffer = ByteArray(4096)
        val startTime = System.currentTimeMillis()
        val timeout = 10000L // 10 seconds max
        var lastDataTime = 0L

        while (System.currentTimeMillis() - startTime < timeout) {
            if (input.available() > 0) {
                // Drain available data (motd, prompt, etc.)
                input.read(buffer, 0, input.available().coerceAtMost(buffer.size))
                lastDataTime = System.currentTimeMillis()
            } else if (lastDataTime > 0 && System.currentTimeMillis() - lastDataTime > 300) {
                // Stream was active but has been quiet for 300ms - shell is ready
                break
            } else {
                kotlinx.coroutines.delay(50)
            }
        }
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
