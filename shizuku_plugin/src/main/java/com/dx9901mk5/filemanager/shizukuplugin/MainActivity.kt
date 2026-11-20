package com.dx9901mk5.filemanager.shizukuplugin

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

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
                                text = "DX Shizuku 安裝助手",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "此 App 為 DX檔案管理器 的專屬輔助安裝外掛，無法獨立點擊操作使用。\n\n請搭配「DX檔案管理器」使用：在檔案管理器內選取 APK 檔案並長壓，選單將自動出現輔助安裝選項。",
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
}
