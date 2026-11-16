package com.dx9901mk5.filemanager.ui.dialogs

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.BuildConfig
import com.dx9901mk5.filemanager.core.settings.AppLanguage
import com.dx9901mk5.filemanager.core.settings.DarkBackgroundStyle
import com.dx9901mk5.filemanager.core.settings.DxSettingsState
import com.dx9901mk5.filemanager.core.settings.dxStringsFor
import com.dx9901mk5.filemanager.core.shizuku.ShizukuApkInstaller
import com.dx9901mk5.filemanager.core.updater.ReleaseInfo
import com.dx9901mk5.filemanager.core.updater.UpdateChecker
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndAboutDialog(
    settings: DxSettingsState,
    onToggleRecycleBin: (Boolean) -> Unit,
    onToggleShizukuInstall: (Boolean) -> Unit,
    onToggleShowHidden: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onChangeDarkBackgroundStyle: (DarkBackgroundStyle) -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenWebShare: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onInstallUpdateApk: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember(settings.language) { dxStringsFor(settings.language) }

    var shizukuAvailable by remember { mutableStateOf(ShizukuApkInstaller.isShizukuAvailable()) }
    var shizukuReady by remember { mutableStateOf(ShizukuApkInstaller.isShizukuReady()) }
    var shizukuStatusMsg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuApkInstaller.SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                shizukuAvailable = ShizukuApkInstaller.isShizukuAvailable()
                shizukuReady = granted
                onToggleShizukuInstall(granted)
                shizukuStatusMsg = if (granted) "Shizuku 授權成功" else "Shizuku 授權遭拒絕"
            }
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }

    var checkingUpdate by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定與關於我") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingSwitchRow(
                    title = "啟用垃圾桶 (30 天自動清除)",
                    subtitle = "刪除本地檔案時暫存至 .dx_trash",
                    checked = settings.recycleBinEnabled,
                    onCheckedChange = onToggleRecycleBin
                )
                SettingSwitchRow(
                    title = s.shizukuTitle,
                    subtitle = shizukuStatusMsg ?: when {
                        shizukuReady -> "Shizuku 已授權就緒"
                        shizukuAvailable -> "偵測到 Shizuku，開啟開關將要求授權"
                        else -> "未偵測到 Shizuku 服務（請先啟動 Shizuku）"
                    },
                    checked = settings.useShizukuInstall && shizukuReady,
                    onCheckedChange = { checked ->
                        shizukuAvailable = ShizukuApkInstaller.isShizukuAvailable()
                        shizukuReady = ShizukuApkInstaller.isShizukuReady()
                        if (!checked) {
                            onToggleShizukuInstall(false)
                            shizukuStatusMsg = null
                        } else if (!shizukuAvailable) {
                            onToggleShizukuInstall(false)
                            shizukuStatusMsg = "請先啟動 Shizuku 服務後再開啟此開關"
                        } else if (!shizukuReady) {
                            ShizukuApkInstaller.requestShizukuPermission()
                            shizukuStatusMsg = "正在向 Shizuku 請求授權..."
                        } else {
                            onToggleShizukuInstall(true)
                        }
                    }
                )
                SettingSwitchRow(
                    title = "顯示隱藏資料夾與檔案",
                    subtitle = "顯示以 . 開頭的項目",
                    checked = settings.showHiddenFiles,
                    onCheckedChange = onToggleShowHidden
                )
                SettingSwitchRow(
                    title = s.darkThemeTitle,
                    subtitle = "關閉則切換為淺色模式",
                    checked = settings.darkTheme,
                    onCheckedChange = onToggleDarkTheme
                )
                if (settings.darkTheme) {
                    Text(s.darkBgStyleTitle, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = settings.darkBackgroundStyle == DarkBackgroundStyle.OLED_BLACK,
                            onClick = { onChangeDarkBackgroundStyle(DarkBackgroundStyle.OLED_BLACK) },
                            label = { Text(s.darkBgOledBlack) }
                        )
                        FilterChip(
                            selected = settings.darkBackgroundStyle == DarkBackgroundStyle.DARK_GRAY,
                            onClick = { onChangeDarkBackgroundStyle(DarkBackgroundStyle.DARK_GRAY) },
                            label = { Text(s.darkBgDarkGray) }
                        )
                    }
                }
                Text(s.languageTitle, style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onChangeLanguage(lang) },
                            label = { Text(lang.label) }
                        )
                    }
                }

                HorizontalDivider()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenStorageAnalyzer()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(s.storageAnalyzer)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenWebShare()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(s.webShare)
                    }
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenShortcuts()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("鍵盤快捷鍵說明 (Ctrl + /)")
                }

                HorizontalDivider()

                Text(
                    text = "關於 DX檔案管理器",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "版本：v${BuildConfig.VERSION_NAME} (固定簽名版)\n專案開發者：dx9901mk5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_REPO_URL))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GitHub 專案")
                    }
                    Button(
                        enabled = !checkingUpdate && !downloading,
                        onClick = {
                            checkingUpdate = true
                            updateMessage = null
                            scope.launch {
                                val res = UpdateChecker.checkLatestRelease()
                                checkingUpdate = false
                                res.onSuccess { info ->
                                    releaseInfo = info
                                    updateMessage = if (info.isNewer) {
                                        "發現新版本 ${info.tagName}！"
                                    } else {
                                        "目前已是最新版本 (${info.tagName})"
                                    }
                                }.onFailure { err ->
                                    updateMessage = "檢查失敗：${err.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (checkingUpdate) "檢查中..." else "檢查更新")
                    }
                }

                updateMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                val info = releaseInfo
                if (info != null && info.isNewer && info.apkDownloadUrl != null) {
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Button(
                            onClick = {
                                downloading = true
                                scope.launch {
                                    val dl = UpdateChecker.downloadApk(context, info.apkDownloadUrl) { p ->
                                        downloadProgress = p
                                    }
                                    downloading = false
                                    dl.onSuccess { apk -> onInstallUpdateApk(apk) }
                                        .onFailure { updateMessage = "下載失敗：${it.message}" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("下載並更新至 ${info.tagName}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
