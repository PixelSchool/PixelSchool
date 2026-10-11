package com.pixelschool.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.pixelschool.app.MainActivity
import com.pixelschool.app.R
import com.pixelschool.app.data.local.CacheStore
import com.pixelschool.app.data.model.DaySchedule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(ACTION_PREV_DAY, ACTION_NEXT_DAY, ACTION_TODAY)) return

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val scheduleList = getCombinedSchedule(context)
        if (scheduleList.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayIndex = getTodayIndex(context, scheduleList)

        var currentIndex = prefs.getInt("${KEY_DAY_INDEX}_$appWidgetId", todayIndex)
        if (currentIndex !in scheduleList.indices) currentIndex = todayIndex

        when (action) {
            ACTION_PREV_DAY -> {
                currentIndex = (currentIndex - 1).coerceAtLeast(0)
                prefs.edit().putInt("${KEY_DAY_INDEX}_$appWidgetId", currentIndex).apply()
            }
            ACTION_NEXT_DAY -> {
                currentIndex = (currentIndex + 1).coerceAtMost(scheduleList.lastIndex)
                prefs.edit().putInt("${KEY_DAY_INDEX}_$appWidgetId", currentIndex).apply()
            }
            ACTION_TODAY -> {
                prefs.edit().putInt("${KEY_DAY_INDEX}_$appWidgetId", todayIndex).apply()
            }
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_lessons_list)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_PREV_DAY = "com.pixelschool.app.action.WIDGET_PREV_DAY"
        const val ACTION_NEXT_DAY = "com.pixelschool.app.action.WIDGET_NEXT_DAY"
        const val ACTION_TODAY = "com.pixelschool.app.action.WIDGET_TODAY"

        private const val PREFS_NAME = "pixel_school_widget_prefs"
        private const val KEY_DAY_INDEX = "widget_day_index"

        fun getCombinedSchedule(context: Context): List<DaySchedule> {
            val cacheStore = CacheStore(context)
            val currentWeek = cacheStore.getScheduleCache(0) ?: emptyList()
            val nextWeek = cacheStore.getScheduleCache(-1) ?: emptyList()

            // Filter out weekends and days without lessons
            val filteredCurrent = currentWeek.filter { it.lessons.any { l -> l.subject.isNotBlank() } }
            val filteredNext = nextWeek.filter { it.lessons.any { l -> l.subject.isNotBlank() } }

            return filteredCurrent + filteredNext
        }

        fun getTodayIndex(context: Context, scheduleList: List<DaySchedule>): Int {
            if (scheduleList.isEmpty()) return 0

            val cacheStore = CacheStore(context)
            val currentWeekCount = (cacheStore.getScheduleCache(0) ?: emptyList())
                .count { it.lessons.any { l -> l.subject.isNotBlank() } }

            val cal = Calendar.getInstance()
            val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            val isSaturday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
            val todayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val todayStr = todayDateFormat.format(cal.time)
            val todayDayOfWeekFormat = SimpleDateFormat("EEEE", Locale("ru"))
            val todayDayOfWeek = todayDayOfWeekFormat.format(cal.time).replaceFirstChar { it.uppercase() }

            val hasSaturdayLessons = scheduleList.any { it.dayOfWeek.equals("Суббота", ignoreCase = true) }
            val isWeekend = isSunday || todayDayOfWeek.equals("Воскресенье", ignoreCase = true) ||
                    ((isSaturday || todayDayOfWeek.equals("Суббота", ignoreCase = true)) && !hasSaturdayLessons)

            if (isWeekend) {
                val daysToMonday = if (isSunday || todayDayOfWeek.equals("Воскресенье", ignoreCase = true)) 1 else 2
                val nextMondayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, daysToMonday) }
                val nextMondayStr = todayDateFormat.format(nextMondayCal.time)

                val byDate = scheduleList.indexOfFirst { it.date == nextMondayStr }.takeIf { it >= 0 }
                val byNextWeek = if (currentWeekCount in scheduleList.indices) currentWeekCount else null
                val byMonday = scheduleList.indexOfFirst { it.dayOfWeek.equals("Понедельник", ignoreCase = true) }.takeIf { it >= 0 }

                return byDate ?: byNextWeek ?: byMonday ?: 0
            }

            val byDate = scheduleList.indexOfFirst { it.date == todayStr }.takeIf { it >= 0 }
            val byDayOfWeek = scheduleList.indexOfFirst { it.dayOfWeek.equals(todayDayOfWeek, ignoreCase = true) }.takeIf { it >= 0 }
            return byDate ?: byDayOfWeek ?: 0
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                    appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_lessons_list)
                }
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            val scheduleList = getCombinedSchedule(context)
            val cacheStore = CacheStore(context)
            val currentWeekCount = (cacheStore.getScheduleCache(0) ?: emptyList())
                .count { it.lessons.any { l -> l.subject.isNotBlank() } }

            val todayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val cal = Calendar.getInstance()
            val todayStr = todayDateFormat.format(cal.time)
            val todayDayOfWeekFormat = SimpleDateFormat("EEEE", Locale("ru"))
            val todayDayOfWeek = todayDayOfWeekFormat.format(cal.time).replaceFirstChar { it.uppercase() }
            val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            val isSaturday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
            val hasSaturdayLessons = scheduleList.any { it.dayOfWeek.equals("Суббота", ignoreCase = true) }
            val isWeekend = isSunday || todayDayOfWeek.equals("Воскресенье", ignoreCase = true) ||
                    ((isSaturday || todayDayOfWeek.equals("Суббота", ignoreCase = true)) && !hasSaturdayLessons)

            val todayIndex = getTodayIndex(context, scheduleList)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentIndex = prefs.getInt("${KEY_DAY_INDEX}_$appWidgetId", todayIndex)

            val currentSchedule: DaySchedule? = if (scheduleList.isNotEmpty() && currentIndex in scheduleList.indices) {
                scheduleList[currentIndex]
            } else {
                scheduleList.getOrNull(todayIndex) ?: scheduleList.firstOrNull()
            }

            val lessons = currentSchedule?.lessons?.filter { it.subject.isNotBlank() } ?: emptyList()

            // Header info
            val isNextWeek = currentIndex >= currentWeekCount
            val isToday = !isWeekend && (currentSchedule?.date == todayStr || (!isNextWeek && currentSchedule?.dayOfWeek.equals(todayDayOfWeek, ignoreCase = true)))
            val isTomorrow = if (isWeekend && (isSunday || todayDayOfWeek.equals("Воскресенье", ignoreCase = true))) {
                currentIndex == todayIndex
            } else {
                scheduleList.isNotEmpty() && currentIndex == todayIndex + 1
            }

            val dayTitle = when {
                isToday -> "Сегодня"
                isTomorrow -> "Завтра"
                else -> currentSchedule?.dayOfWeek ?: todayDayOfWeek
            }
            views.setTextViewText(R.id.widget_header_day, dayTitle)

            val formattedDate = currentSchedule?.date?.let { formatWidgetDate(it) } ?: ""
            views.setTextViewText(R.id.widget_header_date, formattedDate)

            val badgeText = when {
                scheduleList.isEmpty() -> "PixelSchool"
                isNextWeek && lessons.isNotEmpty() -> "След. нед. • ${lessons.size} ${getLessonWord(lessons.size)}"
                isNextWeek -> "След. неделя"
                lessons.isNotEmpty() -> "${lessons.size} ${getLessonWord(lessons.size)}"
                else -> "PixelSchool"
            }
            views.setTextViewText(R.id.widget_header_badge, badgeText)

            // Setup Navigation Buttons
            val prevIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_PREV_DAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev_day, prevPendingIntent)

            val nextIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_NEXT_DAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next_day, nextPendingIntent)

            val todayIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_TODAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val todayPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 3,
                todayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_center, todayPendingIntent)

            // Empty state setup (only when no schedule is synced)
            if (scheduleList.isEmpty()) {
                views.setTextViewText(R.id.widget_empty_icon, "📅")
                views.setTextViewText(R.id.widget_empty_title, "Дневник не синхронизирован")
                views.setTextViewText(R.id.widget_empty_subtitle, "Откройте приложение для первой загрузки")
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_lessons_list, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                views.setViewVisibility(R.id.widget_lessons_list, View.VISIBLE)
            }

            // Scrollable ListView setup with RemoteViewsService
            val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://$appWidgetId/$currentIndex/${System.currentTimeMillis()}")
            }
            views.setRemoteAdapter(R.id.widget_lessons_list, serviceIntent)
            views.setEmptyView(R.id.widget_lessons_list, R.id.widget_empty)

            // Click on lesson opens MainActivity
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_lessons_list, clickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getLessonWord(count: Int): String {
            return when {
                count % 10 == 1 && count % 100 != 11 -> "урок"
                count % 10 in 2..4 && (count % 100 !in 12..14) -> "урока"
                else -> "уроков"
            }
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
