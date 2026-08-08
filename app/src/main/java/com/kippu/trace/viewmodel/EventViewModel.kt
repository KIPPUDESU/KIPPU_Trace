package com.kippu.trace.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.kippu.trace.R
import androidx.lifecycle.viewModelScope
import com.kippu.trace.data.AppDatabase
import com.kippu.trace.data.EventRepository
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.BackupManager
import com.kippu.trace.widget.TraceWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EventRepository
    val allEvents: StateFlow<List<DateEvent>>

    private val advancing = AtomicBoolean(false)

    init {
        val eventDao = AppDatabase.getDatabase(application).eventDao()
        repository = EventRepository(eventDao)
        allEvents = repository.allEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
        checkAndAdvanceCountdowns()
    }

    /** 检查所有倒数模式事件，若目标日期已过则按重复规则推进 */
    fun checkAndAdvanceCountdowns() {
        if (!advancing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val events = withContext(Dispatchers.IO) { repository.getAllEventsOnce() }
                val updated = events.mapNotNull { event ->
                    if (event.mode == DisplayMode.COUNT_DOWN && event.repeatMode != RepeatMode.NONE) {
                        val repeatAnchor = event.repeatAnchorDate ?: event.targetDate
                        val newTarget = AnniversaryUtils.advanceTargetDate(
                            event.targetDate,
                            repeatAnchor,
                            event.repeatMode,
                            event.repeatCustomDays,
                        )
                        if (newTarget != null) {
                            event.copy(
                                targetDate = newTarget,
                                isFuture = true,
                                repeatAnchorDate = repeatAnchor,
                            )
                        } else null
                    } else null
                }
                if (updated.isNotEmpty()) {
                    withContext(Dispatchers.IO) { repository.updateEvents(updated) }
                    TraceWidgetUpdater.requestAllUpdate(getApplication())
                }
            } finally {
                advancing.set(false)
            }
        }
    }

    fun addEvent(event: DateEvent) {
        viewModelScope.launch {
            val eventWithAnchor = if (
                event.repeatMode != RepeatMode.NONE && event.repeatAnchorDate == null
            ) {
                event.copy(repeatAnchorDate = event.targetDate)
            } else {
                event
            }
            repository.insert(eventWithAnchor)
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
