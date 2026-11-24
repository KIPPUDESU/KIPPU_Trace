package com.kippu.trace.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kippu.trace.model.DateEvent
import com.kippu.trace.utils.AnniversaryUtils
import java.time.LocalDate
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

    @Transaction
    suspend fun advanceRepeatingEvents(today: LocalDate = LocalDate.now()) {
        val changed = getAllEventsOnce().filter { !it.isStopped }.mapNotNull { event ->
            AnniversaryUtils.advance(event, today).takeIf { it != event }
        }
        if (changed.isNotEmpty()) updateEvents(changed)
    }

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

@Database(entities = [DateEvent::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_events ADD COLUMN anniversaryType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE date_events ADD COLUMN customDays INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE date_events ADD COLUMN showYear INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE date_events ADD COLUMN showMonth INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE date_events ADD COLUMN showWeek INTEGER NOT NULL DEFAULT 1")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // version 3 曾分别存在于两个功能分支：一个包含纪念日字段，另一个
                // 只包含 dayChangeMinutes。逐列检查可兼容两种已安装数据库。
                addColumnIfMissing(db, "anniversaryType", "TEXT NOT NULL DEFAULT 'NONE'")
                addColumnIfMissing(db, "customDays", "INTEGER NOT NULL DEFAULT 100")
                addColumnIfMissing(db, "showYear", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "showMonth", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "showWeek", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "anniversaryMessage", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "repeatMode", "TEXT NOT NULL DEFAULT 'NONE'")
                addColumnIfMissing(db, "repeatCustomDays", "INTEGER NOT NULL DEFAULT 100")
                addColumnIfMissing(db, "repeatAnchorDate", "INTEGER")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_events ADD COLUMN repeatInterval INTEGER NOT NULL DEFAULT 1")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "dayChangeMinutes", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "isStopped", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "stoppedAt", "INTEGER")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "stoppedAt", "INTEGER")
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            name: String,
            definition: String,
        ) {
            val exists = db.query("PRAGMA table_info(date_events)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == name) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!exists) {
                db.execSQL("ALTER TABLE date_events ADD COLUMN $name $definition")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trace_database",
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
