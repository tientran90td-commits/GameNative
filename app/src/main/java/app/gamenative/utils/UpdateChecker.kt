package app.gamenative.utils

import android.content.Context
import app.gamenative.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

// Giữ nguyên lớp này vì các file khác trong app đang dùng nó.
@Serializable
data class UpdateInfo(
    val updateAvailable: Boolean,
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String? = null,
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

/**
 * Kiểm tra bản mới trên trang Releases GitHub của chính bạn.
 * Quy ước: tên tag kết thúc bằng số versionCode, ví dụ "build-100012".
 * GITHUB_REPO sẽ được GitHub Actions tự thay bằng "tên-bạn/tên-repo" khi build.
 */
object UpdateChecker {
    const val GITHUB_REPO = "OWNER/REPO"

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Update check failed: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val release = json.decodeFromString<GithubRelease>(body)

                val remoteVersionCode = Regex("(\\d+)$").find(release.tagName)
                    ?.groupValues?.get(1)?.toIntOrNull()
                if (remoteVersionCode == null) {
                    Timber.w("Update check: cannot read versionCode from tag '${release.tagName}'")
                    return@withContext null
                }

                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apk == null) {
                    Timber.w("Update check: release '${release.tagName}' has no .apk file")
                    return@withContext null
                }

                Timber.i("Update check: local=${BuildConfig.VERSION_CODE}, remote=$remoteVersionCode")
                return@withContext UpdateInfo(
                    updateAvailable = remoteVersionCode > BuildConfig.VERSION_CODE,
                    versionCode = remoteVersionCode,
                    versionName = release.tagName,
                    downloadUrl = apk.browserDownloadUrl,
                    releaseNotes = release.body?.take(600),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
        }
        return@withContext null
    }
}
