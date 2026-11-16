package com.dx9901mk5.filemanager.core.vfs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 統一虛擬檔案系統 (VFS) 註冊表與跨協定檔案傳輸器
 */
class VfsRegistry {
    val localProvider = LocalVfsProvider()
    private val mountedProviders = mutableMapOf<String, VfsProvider>()
    private val mountConfigs = mutableMapOf<String, VfsMountConfig>()

    fun registerMount(config: VfsMountConfig) {
        mountConfigs[config.id] = config
        mountedProviders[config.id] = when (config.protocol) {
            VfsProtocol.LOCAL -> localProvider
            VfsProtocol.LAN_SMB -> SmbVfsProvider(config)
            VfsProtocol.FTP -> FtpVfsProvider(config)
            VfsProtocol.SFTP -> SftpVfsProvider(config)
            else -> CloudAndWebDavVfsProvider(config)
        }
    }

    fun removeMount(mountId: String) {
        mountConfigs.remove(mountId)
        mountedProviders.remove(mountId)
    }

    fun getMounts(): List<VfsMountConfig> = mountConfigs.values.toList()

    fun getProvider(mountId: String): VfsProvider {
        return if (mountId == "local") localProvider else mountedProviders[mountId] ?: localProvider
    }

    /**
     * 跨面板 / 跨協定傳輸 (支援 本地 <-> 雲端/SMB/SFTP 串流複製或移動)
     */
    suspend fun transferBetweenMounts(
        sourceMountId: String,
        sourceFile: VfsFile,
        targetMountId: String,
        targetDirPath: String,
        isMove: Boolean,
        onProgress: (bytesDelta: Long, currentFileName: String) -> Unit = { _, _ -> }
    ): Result<VfsFile> = withContext(Dispatchers.IO) {
        if (sourceMountId == targetMountId) {
            val provider = getProvider(sourceMountId)
            val result = if (isMove) {
                provider.move(sourceFile.path, targetDirPath)
            } else {
                provider.copy(sourceFile.path, targetDirPath)
            }
            if (result.isSuccess) {
                onProgress(sourceFile.size, sourceFile.name)
            }
            return@withContext result
        }

        runCatching {
            val srcProvider = getProvider(sourceMountId)
            val dstProvider = getProvider(targetMountId)
            val targetPath = if (targetDirPath.endsWith("/")) {
                "$targetDirPath${sourceFile.name}"
            } else {
                "$targetDirPath/${sourceFile.name}"
            }

            if (sourceFile.isDirectory) {
                val createdDir = dstProvider.createDirectory(targetDirPath, sourceFile.name).getOrThrow()
                val children = srcProvider.listFiles(sourceFile.path, showHidden = true).getOrThrow()
                for (child in children) {
                    transferBetweenMounts(
                        sourceMountId,
                        child,
                        targetMountId,
                        createdDir.path,
                        isMove,
                        onProgress
                    ).getOrThrow()
                }
                if (isMove) srcProvider.delete(sourceFile.path).getOrThrow()
                createdDir
            } else {
                srcProvider.openInputStream(sourceFile.path).getOrThrow().use { input ->
                    dstProvider.openOutputStream(targetPath).getOrThrow().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            onProgress(read.toLong(), sourceFile.name)
                        }
                        output.flush()
                    }
                }
                if (isMove) srcProvider.delete(sourceFile.path).getOrThrow()
                VfsFile(
                    path = targetPath,
                    name = sourceFile.name,
                    isDirectory = false,
                    size = sourceFile.size,
                    lastModified = System.currentTimeMillis(),
                    mimeType = sourceFile.mimeType,
                    protocol = dstProvider.protocol,
                    mountId = targetMountId
                )
            }
        }
    }
}
