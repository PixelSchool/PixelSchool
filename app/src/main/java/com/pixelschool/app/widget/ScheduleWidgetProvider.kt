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

            // Load today's schedule from CacheStore
            val cacheStore = CacheStore(context)
            val scheduleList = cacheStore.getScheduleCache(0) // Current week

            val todayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val todayStr = todayDateFormat.format(Date())

            val todayDayOfWeekFormat = SimpleDateFormat("EEEE", Locale("ru"))
            val todayDayOfWeek = todayDayOfWeekFormat.format(Date()).replaceFirstChar { it.uppercase() }

            views.setTextViewText(R.id.widget_day, todayDayOfWeek)

            val todaySchedule: DaySchedule? = scheduleList?.firstOrNull { it.date == todayStr }
                ?: scheduleList?.firstOrNull { it.dayOfWeek.equals(todayDayOfWeek, ignoreCase = true) }
                ?: scheduleList?.firstOrNull()

            val lessons = todaySchedule?.lessons?.filter { it.subject.isNotBlank() } ?: emptyList()

            val lessonViewIds = listOf(
                R.id.widget_lesson_1,
                R.id.widget_lesson_2,
                R.id.widget_lesson_3,
                R.id.widget_lesson_4
            )

            if (lessons.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_lessons_container, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                views.setViewVisibility(R.id.widget_lessons_container, View.VISIBLE)

                for (i in lessonViewIds.indices) {
                    val viewId = lessonViewIds[i]
                    if (i < lessons.size) {
                        val lesson = lessons[i]
                        val roomStr = if (lesson.room.isNotBlank()) " • ${lesson.room}" else ""
                        val text = "${lesson.num}. ${lesson.startTime} ${lesson.subject}$roomStr"
                        views.setTextViewText(viewId, text)
                        views.setViewVisibility(viewId, View.VISIBLE)
                    } else {
                        views.setViewVisibility(viewId, View.GONE)
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
