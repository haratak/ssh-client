package com.harataku.sshclient.ui.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Custom View that reliably receives soft keyboard input.
 * The View is invisible but handles InputConnection for the IME.
 */
class TerminalInputView(context: Context) : View(context) {

    var onTextInput: ((String) -> Unit)? = null
    var onKeyInput: ((Int) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.toString()?.let { onTextInput?.invoke(it) }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) { onKeyInput?.invoke(0x7F) }
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
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            onTextInput?.invoke(String(Character.toChars(unicodeChar)))
            return true
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> { onKeyInput?.invoke(0x0D); true }
            KeyEvent.KEYCODE_DEL -> { onKeyInput?.invoke(0x7F); true }
            KeyEvent.KEYCODE_TAB -> { onKeyInput?.invoke(0x09); true }
            KeyEvent.KEYCODE_ESCAPE -> { onKeyInput?.invoke(0x1B); true }
            KeyEvent.KEYCODE_DPAD_UP -> { onTextInput?.invoke("\u001b[A"); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { onTextInput?.invoke("\u001b[B"); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { onTextInput?.invoke("\u001b[C"); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { onTextInput?.invoke("\u001b[D"); true }
            else -> false
        }
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}
