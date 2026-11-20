package com.dx9901mk5.filemanager.shizukuplugin

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream

class InstallProxyActivity : ComponentActivity() {
    companion object {
        const val ACTION_INSTALL_APK = "com.dx9901mk5.filemanager.shizukuplugin.ACTION_INSTALL_APK"
        const val EXTRA_APK_URIS = "extra_apk_uris"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris: List<Uri> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableArrayListExtra(EXTRA_APK_URIS, Uri::class.java)?.let { addAll(it) }
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableArrayListExtra<Uri>(EXTRA_APK_URIS)?.let { addAll(it) }
            }
            intent?.data?.let { if (it !in this) add(it) }
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "請透過 DX檔案管理器 選取 APK 進行安裝", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        if (!shizukuRunning) {
            Toast.makeText(this, "Shizuku 服務未啟動，請先開啟 Shizuku App", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val shizukuGranted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        if (!shizukuGranted) {
            runCatching { Shizuku.requestPermission(1002) }
            Toast.makeText(this, "請先允許 Shizuku 授權後再重試安裝", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Toast.makeText(this, "DX Shizuku 助手正在安裝 (${uris.size} 個 APK)...", Toast.LENGTH_SHORT).show()

        scope.launch {
            var successCount = 0
            var lastError: String? = null
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val res = installFromUri(uri)
                    if (res.isSuccess) {
                        successCount++
                    } else {
                        lastError = res.exceptionOrNull()?.message
                    }
                }
            }
            val msg = if (lastError == null) {
                "Shizuku 安裝完成（共 $successCount 個 APK）"
            } else {
                "安裝結果：成功 $successCount / ${uris.size}（$lastError）"
            }
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun installFromUri(uri: Uri): Result<String> {
        val tempApk = File(cacheDir, "install_${System.currentTimeMillis()}.apk")
        return try {
            var fileSize = -1L
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) fileSize = cursor.getLong(idx)
                }
            }

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempApk).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return Result.failure(IllegalStateException("無法讀取 APK 檔案串流"))

            val actualSize = if (tempApk.length() > 0L) tempApk.length() else fileSize
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("pm", "install", "-r", "-d", "-S", actualSize.toString()),
                null,
                null
            ) as Process

            tempApk.inputStream().use { input ->
                process.outputStream.use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                    out.flush()
                }
            }

            val outText = process.inputStream.bufferedReader().readText().trim()
            val errText = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && outText.contains("Success", ignoreCase = true)) {
                Result.success(outText)
            } else {
                Result.failure(IllegalStateException(errText.ifBlank { outText.ifBlank { "Exit $exitCode" } }))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tempApk.delete()
        }
    }
}
