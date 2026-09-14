package com.kippu.trace.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kippu.trace.model.DateEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM date_events ORDER BY isPinned DESC, position ASC, id DESC")
    fun getAllEvents(): Flow<List<DateEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DateEvent)

    @Delete
    suspend fun deleteEvent(event: DateEvent)

    @Query("SELECT * FROM date_events WHERE id = :id")
    suspend fun getEventById(id: Long): DateEvent?

    @Query("SELECT * FROM date_events ORDER BY isPinned DESC, position ASC, id DESC")
    suspend fun getAllEventsOnce(): List<DateEvent>

    @Update
    suspend fun updateEvents(events: List<DateEvent>)

    @Query("DELETE FROM date_events")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(events: List<DateEvent>)

    @Transaction
    suspend fun deleteAllAndInsertAll(events: List<DateEvent>) {
        deleteAll()
        insertAll(events)
    }
}

@Database(entities = [DateEvent::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE date_events ADD COLUMN dayChangeMinutes INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trace_database",
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
