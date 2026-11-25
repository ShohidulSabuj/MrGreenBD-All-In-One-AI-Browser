package com.mrgreenbd.browser.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Dao
interface BrowserDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 200")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Database(entities = [BookmarkEntity::class, HistoryEntity::class], version = 1, exportSchema = false)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "mrgreenbd_all_in_one_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
