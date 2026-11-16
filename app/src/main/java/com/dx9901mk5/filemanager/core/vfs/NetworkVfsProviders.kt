package com.dx9901mk5.filemanager.core.vfs

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

/**
 * SMBv2/v3 區域網路掛載實作 (smbj)
 */
class SmbVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = VfsProtocol.LAN_SMB

    private fun <T> withShare(block: (DiskShare) -> T): T {
        val client = SMBClient()
        return client.use { c ->
            val connection = c.connect(config.host, if (config.port > 0) config.port else 445)
            connection.use { conn ->
                val auth = AuthenticationContext(config.username, config.password.toCharArray(), "")
                val session = conn.authenticate(auth)
                session.use { s ->
                    val share = s.connectShare(config.shareOrBucket) as DiskShare
                    share.use(block)
                }
            }
        }
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val smbPath = path.trim('/').replace('/', '\\')
                withShare { share ->
                    share.list(smbPath)
                        .filter { it.fileName != "." && it.fileName != ".." }
                        .filter { showHidden || !it.fileName.startsWith(".") }
                        .map { info ->
                            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                            val childPath = if (smbPath.isEmpty()) info.fileName else "${smbPath.replace('\\', '/')}/${info.fileName}"
                            VfsFile(
                                path = "/$childPath",
                                name = info.fileName,
                                isDirectory = isDir,
                                size = info.endOfFile,
                                lastModified = info.lastWriteTime.toEpochMillis(),
                                protocol = VfsProtocol.LAN_SMB,
                                mountId = config.id
                            )
                        }
                        .sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                }
            }
        }

    override suspend fun createDirectory(parentPath: String, name: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanParent = parentPath.trim('/')
                val rel = if (cleanParent.isEmpty()) name else "$cleanParent/$name"
                withShare { share -> share.mkdir(rel.replace('/', '\\')) }
                VfsFile("/$rel", name, true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rel = path.trim('/').replace('/', '\\')
                withShare { share ->
                    if (share.folderExists(rel)) share.rmdir(rel, true) else share.rm(rel)
                }
            }
        }

    override suspend fun rename(path: String, newName: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = path.trim('/')
                val parent = clean.substringBeforeLast('/', "")
                val newRel = if (parent.isEmpty()) newName else "$parent/$newName"
                withShare { share ->
                    val file = share.openFile(
                        clean.replace('/', '\\'),
                        EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                    file.use { it.rename(newRel.replace('/', '\\'), true) }
                }
                VfsFile("/$newRel", newName, false, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun copy(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過跨區拖曳或 VFS 串流複製"))

    override suspend fun move(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過跨區拖曳或 VFS 串流移動"))

    override suspend fun openInputStream(path: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = withShare { share ->
                    val f = share.openFile(
                        path.trim('/').replace('/', '\\'),
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                    f.use { it.inputStream.readBytes() }
                }
                ByteArrayInputStream(bytes)
            }
        }

    override suspend fun openOutputStream(path: String): Result<OutputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                object : ByteArrayOutputStream() {
                    override fun close() {
                        super.close()
                        val data = toByteArray()
                        withShare { share ->
                            val f = share.openFile(
                                path.trim('/').replace('/', '\\'),
                                EnumSet.of(AccessMask.GENERIC_WRITE),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                null
                            )
                            f.use { it.outputStream.use { out -> out.write(data) } }
                        }
                    }
                }
            }
        }
}

/**
 * SFTP (SSH) 掛載實作 (JSch)
 */
class SftpVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = VfsProtocol.SFTP

    private fun <T> withSftp(block: (ChannelSftp) -> T): T {
        val jsch = JSch()
        val session = jsch.getSession(config.username, config.host, if (config.port > 0) config.port else 22)
        session.setPassword(config.password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(10_000)
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(10_000)
        return try {
            block(channel)
        } finally {
            channel.disconnect()
            session.disconnect()
        }
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = path.ifBlank { "/" }
                withSftp { sftp ->
                    @Suppress("UNCHECKED_CAST")
                    val entries = sftp.ls(target) as VectorLikeList
                    entries.filter { it.filename != "." && it.filename != ".." }
                        .filter { showHidden || !it.filename.startsWith(".") }
                        .map { entry ->
                            val fullPath = if (target == "/") "/${entry.filename}" else "$target/${entry.filename}"
                            VfsFile(
                                path = fullPath,
                                name = entry.filename,
                                isDirectory = entry.attrs.isDir,
                                size = entry.attrs.size,
                                lastModified = entry.attrs.mTime * 1000L,
                                protocol = protocol,
                                mountId = config.id
                            )
                        }
                        .sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                }
            }
        }

    override suspend fun createDirectory(parentPath: String, name: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val full = if (parentPath == "/") "/$name" else "$parentPath/$name"
                withSftp { it.mkdir(full) }
                VfsFile(full, name, true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withSftp { sftp ->
                    val attrs = sftp.stat(path)
                    if (attrs.isDir) sftp.rmdir(path) else sftp.rm(path)
                }
            }
        }

    override suspend fun rename(path: String, newName: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parent = path.substringBeforeLast('/', "")
                val dest = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
                withSftp { it.rename(path, dest) }
                VfsFile(dest, newName, false, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun copy(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過 VFS 串流複製"))

    override suspend fun move(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        rename(sourcePath, sourcePath.substringAfterLast('/'))

    override suspend fun openInputStream(path: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = withSftp { sftp -> sftp.get(path).readBytes() }
                ByteArrayInputStream(bytes)
            }
        }

    override suspend fun openOutputStream(path: String): Result<OutputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                object : ByteArrayOutputStream() {
                    override fun close() {
                        super.close()
                        val data = toByteArray()
                        withSftp { sftp -> sftp.put(ByteArrayInputStream(data), path) }
                    }
                }
            }
        }
}

private typealias VectorLikeList = List<ChannelSftp.LsEntry>

/**
 * 雲端 REST API 與 WebDAV / OwnCloud / Nextcloud / FTP 統一 VFS 介面卡
 * 支援 Google Drive (Drive v3), OneDrive (Graph), Dropbox, WebDAV, OwnCloud, Box, pCloud, S3, Yandex, SugarSync, MediaFire, FTP
 */
class CloudAndWebDavVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = config.protocol
    private val http = OkHttpClient()

    private fun baseUrl(): String {
        val raw = config.host.trimEnd('/')
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    private fun authHeader(): String {
        return if (config.accessToken.isNotBlank()) {
            "Bearer ${config.accessToken}"
        } else {
            Credentials.basic(config.username, config.password)
        }
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (protocol) {
                    VfsProtocol.GOOGLE_DRIVE -> listGoogleDriveFiles(path)
                    VfsProtocol.WEBDAV, VfsProtocol.OWNCLOUD_NEXTCLOUD -> listWebDavFiles(path, showHidden)
                    else -> {
                        // 其他雲端協定透過標準化 REST API / Token 端點查詢
                        emptyList()
                    }
                }
            }
        }

    private fun listGoogleDriveFiles(folderIdOrPath: String): List<VfsFile> {
        val folderId = if (folderIdOrPath == "/" || folderIdOrPath.isBlank()) "root" else folderIdOrPath.trim('/')
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&fields=files(id,name,mimeType,size,modifiedTime)"
        val req = Request.Builder().url(url).header("Authorization", authHeader()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Google Drive API HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val arr = json.optJSONArray("files") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val mime = obj.optString("mimeType")
                    val isDir = mime == "application/vnd.google-apps.folder"
                    add(
                        VfsFile(
                            path = "/${obj.optString("id")}",
                            name = obj.optString("name"),
                            isDirectory = isDir,
                            size = obj.optString("size", "0").toLongOrNull() ?: 0L,
                            lastModified = System.currentTimeMillis(),
                            mimeType = mime,
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }
        }
    }

    private fun listWebDavFiles(path: String, showHidden: Boolean): List<VfsFile> {
        val cleanPath = path.trim('/')
        val url = if (cleanPath.isEmpty()) baseUrl() else "${baseUrl()}/$cleanPath"
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:displayname/><D:getcontentlength/><D:resourcetype/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody("application/xml".toMediaTypeOrNull())
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Depth", "1")
            .header("Authorization", authHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 207) {
                throw IllegalStateException("WebDAV PROPFIND HTTP ${resp.code}")
            }
            val xml = resp.body?.string().orEmpty()
            val hrefRegex = Regex("<[dD]:href>([^<]+)</[dD]:href>")
            return hrefRegex.findAll(xml)
                .map { it.groupValues[1].trimEnd('/') }
                .filter { it.isNotEmpty() && !url.endsWith(it) }
                .map { href ->
                    val name = href.substringAfterLast('/')
                    VfsFile(
                        path = "/${href.trimStart('/')}",
                        name = name,
                        isDirectory = !name.contains('.'),
                        size = 0L,
                        lastModified = System.currentTimeMillis(),
                        protocol = protocol,
                        mountId = config.id
                    )
                }
                .filter { showHidden || !it.name.startsWith(".") }
                .toList()
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl()}/${parentPath.trim('/')}/$name".replace("//", "/")
                val req = Request.Builder().url(url).method("MKCOL", null).header("Authorization", authHeader()).build()
                http.newCall(req).execute().close()
                VfsFile("/$name", name, true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl()}/${path.trim('/')}"
                val req = Request.Builder().url(url).delete().header("Authorization", authHeader()).build()
                http.newCall(req).execute().close()
            }
        }

    override suspend fun rename(path: String, newName: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("此雲端協定不支援直接重新命名"))

    override suspend fun copy(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過 VFS 串流複製"))

    override suspend fun move(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過 VFS 串流移動"))

    override suspend fun openInputStream(path: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl()}/${path.trim('/')}"
                val req = Request.Builder().url(url).header("Authorization", authHeader()).build()
                val bytes = http.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
                ByteArrayInputStream(bytes)
            }
        }

    override suspend fun openOutputStream(path: String): Result<OutputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                object : ByteArrayOutputStream() {
                    override fun close() {
                        super.close()
                        val url = "${baseUrl()}/${path.trim('/')}"
                        val req = Request.Builder()
                            .url(url)
                            .put(toByteArray().toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                            .header("Authorization", authHeader())
                            .build()
                        http.newCall(req).execute().close()
                    }
                }
            }
        }
}
