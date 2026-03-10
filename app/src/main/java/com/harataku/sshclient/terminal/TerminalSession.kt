package com.harataku.sshclient.terminal

import android.util.Log
import com.harataku.sshclient.ssh.SshSessionManager
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream

class TerminalSession(
    private val sshSessionManager: SshSessionManager,
    private val scope: CoroutineScope,
) {
    private lateinit var outputStream: OutputStream
    @Volatile
    private var started = false
    private val writeMutex = Mutex()

    var onRedraw: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    @Volatile
    var suppressDisconnect = false

    val lock = Object()

    private val terminalOutput = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            scope.launch(Dispatchers.IO) {
                writeMutex.withLock {
                    try {
                        outputStream.write(data, offset, count)
                        outputStream.flush()
                    } catch (e: Exception) {
                        Log.e("TerminalSession", "Write failed", e)
                    }
                }
            }
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }

    private val client = object : TerminalSessionClient {
        override fun onTextChanged() {}
        override fun onTitleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int = 0
        override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    val emulator: TerminalEmulator = TerminalEmulator(
        terminalOutput, 80, 24, 12, 24, null, client
    )

    fun resize(cols: Int, rows: Int) {
        synchronized(lock) {
            if (cols != emulator.mColumns || rows != emulator.mRows) {
                emulator.resize(cols, rows, 12, 24)
            }
        }
        scope.launch(Dispatchers.IO) {
            sshSessionManager.resizePty(cols, rows)
        }
        if (!started) {
            started = true
            startReading()
        }
        onRedraw?.invoke()
    }

    private fun startReading() {
        outputStream = sshSessionManager.getOutputStream()
        startRawReading()
    }

    private fun startRawReading() {
        val inputStream = sshSessionManager.getInputStream()
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    synchronized(lock) {
                        emulator.append(buffer, bytesRead)
                    }
                    onRedraw?.invoke()
                }
            } catch (e: Exception) {
                Log.e("TerminalSession", "Read failed", e)
            }
            if (!suppressDisconnect) {
                Log.w("TerminalSession", "Read loop ended, connection lost")
                onDisconnected?.invoke()
            }
        }
    }

    fun reconnect() {
        try {
            outputStream = sshSessionManager.getOutputStream()
            val cols = emulator.mColumns
            val rows = emulator.mRows
            scope.launch(Dispatchers.IO) {
                sshSessionManager.resizePty(cols, rows)
            }
            startRawReading()
        } catch (e: Exception) {
            Log.e("TerminalSession", "Reconnect failed", e)
        }
    }

    /**
     * Switch to a different tmux session in-place (no navigation).
     * Clears the screen and starts reading from the new shell.
     */
    fun switchSession() {
        suppressDisconnect = false
        try {
            outputStream = sshSessionManager.getOutputStream()
            synchronized(lock) {
                emulator.reset()
            }
            // Re-send current terminal size to the new PTY
            val cols = emulator.mColumns
            val rows = emulator.mRows
            scope.launch(Dispatchers.IO) {
                sshSessionManager.resizePty(cols, rows)
            }
            startRawReading()
            onRedraw?.invoke()
        } catch (e: Exception) {
            Log.e("TerminalSession", "Switch session failed", e)
        }
    }

    fun writeInput(text: String) {
        val bytes = text.toByteArray()
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    outputStream.write(bytes)
                    outputStream.flush()
                } catch (e: Exception) {
                    Log.e("TerminalSession", "Write failed", e)
                }
            }
        }
    }

    fun writeReplace(deleteCount: Int, newText: String) {
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    repeat(deleteCount) { outputStream.write(0x7F) }
                    if (newText.isNotEmpty()) {
                        outputStream.write(newText.toByteArray())
                    }
                    outputStream.flush()
                } catch (e: Exception) {
                    Log.e("TerminalSession", "WriteReplace failed", e)
                }
            }
        }
    }

    fun writeByte(b: Int) {
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    outputStream.write(b)
                    outputStream.flush()
                } catch (e: Exception) {
                    Log.e("TerminalSession", "Write failed", e)
                }
            }
        }
    }
}
