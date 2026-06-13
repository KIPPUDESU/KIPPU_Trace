package com.kippu.trace.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.kippu.trace.R
import androidx.lifecycle.viewModelScope
import com.kippu.trace.data.AppDatabase
import com.kippu.trace.data.EventRepository
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.TimelineData
import com.kippu.trace.model.TimelineItemInfo
import com.kippu.trace.utils.BackupManager
import com.kippu.trace.utils.TimeUtils
import com.kippu.trace.widget.TraceWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EventRepository
    val allEvents: StateFlow<List<DateEvent>>
    val timelineData: StateFlow<TimelineData>

    init {
        val eventDao = AppDatabase.getDatabase(application).eventDao()
        repository = EventRepository(eventDao)
        allEvents = repository.allEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
        timelineData = allEvents.map { events -> buildTimelineData(events) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineData(emptyList(), emptyList(), 0),
        )
    }

    fun addEvent(event: DateEvent) {
        viewModelScope.launch {
            repository.insert(event)
            TraceWidgetUpdater.requestAllUpdate(getApplication())
        }
    }

    fun deleteEvent(event: DateEvent) {
        viewModelScope.launch {
            repository.delete(event)
            // 请求更新小组件
            TraceWidgetUpdater.requestAllUpdate(getApplication())
        }
    }

    fun updateEventsOrder(events: List<DateEvent>) {
        viewModelScope.launch {
            repository.updateEvents(events)
            TraceWidgetUpdater.requestAllUpdate(getApplication())
        }
    }

    // 导出备份
    fun exportBackup(uri: Uri, onResult: (Boolean, String) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val events = withContext(Dispatchers.IO) {
                    repository.getAllEventsOnce()
                }
                withContext(Dispatchers.IO) {
                    BackupManager.exportToZip(app, events, uri).getOrThrow()
                }
                onResult(true, app.getString(R.string.backup_success))
            } catch (e: Exception) {
                onResult(false, app.getString(R.string.backup_failed, e.localizedMessage ?: app.getString(R.string.unknown_error)))
            }
        }
    }

    // 导入备份
    fun importBackup(uri: Uri, onResult: (Boolean, String) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val events = withContext(Dispatchers.IO) {
                    BackupManager.importFromZip(app, uri).getOrThrow()
                }
                withContext(Dispatchers.IO) {
                    repository.deleteAllAndInsertAll(events)
                }
                TraceWidgetUpdater.requestAllUpdate(app)
                onResult(true, app.getString(R.string.restore_success, events.size))
            } catch (e: Exception) {
                onResult(false, app.getString(R.string.restore_failed, e.localizedMessage ?: app.getString(R.string.unknown_error)))
            }
        }
    }
}

/**
 * 预计算时间线结构数据，在后台线程（Flow map）执行。
 * 排序 O(N log N) + 遍历 O(N)，对事件数量不敏感。
 */
private fun buildTimelineData(events: List<DateEvent>): TimelineData {
    val sortedEvents = events.sortedBy { it.targetDate }
    val todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    val items = mutableListOf<TimelineItemInfo>()
    var eventCount = 0
    for (event in sortedEvents) {
        val epochDay = TimeUtils.toEpochDay(event.targetDate)
        items.add(TimelineItemInfo(epochDay, isNow = false, event = event, isLeft = eventCount++ % 2 == 0))
    }
    // 插入「现在」节点
    val nowIsLeft = eventCount % 2 == 0
    items.add(TimelineItemInfo(todayEpochDay, isNow = true, event = null, isLeft = nowIsLeft))

    // 按 epochDay 排序，now 节点同级排在最后
    items.sortWith(compareBy<TimelineItemInfo> { it.epochDay }
        .thenBy { if (it.isNow) 1 else 0 })

    val dayGaps = items.zipWithNext { a, b ->
        (b.epochDay - a.epochDay).absoluteValue.toFloat()
    }
    val nowItemIndex = items.indexOfFirst { it.isNow }

    return TimelineData(items, dayGaps, nowItemIndex)
}
