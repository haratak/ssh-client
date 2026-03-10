package com.harataku.sshclient.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.text.InputType
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
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
import kotlin.math.abs

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

    private val fallbackPaint = Paint().apply {
        typeface = Typeface.DEFAULT
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

    // Selection state
    private var selecting = false
    private var selStartCol = 0
    private var selStartRow = 0 // external row
    private var selEndCol = 0
    private var selEndRow = 0
    private var actionMode: ActionMode? = null
    private val selectionPaint = Paint().apply { color = 0xAA336699.toInt() }

    private val scroller = OverScroller(context)

    // Cursor swipe state
    private var swiping = false
    private var swipeHorizontal = false
    private var swipeAccumX = 0f
    private var swipeAccumY = 0f
    private var lastCursorDirection: String? = null
    private val cursorRepeatHandler = Handler(Looper.getMainLooper())
    private val cursorRepeatDelay = 80L // ms between repeats
    private val cursorRepeatRunnable = object : Runnable {
        override fun run() {
            lastCursorDirection?.let { dir ->
                terminalSession?.writeInput(dir)
            }
            cursorRepeatHandler.postDelayed(this, cursorRepeatDelay)
        }
    }

    // Two-finger scroll state
    private var twoFingerScrolling = false
    private var twoFingerLastY = 0f
    private var twoFingerAccumY = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (selecting || twoFingerScrolling) return true

            // Determine direction on first scroll event
            if (!swiping) {
                swiping = true
                swipeHorizontal = abs(distanceX) > abs(distanceY)
                swipeAccumX = 0f
                swipeAccumY = 0f
            }

            if (swipeHorizontal) {
                // Horizontal swipe → cursor left/right
                swipeAccumX += -distanceX
                val steps = (swipeAccumX / charWidth).toInt()
                if (steps != 0) {
                    val dir = if (steps > 0) "\u001b[C" else "\u001b[D"
                    repeat(abs(steps)) { terminalSession?.writeInput(dir) }
                    swipeAccumX -= steps * charWidth
                    hapticTick()
                    // Start repeat if holding
                    lastCursorDirection = dir
                    cursorRepeatHandler.removeCallbacks(cursorRepeatRunnable)
                    cursorRepeatHandler.postDelayed(cursorRepeatRunnable, 400)
                }
            } else {
                // Vertical swipe → cursor up/down
                swipeAccumY += -distanceY
                val steps = (swipeAccumY / charHeight).toInt()
                if (steps != 0) {
                    val dir = if (steps > 0) "\u001b[B" else "\u001b[A"
                    repeat(abs(steps)) { terminalSession?.writeInput(dir) }
                    swipeAccumY -= steps * charHeight
                    hapticTick()
                    lastCursorDirection = dir
                    cursorRepeatHandler.removeCallbacks(cursorRepeatRunnable)
                    cursorRepeatHandler.postDelayed(cursorRepeatRunnable, 400)
                }
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (selecting) {
                clearSelection()
                return true
            }

            // Send mouse click at tap position for vim/oil.nvim support
            val col = (e.x / charWidth).toInt().coerceAtLeast(0) + 1 // 1-based
            val row = (e.y / charHeight).toInt().coerceAtLeast(0) + 1 // 1-based
            // SGR mouse press: \e[<0;col;rowM  release: \e[<0;col;rowm
            val press = "\u001b[<0;${col};${row}M"
            val release = "\u001b[<0;${col};${row}m"
            terminalSession?.writeInput(press)
            terminalSession?.writeInput(release)
            hapticTick()

            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this@TerminalView, InputMethodManager.SHOW_IMPLICIT)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            terminalSession?.writeByte(0x0D) // Enter
            hapticClick()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (swiping || twoFingerScrolling) return
            hapticClick()
            val col = (e.x / charWidth).toInt()
            val row = (e.y / charHeight).toInt() - scrollOffset
            selStartCol = col
            selStartRow = row
            selEndCol = col
            selEndRow = row
            selecting = true
            startActionMode()
            invalidate()
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    private val vibrator: Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun hapticTick() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun hapticClick() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(0xFF1E1E1E.toInt())
    }

    private fun adjustScroll(delta: Int) {
        val session = terminalSession ?: return
        val isAltBuffer = synchronized(session.lock) { session.emulator.isAlternateBufferActive }
        if (isAltBuffer) {
            // In alt buffer (vim, less, etc.), send mouse wheel events
            // SGR mouse: scroll up = button 64, scroll down = button 65
            val button = if (delta > 0) 64 else 65
            val count = abs(delta)
            // Use center of screen as mouse position
            val cols = synchronized(session.lock) { session.emulator.mColumns }
            val rows = synchronized(session.lock) { session.emulator.mRows }
            val col = cols / 2
            val row = rows / 2
            repeat(count) {
                session.writeInput("\u001b[<$button;$col;${row}M")
            }
        } else {
            // In main buffer, scroll the transcript
            val maxScroll = synchronized(session.lock) { session.emulator.screen.activeTranscriptRows }
            scrollOffset = (scrollOffset + delta).coerceIn(0, maxScroll)
            invalidate()
        }
    }

    fun attachSession(session: TerminalSession) {
        terminalSession = session
    }

    private var redrawPending = false

    fun triggerRedraw() {
        // Auto-scroll to bottom when new data arrives and user is at bottom
        if (scrollOffset <= 1) scrollOffset = 0
        // Batch redraws: only post one invalidate per frame
        if (!redrawPending) {
            redrawPending = true
            postOnAnimation {
                redrawPending = false
                invalidate()
            }
        }
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
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        // true = maintain internal Editable so IME (especially voice input) stays connected
        return object : BaseInputConnection(this, true) {
            private var sentComposing = ""   // what we've sent during current composing session
            private var prevComposing = ""   // previous composing text from IME
            private var totalSent = ""       // total text sent across commit boundaries (for voice dedup)

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val t = text?.toString() ?: ""
                val baseline = if (sentComposing.isNotEmpty()) sentComposing else totalSent
                Log.d("IME", "setComposing: t=\"$t\" baseline=\"$baseline\" sent=\"$sentComposing\" total=\"$totalSent\" prev=\"$prevComposing\"")

                if (baseline.isNotEmpty() && t.startsWith(baseline)) {
                    val unsent = t.substring(baseline.length)
                    if (unsent.isNotEmpty()) {
                        Log.d("IME", "  -> SEND unsent: \"$unsent\"")
                        terminalSession?.writeInput(unsent)
                        sentComposing = t
                    } else {
                        Log.d("IME", "  -> SKIP (no new text)")
                    }
                } else if (baseline.isEmpty() && t.isNotEmpty()) {
                    Log.d("IME", "  -> SEND all: \"$t\"")
                    terminalSession?.writeInput(t)
                    sentComposing = t
                } else if (t.isNotEmpty() && !t.startsWith(baseline) && !baseline.startsWith(t)) {
                    Log.d("IME", "  -> REPLACE incompatible")
                    totalSent = ""
                    sentComposing = ""
                    terminalSession?.writeReplace(prevComposing.length, t)
                    sentComposing = t
                } else {
                    Log.d("IME", "  -> SKIP (baseline prefix of t)")
                }

                prevComposing = t
                return super.setComposingText(text, newCursorPosition)
            }

            override fun finishComposingText(): Boolean {
                Log.d("IME", "finishComposing: prev=\"$prevComposing\" sent=\"$sentComposing\" total=\"$totalSent\"")
                if (prevComposing.isNotEmpty()) {
                    if (sentComposing.isEmpty() && totalSent.isEmpty()) {
                        Log.d("IME", "  -> SEND all prev: \"$prevComposing\"")
                        terminalSession?.writeInput(prevComposing)
                        sentComposing = prevComposing
                    } else {
                        val baseline = if (sentComposing.isNotEmpty()) sentComposing else totalSent
                        if (prevComposing.startsWith(baseline) && prevComposing != baseline) {
                            val unsent = prevComposing.substring(baseline.length)
                            Log.d("IME", "  -> SEND unsent: \"$unsent\"")
                            terminalSession?.writeInput(unsent)
                            sentComposing = prevComposing
                        } else {
                            Log.d("IME", "  -> SKIP (already sent)")
                        }
                    }
                }
                totalSent = sentComposing
                sentComposing = ""
                prevComposing = ""
                val result = super.finishComposingText()
                editable?.clear()
                return result
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val t = text?.toString() ?: ""
                val baseline = if (sentComposing.isNotEmpty()) sentComposing else totalSent
                Log.d("IME", "commitText: t=\"$t\" baseline=\"$baseline\" sent=\"$sentComposing\" total=\"$totalSent\"")
                if (baseline.isNotEmpty()) {
                    if (t.startsWith(baseline)) {
                        val remaining = t.substring(baseline.length)
                        if (remaining.isNotEmpty()) {
                            Log.d("IME", "  -> SEND remaining: \"$remaining\"")
                            terminalSession?.writeInput(remaining)
                        } else {
                            Log.d("IME", "  -> SKIP (already sent)")
                        }
                    } else {
                        Log.d("IME", "  -> REPLACE: delete ${sentComposing.length} send \"$t\"")
                        terminalSession?.writeReplace(sentComposing.length, t)
                    }
                } else {
                    if (t.isNotEmpty()) {
                        Log.d("IME", "  -> SEND all: \"$t\"")
                        terminalSession?.writeInput(t)
                    }
                }
                totalSent = t
                sentComposing = ""
                prevComposing = ""
                val result = super.commitText(text, newCursorPosition)
                editable?.clear()
                return result
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) { terminalSession?.writeByte(0x7F) }
                }
                val result = super.deleteSurroundingText(beforeLength, afterLength)
                editable?.clear()
                return result
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

        val ctrl = event.metaState and KeyEvent.META_CTRL_ON != 0
        val alt = event.metaState and KeyEvent.META_ALT_ON != 0

        // Ctrl+letter → control character (0x01-0x1A)
        if (ctrl && event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val byte = event.keyCode - KeyEvent.KEYCODE_A + 1
            terminalSession?.writeByte(byte)
            return true
        }

        // Alt+letter → ESC prefix + letter
        if (alt && event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val ch = ('a' + (event.keyCode - KeyEvent.KEYCODE_A))
            terminalSession?.writeInput("\u001b$ch")
            return true
        }

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

    private fun enterTwoFingerScroll(event: MotionEvent) {
        twoFingerScrolling = true
        twoFingerLastY = (event.getY(0) + event.getY(1)) / 2f
        twoFingerAccumY = 0f
        cursorRepeatHandler.removeCallbacks(cursorRepeatRunnable)
        lastCursorDirection = null
        swiping = false
        if (selecting) clearSelection()
        // Send a fake ACTION_CANCEL to reset GestureDetector's internal state
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        gestureDetector.onTouchEvent(cancel)
        cancel.recycle()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount

        // Prevent parent from intercepting our touch events
        parent?.requestDisallowInterceptTouchEvent(true)

        // Detect two-finger gesture on any event with 2+ pointers
        if (pointerCount >= 2 && !twoFingerScrolling) {
            enterTwoFingerScroll(event)
            return true
        }

        if (twoFingerScrolling) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount >= 2) {
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        val dy = twoFingerLastY - midY
                        twoFingerLastY = midY
                        twoFingerAccumY += dy
                        val rows = (twoFingerAccumY / charHeight).toInt()
                        if (rows != 0) {
                            adjustScroll(rows)
                            twoFingerAccumY -= rows * charHeight
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    twoFingerScrolling = false
                }
            }
            return true
        }

        // Single finger events
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cursorRepeatHandler.removeCallbacks(cursorRepeatRunnable)
                lastCursorDirection = null
                swiping = false
                swipeHorizontal = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (selecting) {
                    selEndCol = (event.x / charWidth).toInt()
                    selEndRow = (event.y / charHeight).toInt() - scrollOffset
                    invalidate()
                    return true
                }
            }
        }

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

                    // Determine character width first, then compute endIdx accordingly
                    val cellCols: Int
                    val endIdx: Int
                    if (startIdx < line.spaceUsed) {
                        val codePoint = Character.codePointAt(line.mText, startIdx)
                        cellCols = WcWidth.width(codePoint).coerceAtLeast(1)
                        val endCol = col + cellCols
                        endIdx = if (endCol < cols) line.findStartOfColumn(endCol) else line.spaceUsed
                    } else {
                        cellCols = 1
                        endIdx = startIdx
                    }

                    val cellPixelWidth = cellCols * charWidth

                    if (backColor != 0xFF1E1E1E.toInt()) {
                        bgPaint.color = backColor
                        canvas.drawRect(x, y, x + cellPixelWidth, y + charHeight, bgPaint)
                    }

                    if (startIdx < endIdx) {
                        val c = line.mText[startIdx]
                        if (c != ' ') {
                            val charCount = endIdx - startIdx
                            val str = String(line.mText, startIdx, charCount)
                            val paint = if (textPaint.hasGlyph(str)) textPaint else fallbackPaint
                            paint.color = foreColor
                            paint.isFakeBoldText = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                            canvas.drawText(line.mText, startIdx, charCount, x, y + baselineOffset, paint)
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

            // Selection highlight
            drawSelection(canvas, screenRows)
        }
    }

    // --- Selection & Copy/Paste ---

    private fun drawSelection(canvas: Canvas, screenRows: Int) {
        if (!selecting) return
        // Normalize selection range
        var r1 = selStartRow; var c1 = selStartCol
        var r2 = selEndRow; var c2 = selEndCol
        if (r1 > r2 || (r1 == r2 && c1 > c2)) {
            r1 = selEndRow; c1 = selEndCol
            r2 = selStartRow; c2 = selStartCol
        }

        for (i in 0 until screenRows) {
            val externalRow = i - scrollOffset
            if (externalRow < r1 || externalRow > r2) continue
            val y = i * charHeight
            val xStart = if (externalRow == r1) c1 * charWidth else 0f
            val xEnd = if (externalRow == r2) (c2 + 1) * charWidth else width.toFloat()
            canvas.drawRect(xStart, y, xEnd, y + charHeight, selectionPaint)
        }
    }

    private fun getSelectedText(): String {
        val session = terminalSession ?: return ""
        return synchronized(session.lock) {
            val screen = session.emulator.screen
            val cols = session.emulator.mColumns
            var r1 = selStartRow; var c1 = selStartCol
            var r2 = selEndRow; var c2 = selEndCol
            if (r1 > r2 || (r1 == r2 && c1 > c2)) {
                r1 = selEndRow; c1 = selEndCol
                r2 = selStartRow; c2 = selStartCol
            }

            val sb = StringBuilder()
            for (row in r1..r2) {
                if (row < -screen.activeTranscriptRows || row >= session.emulator.mRows) continue
                val internalRow = screen.externalToInternalRow(row)
                val line = screen.mLines[internalRow] ?: continue
                val startCol = if (row == r1) c1.coerceIn(0, cols - 1) else 0
                val endCol = if (row == r2) c2.coerceIn(0, cols - 1) else cols - 1

                for (col in startCol..endCol) {
                    val startIdx = line.findStartOfColumn(col)
                    val endIdx = if (col + 1 < cols) line.findStartOfColumn(col + 1) else line.spaceUsed
                    if (startIdx < endIdx) {
                        sb.appendRange(line.mText, startIdx, endIdx)
                    }
                }
                if (row < r2) sb.append('\n')
            }
            sb.toString().trimEnd()
        }
    }

    private fun copyToClipboard() {
        val text = getSelectedText()
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }
        clearSelection()
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        terminalSession?.writeInput(text)
    }

    private fun clearSelection() {
        selecting = false
        actionMode?.finish()
        actionMode = null
        invalidate()
    }

    private fun startActionMode() {
        actionMode = startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, 1, 0, "Copy")
                menu.add(0, 2, 0, "Paste")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    1 -> { copyToClipboard(); true }
                    2 -> { pasteFromClipboard(); clearSelection(); true }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                selecting = false
                actionMode = null
                invalidate()
            }
        }, ActionMode.TYPE_FLOATING)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cursorRepeatHandler.removeCallbacks(cursorRepeatRunnable)
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
