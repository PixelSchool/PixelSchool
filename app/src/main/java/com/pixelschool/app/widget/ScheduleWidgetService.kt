package com.pixelschool.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.pixelschool.app.R
import com.pixelschool.app.data.local.CacheStore
import com.pixelschool.app.data.model.Lesson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetFactory(applicationContext, intent)
    }
}

class ScheduleWidgetFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var lessons: List<Lesson> = emptyList()
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val cacheStore = CacheStore(context)
        val scheduleList = cacheStore.getScheduleCache(0) ?: emptyList()
        if (scheduleList.isEmpty()) {
            lessons = emptyList()
            return
        }

        val prefs = context.getSharedPreferences("pixel_school_widget_prefs", Context.MODE_PRIVATE)
        val todayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val todayStr = todayDateFormat.format(Date())
        val todayIndex = scheduleList.indexOfFirst { it.date == todayStr }.takeIf { it >= 0 } ?: 0

        val selectedIndex = prefs.getInt("widget_day_index_$appWidgetId", todayIndex)
        val effectiveDay = if (selectedIndex in scheduleList.indices) {
            scheduleList[selectedIndex]
        } else {
            scheduleList.getOrNull(todayIndex) ?: scheduleList.firstOrNull()
        }

        lessons = effectiveDay?.lessons?.filter { it.subject.isNotBlank() } ?: emptyList()
    }

    override fun onDestroy() {
        lessons = emptyList()
    }

    override fun getCount(): Int = lessons.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_lesson_item)
        if (position !in lessons.indices) return views

        val lesson = lessons[position]
        val timeStr = if (lesson.startTime.isNotBlank()) {
            "${lesson.num} • ${lesson.startTime}"
        } else {
            "${lesson.num} урок"
        }
        views.setTextViewText(R.id.widget_item_time, timeStr)
        views.setTextViewText(R.id.widget_item_subject, lesson.subject)

        if (lesson.room.isNotBlank()) {
            val roomDisplay = if (lesson.room.startsWith("каб", ignoreCase = true)) {
                lesson.room
            } else {
                "каб. ${lesson.room}"
            }
            views.setTextViewText(R.id.widget_item_room, roomDisplay)
            views.setViewVisibility(R.id.widget_item_room, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_item_room, View.GONE)
        }

        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
