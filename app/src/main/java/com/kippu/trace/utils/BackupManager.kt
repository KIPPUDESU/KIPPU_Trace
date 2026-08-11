package com.kippu.trace.utils

import android.content.Context
import android.net.Uri
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    // 导出备份到 ZIP
    fun exportToZip(context: Context, events: List<DateEvent>, outputUri: Uri): Result<Unit> {
        return runCatching {
            val backgroundsDir = File(context.filesDir, "backgrounds")
            val tempDir = File(context.cacheDir, "backup_export_${System.currentTimeMillis()}")
            val tempBackgroundsDir = File(tempDir, "backgrounds")
            tempBackgroundsDir.mkdirs()

            // 构造 JSON 数组
            val jsonArray = JSONArray()
            for (event in events) {
                jsonArray.put(event.toExportJson())
            }
            File(tempDir, "events.json").writeText(jsonArray.toString(2))

            // 保存背景图
            if (backgroundsDir.exists()) {
                for (event in events) {
                    val fileName = event.backgroundUri?.let { File(it).name } ?: continue
                    val sourceFile = File(backgroundsDir, fileName)
                    runCatching {
                        sourceFile.copyTo(File(tempBackgroundsDir, fileName), overwrite = true)
                    }
                }
            }

            // 压缩包创建
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zip ->
                    tempDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relativePath = file.relativeTo(tempDir).path
                        zip.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }

            // 清理临时目录
            tempDir.deleteRecursively()
        }
    }

    // 从压缩包导入备份
    fun importFromZip(context: Context, inputUri: Uri): Result<List<DateEvent>> {
        return runCatching {
            val backgroundsDir = File(context.filesDir, "backgrounds")
            backgroundsDir.mkdirs()
            val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // 解压
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val targetFile = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            // 校验 events.json
            val eventsFile = File(tempDir, "events.json")
            if (!eventsFile.exists()) {
                tempDir.deleteRecursively()
                throw IllegalStateException("备份文件无效 找不到 events.json")
            }

            // 复制背景图
            val importBackgroundsDir = File(tempDir, "backgrounds")
            if (importBackgroundsDir.exists()) {
                importBackgroundsDir.listFiles()?.forEach { file ->
                    file.copyTo(File(backgroundsDir, file.name), overwrite = true)
                }
            }

            // 解析事件
            val jsonArray = JSONArray(eventsFile.readText())
            val events = mutableListOf<DateEvent>()
            for (i in 0 until jsonArray.length()) {
                events.add(jsonArray.getJSONObject(i).toDateEvent(backgroundsDir))
            }

            // 清理临时目录
            tempDir.deleteRecursively()

            events
        }
    }

    private fun DateEvent.toExportJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("targetDate", targetDate)
            put("isFuture", isFuture)
            put("isLunar", isLunar)
            put("mode", mode.name)
            if (backgroundUri != null) {
                put("backgroundFile", File(backgroundUri).name)
            }
            put("isPinned", isPinned)
            put("maskOpacity", maskOpacity.toDouble())
            // 倒数模式 - 自动重置
            put("repeatMode", repeatMode.name)
            if (repeatCustomDays > 0) {
                put("repeatCustomDays", repeatCustomDays)
            }
            if (repeatMode != RepeatMode.NONE) {
                put("repeatAnchorDate", repeatAnchorDate ?: targetDate)
            }
            // 累计模式 - 纪念日
            if (customAnniversaryDays > 0) {
                put("customAnniversaryDays", customAnniversaryDays)
            }
            put("anniversaryYearEnabled", anniversaryYearEnabled)
            put("anniversaryMonthEnabled", anniversaryMonthEnabled)
            put("anniversaryWeekEnabled", anniversaryWeekEnabled)
            if (anniversaryCombinedText.isNotEmpty()) {
                put("anniversaryCombinedText", anniversaryCombinedText)
            }
        }
    }

    private fun JSONObject.toDateEvent(backgroundsDir: File): DateEvent {
        val targetDate = getLong("targetDate")
        val repeatMode = if (has("repeatMode")) {
            RepeatMode.valueOf(getString("repeatMode"))
        } else {
            RepeatMode.NONE
        }
        return DateEvent(
            id = getLong("id"),
            title = getString("title"),
            targetDate = targetDate,
            isFuture = getBoolean("isFuture"),
            isLunar = optBoolean("isLunar", false),
            mode = DisplayMode.valueOf(getString("mode")),
            backgroundUri = if (has("backgroundFile")) {
                File(backgroundsDir, getString("backgroundFile")).absolutePath
            } else null,
            isPinned = optBoolean("isPinned", false),
            maskOpacity = optDouble("maskOpacity", 0.3).toFloat(),
            repeatMode = repeatMode,
            repeatCustomDays = optInt("repeatCustomDays", 0),
            repeatAnchorDate = if (repeatMode != RepeatMode.NONE) {
                optLong("repeatAnchorDate", targetDate)
            } else {
                null
            },
            customAnniversaryDays = optInt("customAnniversaryDays", 0),
            anniversaryYearEnabled = optBoolean("anniversaryYearEnabled", false),
            anniversaryMonthEnabled = optBoolean("anniversaryMonthEnabled", false),
            anniversaryWeekEnabled = optBoolean("anniversaryWeekEnabled", false),
            anniversaryCombinedText = optString("anniversaryCombinedText", ""),
        )
    }
}
