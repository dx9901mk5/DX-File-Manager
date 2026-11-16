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
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
 * FTP 掛載實作 (Apache Commons Net)
 */
class FtpVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = VfsProtocol.FTP

    private fun <T> withFtp(block: (FTPClient) -> T): T {
        val ftp = FTPClient()
        ftp.connectTimeout = 10_000
        ftp.controlEncoding = "UTF-8"
        ftp.connect(config.host, if (config.port > 0) config.port else 21)
        val loggedIn = ftp.login(config.username.ifBlank { "anonymous" }, config.password)
        if (!loggedIn) {
            ftp.disconnect()
            throw IllegalStateException("FTP 登入失敗：${ftp.replyString}")
        }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        return try {
            block(ftp)
        } finally {
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
        }
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = path.ifBlank { "/" }
                withFtp { ftp ->
                    ftp.listFiles(target)
                        .filter { it.name != "." && it.name != ".." }
                        .filter { showHidden || !it.name.startsWith(".") }
                        .map { f ->
                            val fullPath = if (target == "/") "/${f.name}" else "$target/${f.name}"
                            VfsFile(
                                path = fullPath,
                                name = f.name,
                                isDirectory = f.isDirectory,
                                size = f.size,
                                lastModified = f.timestamp?.timeInMillis ?: System.currentTimeMillis(),
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
                withFtp { it.makeDirectory(full) }
                VfsFile(full, name, true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withFtp { ftp ->
                    if (!ftp.deleteFile(path)) {
                        ftp.removeDirectory(path)
                    }
                }
            }
        }

    override suspend fun rename(path: String, newName: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parent = path.substringBeforeLast('/', "")
                val dest = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
                withFtp { it.rename(path, dest) }
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
                val bytes = withFtp { ftp ->
                    val out = ByteArrayOutputStream()
                    if (!ftp.retrieveFile(path, out)) {
                        throw IllegalStateException("FTP 下載失敗：${ftp.replyString}")
                    }
                    out.toByteArray()
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
                        withFtp { ftp ->
                            ftp.storeFile(path, ByteArrayInputStream(data))
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
                    val entries = sftp.ls(target) as List<ChannelSftp.LsEntry>
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

/**
 * 雲端 REST API 與 WebDAV / OwnCloud / Nextcloud / Amazon S3 統一實作
 */
class CloudAndWebDavVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = config.protocol
    private val http = OkHttpClient()

    private fun baseUrl(): String {
        val raw = config.host.trimEnd('/')
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return if (protocol == VfsProtocol.OWNCLOUD_NEXTCLOUD &&
            !withScheme.contains("/remote.php/dav", ignoreCase = true) &&
            config.username.isNotBlank()
        ) {
            "$withScheme/remote.php/dav/files/${URLEncoder.encode(config.username, "UTF-8")}"
        } else {
            withScheme
        }
    }

    private fun authHeader(): String {
        return if (config.accessToken.isNotBlank()) {
            "Bearer ${config.accessToken.trim()}"
        } else {
            Credentials.basic(config.username, config.password)
        }
    }

    private fun requireToken(): String {
        val token = config.accessToken.trim()
        if (token.isEmpty()) {
            throw IllegalStateException("尚未完成 ${protocol.displayName} 帳號授權，請點選「網頁登入授權」或填入 Access Token")
        }
        return token
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (protocol) {
                    VfsProtocol.GOOGLE_DRIVE -> listGoogleDriveFiles(path)
                    VfsProtocol.ONEDRIVE -> listOneDriveFiles(path)
                    VfsProtocol.DROPBOX -> listDropboxFiles(path)
                    VfsProtocol.BOX -> listBoxFiles(path)
                    VfsProtocol.PCLOUD -> listPCloudFiles(path)
                    VfsProtocol.YANDEX -> listYandexFiles(path)
                    VfsProtocol.AMAZON_S3 -> listS3Objects(path)
                    VfsProtocol.WEBDAV, VfsProtocol.OWNCLOUD_NEXTCLOUD -> listWebDavFiles(path, showHidden)
                    VfsProtocol.SUGARSYNC, VfsProtocol.MEDIAFIRE -> listGenericBearerCheck(path)
                    else -> emptyList()
                }
            }
        }

    private fun listGoogleDriveFiles(folderIdOrPath: String): List<VfsFile> {
        requireToken()
        val folderId = if (folderIdOrPath == "/" || folderIdOrPath.isBlank()) "root" else folderIdOrPath.trim('/')
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&fields=files(id,name,mimeType,size,modifiedTime)&pageSize=200"
        val req = Request.Builder().url(url).header("Authorization", authHeader()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Google Drive 驗證/讀取失敗 (HTTP ${resp.code})")
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
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listOneDriveFiles(path: String): List<VfsFile> {
        requireToken()
        val clean = path.trim('/')
        val url = if (clean.isEmpty()) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            val encoded = clean.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            "https://graph.microsoft.com/v1.0/me/drive/root:/$encoded:/children"
        }
        val req = Request.Builder().url(url).header("Authorization", authHeader()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("OneDrive 驗證/讀取失敗 (HTTP ${resp.code})")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val arr = json.optJSONArray("value") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name")
                    val isDir = obj.has("folder")
                    val childPath = if (clean.isEmpty()) "/$name" else "/$clean/$name"
                    add(
                        VfsFile(
                            path = childPath,
                            name = name,
                            isDirectory = isDir,
                            size = obj.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listDropboxFiles(path: String): List<VfsFile> {
        requireToken()
        val dbxPath = if (path == "/" || path.isBlank()) "" else "/${path.trim('/')}"
        val payload = JSONObject().put("path", dbxPath).toString()
        val req = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", authHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Dropbox 驗證/讀取失敗 (HTTP ${resp.code})")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val arr = json.optJSONArray("entries") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tag = obj.optString(".tag")
                    add(
                        VfsFile(
                            path = obj.optString("path_display"),
                            name = obj.optString("name"),
                            isDirectory = tag == "folder",
                            size = obj.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listBoxFiles(path: String): List<VfsFile> {
        requireToken()
        val folderId = if (path == "/" || path.isBlank()) "0" else path.trim('/')
        val req = Request.Builder()
            .url("https://api.box.com/2.0/folders/$folderId/items?limit=200&fields=id,name,type,size")
            .header("Authorization", authHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Box 驗證/讀取失敗 (HTTP ${resp.code})")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val arr = json.optJSONArray("entries") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        VfsFile(
                            path = "/${obj.optString("id")}",
                            name = obj.optString("name"),
                            isDirectory = obj.optString("type") == "folder",
                            size = obj.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listPCloudFiles(path: String): List<VfsFile> {
        val token = requireToken()
        val folderId = if (path == "/" || path.isBlank()) "0" else path.trim('/')
        val req = Request.Builder()
            .url("https://api.pcloud.com/listfolder?folderid=$folderId")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("pCloud 驗證/讀取失敗 (HTTP ${resp.code})")
            val json = JSONObject(resp.body?.string() ?: "{}")
            if (json.optInt("result", -1) != 0) {
                throw IllegalStateException("pCloud API 錯誤：${json.optString("error")}")
            }
            val arr = json.optJSONObject("metadata")?.optJSONArray("contents") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val isDir = obj.optBoolean("isfolder", false)
                    val id = if (isDir) obj.optLong("folderid").toString() else obj.optLong("fileid").toString()
                    add(
                        VfsFile(
                            path = "/$id",
                            name = obj.optString("name"),
                            isDirectory = isDir,
                            size = obj.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listYandexFiles(path: String): List<VfsFile> {
        requireToken()
        val yPath = if (path.isBlank()) "/" else path
        val encoded = URLEncoder.encode(yPath, "UTF-8")
        val req = Request.Builder()
            .url("https://cloud-api.yandex.net/v1/disk/resources?path=$encoded&limit=200")
            .header("Authorization", "OAuth ${config.accessToken.trim()}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Yandex.Disk 驗證/讀取失敗 (HTTP ${resp.code})")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val arr = json.optJSONObject("_embedded")?.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawPath = obj.optString("path").removePrefix("disk:")
                    add(
                        VfsFile(
                            path = rawPath.ifBlank { "/${obj.optString("name")}" },
                            name = obj.optString("name"),
                            isDirectory = obj.optString("type") == "dir",
                            size = obj.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listS3Objects(path: String): List<VfsFile> {
        if (config.username.isBlank() || config.password.isBlank() || config.shareOrBucket.isBlank()) {
            throw IllegalStateException("Amazon S3 請填寫 Endpoint、Bucket、Access Key 與 Secret Key")
        }
        val endpoint = baseUrl()
        val bucket = config.shareOrBucket.trim('/')
        val prefix = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val query = "list-type=2&delimiter=%2F" + if (prefix.isNotEmpty()) "&prefix=${URLEncoder.encode(prefix, "UTF-8")}" else ""
        val url = "$endpoint/$bucket?$query"
        val signedReq = signAwsV4(url, "GET", ByteArray(0))
        http.newCall(signedReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("S3 連線/簽名驗證失敗 (HTTP ${resp.code})")
            val xml = resp.body?.string().orEmpty()
            val results = mutableListOf<VfsFile>()
            Regex("<Prefix>([^<]+)</Prefix>").findAll(xml).forEach { m ->
                val p = m.groupValues[1].trimEnd('/')
                if (p != prefix.trimEnd('/') && p.isNotEmpty()) {
                    results.add(VfsFile("/$p", p.substringAfterLast('/'), true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id))
                }
            }
            Regex("<Contents>.*?<Key>([^<]+)</Key>.*?<Size>(\\d+)</Size>.*?</Contents>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml).forEach { m ->
                    val k = m.groupValues[1]
                    val sz = m.groupValues[2].toLongOrNull() ?: 0L
                    if (!k.endsWith("/")) {
                        results.add(VfsFile("/$k", k.substringAfterLast('/'), false, sz, System.currentTimeMillis(), protocol = protocol, mountId = config.id))
                    }
                }
            return results.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listGenericBearerCheck(path: String): List<VfsFile> {
        requireToken()
        val endpoint = if (protocol == VfsProtocol.SUGARSYNC) {
            "https://api.sugarsync.com/user"
        } else {
            "https://www.mediafire.com/api/1.5/folder/get_content.php?session_token=${URLEncoder.encode(config.accessToken.trim(), "UTF-8")}&content_type=folders&response_format=json"
        }
        val req = Request.Builder().url(endpoint).header("Authorization", authHeader()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("${protocol.displayName} 驗證失敗 (HTTP ${resp.code})")
            return emptyList()
        }
    }

    private fun listWebDavFiles(path: String, showHidden: Boolean): List<VfsFile> {
        if (config.host.isBlank()) throw IllegalStateException("請輸入伺服器網址")
        val cleanPath = path.trim('/')
        val url = if (cleanPath.isEmpty()) "${baseUrl()}/" else "${baseUrl()}/$cleanPath"
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
                throw IllegalStateException("WebDAV 驗證/連線失敗 (HTTP ${resp.code})")
            }
            val xml = resp.body?.string().orEmpty()
            val hrefRegex = Regex("<[dD]:href>([^<]+)</[dD]:href>")
            return hrefRegex.findAll(xml)
                .map { it.groupValues[1].trimEnd('/') }
                .filter { it.isNotEmpty() && !url.trimEnd('/').endsWith(it) }
                .map { href ->
                    val name = java.net.URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
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
                .sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                .toList()
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (protocol) {
                    VfsProtocol.WEBDAV, VfsProtocol.OWNCLOUD_NEXTCLOUD -> {
                        val url = "${baseUrl()}/${parentPath.trim('/')}/$name".replace("//", "/")
                        val req = Request.Builder()
                            .url(url)
                            .method("MKCOL", ByteArray(0).toRequestBody(null))
                            .header("Authorization", authHeader())
                            .build()
                        http.newCall(req).execute().use { if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}") }
                    }
                    VfsProtocol.DROPBOX -> {
                        val full = "/${parentPath.trim('/')}/$name".replace("//", "/")
                        val req = Request.Builder()
                            .url("https://api.dropboxapi.com/2/files/create_folder_v2")
                            .post(JSONObject().put("path", full).toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .header("Authorization", authHeader())
                            .build()
                        http.newCall(req).execute().use { if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}") }
                    }
                    else -> Unit
                }
                VfsFile("/$name", name, true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (protocol) {
                    VfsProtocol.GOOGLE_DRIVE -> {
                        val id = path.trim('/')
                        val req = Request.Builder()
                            .url("https://www.googleapis.com/drive/v3/files/$id")
                            .delete()
                            .header("Authorization", authHeader())
                            .build()
                        http.newCall(req).execute().close()
                    }
                    VfsProtocol.DROPBOX -> {
                        val req = Request.Builder()
                            .url("https://api.dropboxapi.com/2/files/delete_v2")
                            .post(JSONObject().put("path", path).toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .header("Authorization", authHeader())
                            .build()
                        http.newCall(req).execute().close()
                    }
                    else -> {
                        val url = "${baseUrl()}/${path.trim('/')}"
                        val req = Request.Builder().url(url).delete().header("Authorization", authHeader()).build()
                        http.newCall(req).execute().close()
                    }
                }
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
                val req = when (protocol) {
                    VfsProtocol.GOOGLE_DRIVE -> Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/${path.trim('/')}?alt=media")
                        .header("Authorization", authHeader())
                        .build()
                    VfsProtocol.ONEDRIVE -> {
                        val clean = path.trim('/')
                        Request.Builder()
                            .url("https://graph.microsoft.com/v1.0/me/drive/root:/$clean:/content")
                            .header("Authorization", authHeader())
                            .build()
                    }
                    VfsProtocol.DROPBOX -> Request.Builder()
                        .url("https://content.dropboxapi.com/2/files/download")
                        .post(ByteArray(0).toRequestBody(null))
                        .header("Authorization", authHeader())
                        .header("Dropbox-API-Arg", JSONObject().put("path", path).toString())
                        .build()
                    else -> Request.Builder()
                        .url("${baseUrl()}/${path.trim('/')}")
                        .header("Authorization", authHeader())
                        .build()
                }
                val bytes = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("下載失敗 (HTTP ${resp.code})")
                    resp.body?.bytes() ?: ByteArray(0)
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
                        val req = when (protocol) {
                            VfsProtocol.DROPBOX -> Request.Builder()
                                .url("https://content.dropboxapi.com/2/files/upload")
                                .post(data.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .header("Authorization", authHeader())
                                .header("Dropbox-API-Arg", JSONObject().put("path", path).put("mode", "overwrite").toString())
                                .build()
                            VfsProtocol.ONEDRIVE -> Request.Builder()
                                .url("https://graph.microsoft.com/v1.0/me/drive/root:/${path.trim('/')}:/content")
                                .put(data.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .header("Authorization", authHeader())
                                .build()
                            else -> Request.Builder()
                                .url("${baseUrl()}/${path.trim('/')}")
                                .put(data.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .header("Authorization", authHeader())
                                .build()
                        }
                        http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IllegalStateException("上傳失敗 (HTTP ${resp.code})")
                        }
                    }
                }
            }
        }

    private fun signAwsV4(url: String, method: String, payload: ByteArray): Request {
        val parsed = okhttp3.HttpUrl.Builder().let {
            val u = java.net.URI(url)
            val now = Date()
            val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
            val dateStamp = amzDate.substring(0, 8)
            val region = "us-east-1"
            val service = "s3"
            val payloadHash = sha256Hex(payload)
            val canonicalUri = u.rawPath.ifBlank { "/" }
            val canonicalQuery = u.rawQuery.orEmpty()
            val host = if (u.port > 0) "${u.host}:${u.port}" else u.host
            val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
            val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
            val canonicalRequest = "$method\n$canonicalUri\n$canonicalQuery\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
            val scope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray())}"
            val kDate = hmacSha256("AWS4${config.password}".toByteArray(), dateStamp)
            val kRegion = hmacSha256(kDate, region)
            val kService = hmacSha256(kRegion, service)
            val kSigning = hmacSha256(kService, "aws4_request")
            val sig = hmacSha256(kSigning, stringToSign).joinToString("") { "%02x".format(it) }
            val auth = "AWS4-HMAC-SHA256 Credential=${config.username}/$scope, SignedHeaders=$signedHeaders, Signature=$sig"
            Request.Builder()
                .url(url)
                .header("Host", host)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", auth)
                .build()
        }
        return parsed
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }
}
