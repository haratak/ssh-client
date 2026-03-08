package com.harataku.sshclient.tmux

/**
 * Parses tmux control mode (-CC) protocol lines.
 *
 * Notifications start with % (e.g., %session-changed, %output).
 * Command responses are delimited by %begin/%end or %error blocks.
 * %output data uses C-style octal escapes for binary data.
 */
class TmuxControlModeParser {

    private var inCommandBlock = false
    private var commandLines = mutableListOf<String>()
    private var blockTimestamp = 0L
    private var blockCommandNum = 0

    fun parseLine(line: String): TmuxEvent? {
        // Inside a command response block
        if (inCommandBlock) {
            if (line.startsWith("%end")) {
                inCommandBlock = false
                val result = TmuxEvent.CommandResponse(commandLines.toList())
                commandLines.clear()
                return result
            }
            if (line.startsWith("%error")) {
                inCommandBlock = false
                val parts = line.split(" ", limit = 3)
                commandLines.clear()
                return TmuxEvent.Error(
                    timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0,
                    commandNum = parts.getOrNull(2)?.toIntOrNull() ?: 0
                )
            }
            commandLines.add(line)
            return null // Accumulating, no event yet
        }

        if (!line.startsWith("%")) return TmuxEvent.Unknown(line)

        val parts = line.split(" ", limit = 4)
        val command = parts[0]

        return when (command) {
            "%begin" -> {
                inCommandBlock = true
                blockTimestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0
                blockCommandNum = parts.getOrNull(2)?.toIntOrNull() ?: 0
                null // No event yet, start accumulating
            }
            "%end" -> {
                TmuxEvent.End(
                    timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0,
                    commandNum = parts.getOrNull(2)?.toIntOrNull() ?: 0
                )
            }
            "%error" -> {
                TmuxEvent.Error(
                    timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0,
                    commandNum = parts.getOrNull(2)?.toIntOrNull() ?: 0
                )
            }
            "%session-changed" -> {
                TmuxEvent.SessionChanged(
                    sessionId = parts.getOrElse(1) { "" },
                    name = parts.getOrElse(2) { "" }
                )
            }
            "%sessions-changed" -> TmuxEvent.SessionsChanged
            "%window-add" -> TmuxEvent.WindowAdd(parts.getOrElse(1) { "" })
            "%window-close" -> TmuxEvent.WindowClose(parts.getOrElse(1) { "" })
            "%window-renamed" -> {
                TmuxEvent.WindowRenamed(
                    windowId = parts.getOrElse(1) { "" },
                    name = parts.getOrElse(2) { "" }
                )
            }
            "%layout-change" -> {
                TmuxEvent.LayoutChange(
                    windowId = parts.getOrElse(1) { "" },
                    layout = parts.getOrElse(2) { "" }
                )
            }
            "%output" -> {
                val paneId = parts.getOrElse(1) { "" }
                val rawData = if (parts.size >= 3) {
                    line.substring(command.length + 1 + paneId.length + 1)
                } else ""
                TmuxEvent.Output(paneId = paneId, data = decodeOutput(rawData))
            }
            "%exit" -> TmuxEvent.Exit(parts.getOrNull(1))
            else -> TmuxEvent.Unknown(line)
        }
    }

    fun reset() {
        inCommandBlock = false
        commandLines.clear()
    }

    companion object {
        /**
         * Decode tmux %output data which uses C-style octal escapes.
         * e.g., \033 for ESC, \\ for backslash
         */
        fun decodeOutput(data: String): ByteArray {
            val result = mutableListOf<Byte>()
            var i = 0
            while (i < data.length) {
                if (data[i] == '\\' && i + 1 < data.length) {
                    when (data[i + 1]) {
                        '\\' -> { result.add('\\'.code.toByte()); i += 2 }
                        'n' -> { result.add('\n'.code.toByte()); i += 2 }
                        'r' -> { result.add('\r'.code.toByte()); i += 2 }
                        't' -> { result.add('\t'.code.toByte()); i += 2 }
                        in '0'..'3' -> {
                            // Octal escape: \NNN (1-3 digits)
                            val end = minOf(i + 4, data.length)
                            var octal = 0
                            var j = i + 1
                            while (j < end && data[j] in '0'..'7') {
                                octal = octal * 8 + (data[j] - '0')
                                j++
                            }
                            result.add(octal.toByte())
                            i = j
                        }
                        else -> { result.add(data[i].code.toByte()); i++ }
                    }
                } else {
                    // Handle multi-byte UTF-8 characters
                    val ch = data[i]
                    val str = ch.toString()
                    for (b in str.toByteArray(Charsets.UTF_8)) {
                        result.add(b)
                    }
                    i++
                }
            }
            return result.toByteArray()
        }
    }
}
