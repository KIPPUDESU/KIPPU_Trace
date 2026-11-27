package com.kippu.trace.utils

import android.content.Context
import android.net.Uri
import com.kippu.trace.model.AnniversaryType
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
            put("position", position)
            put("maskOpacity", maskOpacity.toDouble())
            put("dayChangeMinutes", dayChangeMinutes)
            put("anniversaryType", anniversaryType.name)
            put("customDays", customDays)
            put("showYear", showYear)
            put("showMonth", showMonth)
            put("showWeek", showWeek)
            put("anniversaryMessage", anniversaryMessage)
            put("repeatMode", repeatMode.name)
            put("repeatInterval", repeatInterval)
            put("repeatCustomDays", repeatCustomDays)
            put("repeatAnchorDate", repeatAnchorDate ?: JSONObject.NULL)
            put("isStopped", isStopped)
            put("stoppedAt", stoppedAt ?: JSONObject.NULL)
        }
    }

    private fun JSONObject.toDateEvent(backgroundsDir: File): DateEvent {
        return DateEvent(
            id = getLong("id"),
            title = getString("title"),
            targetDate = getLong("targetDate"),
            isFuture = getBoolean("isFuture"),
            isLunar = optBoolean("isLunar", false),
            mode = DisplayMode.valueOf(getString("mode")),
            backgroundUri = if (has("backgroundFile")) {
                File(backgroundsDir, getString("backgroundFile")).absolutePath
            } else null,
            isPinned = optBoolean("isPinned", false),
            position = optInt("position", 0),
            maskOpacity = optDouble("maskOpacity", 0.3).toFloat(),
            dayChangeMinutes = optInt("dayChangeMinutes", 0),
            anniversaryType = optString("anniversaryType", AnniversaryType.NONE.name).let {
                runCatching { AnniversaryType.valueOf(it) }.getOrDefault(AnniversaryType.NONE)
            },
            customDays = optInt("customDays", 100),
            showYear = optBoolean("showYear", true),
            showMonth = optBoolean("showMonth", true),
            showWeek = optBoolean("showWeek", true),
            anniversaryMessage = optString("anniversaryMessage", ""),
            repeatMode = runCatching {
                RepeatMode.valueOf(optString("repeatMode", RepeatMode.NONE.name))
            }.getOrDefault(RepeatMode.NONE),
            repeatInterval = optInt("repeatInterval", 1).coerceAtLeast(1),
            repeatCustomDays = optInt("repeatCustomDays", 100).coerceAtLeast(1),
            repeatAnchorDate = if (isNull("repeatAnchorDate")) null else optLong("repeatAnchorDate"),
            isStopped = optBoolean("isStopped", false),
            stoppedAt = if (isNull("stoppedAt")) null else optLong("stoppedAt"),
        )
    }
}
