package com.harataku.sshclient.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harataku.sshclient.ssh.SshSessionManager
import kotlinx.coroutines.launch

enum class LogSection(val label: String) {
    GIT_LOG("Git Log"),
    GIT_STATUS("Status"),
    BRANCH("Branch")
}

@Composable
fun LogsTab(
    sshSessionManager: SshSessionManager,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(LogSection.GIT_STATUS) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun loadSection(section: LogSection) {
        selectedSection = section
        loading = true
        scope.launch {
            try {
                val cwd = sshSessionManager.getTmuxPaneCwd()
                val escapedCwd = cwd.replace("'", "'\\''")
                content = when (section) {
                    LogSection.GIT_LOG -> sshSessionManager.exec(
                        "cd '$escapedCwd' && git log --oneline --graph -30 2>/dev/null"
                    )
                    LogSection.GIT_STATUS -> sshSessionManager.exec(
                        "cd '$escapedCwd' && git status 2>/dev/null"
                    )
                    LogSection.BRANCH -> sshSessionManager.exec(
                        "cd '$escapedCwd' && echo '## Current Branch' && git branch --show-current 2>/dev/null && echo '' && echo '## All Branches' && git branch -a 2>/dev/null && echo '' && echo '## Recent Tags' && git tag --sort=-creatordate | head -10 2>/dev/null"
                    )
                }
            } catch (e: Exception) {
                content = "Error: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Load initial section
    LaunchedEffect(Unit) {
        loadSection(LogSection.GIT_STATUS)
    }

    Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
        // Section tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LogSection.entries.forEach { section ->
                val selected = section == selectedSection
                TextButton(
                    onClick = { loadSection(section) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (selected) MaterialTheme.colorScheme.primary
                        else Color(0xFF888888)
                    )
                ) {
                    Text(section.label, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { loadSection(selectedSection) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Refresh", fontSize = 12.sp)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            val lines = content.lines()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        color = when {
                            line.startsWith("##") -> Color(0xFF8FB8D9)
                            line.trimStart().startsWith("*") -> Color(0xFF4CAF50)
                            line.contains("modified:") -> Color(0xFFFFC107)
                            line.contains("deleted:") -> Color(0xFFF44336)
                            line.contains("new file:") -> Color(0xFF4CAF50)
                            line.contains("Untracked") -> Color(0xFF888888)
                            else -> Color(0xFFCCCCCC)
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
