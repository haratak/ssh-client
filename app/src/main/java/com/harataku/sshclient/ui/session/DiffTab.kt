package com.harataku.sshclient.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harataku.sshclient.ssh.SshSessionManager
import kotlinx.coroutines.launch

data class ChangedFile(
    val path: String,
    val status: String, // M, A, D, ?, etc.
    val additions: Int = 0,
    val deletions: Int = 0
)

data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>
)

data class DiffLine(
    val text: String,
    val type: DiffLineType
)

enum class DiffLineType { CONTEXT, ADD, DELETE, HEADER }

@Composable
fun DiffTab(
    sshSessionManager: SshSessionManager,
    modifier: Modifier = Modifier
) {
    var files by remember { mutableStateOf<List<ChangedFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var diffContent by remember { mutableStateOf<List<DiffLine>>(emptyList()) }
    var diffLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load changed files list
    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val cwd = sshSessionManager.getTmuxPaneCwd()
            // Get both staged and unstaged changes + untracked
            val statusOutput = sshSessionManager.exec("cd '$cwd' && git status --porcelain 2>/dev/null")
            val statOutput = sshSessionManager.exec("cd '$cwd' && git diff --stat HEAD 2>/dev/null")

            // Parse stat for +/- counts
            val statMap = mutableMapOf<String, Pair<Int, Int>>()
            statOutput.lines().forEach { line ->
                // e.g. " src/Foo.kt | 12 ++++----"
                val match = Regex("""^\s*(.+?)\s*\|\s*\d+\s*(\+*)(-*)""").find(line)
                if (match != null) {
                    val path = match.groupValues[1].trim()
                    statMap[path] = match.groupValues[2].length to match.groupValues[3].length
                }
            }

            files = statusOutput.lines().filter { it.length >= 3 }.map { line ->
                val status = line.substring(0, 2).trim().ifEmpty { "?" }
                val path = line.substring(3)
                val (adds, dels) = statMap[path] ?: (0 to 0)
                ChangedFile(path = path, status = status, additions = adds, deletions = dels)
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    fun loadFileDiff(path: String) {
        selectedFile = path
        diffLoading = true
        scope.launch {
            try {
                val cwd = sshSessionManager.getTmuxPaneCwd()
                // Try staged diff first, then unstaged, then show for new files
                var raw = sshSessionManager.exec("cd '$cwd' && git diff HEAD -- '${path.replace("'", "'\\''")}' 2>/dev/null")
                if (raw.isBlank()) {
                    // Untracked file - show full content as additions
                    raw = sshSessionManager.exec("cd '$cwd' && cat '${path.replace("'", "'\\''")}' 2>/dev/null")
                    diffContent = raw.lines().map { DiffLine("+$it", DiffLineType.ADD) }
                } else {
                    diffContent = parseDiff(raw)
                }
            } catch (e: Exception) {
                diffContent = listOf(DiffLine("Error: ${e.message}", DiffLineType.CONTEXT))
            } finally {
                diffLoading = false
            }
        }
    }

    fun refresh() {
        selectedFile = null
        diffContent = emptyList()
        loading = true
        error = null
        scope.launch {
            try {
                val cwd = sshSessionManager.getTmuxPaneCwd()
                val statusOutput = sshSessionManager.exec("cd '$cwd' && git status --porcelain 2>/dev/null")
                val statOutput = sshSessionManager.exec("cd '$cwd' && git diff --stat HEAD 2>/dev/null")

                val statMap = mutableMapOf<String, Pair<Int, Int>>()
                statOutput.lines().forEach { line ->
                    val match = Regex("""^\s*(.+?)\s*\|\s*\d+\s*(\+*)(-*)""").find(line)
                    if (match != null) {
                        val path = match.groupValues[1].trim()
                        statMap[path] = match.groupValues[2].length to match.groupValues[3].length
                    }
                }

                files = statusOutput.lines().filter { it.length >= 3 }.map { line ->
                    val status = line.substring(0, 2).trim().ifEmpty { "?" }
                    val path = line.substring(3)
                    val (adds, dels) = statMap[path] ?: (0 to 0)
                    ChangedFile(path = path, status = status, additions = adds, deletions = dels)
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    if (selectedFile != null) {
        // File diff view
        Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedFile = null; diffContent = emptyList() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = selectedFile!!.substringAfterLast("/"),
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            // File path
            Text(
                text = selectedFile!!,
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (diffLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(diffContent) { line ->
                        val (bgColor, textColor) = when (line.type) {
                            DiffLineType.ADD -> Color(0xFF1B3A1B) to Color(0xFF8FD98F)
                            DiffLineType.DELETE -> Color(0xFF3A1B1B) to Color(0xFFD98F8F)
                            DiffLineType.HEADER -> Color(0xFF1B2A3A) to Color(0xFF8FB8D9)
                            DiffLineType.CONTEXT -> Color.Transparent to Color(0xFFCCCCCC)
                        }
                        Text(
                            text = line.text,
                            color = textColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    } else {
        // File list view
        Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
            // Header with refresh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Changed Files",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (files.isNotEmpty()) {
                        Text(
                            "${files.size} files",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { refresh() }) {
                        Text("Refresh", fontSize = 12.sp)
                    }
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                error != null -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    }
                }
                files.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No changes",
                            color = Color(0xFF888888),
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(files) { file ->
                            ChangedFileRow(
                                file = file,
                                onClick = { loadFileDiff(file.path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangedFileRow(
    file: ChangedFile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status badge
        val (statusColor, statusLabel) = when (file.status) {
            "M" -> Color(0xFFFFC107) to "M"
            "A" -> Color(0xFF4CAF50) to "A"
            "D" -> Color(0xFFF44336) to "D"
            "R" -> Color(0xFF2196F3) to "R"
            "??" -> Color(0xFF888888) to "?"
            else -> Color(0xFF888888) to file.status
        }
        Text(
            text = statusLabel,
            color = statusColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )

        // File path
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.path.substringAfterLast("/"),
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.path.substringBeforeLast("/", ""),
                color = Color(0xFF888888),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // +/- counts
        if (file.additions > 0 || file.deletions > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (file.additions > 0) {
                    Text("+${file.additions}", color = Color(0xFF4CAF50), fontSize = 11.sp)
                }
                if (file.deletions > 0) {
                    Text("-${file.deletions}", color = Color(0xFFF44336), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun parseDiff(raw: String): List<DiffLine> {
    return raw.lines().map { line ->
        when {
            line.startsWith("+++") || line.startsWith("---") ->
                DiffLine(line, DiffLineType.HEADER)
            line.startsWith("@@") ->
                DiffLine(line, DiffLineType.HEADER)
            line.startsWith("diff ") ->
                DiffLine(line, DiffLineType.HEADER)
            line.startsWith("index ") ->
                DiffLine(line, DiffLineType.HEADER)
            line.startsWith("+") ->
                DiffLine(line, DiffLineType.ADD)
            line.startsWith("-") ->
                DiffLine(line, DiffLineType.DELETE)
            else ->
                DiffLine(line, DiffLineType.CONTEXT)
        }
    }
}
