package com.dx9901mk5.filemanager.core.shizuku

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import rikka.shizuku.Shizuku
import java.io.File

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val apkSizeBytes: Long,
    val hasSplitApks: Boolean,
    val firstInstallTime: Long
)

object ShizukuApkInstaller {

    const val SHIZUKU_REQUEST_CODE = 1001

    fun ensureBinderRequested(context: Context) {
        // 單進程 App 由 ShizukuProvider 自動接收 Binder，勿呼叫 enableMultiProcessSupport(false) 以免覆寫 isProviderProcess
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * 使用 Shizuku 以 Shell 權限靜默安裝 APK (透過 stdin 串流寫入 pm install -r -d -S，避開 Android 11+ FUSE/SELinux 讀檔限制)
     */
    suspend fun installApkSilently(apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable()) {
            return@withContext Result.failure(IllegalStateException("Shizuku 服務未啟動，請先開啟 Shizuku App"))
        }
        if (!isShizukuReady()) {
            requestShizukuPermission()
            return@withContext Result.failure(IllegalStateException("尚未取得 Shizuku 授權，請於彈窗點擊允許"))
        }
        if (!apkFile.exists() || !apkFile.isFile) {
            return@withContext Result.failure(IllegalArgumentException("找不到 APK 檔案：${apkFile.absolutePath}"))
        }
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val fileSize = apkFile.length()
            val process = newProcessMethod.invoke(
                null,
                arrayOf("pm", "install", "-r", "-d", "-S", fileSize.toString()),
                null,
                null
            ) as Process

            apkFile.inputStream().use { input ->
                process.outputStream.use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                    out.flush()
                }
            }

            val output = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.contains("Success", ignoreCase = true)) {
                Result.success(output.trim())
            } else {
                Result.failure(RuntimeException("Shizuku 安裝失敗 (Code $exitCode): ${output.trim()} ${err.trim()}".trim()))
            }
        } catch (e: Throwable) {
            val causeMsg = e.cause?.message ?: e.message ?: e.javaClass.simpleName
            Result.failure(RuntimeException("無法呼叫 Shizuku：$causeMsg", e))
        }
    }

    /**
     * 使用 Shizuku 列出受保護目錄 (如 Android/data、Android/obb、系統根目錄)
     */
    suspend fun listDirectoryWithShizuku(dirPath: String, showHidden: Boolean): List<VfsFile> =
        withContext(Dispatchers.IO) {
            if (!isShizukuReady()) return@withContext emptyList()
            runCatching {
                val escaped = dirPath.replace("\"", "\\\"")
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                // 輸出格式：類型(d/f/l)|大小|名稱
                val cmd = "for f in \"$escaped\"/* \"$escaped\"/.*; do " +
                    "b=\"\${f##*/}\"; [ \"\$b\" = \".\" ] || [ \"\$b\" = \"..\" ] || [ ! -e \"\$f\" ] && continue; " +
                    "if [ -d \"\$f\" ]; then t=d; s=0; else t=f; s=\$(wc -c < \"\$f\" 2>/dev/null || echo 0); fi; " +
                    "echo \"\$t|\$s|\$b\"; done"
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                ) as Process
                val out = process.inputStream.bufferedReader().readLines()
                process.waitFor()
                val normalizedDir = dirPath.trimEnd('/').ifEmpty { "/" }
                out.mapNotNull { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size < 3) return@mapNotNull null
                    val isDir = parts[0] == "d"
                    val size = parts[1].trim().toLongOrNull() ?: 0L
                    val name = parts[2]
                    if (!showHidden && name.startsWith(".")) return@mapNotNull null
                    val fullPath = if (normalizedDir == "/") "/$name" else "$normalizedDir/$name"
                    VfsFile(
                        name = name,
                        path = fullPath,
                        isDirectory = isDir,
                        size = size,
                        lastModified = System.currentTimeMillis(),
                        mimeType = if (isDir) "inode/directory" else "application/octet-stream",
                        mountId = "local",
                        isEmpty = false
                    )
                }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
            }.getOrDefault(emptyList())
        }

    /**
     * 讀取已安裝應用程式清單
     */
    suspend fun listInstalledApps(context: Context, includeSystem: Boolean): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            packages.mapNotNull { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@mapNotNull null

                val sourceFile = File(appInfo.sourceDir)
                val splitFiles = appInfo.splitSourceDirs?.map { File(it) }.orEmpty()
                val totalSize = sourceFile.length() + splitFiles.sumOf { it.length() }
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                InstalledAppInfo(
                    packageName = pkgInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    versionName = pkgInfo.versionName ?: "1.0",
                    versionCode = versionCode,
                    isSystemApp = isSystem,
                    apkSizeBytes = totalSize,
                    hasSplitApks = splitFiles.isNotEmpty(),
                    firstInstallTime = pkgInfo.firstInstallTime
                )
            }.sortedBy { it.appName.lowercase() }
        }

    /**
     * 應用程式專區：將已安裝的 App 備份轉出為 APK 或 Split APKs (.apks)
     */
    suspend fun exportInstalledAppApk(
        context: Context,
        packageName: String,
        destDir: File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "DX_Backup_APK"
        )
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString()
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val versionName = pkgInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            if (!destDir.exists()) destDir.mkdirs()
            val sourceApk = File(appInfo.sourceDir)
            val splitDirs = appInfo.splitSourceDirs

            if (splitDirs.isNullOrEmpty()) {
                val targetApk = File(destDir, "${appLabel}_${versionName}($versionCode).apk")
                sourceApk.copyTo(targetApk, overwrite = true)
                Result.success(targetApk)
            } else {
                val targetApks = File(destDir, "${appLabel}_${versionName}($versionCode).apks")
                if (targetApks.exists()) targetApks.delete()
                val zip = ZipFile(targetApks)
                zip.addFile(sourceApk)
                splitDirs.forEach { splitPath ->
                    val f = File(splitPath)
                    if (f.exists()) zip.addFile(f)
                }
                Result.success(targetApks)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
