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
    private val client = OkHttpClient()

    suspend fun checkLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(BuildConfig.GITHUB_API_LATEST)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("GitHub API 回應錯誤: HTTP ${resp.code}")
                }
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
                ReleaseInfo(
                    tagName = tag,
                    releaseNotes = body,
                    apkDownloadUrl = apkUrl,
                    htmlUrl = htmlUrl,
                    isNewer = isNewerVersion(tag, BuildConfig.VERSION_NAME)
                )
            }
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(downloadUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("下載失敗: HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("空的回應內容")
                val total = body.contentLength()
                val outFile = File(context.cacheDir, "dx_file_manager_update.apk")
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } > 0) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) onProgress(downloaded.toFloat() / total.toFloat())
                        }
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
