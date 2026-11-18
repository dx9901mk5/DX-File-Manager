package com.dx9901mk5.filemanager.ui.dialogs

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.dx9901mk5.filemanager.core.settings.AiProvider
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
    onChangeAiProvider: (AiProvider) -> Unit,
    onSaveAiApiKey: (AiProvider, String) -> Unit,
    onChangeAiModel: (AiProvider, String) -> Unit,
    onToggleAiThinking: (Boolean) -> Unit,
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
        ShizukuApkInstaller.ensureBinderRequested(context)
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            shizukuAvailable = ShizukuApkInstaller.isShizukuAvailable()
            shizukuReady = ShizukuApkInstaller.isShizukuReady()
            if (shizukuReady) {
                shizukuStatusMsg = "Shizuku 已授權就緒"
            } else if (shizukuAvailable) {
                shizukuStatusMsg = "偵測到 Shizuku，點擊開關即可請求授權"
            }
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            shizukuAvailable = false
            shizukuReady = false
        }
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuApkInstaller.SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                shizukuAvailable = ShizukuApkInstaller.isShizukuAvailable()
                shizukuReady = granted
                onToggleShizukuInstall(granted)
                shizukuStatusMsg = if (granted) "Shizuku 授權成功" else "Shizuku 授權遭拒絕"
            }
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(listener)
        }
        onDispose {
            runCatching {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
                Shizuku.removeRequestPermissionResultListener(listener)
            }
        }
    }

    var checkingUpdate by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(-1L) }
    var downloading by remember { mutableStateOf(false) }

    fun startUpdateDownload(url: String, tagName: String) {
        downloading = true
        downloadProgress = 0f
        downloadedBytes = 0L
        totalBytes = -1L
        updateMessage = "發現新版本 $tagName，正在自動下載..."
        scope.launch {
            val dl = UpdateChecker.downloadApk(context, url) { downloaded, total ->
                downloadedBytes = downloaded
                totalBytes = total
                if (total > 0L) {
                    downloadProgress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
            }
            downloading = false
            dl.onSuccess { apk ->
                updateMessage = "下載完成 ($tagName)，準備安裝..."
                onInstallUpdateApk(apk)
            }.onFailure {
                updateMessage = "下載失敗：${it.message}"
            }
        }
    }

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
                        else -> "未偵測到 Shizuku 服務（請先於 Shizuku App 啟動服務）"
                    },
                    checked = settings.useShizukuInstall,
                    onCheckedChange = { checked ->
                        ShizukuApkInstaller.ensureBinderRequested(context)
                        shizukuAvailable = ShizukuApkInstaller.isShizukuAvailable()
                        shizukuReady = ShizukuApkInstaller.isShizukuReady()
                        if (!checked) {
                            onToggleShizukuInstall(false)
                            shizukuStatusMsg = null
                        } else if (!shizukuAvailable) {
                            onToggleShizukuInstall(true)
                            shizukuStatusMsg = "已開啟設定；目前尚未偵測到 Shizuku 服務，請先啟動 Shizuku App"
                        } else if (!shizukuReady) {
                            onToggleShizukuInstall(true)
                            ShizukuApkInstaller.requestShizukuPermission()
                            shizukuStatusMsg = "正在向 Shizuku 請求授權..."
                        } else {
                            onToggleShizukuInstall(true)
                            shizukuStatusMsg = "Shizuku 已授權就緒"
                        }
                    }
                )
                if (!shizukuReady) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shizukuAvailable) {
                            OutlinedButton(
                                onClick = {
                                    ShizukuApkInstaller.requestShizukuPermission()
                                    shizukuStatusMsg = "已送出 Shizuku 授權請求..."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("請求 Shizuku 授權")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    } else {
                                        shizukuStatusMsg = "未安裝 Shizuku App（moe.shizuku.privileged.api）"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("開啟 Shizuku App")
                            }
                            OutlinedButton(
                                onClick = {
                                    ShizukuApkInstaller.ensureBinderRequested(context)
                                    shizukuAvailable = ShizukuApkInstaller.isShizukuAvailable()
                                    shizukuReady = ShizukuApkInstaller.isShizukuReady()
                                    shizukuStatusMsg = when {
                                        shizukuReady -> "Shizuku 已授權就緒"
                                        shizukuAvailable -> {
                                            ShizukuApkInstaller.requestShizukuPermission()
                                            "已偵測到 Shizuku，正在請求授權..."
                                        }
                                        else -> "仍未收到 Shizuku Binder（請確認系統允許關聯啟動／背景執行，或重開本 App）"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重新偵測")
                            }
                        }
                    }
                }
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
                        FilterChip(
                            selected = settings.darkBackgroundStyle == DarkBackgroundStyle.BLACK_TO_GRAY_GLASS,
                            onClick = { onChangeDarkBackgroundStyle(DarkBackgroundStyle.BLACK_TO_GRAY_GLASS) },
                            label = { Text(s.darkBgBlackToGrayGlass) }
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

                Text("AI 檔案整理助理", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = settings.aiProvider == provider,
                            onClick = { onChangeAiProvider(provider) },
                            label = {
                                Text(if (provider == AiProvider.GEMINI) "Gemini (預設)" else provider.displayName)
                            }
                        )
                    }
                }
                var aiKeyDraft by remember(settings.aiProvider, settings.currentAiApiKey) {
                    mutableStateOf(settings.currentAiApiKey)
                }
                OutlinedTextField(
                    value = aiKeyDraft,
                    onValueChange = { aiKeyDraft = it },
                    label = { Text("${settings.aiProvider.displayName} API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSaveAiApiKey(settings.aiProvider, aiKeyDraft.trim()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("儲存金鑰")
                    }
                    if (settings.currentAiApiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                aiKeyDraft = ""
                                onSaveAiApiKey(settings.aiProvider, "")
                            }
                        ) {
                            Text("登出 / 清除")
                        }
                    }
                }
                Text("AI 模型 (${settings.aiProvider.displayName})", style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    settings.aiProvider.presetModels.forEach { preset ->
                        FilterChip(
                            selected = settings.currentAiModel == preset,
                            onClick = { onChangeAiModel(settings.aiProvider, preset) },
                            label = { Text(preset) }
                        )
                    }
                }
                SettingSwitchRow(
                    title = "AI 深度思考模式 (Thinking)",
                    subtitle = "啟用推理模型思考（Gemini / DeepSeek / Claude / GPT / Grok）",
                    checked = settings.aiThinkingEnabled,
                    onCheckedChange = onToggleAiThinking
                )

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
                            updateMessage = "正在檢查最新版本..."
                            scope.launch {
                                val res = UpdateChecker.checkLatestRelease()
                                checkingUpdate = false
                                res.onSuccess { info ->
                                    releaseInfo = info
                                    if (info.isNewer) {
                                        val apkUrl = info.apkDownloadUrl
                                        if (apkUrl != null) {
                                            startUpdateDownload(apkUrl, info.tagName)
                                        } else {
                                            updateMessage = "發現新版本 ${info.tagName}，但找不到 APK 下載連結"
                                        }
                                    } else {
                                        updateMessage = "目前已是最新版本 (${info.tagName})"
                                    }
                                }.onFailure { err ->
                                    updateMessage = "檢查失敗：${err.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (checkingUpdate) "檢查中..." else if (downloading) "下載中..." else "檢查更新")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://drive.google.com/drive/folders/1h_I2rDnRJvTYe2V0_04AM9hAnk17wO5x")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            }.onFailure { updateMessage = "無法開啟測試版雲端硬碟：${it.message}" }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("檢查測試版雲端")
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://drive.google.com/drive/folders/1JrrkNtKZZIGEvpLaHuXRxGiCRJPQO6Vu")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            }.onFailure { updateMessage = "無法開啟正式版雲端硬碟：${it.message}" }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("檢查正式版雲端")
                    }
                }

                updateMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (checkingUpdate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val info = releaseInfo
                if (info != null && info.isNewer && info.apkDownloadUrl != null) {
                    if (downloading) {
                        val downloadedMb = downloadedBytes / (1024f * 1024f)
                        if (totalBytes > 0L) {
                            val totalMb = totalBytes / (1024f * 1024f)
                            val pct = (downloadProgress * 100).toInt().coerceIn(0, 100)
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = String.format("下載進度：%d%% (%.1f MB / %.1f MB)", pct, downloadedMb, totalMb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = String.format("下載中：已下載 %.1f MB", downloadedMb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Button(
                            onClick = { startUpdateDownload(info.apkDownloadUrl, info.tagName) },
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
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
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
