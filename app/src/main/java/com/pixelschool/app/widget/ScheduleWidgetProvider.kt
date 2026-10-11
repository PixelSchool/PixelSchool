package com.pixelschool.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.pixelschool.app.MainActivity
import com.pixelschool.app.R
import com.pixelschool.app.data.local.CacheStore
import com.pixelschool.app.data.model.DaySchedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            // Click opens MainActivity
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Load schedule from CacheStore
            val cacheStore = CacheStore(context)
            val scheduleList = cacheStore.getScheduleCache(0) // Current week

            val todayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val todayStr = todayDateFormat.format(Date())

            val todayDayOfWeekFormat = SimpleDateFormat("EEEE", Locale("ru"))
            val todayDayOfWeek = todayDayOfWeekFormat.format(Date()).replaceFirstChar { it.uppercase() }

            val todaySchedule: DaySchedule? = scheduleList?.firstOrNull { it.date == todayStr }
                ?: scheduleList?.firstOrNull { it.dayOfWeek.equals(todayDayOfWeek, ignoreCase = true) }
                ?: scheduleList?.firstOrNull()

            val lessons = todaySchedule?.lessons?.filter { it.subject.isNotBlank() } ?: emptyList()

            // Header binding
            val isToday = todaySchedule?.date == todayStr || todaySchedule?.dayOfWeek.equals(todayDayOfWeek, ignoreCase = true)
            val dayTitle = if (isToday) "Сегодня" else (todaySchedule?.dayOfWeek ?: todayDayOfWeek)
            views.setTextViewText(R.id.widget_header_day, dayTitle)

            val formattedDate = todaySchedule?.date?.let { formatWidgetDate(it) } ?: ""
            views.setTextViewText(R.id.widget_header_date, formattedDate)

            val badgeText = if (lessons.isNotEmpty()) {
                val count = lessons.size
                val word = when {
                    count % 10 == 1 && count % 100 != 11 -> "урок"
                    count % 10 in 2..4 && (count % 100 !in 12..14) -> "урока"
                    else -> "уроков"
                }
                "$count $word"
            } else {
                "PixelSchool"
            }
            views.setTextViewText(R.id.widget_header_badge, badgeText)

            // Lessons binding
            val rowIds = listOf(
                R.id.widget_row_1,
                R.id.widget_row_2,
                R.id.widget_row_3,
                R.id.widget_row_4,
                R.id.widget_row_5
            )
            val timeIds = listOf(
                R.id.widget_time_1,
                R.id.widget_time_2,
                R.id.widget_time_3,
                R.id.widget_time_4,
                R.id.widget_time_5
            )
            val subjectIds = listOf(
                R.id.widget_subject_1,
                R.id.widget_subject_2,
                R.id.widget_subject_3,
                R.id.widget_subject_4,
                R.id.widget_subject_5
            )
            val roomIds = listOf(
                R.id.widget_room_1,
                R.id.widget_room_2,
                R.id.widget_room_3,
                R.id.widget_room_4,
                R.id.widget_room_5
            )

            if (scheduleList == null) {
                views.setTextViewText(R.id.widget_empty, "Откройте дневник для загрузки")
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_lessons_container, View.GONE)
            } else if (lessons.isEmpty()) {
                views.setTextViewText(R.id.widget_empty, "Уроков на сегодня нет 🎉")
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_lessons_container, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                views.setViewVisibility(R.id.widget_lessons_container, View.VISIBLE)

                val displayCount = minOf(lessons.size, rowIds.size)
                for (i in rowIds.indices) {
                    if (i < displayCount) {
                        val lesson = lessons[i]
                        val timeStr = if (lesson.startTime.isNotBlank()) {
                            "${lesson.num} • ${lesson.startTime}"
                        } else {
                            "${lesson.num} урок"
                        }
                        views.setTextViewText(timeIds[i], timeStr)
                        views.setTextViewText(subjectIds[i], lesson.subject)

                        if (lesson.room.isNotBlank()) {
                            val roomDisplay = if (lesson.room.startsWith("каб", ignoreCase = true)) {
                                lesson.room
                            } else {
                                "каб. ${lesson.room}"
                            }
                            views.setTextViewText(roomIds[i], roomDisplay)
                            views.setViewVisibility(roomIds[i], View.VISIBLE)
                        } else {
                            views.setViewVisibility(roomIds[i], View.GONE)
                        }

                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                    } else {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    }
                }

                // Show footer if there are more than 5 lessons
                if (lessons.size > rowIds.size) {
                    val remaining = lessons.size - rowIds.size
                    val moreWord = when {
                        remaining % 10 == 1 && remaining % 100 != 11 -> "урок"
                        remaining % 10 in 2..4 && (remaining % 100 !in 12..14) -> "урока"
                        else -> "уроков"
                    }
                    views.setTextViewText(R.id.widget_more_lessons, "+ ещё $remaining $moreWord")
                    views.setViewVisibility(R.id.widget_more_lessons, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_more_lessons, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun formatWidgetDate(dateStr: String): String {
            val parts = dateStr.split(".")
            if (parts.size >= 2) {
                val day = parts[0].toIntOrNull() ?: return dateStr
                val month = parts[1].toIntOrNull() ?: return dateStr
                val months = listOf("", "янв.", "февр.", "марта", "апр.", "мая", "июня", "июля", "авг.", "сент.", "окт.", "нояб.", "дек.")
                if (month in 1..12) {
                    return "$day ${months[month]}"
                }
            }
            return dateStr
        }
    }
}
