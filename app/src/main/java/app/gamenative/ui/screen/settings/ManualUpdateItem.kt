package app.gamenative.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.gamenative.BuildConfig
import app.gamenative.ui.component.settings.SettingsMenuLink
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.utils.UpdateChecker
import app.gamenative.utils.UpdateInfo
import app.gamenative.utils.UpdateInstaller
import kotlinx.coroutines.launch

/**
 * Nút "Kiểm tra cập nhật" trong màn hình Cài đặt.
 * Tự đứng riêng một file: muốn bỏ nút này chỉ cần xoá file và xoá 1 dòng gọi nó.
 */
@Composable
fun ManualUpdateSettingsItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Bấm để kiểm tra bản mới") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var available by remember { mutableStateOf<UpdateInfo?>(null) }

    SettingsMenuLink(
        colors = settingsTileColors(),
        title = { Text("Kiểm tra cập nhật") },
        subtitle = {
            Text("Bản hiện tại: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n$status")
        },
        icon = { Icon(imageVector = Icons.Filled.Refresh, contentDescription = null) },
        onClick = {
            if (!checking && !downloading) {
                checking = true
                status = "Đang kiểm tra..."
                scope.launch {
                    val info = UpdateChecker.checkForUpdate(context)
                    checking = false
                    when {
                        info == null ->
                            status = "Không kiểm tra được (kiểm tra mạng, hoặc chưa có bản phát hành nào)"
                        info.updateAvailable -> {
                            available = info
                            status = "Có bản mới: ${info.versionName}"
                        }
                        else -> status = "Bạn đang dùng bản mới nhất"
                    }
                }
            }
        },
    )

    val info = available
    if (info != null) {
        AlertDialog(
            onDismissRequest = { if (!downloading) available = null },
            title = { Text("Có bản cập nhật mới") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Phiên bản: ${info.versionName}")
                    val notes = info.releaseNotes
                    if (!notes.isNullOrBlank()) {
                        Text("\n$notes")
                    }
                    if (downloading) {
                        Text("\nĐang tải: ${(progress * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!downloading) {
                            downloading = true
                            progress = 0f
                            scope.launch {
                                val ok = UpdateInstaller.downloadAndInstall(
                                    context = context,
                                    downloadUrl = info.downloadUrl,
                                    versionName = info.versionName,
                                    onProgress = { progress = it },
                                )
                                downloading = false
                                available = null
                                status = if (ok) {
                                    "Đã tải xong, hãy bấm Cài đặt ở màn hình hiện ra"
                                } else {
                                    "Tải bản cập nhật thất bại, thử lại sau"
                                }
                            }
                        }
                    },
                ) { Text(if (downloading) "Đang tải..." else "Cập nhật") }
            },
            dismissButton = {
                TextButton(onClick = { if (!downloading) available = null }) { Text("Để sau") }
            },
        )
    }
}
