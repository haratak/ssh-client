package com.harataku.sshclient.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    onOpenInTerminal: ((String) -> Unit)? = null,
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
        val isMarkdown = viewingFile!!.endsWith(".md") || viewingFile!!.endsWith(".markdown")

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
            } else if (isMarkdown) {
                // Markdown rendering
                MarkdownContent(
                    content = fileContent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Code view with line numbers
                val lines = fileContent.lines()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lines) { index, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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

@Composable
private fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = parseMarkdownBlocks(content)

    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(blocks) { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val (fontSize, padding) = when (block.level) {
                        1 -> 20.sp to 12.dp
                        2 -> 17.sp to 8.dp
                        3 -> 15.sp to 6.dp
                        else -> 14.sp to 4.dp
                    }
                    Text(
                        text = renderInlineMarkdown(block.text),
                        color = Color.White,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = padding, bottom = 4.dp)
                    )
                    if (block.level <= 2) {
                        Divider(color = Color(0xFF333333), thickness = 1.dp)
                    }
                }
                is MdBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117))
                            .padding(8.dp)
                    ) {
                        if (block.language.isNotEmpty()) {
                            Text(
                                text = block.language,
                                color = Color(0xFF555555),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = block.code,
                            color = Color(0xFFE6EDF3),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
                is MdBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = (block.indent * 16).dp)) {
                        Text(
                            text = block.bullet,
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = renderInlineMarkdown(block.text),
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                is MdBlock.HorizontalRule -> {
                    Divider(
                        color = Color(0xFF444444),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is MdBlock.Blank -> {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class ListItem(val bullet: String, val text: String, val indent: Int) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data object HorizontalRule : MdBlock()
    data object Blank : MdBlock()
}

private fun parseMarkdownBlocks(content: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            // Code block
            line.trimStart().startsWith("```") -> {
                val language = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.CodeBlock(language, codeLines.joinToString("\n")))
                i++ // skip closing ```
            }
            // Heading
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                val text = line.drop(level).trimStart()
                blocks.add(MdBlock.Heading(level.coerceAtMost(6), text))
                i++
            }
            // Horizontal rule
            line.trim().matches(Regex("^[-*_]{3,}$")) -> {
                blocks.add(MdBlock.HorizontalRule)
                i++
            }
            // List item
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ||
            line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val trimmed = line.trimStart()
                val (bullet, text) = if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
                    val num = trimmed.takeWhile { it.isDigit() || it == '.' }
                    num to trimmed.drop(num.length).trimStart()
                } else {
                    trimmed.take(1) to trimmed.drop(2)
                }
                blocks.add(MdBlock.ListItem(bullet, text, indent / 2))
                i++
            }
            // Blank line
            line.isBlank() -> {
                blocks.add(MdBlock.Blank)
                i++
            }
            // Paragraph
            else -> {
                val paraLines = mutableListOf(line)
                i++
                while (i < lines.size && lines[i].isNotBlank() &&
                    !lines[i].startsWith("#") &&
                    !lines[i].trimStart().startsWith("```") &&
                    !lines[i].trimStart().startsWith("- ") &&
                    !lines[i].trimStart().startsWith("* ") &&
                    !lines[i].trim().matches(Regex("^[-*_]{3,}$"))
                ) {
                    paraLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
            }
        }
    }

    return blocks
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold + Italic ***text***
                text.startsWith("***", i) -> {
                    val end = text.indexOf("***", i + 3)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 3, end))
                        }
                        i = end + 3
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Bold **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic *text* (but not **)
                text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline code `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFFE6EDF3)
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Link [text](url) - just show text
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket > 0 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > 0) {
                            withStyle(SpanStyle(color = Color(0xFF64B5F6))) {
                                append(text.substring(i + 1, closeBracket))
                            }
                            i = closeParen + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
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
