package com.dx9901mk5.filemanager.core.updater

import android.content.Context
import com.dx9901mk5.filemanager.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val isNewer: Boolean
)

object UpdateChecker {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DRIVE_RELEASE_FOLDER_ID = "1JrrkNtKZZIGEvpLaHuXRxGiCRJPQO6Vu"
    private const val DRIVE_TEST_FOLDER_ID = "1h_I2rDnRJvTYe2V0_04AM9hAnk17wO5x"

    private val driveEntryRegex = Regex(
        """/file/d/([A-Za-z0-9_-]+)/view[^>]*>.*?class="flip-entry-title">([^<]+\.apk)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val versionInNameRegex = Regex("""v?(\d+(?:\.\d+)+)""", RegexOption.IGNORE_CASE)

    suspend fun checkLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = mutableListOf<ReleaseInfo>()

            // 1. 嘗試 GitHub Releases API
            runCatching {
                val req = Request.Builder()
                    .url(BuildConfig.GITHUB_API_LATEST)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "DX-FileManager")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string() ?: "{}")
                        val tag = json.optString("tag_name", BuildConfig.VERSION_NAME)
                        val body = json.optString("body", "")
                        val htmlUrl = json.optString("html_url", BuildConfig.GITHUB_REPO_URL)
                        val assets = json.optJSONArray("assets")
                        var apkUrl: String? = null
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkUrl = asset.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                        if (apkUrl != null) {
                            candidates += ReleaseInfo(
                                tagName = tag,
                                releaseNotes = body,
                                apkDownloadUrl = apkUrl,
                                htmlUrl = htmlUrl,
                                isNewer = isNewerVersion(tag, BuildConfig.VERSION_NAME)
                            )
                        }
                    }
                }
            }

            // 2. 檢查 Google 雲端硬碟（正式版資料夾、測試版資料夾）
            fetchLatestFromDriveFolder(DRIVE_RELEASE_FOLDER_ID, "正式版雲端硬碟")?.let { candidates += it }
            fetchLatestFromDriveFolder(DRIVE_TEST_FOLDER_ID, "測試版雲端硬碟")?.let { candidates += it }

            if (candidates.isEmpty()) {
                throw IllegalStateException("無法取得線上版本資訊，請檢查網路連線")
            }

            candidates.maxWithOrNull { a, b ->
                when {
                    isNewerVersion(a.tagName, b.tagName) -> 1
                    isNewerVersion(b.tagName, a.tagName) -> -1
                    else -> 0
                }
            }!!
        }
    }

    suspend fun checkDriveRelease(isTestChannel: Boolean): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = if (isTestChannel) DRIVE_TEST_FOLDER_ID else DRIVE_RELEASE_FOLDER_ID
            val label = if (isTestChannel) "測試版雲端硬碟" else "正式版雲端硬碟"
            fetchLatestFromDriveFolder(folderId, label)
                ?: ReleaseInfo(
                    tagName = BuildConfig.VERSION_NAME,
                    releaseNotes = "${label}目前尚無 APK 檔案",
                    apkDownloadUrl = null,
                    htmlUrl = "https://drive.google.com/drive/folders/$folderId",
                    isNewer = false
                )
        }
    }

    private fun fetchLatestFromDriveFolder(folderId: String, sourceLabel: String): ReleaseInfo? {
        return runCatching {
            val folderUrl = "https://drive.google.com/embeddedfolderview?id=$folderId#list"
            val req = Request.Builder()
                .url(folderUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val parsed = driveEntryRegex.findAll(html).mapNotNull { match ->
                    val fileId = match.groupValues[1]
                    val fileName = match.groupValues[2].trim()
                    val verMatch = versionInNameRegex.find(fileName) ?: return@mapNotNull null
                    val tag = "v${verMatch.groupValues[1]}"
                    Triple(fileId, fileName, tag)
                }.toList()

                val latest = parsed.maxWithOrNull { a, b ->
                    when {
                        isNewerVersion(a.third, b.third) -> 1
                        isNewerVersion(b.third, a.third) -> -1
                        else -> 0
                    }
                } ?: return null

                val (fileId, fileName, tag) = latest
                ReleaseInfo(
                    tagName = tag,
                    releaseNotes = "來源：$sourceLabel ($fileName)",
                    apkDownloadUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t",
                    htmlUrl = "https://drive.google.com/drive/folders/$folderId",
                    isNewer = isNewerVersion(tag, BuildConfig.VERSION_NAME)
                )
            }
        }.getOrNull()
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(downloadUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("下載失敗: HTTP ${resp.code}")
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw IllegalStateException("雲端回傳網頁而非 APK 檔案，請改用網頁下載")
                }
                val body = resp.body ?: throw IllegalStateException("空的回應內容")
                val total = body.contentLength()
                val outFile = File(context.cacheDir, "dx_file_manager_update.apk")
                withContext(Dispatchers.Main) { onProgress(0L, total) }
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(16384)
                        var downloaded = 0L
                        var read: Int
                        var lastReportTime = 0L
                        while (input.read(buf).also { read = it } > 0) {
                            output.write(buf, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime >= 80L) {
                                lastReportTime = now
                                val curDownloaded = downloaded
                                withContext(Dispatchers.Main) { onProgress(curDownloaded, total) }
                            }
                        }
                        withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                    }
                }
                outFile
            }
        }
    }

    internal fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val rParts = remoteTag.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val cParts = currentVersion.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
