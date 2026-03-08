package com.harataku.sshclient.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
        ShortcutItem("中断", ShortcutAction.SendByte(0x03)),
        ShortcutItem("終了", ShortcutAction.SendByte(0x04)),
        ShortcutItem("停止", ShortcutAction.SendByte(0x1A)),
        ShortcutItem("行頭", ShortcutAction.SendByte(0x01)),
        ShortcutItem("行末", ShortcutAction.SendByte(0x05)),
        ShortcutItem("画面消去", ShortcutAction.SendByte(0x0C)),
        ShortcutItem("履歴検索", ShortcutAction.SendByte(0x12)),
        ShortcutItem("語削除", ShortcutAction.SendByte(0x17)),
        ShortcutItem("行削除", ShortcutAction.SendByte(0x15)),
    )),
    ShortcutGroup("Vim", listOf(
        ShortcutItem("保存", ShortcutAction.SendText("\u001b:w\r")),
        ShortcutItem("閉じる", ShortcutAction.SendText("\u001b:q\r")),
        ShortcutItem("保存+閉", ShortcutAction.SendText("\u001b:wq\r")),
        ShortcutItem("強制閉", ShortcutAction.SendText("\u001b:q!\r")),
        ShortcutItem("行削除", ShortcutAction.SendText("\u001bdd")),
        ShortcutItem("元に戻す", ShortcutAction.SendText("\u001bu")),
        ShortcutItem("やり直し", ShortcutAction.SendByte(0x12)),
    )),
    ShortcutGroup("tmux", listOf(
        ShortcutItem("prefix", ShortcutAction.SendByte(0x02)),
        ShortcutItem("デタッチ", ShortcutAction.SendText("\u0002d")),
        ShortcutItem("新窓", ShortcutAction.SendText("\u0002c")),
        ShortcutItem("次窓", ShortcutAction.SendText("\u0002n")),
        ShortcutItem("前窓", ShortcutAction.SendText("\u0002p")),
        ShortcutItem("コピー", ShortcutAction.SendText("\u0002[")),
        ShortcutItem("横分割", ShortcutAction.SendText("\u0002%")),
        ShortcutItem("縦分割", ShortcutAction.SendText("\u0002\"")),
    )),
    ShortcutGroup("Claude", listOf(
        ShortcutItem("入力", ShortcutAction.SendText("i")),
        ShortcutItem("モード切替", ShortcutAction.SendText("\u001b[Z")),
        ShortcutItem("承認", ShortcutAction.SendText("y\r")),
        ShortcutItem("拒否", ShortcutAction.SendText("n\r")),
        ShortcutItem("簡素化", ShortcutAction.SendText("/simplify\r")),
        ShortcutItem("一時退避", ShortcutAction.SendByte(0x13)),
        ShortcutItem("圧縮", ShortcutAction.SendText("/compact\r")),
        ShortcutItem("消去", ShortcutAction.SendText("/clear\r")),
    )),
    ShortcutGroup("Nav", listOf(
        ShortcutItem("↑", ShortcutAction.SendText("\u001b[A")),
        ShortcutItem("↓", ShortcutAction.SendText("\u001b[B")),
        ShortcutItem("←", ShortcutAction.SendText("\u001b[D")),
        ShortcutItem("→", ShortcutAction.SendText("\u001b[C")),
        ShortcutItem("先頭", ShortcutAction.SendText("\u001b[H")),
        ShortcutItem("末尾", ShortcutAction.SendText("\u001b[F")),
        ShortcutItem("補完", ShortcutAction.SendByte(0x09)),
    )),
)

@Composable
fun ModifierKeyBar(
    onShortcut: (ShortcutAction) -> Unit,
    onPaste: () -> Unit = {},
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
                label = "Esc",
                onClick = { onShortcut(ShortcutAction.SendByte(0x1B)) },
            )

            BarButton(
                label = "Enter",
                onClick = { onShortcut(ShortcutAction.SendByte(0x0D)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            BarButton(
                label = "Paste",
                onClick = onPaste,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
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
