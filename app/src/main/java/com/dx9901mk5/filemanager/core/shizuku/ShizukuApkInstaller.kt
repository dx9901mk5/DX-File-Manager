package com.dx9901mk5.filemanager.core.shizuku

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
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
    const val PLUGIN_PACKAGE = "com.dx9901mk5.filemanager.shizukuplugin"
    const val ACTION_PLUGIN_INSTALL_APK = "com.dx9901mk5.filemanager.shizukuplugin.ACTION_INSTALL_APK"
    const val EXTRA_APK_URIS = "extra_apk_uris"

    fun isShizukuPluginInstalled(context: Context): Boolean {
        val intent = Intent(ACTION_PLUGIN_INSTALL_APK)
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved != null
    }

    fun installApksViaPlugin(context: Context, apkFiles: List<File>): Result<Unit> {
        val validFiles = apkFiles.filter { it.exists() && it.isFile }
        if (validFiles.isEmpty()) {
            return Result.failure(IllegalArgumentException("找不到有效的 APK 檔案"))
        }
        if (!isShizukuPluginInstalled(context)) {
            return Result.failure(IllegalStateException("尚未安裝「DX Shizuku 安裝助手」外掛"))
        }
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uris = ArrayList<Uri>(validFiles.map { FileProvider.getUriForFile(context, authority, it) })
            val intent = Intent(ACTION_PLUGIN_INSTALL_APK).apply {
                putParcelableArrayListExtra(EXTRA_APK_URIS, uris)
                data = uris.first()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clip = ClipData.newRawUri("apk", uris.first())
                for (i in 1 until uris.size) {
                    clip.addItem(ClipData.Item(uris[i]))
                }
                clipData = clip
            }
            context.startActivity(intent)
        }
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
