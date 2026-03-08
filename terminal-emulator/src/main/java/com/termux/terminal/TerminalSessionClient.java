package com.termux.terminal;

import androidx.annotation.NonNull;

/**
 * The interface for communication between a terminal emulator and its client.
 * Used for sending callbacks when the terminal state changes or for logging.
 */
public interface TerminalSessionClient {

    void onTextChanged();

    void onTitleChanged(String oldTitle, String newTitle);

    void onCopyTextToClipboard(String text);

    void onPasteTextFromClipboard();

    void onBell();

    void onColorsChanged();

    void onTerminalCursorStateChange(boolean state);

    Integer getTerminalCursorStyle();

    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
