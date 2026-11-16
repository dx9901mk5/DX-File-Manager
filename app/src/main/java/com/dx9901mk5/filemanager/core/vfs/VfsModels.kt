package com.dx9901mk5.filemanager.core.vfs

import java.io.InputStream
import java.io.OutputStream

enum class VfsProtocol(
    val displayName: String,
    val isCloud: Boolean,
    val defaultPort: Int = 0
) {
    LOCAL("本機儲存空間", false),
    GOOGLE_DRIVE("Google 雲端硬碟", true),
    DROPBOX("Dropbox", true),
    ONEDRIVE("OneDrive", true),
    LAN_SMB("區域網路 / SMB", false, 445),
    FTP("FTP", false, 21),
    SFTP("SFTP (SSH)", false, 22),
    WEBDAV("WebDAV", false, 443),
    YANDEX("Yandex.Disk", true),
    OWNCLOUD_NEXTCLOUD("OwnCloud / Nextcloud", false, 443),
    BOX("Box", true),
    PCLOUD("pCloud", true),
    AMAZON_S3("Amazon S3 / MinIO / R2", true, 443),
    SUGARSYNC("SugarSync", true),
    MEDIAFIRE("MediaFire", true)
}

enum class FileCategory(val title: String) {
    MEDIA("相片與影片"),
    IMAGES("照片"),
    VIDEOS("影片"),
    AUDIO("音訊 / 音樂"),
    DOCUMENTS("文件"),
    APKS("安裝套件 (APK)"),
    ARCHIVES("壓縮檔"),
    RECENT("最近的檔案"),
    APPS("應用程式"),
    TRASH("垃圾桶")
}

data class VfsMountConfig(
    val id: String,
    val name: String,
    val protocol: VfsProtocol,
    val host: String = "",
    val port: Int = protocol.defaultPort,
    val username: String = "",
    val password: String = "",
    val shareOrBucket: String = "",
    val accessToken: String = "",
    val rootPath: String = "/"
)

data class VfsFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String = if (isDirectory) "inode/directory" else "*/*",
    val isHidden: Boolean = name.startsWith("."),
    val protocol: VfsProtocol = VfsProtocol.LOCAL,
    val mountId: String = "local"
)

data class StorageSpaceInfo(
    val totalBytes: Long,
    val availableBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

interface VfsProvider {
    val protocol: VfsProtocol
    suspend fun listFiles(path: String, showHidden: Boolean = false): Result<List<VfsFile>>
    suspend fun createDirectory(parentPath: String, name: String): Result<VfsFile>
    suspend fun delete(path: String): Result<Unit>
    suspend fun rename(path: String, newName: String): Result<VfsFile>
    suspend fun copy(sourcePath: String, targetDirPath: String): Result<VfsFile>
    suspend fun move(sourcePath: String, targetDirPath: String): Result<VfsFile>
    suspend fun openInputStream(path: String): Result<InputStream>
    suspend fun openOutputStream(path: String): Result<OutputStream>
    suspend fun getStorageSpace(path: String = "/"): StorageSpaceInfo? = null
}
