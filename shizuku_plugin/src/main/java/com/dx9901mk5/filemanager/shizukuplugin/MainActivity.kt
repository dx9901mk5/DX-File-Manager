package com.dx9901mk5.filemanager.shizukuplugin

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val colors = darkColorScheme(
                primary = Color(0xFFF59E0B),
                onPrimary = Color(0xFF111318),
                background = Color(0xFF0E1014),
                surface = Color(0xFF181B22),
                surfaceVariant = Color(0xFF222630)
            )
            MaterialTheme(colorScheme = colors) {
                var shizukuAvailable by remember { mutableStateOf(pingShizuku()) }
                var shizukuGranted by remember { mutableStateOf(checkShizukuGranted()) }
                var statusNote by remember { mutableStateOf<String?>(null) }
                var checkingUpdate by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val currentVersion = remember { currentVersionName() }

                DisposableEffect(Unit) {
                    val binderListener = Shizuku.OnBinderReceivedListener {
                        shizukuAvailable = pingShizuku()
                        shizukuGranted = checkShizukuGranted()
                    }
                    val permListener = Shizuku.OnRequestPermissionResultListener { reqCode, grant ->
                        if (reqCode == 1001) {
                            shizukuAvailable = pingShizuku()
                            shizukuGranted = grant == PackageManager.PERMISSION_GRANTED
                            statusNote = if (shizukuGranted) "Shizuku 已授權就緒！" else "Shizuku 授權遭拒絕"
                        }
                    }
                    runCatching {
                        Shizuku.addBinderReceivedListenerSticky(binderListener)
                        Shizuku.addRequestPermissionResultListener(permListener)
                    }
                    onDispose {
                        runCatching {
                            Shizuku.removeBinderReceivedListener(binderListener)
                            Shizuku.removeRequestPermissionResultListener(permListener)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "DX Shizuku 安裝助手 v$currentVersion",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "此 App 為 DX檔案管理器 的專屬輔助安裝外掛（同時支援 GitHub 版與 Google Play 版），無法獨立操作使用。\n\n安裝後，在「DX檔案管理器」內長壓任何 APK 檔案，選單即會自動出現透過 Shizuku 輔助安裝的選項。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val shizukuText = statusNote ?: when {
                                shizukuGranted -> "Shizuku 狀態：已連線並取得授權"
                                shizukuAvailable -> "Shizuku 狀態：已偵測到服務，尚未授權"
                                else -> "Shizuku 狀態：未偵測到服務（請先啟動 Shizuku App）"
                            }
                            Text(
                                text = shizukuText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (shizukuGranted) Color(0xFF34D399) else MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!shizukuGranted) {
                                    OutlinedButton(
                                        onClick = {
                                            shizukuAvailable = pingShizuku()
                                            if (shizukuAvailable) {
                                                runCatching { Shizuku.requestPermission(1001) }
                                            } else {
                                                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                                                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                } ?: run {
                                                    statusNote = "尚未安裝 Shizuku App"
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (shizukuAvailable) "請求 Shizuku 授權" else "開啟 Shizuku")
                                    }
                                }
                                Button(
                                    onClick = {
                                        val dxIntent = packageManager.getLaunchIntentForPackage("com.dx9901mk5.filemanager")
                                            ?: packageManager.getLaunchIntentForPackage("com.dx9901mk5.filemanager.github")
                                        if (dxIntent != null) {
                                            startActivity(dxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            finish()
                                        } else {
                                            statusNote = "尚未安裝 DX檔案管理器 主程式"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("開啟 DX檔案管理器")
                                }
                            }
                            OutlinedButton(
                                enabled = !checkingUpdate,
                                onClick = {
                                    checkingUpdate = true
                                    statusNote = "正在檢查 GitHub 最新外掛版本..."
                                    scope.launch {
                                        val result = checkAndDownloadPluginUpdate(currentVersion)
                                        result.onSuccess { msg ->
                                            statusNote = msg
                                        }.onFailure { err ->
                                            statusNote = "檢查更新失敗：${err.message ?: "網路錯誤"}"
                                        }
                                        checkingUpdate = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (checkingUpdate) "檢查／下載更新中..." else "檢查外掛更新 (GitHub)")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pingShizuku(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun checkShizukuGranted(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun currentVersionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull().orEmpty().ifBlank { "1.1.0" }

    private suspend fun checkAndDownloadPluginUpdate(currentVer: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = "https://api.github.com/repos/dx9901mk5/DX_fileManager/releases?per_page=10"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "DX-Shizuku-Helper")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val releases = JSONArray(body)
            val versionRegex = Regex("""DX_Shizuku_Helper-v?(\d+(?:\.\d+)+)""", RegexOption.IGNORE_CASE)
            var bestVer: String? = null
            var bestUrl: String? = null

            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name")
                    if (name.startsWith("DX_Shizuku_Helper", ignoreCase = true) &&
                        name.endsWith(".apk", ignoreCase = true)
                    ) {
                        val ver = versionRegex.find(name)?.groupValues?.getOrNull(1) ?: continue
                        if (bestVer == null || isNewerVersion(ver, bestVer)) {
                            bestVer = ver
                            bestUrl = asset.optString("browser_download_url")
                        }
                    }
                }
            }

            if (bestVer == null || bestUrl.isNullOrBlank()) {
                return@runCatching "目前已是最新版本 (v$currentVer)"
            }
            if (!isNewerVersion(bestVer, currentVer)) {
                return@runCatching "目前已是最新外掛版本 (v$currentVer)"
            }

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                    val permIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(permIntent)
                }
            }

            val apkFile = File(cacheDir, "DX_Shizuku_Helper-v$bestVer.apk")
            val dlConn = (URL(bestUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DX-Shizuku-Helper")
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            dlConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            }
            dlConn.disconnect()

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(installIntent)
            }
            "已下載外掛 v$bestVer，正在啟動安裝..."
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val cParts = current.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
