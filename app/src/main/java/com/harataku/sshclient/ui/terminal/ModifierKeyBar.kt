package com.harataku.sshclient.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class ShortcutAction {
    data class SendByte(val byte: Int) : ShortcutAction()
    data class SendText(val text: String) : ShortcutAction()
}

data class ShortcutItem(val label: String, val action: ShortcutAction)

data class ShortcutGroup(val name: String, val shortcuts: List<ShortcutItem>)

val shortcutGroups = listOf(
    ShortcutGroup("Shell", listOf(
        ShortcutItem("C-c", ShortcutAction.SendByte(0x03)),
        ShortcutItem("C-d", ShortcutAction.SendByte(0x04)),
        ShortcutItem("C-z", ShortcutAction.SendByte(0x1A)),
        ShortcutItem("C-a", ShortcutAction.SendByte(0x01)),
        ShortcutItem("C-e", ShortcutAction.SendByte(0x05)),
        ShortcutItem("C-l", ShortcutAction.SendByte(0x0C)),
        ShortcutItem("C-r", ShortcutAction.SendByte(0x12)),
        ShortcutItem("C-w", ShortcutAction.SendByte(0x17)),
        ShortcutItem("C-u", ShortcutAction.SendByte(0x15)),
    )),
    ShortcutGroup("Vim", listOf(
        ShortcutItem("Esc", ShortcutAction.SendByte(0x1B)),
        ShortcutItem(":w", ShortcutAction.SendText("\u001b:w\r")),
        ShortcutItem(":q", ShortcutAction.SendText("\u001b:q\r")),
        ShortcutItem(":wq", ShortcutAction.SendText("\u001b:wq\r")),
        ShortcutItem(":q!", ShortcutAction.SendText("\u001b:q!\r")),
        ShortcutItem("dd", ShortcutAction.SendText("\u001bdd")),
        ShortcutItem("u", ShortcutAction.SendText("\u001bu")),
        ShortcutItem("C-r", ShortcutAction.SendByte(0x12)),
    )),
    ShortcutGroup("tmux", listOf(
        ShortcutItem("C-b", ShortcutAction.SendByte(0x02)),
        ShortcutItem("prefix d", ShortcutAction.SendText("\u0002d")),
        ShortcutItem("prefix c", ShortcutAction.SendText("\u0002c")),
        ShortcutItem("prefix n", ShortcutAction.SendText("\u0002n")),
        ShortcutItem("prefix p", ShortcutAction.SendText("\u0002p")),
        ShortcutItem("prefix [", ShortcutAction.SendText("\u0002[")),
        ShortcutItem("prefix %", ShortcutAction.SendText("\u0002%")),
        ShortcutItem("prefix \"", ShortcutAction.SendText("\u0002\"")),
    )),
    ShortcutGroup("Nav", listOf(
        ShortcutItem("Up", ShortcutAction.SendText("\u001b[A")),
        ShortcutItem("Down", ShortcutAction.SendText("\u001b[B")),
        ShortcutItem("Left", ShortcutAction.SendText("\u001b[D")),
        ShortcutItem("Right", ShortcutAction.SendText("\u001b[C")),
        ShortcutItem("Home", ShortcutAction.SendText("\u001b[H")),
        ShortcutItem("End", ShortcutAction.SendText("\u001b[F")),
        ShortcutItem("Tab", ShortcutAction.SendByte(0x09)),
    )),
)

@Composable
fun ModifierKeyBar(
    onShortcut: (ShortcutAction) -> Unit,
    onSwitchSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedGroup by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Popup row for expanded group
        if (expandedGroup != null) {
            val group = shortcutGroups.find { it.name == expandedGroup }
            if (group != null) {
                ShortcutPopupRow(
                    group = group,
                    onShortcut = { action ->
                        onShortcut(action)
                    },
                    onDismiss = { expandedGroup = null }
                )
            }
        }

        // Main bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarButton(
                label = "Sessions",
                onClick = onSwitchSession,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )

            Spacer(Modifier.width(2.dp))

            BarButton(
                label = "Enter",
                onClick = { onShortcut(ShortcutAction.SendByte(0x0D)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.width(2.dp))

            shortcutGroups.forEach { group ->
                BarButton(
                    label = group.name,
                    onClick = {
                        expandedGroup = if (expandedGroup == group.name) null else group.name
                    },
                    containerColor = if (expandedGroup == group.name)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (expandedGroup == group.name)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShortcutPopupRow(
    group: ShortcutGroup,
    onShortcut: (ShortcutAction) -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        group.shortcuts.forEach { item ->
            BarButton(
                label = item.label,
                onClick = { onShortcut(item.action) }
            )
        }
    }
}

@Composable
private fun BarButton(
    label: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(label, fontSize = 13.sp)
    }
}
