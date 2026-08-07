package com.kippu.trace.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrationFrom2To3PreservesEventsAndAddsDefaultSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-2-3-test.db"
        context.deleteDatabase(databaseName)

        createVersion2Database(context, databaseName)

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

        try {
            val event = runBlocking { database.eventDao().getAllEventsOnce().single() }

            assertEquals(7L, event.id)
            assertEquals("Existing event", event.title)
            assertEquals(1_234_567_890L, event.targetDate)
            assertTrue(event.isFuture)
            assertFalse(event.isLunar)
            assertEquals(DisplayMode.COUNT_DOWN, event.mode)
            assertNull(event.backgroundUri)
            assertTrue(event.isPinned)
            assertEquals(0.4f, event.maskOpacity)
            assertEquals(3, event.position)
            assertEquals(RepeatMode.NONE, event.repeatMode)
            assertEquals(0, event.repeatCustomDays)
            assertEquals(0, event.customAnniversaryDays)
            assertFalse(event.anniversaryYearEnabled)
            assertFalse(event.anniversaryMonthEnabled)
            assertFalse(event.anniversaryWeekEnabled)
            assertEquals("", event.anniversaryCombinedText)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion2Database(context: Context, databaseName: String) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `date_events` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `targetDate` INTEGER NOT NULL,
                            `isFuture` INTEGER NOT NULL,
                            `isLunar` INTEGER NOT NULL,
                            `mode` TEXT NOT NULL,
                            `backgroundUri` TEXT,
                            `isPinned` INTEGER NOT NULL,
                            `maskOpacity` REAL NOT NULL,
                            `position` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO `date_events` (
                            `id`, `title`, `targetDate`, `isFuture`, `isLunar`, `mode`,
                            `backgroundUri`, `isPinned`, `maskOpacity`, `position`
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            7L,
                            "Existing event",
                            1_234_567_890L,
                            1,
                            0,
                            "COUNT_DOWN",
                            null,
                            1,
                            0.4f,
                            3,
                        )
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.writableDatabase
        helper.close()
    }
}
