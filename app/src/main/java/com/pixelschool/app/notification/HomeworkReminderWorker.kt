package com.pixelschool.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixelschool.app.data.local.CacheStore
import com.pixelschool.app.data.local.SessionStore
import com.pixelschool.app.data.local.SettingsStore
import com.pixelschool.app.data.repository.DiaryRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeworkReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        if (!settingsStore.homeworkReminderEnabled.value) {
            return Result.success()
        }

        val sessionStore = SessionStore(applicationContext)
        val session = sessionStore.getSession() ?: return Result.success()

        val cacheStore = CacheStore(applicationContext)

        val today = LocalDate.now()
        val (targetDate, targetDayLabel) = when (today.dayOfWeek) {
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> {
                val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                Pair(monday, "понедельник")
            }
            DayOfWeek.SUNDAY -> {
                val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                Pair(monday, "завтра")
            }
            else -> {
                val tomorrow = today.plusDays(1)
                Pair(tomorrow, "завтра")
            }
        }

        val fullFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US)
        val shortFormatter = DateTimeFormatter.ofPattern("dd.MM", Locale.US)
        val targetFull = targetDate.format(fullFormatter)
        val targetShort = targetDate.format(shortFormatter)

        val homeworkList = try {
            val repository = DiaryRepository(
                sessionStore = sessionStore,
                cacheStore = cacheStore,
                context = applicationContext
            )
            repository.getHomework(weekOffset = if (today.dayOfWeek == DayOfWeek.SUNDAY) -1 else 0)
        } catch (_: Exception) {
            cacheStore.getHomeworkCache() ?: emptyList()
        }

        val completedIds = cacheStore.getCompletedHomeworkIds()
        val pendingForTarget = homeworkList.filter { hw ->
            val matchesDate = hw.date == targetFull || hw.date.startsWith(targetShort)
            matchesDate && !hw.isCompleted && !completedIds.contains(hw.id)
        }

        if (pendingForTarget.isNotEmpty()) {
            val subjects = pendingForTarget.map { it.subject }.distinct()
            NotificationHelper.notifyHomeworkReminder(
                context = applicationContext,
                targetDayName = targetDayLabel,
                pendingCount = pendingForTarget.size,
                subjects = subjects
            )
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "pixel_school_hw_reminder"

        fun schedule(context: Context, timeStr: String? = null) {
            val settingsStore = SettingsStore(context)
            if (!settingsStore.homeworkReminderEnabled.value) {
                cancel(context)
                return
            }

            val chosenTime = timeStr ?: settingsStore.homeworkReminderTime.value
            val parts = chosenTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 19
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val initialDelayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)

            val request = PeriodicWorkRequestBuilder<HomeworkReminderWorker>(
                24, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
