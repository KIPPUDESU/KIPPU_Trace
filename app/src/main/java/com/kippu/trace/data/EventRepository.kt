package com.kippu.trace.data

import com.kippu.trace.model.DateEvent
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {
    val allEvents: Flow<List<DateEvent>> = eventDao.getAllEvents()

    suspend fun insert(event: DateEvent) {
        eventDao.insertEvent(event)
    }

    suspend fun delete(event: DateEvent) {
        eventDao.deleteEvent(event)
    }

    suspend fun getEventById(id: Long): DateEvent? {
        return eventDao.getEventById(id)
    }

    suspend fun getAllEventsOnce(): List<DateEvent> {
        return eventDao.getAllEventsOnce()
    }

    suspend fun updateEvents(events: List<DateEvent>) {
        eventDao.updateEvents(events)
    }

    suspend fun advanceCountdownIfUnchanged(
        event: DateEvent,
        newTargetDate: Long,
        newAnchorDate: Long,
    ): Boolean = eventDao.advanceCountdownIfUnchanged(
        id = event.id,
        expectedTargetDate = event.targetDate,
        expectedRepeatMode = event.repeatMode,
        expectedCustomDays = event.repeatCustomDays,
        expectedAnchorDate = event.repeatAnchorDate,
        newTargetDate = newTargetDate,
        newAnchorDate = newAnchorDate,
    ) == 1

    suspend fun deleteAllAndInsertAll(events: List<DateEvent>) {
        eventDao.deleteAllAndInsertAll(events)
    }
}
