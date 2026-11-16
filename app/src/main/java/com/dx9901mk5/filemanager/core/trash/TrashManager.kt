package com.dx9901mk5.filemanager.core.trash

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TrashManager(private val context: Context) {
    private val dao = TrashDatabase.getInstance(context).trashDao()

    private val trashDir: File
        get() = File(Environment.getExternalStorageDirectory(), ".dx_trash").apply {
            if (!exists()) mkdirs()
        }

    fun observeTrashItems(): Flow<List<TrashItemEntity>> = dao.observeAll()

    suspend fun moveToTrash(sourceFile: File): Result<TrashItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = System.currentTimeMillis()
            val targetTrashFile = File(trashDir, "${timestamp}_${sourceFile.name}")
            if (!sourceFile.renameTo(targetTrashFile)) {
                if (sourceFile.isDirectory) {
                    sourceFile.copyRecursively(targetTrashFile, overwrite = true)
                } else {
                    sourceFile.copyTo(targetTrashFile, overwrite = true)
                }
                sourceFile.deleteRecursively()
            }
            val size = if (targetTrashFile.isDirectory) {
                targetTrashFile.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                targetTrashFile.length()
            }
            val entity = TrashItemEntity(
                trashPath = targetTrashFile.absolutePath,
                originalPath = sourceFile.absolutePath,
                originalName = sourceFile.name,
                isDirectory = targetTrashFile.isDirectory,
                sizeBytes = size,
                deletedAt = timestamp
            )
            val id = dao.insert(entity)
            entity.copy(id = id)
        }
    }

    suspend fun restoreFromTrash(item: TrashItemEntity): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val trashFile = File(item.trashPath)
            val original = File(item.originalPath)
            original.parentFile?.mkdirs()
            if (trashFile.exists()) {
                if (!trashFile.renameTo(original)) {
                    if (trashFile.isDirectory) {
                        trashFile.copyRecursively(original, overwrite = true)
                    } else {
                        trashFile.copyTo(original, overwrite = true)
                    }
                    trashFile.deleteRecursively()
                }
            }
            dao.delete(item)
            original
        }
    }

    suspend fun permanentlyDelete(item: TrashItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(item.trashPath).deleteRecursively()
            dao.delete(item)
        }
    }

    suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dao.getAll().forEach { File(it.trashPath).deleteRecursively() }
            dao.deleteAll()
        }
    }

    suspend fun cleanupExpiredItems(retentionDays: Long = 30L): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        val expired = dao.getExpiredItems(cutoff)
        expired.forEach { item ->
            File(item.trashPath).deleteRecursively()
            dao.delete(item)
        }
        expired.size
    }
}
