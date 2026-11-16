package com.dx9901mk5.filemanager.core.trash

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trashPath: String,
    val originalPath: String,
    val originalName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val deletedAt: Long
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    suspend fun getAll(): List<TrashItemEntity>

    @Query("SELECT * FROM trash_items WHERE deletedAt < :cutoffMillis")
    suspend fun getExpiredItems(cutoffMillis: Long): List<TrashItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashItemEntity): Long

    @Delete
    suspend fun delete(item: TrashItemEntity)

    @Query("DELETE FROM trash_items")
    suspend fun deleteAll()
}

@Database(entities = [TrashItemEntity::class], version = 1, exportSchema = false)
abstract class TrashDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var INSTANCE: TrashDatabase? = null

        fun getInstance(context: Context): TrashDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrashDatabase::class.java,
                    "dx_trash.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
