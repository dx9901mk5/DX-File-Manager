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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.net.URLEncoder
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
 * FTP 掛載實作 (Apache Commons Net)
 */
class FtpVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = VfsProtocol.FTP

    private fun <T> withFtp(block: (FTPClient) -> T): T {
        val ftp = FTPClient()
        ftp.controlEncoding = "UTF-8"
        ftp.connectTimeout = 10_000
        ftp.connect(config.host, if (config.port > 0) config.port else 21)
        val user = config.username.ifBlank { "anonymous" }
        val pass = config.password.ifBlank { "anonymous@" }
        if (!ftp.login(user, pass)) {
            ftp.disconnect()
            throw IllegalStateException("FTP 登入失敗 (${ftp.replyString.trim()})")
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
                        .map { entry ->
                            val fullPath = if (target == "/") "/${entry.name}" else "${target.trimEnd('/')}/${entry.name}"
                            VfsFile(
                                path = fullPath,
                                name = entry.name,
                                isDirectory = entry.isDirectory,
                                size = entry.size,
                                lastModified = entry.timestamp?.timeInMillis ?: System.currentTimeMillis(),
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
                val full = if (parentPath == "/") "/$name" else "${parentPath.trimEnd('/')}/$name"
                withFtp { ftp ->
                    if (!ftp.makeDirectory(full)) throw IllegalStateException("FTP 建立資料夾失敗: ${ftp.replyString}")
                }
                VfsFile(full, name, true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withFtp { ftp ->
                    if (!ftp.deleteFile(path) && !ftp.removeDirectory(path)) {
                        throw IllegalStateException("FTP 刪除失敗: ${ftp.replyString}")
                    }
                }
            }
        }

    override suspend fun rename(path: String, newName: String): Result<VfsFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parent = path.substringBeforeLast('/', "")
                val dest = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
                withFtp { ftp ->
                    if (!ftp.rename(path, dest)) throw IllegalStateException("FTP 重命名失敗: ${ftp.replyString}")
                }
                VfsFile(dest, newName, false, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
        }

    override suspend fun copy(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過 VFS 串流複製"))

    override suspend fun move(sourcePath: String, targetDirPath: String): Result<VfsFile> =
        Result.failure(UnsupportedOperationException("請透過 VFS 串流移動"))

    override suspend fun openInputStream(path: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = withFtp { ftp ->
                    val out = ByteArrayOutputStream()
                    if (!ftp.retrieveFile(path, out)) throw IllegalStateException("FTP 下載失敗")
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
                            if (!ftp.storeFile(path, ByteArrayInputStream(data))) {
                                throw IllegalStateException("FTP 上傳失敗")
                            }
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
                            val fullPath = if (target == "/") "/${entry.filename}" else "${target.trimEnd('/')}/${entry.filename}"
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
                val full = if (parentPath == "/") "/$name" else "${parentPath.trimEnd('/')}/$name"
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
 * 雲端 REST API 與 WebDAV / OwnCloud / Nextcloud / S3 統一 VFS 介面卡
 */
class CloudAndWebDavVfsProvider(private val config: VfsMountConfig) : VfsProvider {
    override val protocol: VfsProtocol = config.protocol
    private val http = OkHttpClient()

    @Volatile
    private var runtimeAccessToken: String = config.accessToken

    private fun baseUrl(): String {
        val raw = config.host.trimEnd('/')
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return if (protocol == VfsProtocol.OWNCLOUD_NEXTCLOUD && !withScheme.contains("/remote.php/dav")) {
            "$withScheme/remote.php/dav/files/${URLEncoder.encode(config.username, "UTF-8")}"
        } else {
            withScheme
        }
    }

    private fun authHeader(): String {
        val tok = runtimeAccessToken.ifBlank { config.accessToken }
        return if (tok.isNotBlank()) {
            "Bearer ${tok.trim()}"
        } else {
            Credentials.basic(config.username, config.password)
        }
    }

    private fun executeGoogleDriveRequest(buildReq: () -> Request): Response {
        if (runtimeAccessToken.isBlank() && config.username.isNotBlank()) {
            OAuth2AuthHelper.refreshGoogleDriveTokenSync(
                com.dx9901mk5.filemanager.DxFileManagerApp.instance,
                config.username,
                null
            )?.let { refreshed ->
                runtimeAccessToken = refreshed
                persistRefreshedToken(refreshed)
            }
        }
        val firstResp = http.newCall(buildReq()).execute()
        if (firstResp.code == 401 && config.username.isNotBlank()) {
            firstResp.close()
            val refreshed = OAuth2AuthHelper.refreshGoogleDriveTokenSync(
                com.dx9901mk5.filemanager.DxFileManagerApp.instance,
                config.username,
                runtimeAccessToken
            )
            if (!refreshed.isNullOrBlank()) {
                runtimeAccessToken = refreshed
                persistRefreshedToken(refreshed)
                return http.newCall(buildReq()).execute()
            }
        }
        return firstResp
    }

    private fun persistRefreshedToken(newToken: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                com.dx9901mk5.filemanager.DxFileManagerApp.instance.preferences.saveCloudMount(
                    config.copy(accessToken = newToken)
                )
            }
        }
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): Result<List<VfsFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (protocol) {
                    VfsProtocol.GOOGLE_DRIVE -> listGoogleDriveFiles(path)
                    VfsProtocol.DROPBOX -> listDropboxFiles(path)
                    VfsProtocol.ONEDRIVE -> listOneDriveFiles(path)
                    VfsProtocol.BOX -> listBoxFiles(path)
                    VfsProtocol.PCLOUD -> listPCloudFiles(path)
                    VfsProtocol.YANDEX -> listYandexFiles(path)
                    VfsProtocol.WEBDAV, VfsProtocol.OWNCLOUD_NEXTCLOUD -> listWebDavFiles(path, showHidden)
                    VfsProtocol.AMAZON_S3 -> listS3Bucket(path)
                    VfsProtocol.SUGARSYNC, VfsProtocol.MEDIAFIRE -> {
                        require(config.accessToken.isNotBlank()) { "請先完成 ${protocol.displayName} 帳號授權或填入 Access Token" }
                        emptyList()
                    }
                    else -> emptyList()
                }
            }
        }

    private fun listGoogleDriveFiles(folderIdOrPath: String): List<VfsFile> {
        require(runtimeAccessToken.isNotBlank() || config.username.isNotBlank()) {
            "請先登入 Google 雲端硬碟取得授權 Token"
        }
        val folderId = if (folderIdOrPath == "/" || folderIdOrPath.isBlank()) "root" else folderIdOrPath.trim('/')
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&fields=files(id,name,mimeType,size,modifiedTime)&pageSize=200"
        executeGoogleDriveRequest {
            Request.Builder().url(url).header("Authorization", authHeader()).build()
        }.use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Google Drive 驗證/連線失敗 (HTTP ${resp.code})")
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

    private fun listDropboxFiles(path: String): List<VfsFile> {
        require(config.accessToken.isNotBlank()) { "請先登入 Dropbox 取得授權 Token" }
        val dbxPath = if (path == "/" || path.isBlank()) "" else "/${path.trim('/')}"
        val payload = JSONObject().put("path", dbxPath).toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(payload)
            .header("Authorization", authHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Dropbox 連線失敗 (HTTP ${resp.code})")
            val entries = JSONObject(resp.body?.string() ?: "{}").optJSONArray("entries") ?: return emptyList()
            return buildList {
                for (i in 0 until entries.length()) {
                    val o = entries.getJSONObject(i)
                    val isDir = o.optString(".tag") == "folder"
                    add(
                        VfsFile(
                            path = o.optString("path_display"),
                            name = o.optString("name"),
                            isDirectory = isDir,
                            size = o.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }.sortedWith(compareByDescending<VfsFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun listOneDriveFiles(path: String): List<VfsFile> {
        require(config.accessToken.isNotBlank()) { "請先登入 OneDrive 取得授權 Token" }
        val clean = path.trim('/')
        val url = if (clean.isEmpty()) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/root:/$clean:/children"
        }
        val req = Request.Builder().url(url).header("Authorization", authHeader()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("OneDrive 連線失敗 (HTTP ${resp.code})")
            val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("value") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name")
                    val isDir = o.has("folder")
                    val childPath = if (clean.isEmpty()) "/$name" else "/$clean/$name"
                    add(
                        VfsFile(
                            path = childPath,
                            name = name,
                            isDirectory = isDir,
                            size = o.optLong("size", 0L),
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
        require(config.accessToken.isNotBlank()) { "請先登入 Box 取得授權 Token" }
        val folderId = if (path == "/" || path.isBlank()) "0" else path.trim('/')
        val req = Request.Builder()
            .url("https://api.box.com/2.0/folders/$folderId/items?limit=200")
            .header("Authorization", authHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Box 連線失敗 (HTTP ${resp.code})")
            val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("entries") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        VfsFile(
                            path = "/${o.optString("id")}",
                            name = o.optString("name"),
                            isDirectory = o.optString("type") == "folder",
                            size = o.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }
        }
    }

    private fun listPCloudFiles(path: String): List<VfsFile> {
        require(config.accessToken.isNotBlank()) { "請先登入 pCloud 取得授權 Token" }
        val folderId = if (path == "/" || path.isBlank()) "0" else path.trim('/')
        val req = Request.Builder()
            .url("https://api.pcloud.com/listfolder?folderid=$folderId")
            .header("Authorization", authHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("pCloud 連線失敗 (HTTP ${resp.code})")
            val meta = JSONObject(resp.body?.string() ?: "{}").optJSONObject("metadata") ?: return emptyList()
            val arr = meta.optJSONArray("contents") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val isDir = o.optBoolean("isfolder", false)
                    val id = if (isDir) o.optLong("folderid").toString() else o.optLong("fileid").toString()
                    add(
                        VfsFile(
                            path = "/$id",
                            name = o.optString("name"),
                            isDirectory = isDir,
                            size = o.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
                            protocol = protocol,
                            mountId = config.id
                        )
                    )
                }
            }
        }
    }

    private fun listYandexFiles(path: String): List<VfsFile> {
        require(config.accessToken.isNotBlank()) { "請先登入 Yandex.Disk 取得授權 Token" }
        val yPath = if (path == "/" || path.isBlank()) "/" else "/${path.trim('/')}"
        val req = Request.Builder()
            .url("https://cloud-api.yandex.net/v1/disk/resources?path=${URLEncoder.encode(yPath, "UTF-8")}&limit=200")
            .header("Authorization", "OAuth ${config.accessToken.trim()}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Yandex.Disk 連線失敗 (HTTP ${resp.code})")
            val items = JSONObject(resp.body?.string() ?: "{}")
                .optJSONObject("_embedded")
                ?.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    add(
                        VfsFile(
                            path = o.optString("path").removePrefix("disk:"),
                            name = o.optString("name"),
                            isDirectory = o.optString("type") == "dir",
                            size = o.optLong("size", 0L),
                            lastModified = System.currentTimeMillis(),
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
                throw IllegalStateException("WebDAV PROPFIND 失敗 (HTTP ${resp.code})")
            }
            val xml = resp.body?.string().orEmpty()
            val hrefRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?href>([^<]+)</(?:[a-zA-Z0-9_-]+:)?href>")
            return hrefRegex.findAll(xml)
                .map { it.groupValues[1].trimEnd('/') }
                .filter { it.isNotEmpty() && !url.trimEnd('/').endsWith(it) }
                .map { href ->
                    val decoded = URLDecoder.decode(href, "UTF-8")
                    val name = decoded.substringAfterLast('/')
                    VfsFile(
                        path = "/${decoded.trimStart('/')}",
                        name = name,
                        isDirectory = !name.contains('.'),
                        size = 0L,
                        lastModified = System.currentTimeMillis(),
                        protocol = protocol,
                        mountId = config.id
                    )
                }
                .filter { it.name.isNotBlank() && (showHidden || !it.name.startsWith(".")) }
                .toList()
        }
    }

    private fun listS3Bucket(path: String): List<VfsFile> {
        val prefix = path.trim('/')
        val bucketUrl = "${baseUrl()}/${config.shareOrBucket}?list-type=2&delimiter=/&prefix=${URLEncoder.encode(if (prefix.isEmpty()) "" else "$prefix/", "UTF-8")}"
        val req = Request.Builder().url(bucketUrl).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("S3 連線失敗 (HTTP ${resp.code})")
            val xml = resp.body?.string().orEmpty()
            val keys = Regex("<Key>([^<]+)</Key>").findAll(xml).map { it.groupValues[1] }.toList()
            val prefixes = Regex("<Prefix>([^<]+)</Prefix>").findAll(xml).map { it.groupValues[1].trimEnd('/') }.toList()
            val dirItems = prefixes.filter { it.isNotBlank() && it != prefix }.map { p ->
                VfsFile("/$p", p.substringAfterLast('/'), true, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
            val fileItems = keys.filter { !it.endsWith("/") }.map { k ->
                VfsFile("/$k", k.substringAfterLast('/'), false, 0L, System.currentTimeMillis(), protocol = protocol, mountId = config.id)
            }
            return dirItems + fileItems
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
                            .method("MKCOL", "".toRequestBody(null))
                            .header("Authorization", authHeader())
                            .build()
                        http.newCall(req).execute().close()
                    }
                    VfsProtocol.GOOGLE_DRIVE -> {
                        val parentId = if (parentPath == "/" || parentPath.isBlank()) "root" else parentPath.trim('/')
                        val json = JSONObject()
                            .put("name", name)
                            .put("mimeType", "application/vnd.google-apps.folder")
                            .put("parents", org.json.JSONArray().put(parentId))
                        executeGoogleDriveRequest {
                            Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files")
                                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                                .header("Authorization", authHeader())
                                .build()
                        }.close()
                    }
                    else -> {}
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
                        executeGoogleDriveRequest {
                            Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files/$id")
                                .delete()
                                .header("Authorization", authHeader())
                                .build()
                        }.close()
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
                if (protocol == VfsProtocol.GOOGLE_DRIVE) {
                    val id = path.trim('/')
                    val bytes = executeGoogleDriveRequest {
                        Request.Builder()
                            .url("https://www.googleapis.com/drive/v3/files/$id?alt=media")
                            .header("Authorization", authHeader())
                            .build()
                    }.use { it.body?.bytes() ?: ByteArray(0) }
                    return@runCatching ByteArrayInputStream(bytes)
                }
                val req = when (protocol) {
                    VfsProtocol.DROPBOX -> {
                        val arg = JSONObject().put("path", path).toString()
                        Request.Builder()
                            .url("https://content.dropboxapi.com/2/files/download")
                            .post("".toRequestBody(null))
                            .header("Authorization", authHeader())
                            .header("Dropbox-API-Arg", arg)
                            .build()
                    }
                    VfsProtocol.ONEDRIVE -> {
                        val clean = path.trim('/')
                        Request.Builder()
                            .url("https://graph.microsoft.com/v1.0/me/drive/root:/$clean:/content")
                            .header("Authorization", authHeader())
                            .build()
                    }
                    else -> {
                        val url = "${baseUrl()}/${path.trim('/')}"
                        Request.Builder().url(url).header("Authorization", authHeader()).build()
                    }
                }
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
                        val data = toByteArray()
                        val req = when (protocol) {
                            VfsProtocol.DROPBOX -> {
                                val arg = JSONObject().put("path", path).put("mode", "overwrite").toString()
                                Request.Builder()
                                    .url("https://content.dropboxapi.com/2/files/upload")
                                    .post(data.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                    .header("Authorization", authHeader())
                                    .header("Dropbox-API-Arg", arg)
                                    .build()
                            }
                            VfsProtocol.ONEDRIVE -> {
                                val clean = path.trim('/')
                                Request.Builder()
                                    .url("https://graph.microsoft.com/v1.0/me/drive/root:/$clean:/content")
                                    .put(data.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                    .header("Authorization", authHeader())
                                    .build()
                            }
                            else -> {
                                val url = "${baseUrl()}/${path.trim('/')}"
                                Request.Builder()
                                    .url(url)
                                    .put(data.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                    .header("Authorization", authHeader())
                                    .build()
                            }
                        }
                        http.newCall(req).execute().close()
                    }
                }
            }
        }
}
