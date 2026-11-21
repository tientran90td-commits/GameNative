package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tải file APK từ GitHub Releases của bạn rồi mở màn hình cài đặt của Android.
 */
object UpdateInstaller {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeName = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destFile = File(context.cacheDir, "update-$safeName.apk")
            if (destFile.exists()) destFile.delete()

            Timber.i("Downloading update from $downloadUrl")
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("Download failed: HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) onProgress(downloaded.toFloat() / total.toFloat())
                        }
                    }
                }
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                Timber.e("Downloaded file is missing or empty")
                return@withContext false
            }
            Timber.i("Download complete: ${destFile.length()} bytes")

            withContext(Dispatchers.Main) {
                installApk(context, destFile)
            }
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "Error downloading/installing update")
            return@withContext false
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    apkFile,
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
            for (resolveInfo in resInfoList) {
                context.grantUriPermission(
                    resolveInfo.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            context.startActivity(intent)
            Timber.i("Install intent launched")
        } catch (e: Exception) {
            Timber.e(e, "Error launching install intent")
        }
    }
}
