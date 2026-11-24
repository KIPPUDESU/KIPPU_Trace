package com.kippu.trace.data

import com.kippu.trace.model.DateEvent
import com.kippu.trace.utils.AnniversaryUtils
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {
    val allEvents: Flow<List<DateEvent>> = eventDao.getAllEvents()

    suspend fun advanceRepeatingEvents() = eventDao.advanceRepeatingEvents()

    suspend fun insert(event: DateEvent) {
        eventDao.insertEvent(if (event.isStopped) event else AnniversaryUtils.advance(event))
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

    suspend fun deleteAllAndInsertAll(events: List<DateEvent>) {
        eventDao.deleteAllAndInsertAll(events)
    }
}
