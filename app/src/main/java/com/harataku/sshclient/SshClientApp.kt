package com.harataku.sshclient

import android.app.Application
import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SshClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val log = "=== CRASH $timestamp ===\nThread: ${thread.name}\n$sw\n"
                val file = File(filesDir, "crash.log")
                file.appendText(log)
                Log.e("CRASH", log)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
