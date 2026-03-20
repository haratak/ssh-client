package com.harataku.sshclient.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harataku.sshclient.ssh.SshSessionManager
import kotlinx.coroutines.launch

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

@Composable
fun FilesTab(
    sshSessionManager: SshSessionManager,
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewingFile by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var fileLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Initialize with cwd
    LaunchedEffect(Unit) {
        try {
            val cwd = sshSessionManager.getTmuxPaneCwd()
            currentPath = cwd
            loadDirectory(sshSessionManager, cwd) { result, err ->
                entries = result
                error = err
                loading = false
            }
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    fun navigateTo(path: String) {
        currentPath = path
        loading = true
        error = null
        viewingFile = null
        scope.launch {
            loadDirectory(sshSessionManager, path) { result, err ->
                entries = result
                error = err
                loading = false
            }
        }
    }

    fun openFile(path: String) {
        viewingFile = path
        fileLoading = true
        scope.launch {
            try {
                fileContent = sshSessionManager.exec("cat '${path.replace("'", "'\\''")}' 2>/dev/null")
            } catch (e: Exception) {
                fileContent = "Error: ${e.message}"
            } finally {
                fileLoading = false
            }
        }
    }

    if (viewingFile != null) {
        // File content viewer
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
                    onClick = { viewingFile = null; fileContent = "" },
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
                    text = viewingFile!!.substringAfterLast("/"),
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            // File path
            Text(
                text = viewingFile!!,
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (fileLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                val lines = fileContent.lines()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lines) { index, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Line number
                            Text(
                                text = "${index + 1}",
                                color = Color(0xFF555555),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .width(40.dp)
                                    .padding(end = 8.dp, start = 4.dp),
                                maxLines = 1
                            )
                            // Line content
                            Text(
                                text = line,
                                color = Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Directory browser
        Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
            // Path bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentPath != null) {
                    val parent = currentPath!!.substringBeforeLast("/").ifEmpty { "/" }
                    if (currentPath != "/") {
                        IconButton(
                            onClick = { navigateTo(parent) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    text = currentPath ?: "",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Search
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (searchQuery.isEmpty()) {
                    Text(
                        "Search files...",
                        color = Color(0xFF555555),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
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
                else -> {
                    val filtered = if (searchQuery.isNotEmpty()) {
                        entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    } else {
                        entries
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filtered) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isDirectory) {
                                            navigateTo(entry.path)
                                        } else {
                                            openFile(entry.path)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (entry.isDirectory) "/" else " ",
                                    color = if (entry.isDirectory) Color(0xFFFFC107) else Color(0xFF888888),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(14.dp)
                                )
                                Text(
                                    text = entry.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadDirectory(
    sshSessionManager: SshSessionManager,
    path: String,
    onResult: (List<FileEntry>, String?) -> Unit
) {
    try {
        val escapedPath = path.replace("'", "'\\''")
        val output = sshSessionManager.exec("ls -1pA '$escapedPath' 2>/dev/null")
        val entries = output.lines().filter { it.isNotBlank() }.map { name ->
            val isDir = name.endsWith("/")
            val cleanName = if (isDir) name.dropLast(1) else name
            FileEntry(
                name = cleanName,
                path = "$path/$cleanName",
                isDirectory = isDir
            )
        }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
        onResult(entries, null)
    } catch (e: Exception) {
        onResult(emptyList(), e.message)
    }
}
