package com.kippu.trace.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDaoCountdownUpdateTest {
    @Test
    fun conditionalAdvancePreservesOtherEditsAndRejectsAStaleTarget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        try {
            val dao = database.eventDao()
            val original = DateEvent(
                id = 1,
                title = "Original",
                targetDate = 1_000L,
                isFuture = false,
                mode = DisplayMode.COUNT_DOWN,
                repeatMode = RepeatMode.MONTHLY,
                repeatAnchorDate = 1_000L,
            )
            dao.insertEvent(original)
            dao.insertEvent(original.copy(title = "Edited while advancing"))

            val updated = dao.advanceCountdownIfUnchanged(
                id = original.id,
                expectedTargetDate = original.targetDate,
                expectedRepeatMode = original.repeatMode,
                expectedCustomDays = original.repeatCustomDays,
                expectedAnchorDate = original.repeatAnchorDate,
                newTargetDate = 2_000L,
                newAnchorDate = 1_000L,
            )

            assertEquals(1, updated)
            assertEquals("Edited while advancing", dao.getEventById(original.id)?.title)

            dao.insertEvent(dao.getEventById(original.id)!!.copy(targetDate = 3_000L))
            val staleUpdate = dao.advanceCountdownIfUnchanged(
                id = original.id,
                expectedTargetDate = 2_000L,
                expectedRepeatMode = original.repeatMode,
                expectedCustomDays = original.repeatCustomDays,
                expectedAnchorDate = original.repeatAnchorDate,
                newTargetDate = 4_000L,
                newAnchorDate = 1_000L,
            )

            assertEquals(0, staleUpdate)
            assertEquals(3_000L, dao.getEventById(original.id)?.targetDate)
        } finally {
            database.close()
        }
    }
}
