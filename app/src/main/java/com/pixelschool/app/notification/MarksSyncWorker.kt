package com.pixelschool.app.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixelschool.app.data.local.CacheStore
import com.pixelschool.app.data.local.SessionStore
import com.pixelschool.app.data.local.SettingsStore
import com.pixelschool.app.data.repository.DiaryRepository
import java.util.concurrent.TimeUnit

class MarksSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        if (!settingsStore.notificationsEnabled.value) {
            return Result.success()
        }

        val sessionStore = SessionStore(applicationContext)
        val session = sessionStore.getSession() ?: return Result.success()
        if (session.isDemo) return Result.success()

        val cacheStore = CacheStore(applicationContext)
        val repository = DiaryRepository(
            sessionStore = sessionStore,
            cacheStore = cacheStore,
            context = applicationContext
        )

        return try {
            repository.getMarks()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "pixel_school_marks_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MarksSyncWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
