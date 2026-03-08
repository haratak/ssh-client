package com.harataku.sshclient.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import com.harataku.sshclient.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.terminal.WcWidth

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var terminalSession: TerminalSession? = null

    private val textPaint = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
        textSize = 36f
    }

    private val bgPaint = Paint()

    private val charWidth = textPaint.measureText("M")
    private val fontMetrics = textPaint.fontMetrics
    private val charHeight = fontMetrics.descent - fontMetrics.ascent
    private val baselineOffset = -fontMetrics.ascent

    /** How many rows we're scrolled back. 0 = at bottom (live). Positive = scrolled up. */
    private var scrollOffset = 0

    private val scroller = OverScroller(context)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val rowDelta = (-distanceY / charHeight).toInt()
            if (rowDelta != 0) {
                adjustScroll(rowDelta)
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val maxScroll = terminalSession?.let {
                synchronized(it.lock) { it.emulator.screen.activeTranscriptRows }
            } ?: 0
            scroller.fling(0, scrollOffset, 0, (velocityY / charHeight).toInt(), 0, 0, 0, maxScroll)
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this@TerminalView, InputMethodManager.SHOW_IMPLICIT)
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(0xFF1E1E1E.toInt())
    }

    private fun adjustScroll(delta: Int) {
        val session = terminalSession ?: return
        val maxScroll = synchronized(session.lock) { session.emulator.screen.activeTranscriptRows }
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxScroll)
        invalidate()
    }

    fun attachSession(session: TerminalSession) {
        terminalSession = session
    }

    fun triggerRedraw() {
        // Auto-scroll to bottom when new data arrives and user is at bottom
        if (scrollOffset <= 1) scrollOffset = 0
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val cols = (w / charWidth).toInt().coerceAtLeast(1)
        val rows = (h / charHeight).toInt().coerceAtLeast(1)
        terminalSession?.resize(cols, rows)
    }

    // --- Input handling ---

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.toString()?.let { t ->
                    terminalSession?.writeInput(t)
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) { terminalSession?.writeByte(0x7F) }
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyDown(event)
                }
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return handleKeyDown(event) || super.onKeyDown(keyCode, event)
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
        // Snap to bottom on key input
        scrollOffset = 0

        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            terminalSession?.writeInput(String(Character.toChars(unicodeChar)))
            return true
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> { terminalSession?.writeByte(0x0D); true }
            KeyEvent.KEYCODE_DEL -> { terminalSession?.writeByte(0x7F); true }
            KeyEvent.KEYCODE_TAB -> { terminalSession?.writeByte(0x09); true }
            KeyEvent.KEYCODE_ESCAPE -> { terminalSession?.writeByte(0x1B); true }
            KeyEvent.KEYCODE_DPAD_UP -> { terminalSession?.writeInput("\u001b[A"); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { terminalSession?.writeInput("\u001b[B"); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { terminalSession?.writeInput("\u001b[C"); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { terminalSession?.writeInput("\u001b[D"); true }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun showKeyboard() {
        requestFocus()
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY
            val session = terminalSession ?: return
            val maxScroll = synchronized(session.lock) { session.emulator.screen.activeTranscriptRows }
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)
            postInvalidateOnAnimation()
        }
    }

    // --- Rendering ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val session = terminalSession ?: return

        synchronized(session.lock) {
            val emulator = session.emulator
            val screen = emulator.screen
            val screenRows = emulator.mRows
            val cols = emulator.mColumns
            val colors = emulator.mColors.mCurrentColors

            // externalRow range: -scrollOffset to screenRows-1-scrollOffset
            val startExternalRow = -scrollOffset
            val endExternalRow = screenRows - scrollOffset

            for (i in 0 until screenRows) {
                val externalRow = startExternalRow + i
                if (externalRow < -screen.activeTranscriptRows || externalRow >= screenRows) continue

                val internalRow = screen.externalToInternalRow(externalRow)
                val line = screen.mLines[internalRow] ?: continue

                var col = 0
                while (col < cols) {
                    val style = line.getStyle(col)
                    val foreColorIdx = TextStyle.decodeForeColor(style)
                    val backColorIdx = TextStyle.decodeBackColor(style)
                    val effect = TextStyle.decodeEffect(style)

                    var foreColor = resolveColor(foreColorIdx, colors, 0xFFFFFFFF.toInt())
                    var backColor = resolveColor(backColorIdx, colors, 0xFF1E1E1E.toInt())

                    if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0) {
                        val tmp = foreColor; foreColor = backColor; backColor = tmp
                    }

                    val x = col * charWidth
                    val y = i * charHeight

                    val startIdx = line.findStartOfColumn(col)
                    val endIdx = if (col + 1 < cols) line.findStartOfColumn(col + 1) else line.spaceUsed

                    // Determine how many columns this character spans
                    val cellCols = if (startIdx < endIdx) {
                        val codePoint = Character.codePointAt(line.mText, startIdx)
                        WcWidth.width(codePoint).coerceAtLeast(1)
                    } else 1

                    val cellPixelWidth = cellCols * charWidth

                    if (backColor != 0xFF1E1E1E.toInt()) {
                        bgPaint.color = backColor
                        canvas.drawRect(x, y, x + cellPixelWidth, y + charHeight, bgPaint)
                    }

                    if (startIdx < endIdx) {
                        val c = line.mText[startIdx]
                        if (c != ' ') {
                            textPaint.color = foreColor
                            textPaint.isFakeBoldText = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                            canvas.drawText(line.mText, startIdx, endIdx - startIdx, x, y + baselineOffset, textPaint)
                        }
                    }

                    col += cellCols
                }
            }

            // Cursor (only show when at bottom)
            if (scrollOffset == 0) {
                val cursorRow = emulator.cursorRow
                val cursorCol = emulator.cursorCol
                if (cursorRow in 0 until screenRows && cursorCol in 0 until cols) {
                    bgPaint.color = 0xAAFFFFFF.toInt()
                    val cx = cursorCol * charWidth
                    val cy = cursorRow * charHeight
                    canvas.drawRect(cx, cy, cx + charWidth, cy + charHeight, bgPaint)
                }
            }
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
}
