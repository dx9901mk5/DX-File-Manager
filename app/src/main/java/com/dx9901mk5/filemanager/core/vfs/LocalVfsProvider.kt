package com.dx9901mk5.filemanager.core.vfs

import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class LocalVfsProvider : VfsProvider {
    override val protocol: VfsProtocol = VfsProtocol.LOCAL

    val rootStoragePath: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(path.ifBlank { rootStoragePath })
                if (!dir.exists() || !dir.isDirectory) {
                    return@runCatching emptyList()
                }
                val children = dir.listFiles().orEmpty()
                    .filter { showHidden || (!it.isHidden && !it.name.startsWith(".")) }
                    .map { it.toVfsFile() }
                    .sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                children
            }
        }

    override suspend fun createDirectory(parentPath: String, name: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(parentPath, name)
                if (!target.exists() && !target.mkdirs()) {
                    throw IllegalStateException("無法建立資料夾: ${target.absolutePath}")
                }
                target.toVfsFile()
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(path)
                if (target.exists() && !target.deleteRecursively()) {
                    throw IllegalStateException("無法刪除: $path")
                }
            }
        }

    override suspend fun rename(path: String, newName: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = File(path)
                val dest = File(source.parentFile, newName)
                if (!source.renameTo(dest)) {
                    throw IllegalStateException("重新命名失敗: $path -> $newName")
                }
                dest.toVfsFile()
            }
        }

    override suspend fun copy(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(sourcePath)
                val dest = uniqueTargetFile(File(targetDirPath), src.name)
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = false)
                } else {
                    src.copyTo(dest, overwrite = false)
                }
                dest.toVfsFile()
            }
        }

    override suspend fun move(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(sourcePath)
                val dest = uniqueTargetFile(File(targetDirPath), src.name)
                if (!src.renameTo(dest)) {
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = false)
                    } else {
                        src.copyTo(dest, overwrite = false)
                    }
                    src.deleteRecursively()
                }
                dest.toVfsFile()
            }
        }

    override suspend fun openInputStream(path: String): Result<InputStream> =
        withContext(Dispatchers.IO) { runCatching { File(path).inputStream() } }

    override suspend fun openOutputStream(path: String): Result<OutputStream> =
        withContext(Dispatchers.IO) { runCatching { File(path).outputStream() } }

    override suspend fun getStorageSpace(path: String): StorageSpaceInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val stat = StatFs(path.ifBlank { rootStoragePath })
                StorageSpaceInfo(
                    totalBytes = stat.totalBytes,
                    availableBytes = stat.availableBytes
                )
            }.getOrNull()
        }

    suspend fun listByCategory(category: FileCategory, showHidden: Boolean = false): List<VfsFile> =
        withContext(Dispatchers.IO) {
            val root = File(rootStoragePath)
            if (!root.exists()) return@withContext emptyList()
            val results = mutableListOf<VfsFile>()
            val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
            val videoExt = setOf("mp4", "mkv", "avi", "mov", "webm", "flv")
            val audioExt = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a")
            val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv")
            val archiveExt = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz")

            val maxResults = 300
            fun scan(dir: File, depth: Int) {
                if (depth > 6 || results.size >= maxResults) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (results.size >= maxResults) break
                    if (!showHidden && (f.isHidden || f.name.startsWith("."))) continue
                    if (f.name == ".dx_trash") continue
                    if (f.isDirectory) {
                        scan(f, depth + 1)
                    } else {
                        val ext = f.extension.lowercase()
                        val matches = when (category) {
                            FileCategory.IMAGES, FileCategory.PHOTOS -> ext in imageExt
                            FileCategory.VIDEOS -> ext in videoExt
                            FileCategory.AUDIO -> ext in audioExt
                            FileCategory.DOCUMENTS -> ext in docExt
                            FileCategory.APKS -> ext == "apk" || ext == "apks"
                            FileCategory.ARCHIVES -> ext in archiveExt
                            FileCategory.RECENT -> System.currentTimeMillis() - f.lastModified() < 7L * 24 * 60 * 60 * 1000
                            else -> false
                        }
                        if (matches) results.add(f.toVfsFile())
                    }
                }
            }
            scan(root, 0)
            results.sortedByDescending { it.lastModified }
        }

    private fun uniqueTargetFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }

    private fun File.toVfsFile(): VfsFile {
        val ext = extension.lowercase()
        val mime = if (isDirectory) {
            "inode/directory"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        return VfsFile(
            path = absolutePath,
            name = name,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            mimeType = mime,
            isHidden = isHidden || name.startsWith("."),
            protocol = VfsProtocol.LOCAL,
            mountId = "local"
        )
    }
}
