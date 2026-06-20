package com.iptvapp.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val context: Context
) {
    // fix: a self-update download host that never responds must not hang the
    // coroutine forever — give the client real timeouts.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    // The scope of the screen that triggered the check, so a download is bound
    // to that lifecycle and cancels if the user leaves (was a free-floating
    // CoroutineScope that outlived the Activity).
    private var scope: CoroutineScope? = null

    private val versionJsonUrl =
        "https://raw.githubusercontent.com/Oliver29Klozoff/IPTV-Mj/main/version.json"

    fun check(scope: CoroutineScope) {
        this.scope = scope
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(versionJsonUrl).build()
                val body = client.newCall(request).execute().use { it.body?.string() }
                    ?: return@launch
                val json = JSONObject(body)

                val latestCode = json.getLong("versionCode")
                val latestName = json.optString("versionName", "")
                val apkUrl = json.getString("apkUrl")
                val notes = json.optString("notes", "")

                val installedCode = getInstalledVersionCode()

                if (latestCode > installedCode) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestName, notes, apkUrl)
                    }
                }
            } catch (_: Exception) {
                // Silent fail on launch check.
            }
        }
    }

    private fun getInstalledVersionCode(): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun showUpdateDialog(versionName: String, notes: String, apkUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("Update available")
            .setMessage(
                buildString {
                    append("Version ")
                    append(versionName)
                    append(" is available.")
                    if (notes.isNotBlank()) {
                        append("\n\n")
                        append(notes)
                    }
                }
            )
            .setPositiveButton("Download") { _, _ ->
                downloadAndInstall(apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(apkUrl: String) {
        val s = scope ?: return
        s.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apkUrl).build()
                // Write to the FileProvider-scoped Download dir (matches
                // file_paths.xml and SettingsActivity's downloader).
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val apkFile = File(dir, "IPTV-update.apk")
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code} for $apkUrl")
                    val stream = response.body?.byteStream() ?: throw Exception("Empty APK response")
                    stream.use { input ->
                        apkFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        // On API 26+ the app must hold "install unknown apps"; route the user to
        // grant it rather than firing an installer intent that silently no-ops.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                "Allow installing apps from this source, then try again.",
                Toast.LENGTH_LONG
            ).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
