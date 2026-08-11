package com.behaviorlens.app.data.db

import android.content.Context
import androidx.room.*
import com.behaviorlens.app.data.models.SessionEntity

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT 20")
    suspend fun getRecentSessions(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "behaviorlens.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
