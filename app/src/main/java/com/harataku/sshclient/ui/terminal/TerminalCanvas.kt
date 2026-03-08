package com.harataku.sshclient.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalRow
import com.termux.terminal.TextStyle

@Composable
fun TerminalCanvas(
    emulator: TerminalEmulator,
    updateTrigger: Long,
    modifier: Modifier = Modifier
) {
    val textPaint = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
        textSize = 36f
    }

    Canvas(modifier = modifier) {
        val charWidth = textPaint.measureText("M")
        val fontMetrics = textPaint.fontMetrics
        val charHeight = fontMetrics.descent - fontMetrics.ascent
        val baselineOffset = -fontMetrics.ascent

        val screen = emulator.screen
        val rows = emulator.mRows
        val cols = emulator.mColumns
        val colors = emulator.mColors.mCurrentColors

        // Draw background
        drawRect(Color(0xFF1E1E1E))

        for (row in 0 until rows) {
            val internalRow = screen.externalToInternalRow(row)
            val line = screen.mLines[internalRow] ?: continue

            for (col in 0 until cols) {
                val style = line.getStyle(col)
                val foreColorIdx = TextStyle.decodeForeColor(style)
                val backColorIdx = TextStyle.decodeBackColor(style)
                val effect = TextStyle.decodeEffect(style)

                var foreColor = resolveColor(foreColorIdx, colors, 0xFFFFFFFF.toInt())
                var backColor = resolveColor(backColorIdx, colors, 0xFF1E1E1E.toInt())

                // Handle inverse
                if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0) {
                    val tmp = foreColor
                    foreColor = backColor
                    backColor = tmp
                }

                val x = col * charWidth
                val y = row * charHeight

                // Draw cell background if not default
                if (backColor != 0xFF1E1E1E.toInt()) {
                    drawRect(
                        color = Color(backColor),
                        topLeft = Offset(x, y),
                        size = Size(charWidth, charHeight)
                    )
                }

                // Read character from row
                val charStr = getCharAtColumn(line, col, cols)
                if (charStr.isNotEmpty() && charStr[0] != ' ') {
                    textPaint.color = foreColor
                    textPaint.isFakeBoldText = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                    drawContext.canvas.nativeCanvas.drawText(
                        charStr, x, y + baselineOffset, textPaint
                    )
                }
            }
        }

        // Draw cursor
        val cursorRow = emulator.cursorRow
        val cursorCol = emulator.cursorCol
        if (cursorRow in 0 until rows && cursorCol in 0 until cols) {
            drawRect(
                color = Color(0xAAFFFFFF),
                topLeft = Offset(cursorCol * charWidth, cursorRow * charHeight),
                size = Size(charWidth, charHeight)
            )
        }
    }
}

private fun getCharAtColumn(row: TerminalRow, col: Int, totalCols: Int): String {
    val startIdx = row.findStartOfColumn(col)
    val endIdx = if (col + 1 < totalCols) {
        row.findStartOfColumn(col + 1)
    } else {
        row.spaceUsed
    }
    return if (startIdx < endIdx) {
        String(row.mText, startIdx, endIdx - startIdx)
    } else {
        ""
    }
}

private fun resolveColor(colorIdx: Int, colors: IntArray, default: Int): Int {
    return if (colorIdx and 0xff000000.toInt() == 0xff000000.toInt()) {
        colorIdx
    } else if (colorIdx in colors.indices) {
        colors[colorIdx]
    } else {
        default
    }
}
