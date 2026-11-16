package com.dx9901mk5.filemanager.core.trash

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class TrashItemEntity(
    val id: Long = 0,
    val trashPath: String,
    val originalPath: String,
    val originalName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val deletedAt: Long
)

class TrashDao(private val dbHelper: TrashDatabase) {
    private val itemsFlow = MutableStateFlow<List<TrashItemEntity>>(emptyList())

    init {
        itemsFlow.value = queryAllSync()
    }

    fun observeAll(): Flow<List<TrashItemEntity>> = itemsFlow.asStateFlow()

    suspend fun getAll(): List<TrashItemEntity> = withContext(Dispatchers.IO) {
        queryAllSync()
    }

    suspend fun getExpiredItems(cutoffMillis: Long): List<TrashItemEntity> = withContext(Dispatchers.IO) {
        queryList("deleted_at < ?", arrayOf(cutoffMillis.toString()))
    }

    suspend fun insert(item: TrashItemEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("trash_path", item.trashPath)
            put("original_path", item.originalPath)
            put("original_name", item.originalName)
            put("is_directory", if (item.isDirectory) 1 else 0)
            put("size_bytes", item.sizeBytes)
            put("deleted_at", item.deletedAt)
        }
        val id = dbHelper.writableDatabase.insert("trash_items", null, values)
        itemsFlow.value = queryAllSync()
        id
    }

    suspend fun delete(item: TrashItemEntity) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("trash_items", "id = ?", arrayOf(item.id.toString()))
        itemsFlow.value = queryAllSync()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("trash_items", null, null)
        itemsFlow.value = emptyList()
    }

    private fun queryAllSync(): List<TrashItemEntity> = queryList(null, null)

    private fun queryList(selection: String?, selectionArgs: Array<String>?): List<TrashItemEntity> {
        val list = mutableListOf<TrashItemEntity>()
        dbHelper.readableDatabase.query(
            "trash_items",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "deleted_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    TrashItemEntity(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        trashPath = cursor.getString(cursor.getColumnIndexOrThrow("trash_path")),
                        originalPath = cursor.getString(cursor.getColumnIndexOrThrow("original_path")),
                        originalName = cursor.getString(cursor.getColumnIndexOrThrow("original_name")),
                        isDirectory = cursor.getInt(cursor.getColumnIndexOrThrow("is_directory")) == 1,
                        sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                        deletedAt = cursor.getLong(cursor.getColumnIndexOrThrow("deleted_at"))
                    )
                )
            }
        }
        return list
    }
}

class TrashDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dx_trash.db", null, 1) {

    private val dao by lazy { TrashDao(this) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS trash_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trash_path TEXT NOT NULL,
                original_path TEXT NOT NULL,
                original_name TEXT NOT NULL,
                is_directory INTEGER NOT NULL,
                size_bytes INTEGER NOT NULL,
                deleted_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS trash_items")
        onCreate(db)
    }

    fun trashDao(): TrashDao = dao

    companion object {
        @Volatile
        private var INSTANCE: TrashDatabase? = null

        fun getInstance(context: Context): TrashDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrashDatabase(context).also { INSTANCE = it }
            }
        }
    }
}
