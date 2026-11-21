package com.dx9901mk5.filemanager.core.vfs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class ExternalStorageVolume(
    val id: String,
    val label: String,
    val path: String,
    val isUsb: Boolean,
    val spaceInfo: StorageSpaceInfo?
)

object ExternalStorageHelper {

    fun listRemovableStorages(context: Context): List<ExternalStorageVolume> {
        val result = mutableListOf<ExternalStorageVolume>()
        val seenPaths = mutableSetOf<String>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (storageManager != null) {
            for (vol in storageManager.storageVolumes) {
                if (vol.isPrimary) continue
                if (vol.state != Environment.MEDIA_MOUNTED && vol.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue

                val path = resolveVolumePath(vol) ?: continue
                val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
                if (!seenPaths.add(canonical)) continue

                val desc = runCatching { vol.getDescription(context) }.getOrNull().orEmpty().trim()
                val uuid = vol.uuid.orEmpty()
                val isUsb = desc.contains("usb", ignoreCase = true) ||
                    desc.contains("otg", ignoreCase = true) ||
                    desc.contains("ssd", ignoreCase = true) ||
                    desc.contains("disk", ignoreCase = true) ||
                    desc.contains("硬碟") ||
                    desc.contains("隨身碟") ||
                    (!desc.contains("sd", ignoreCase = true) && vol.isRemovable)

                val label = when {
                    desc.isNotBlank() -> desc
                    uuid.isNotBlank() -> if (isUsb) "OTG 外接裝置 ($uuid)" else "記憶卡 ($uuid)"
                    else -> if (isUsb) "OTG 外接儲存" else "外接記憶卡"
                }

                result += ExternalStorageVolume(
                    id = uuid.ifBlank { canonical },
                    label = label,
                    path = canonical,
                    isUsb = isUsb,
                    spaceInfo = readSpaceInfo(canonical)
                )
            }
        }

        // 備援掃描 /storage 與 /mnt/media_rw，涵蓋部分陸版/客製 ROM 未回報 StorageVolume.directory 的 OTG 硬碟
        listOf("/storage", "/mnt/media_rw").forEach { parentPath ->
            val parent = File(parentPath)
            val children = runCatching { parent.listFiles() }.getOrNull() ?: return@forEach
            for (dir in children) {
                if (!dir.isDirectory) continue
                val name = dir.name
                if (name == "emulated" || name == "self") continue
                val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
                if (canonical in seenPaths) continue
                if (!dir.canRead()) continue
                val space = readSpaceInfo(canonical)
                if (space == null || space.totalBytes <= 0L) continue
                seenPaths.add(canonical)
                result += ExternalStorageVolume(
                    id = name,
                    label = "OTG 外接裝置 ($name)",
                    path = canonical,
                    isUsb = true,
                    spaceInfo = space
                )
            }
        }

        return result
    }

    private fun resolveVolumePath(vol: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val dir = vol.directory
            if (dir != null && dir.exists()) return dir.absolutePath
        }
        val uuid = vol.uuid
        if (!uuid.isNullOrBlank()) {
            val candidate = File("/storage/$uuid")
            if (candidate.exists()) return candidate.absolutePath
        }
        return runCatching {
            val method = StorageVolume::class.java.getMethod("getPath")
            (method.invoke(vol) as? String)?.takeIf { File(it).exists() }
        }.getOrNull()
    }

    private fun readSpaceInfo(path: String): StorageSpaceInfo? {
        return runCatching {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val avail = stat.availableBytes
            if (total > 0L) StorageSpaceInfo(totalBytes = total, availableBytes = avail) else null
        }.getOrNull()
    }
}
