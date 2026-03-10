package com.harataku.sshclient.updater

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val downloadUrl: String
)

object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val REPO = "haratak/ssh-client"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: return@withContext null

            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val obj = org.json.JSONObject(json)
            val tagName = obj.getString("tag_name") // e.g. "v0.1.46"
            val latestVersion = tagName.removePrefix("v")

            if (isNewer(latestVersion, currentVersion)) {
                val assets = obj.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        return@withContext UpdateInfo(
                            version = latestVersion,
                            downloadUrl = asset.getString("browser_download_url")
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        val url = URL(updateInfo.downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val apkFile = File(context.cacheDir, "update.apk")
        conn.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
