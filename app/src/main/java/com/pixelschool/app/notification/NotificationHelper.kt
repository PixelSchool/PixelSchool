package com.pixelschool.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pixelschool.app.MainActivity
import com.pixelschool.app.R
import com.pixelschool.app.data.model.Mark

object NotificationHelper {

    private const val CHANNEL_ID = "pixel_school_marks_channel"
    private const val CHANNEL_NAME = "Новые оценки"
    private const val CHANNEL_HW_ID = "pixel_school_hw_channel"
    private const val CHANNEL_HW_NAME = "Напоминания о ДЗ"
    private const val NOTIFICATION_ID = 1001
    private const val NOTIFICATION_HW_ID = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val marksChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о появлении новых оценок в журнале"
                enableVibration(true)
            }
            manager.createNotificationChannel(marksChannel)

            val hwChannel = NotificationChannel(
                CHANNEL_HW_ID,
                CHANNEL_HW_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминания о несделанном домашнем задании"
                enableVibration(true)
            }
            manager.createNotificationChannel(hwChannel)
        }
    }

    fun notifyNewMarks(context: Context, newMarks: List<Pair<String, Mark>>) {
        if (newMarks.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText = newMarks.take(3).joinToString("; ") { (subject, mark) ->
            "$subject: ${mark.value}"
        }
        val countSuffix = if (newMarks.size > 3) " и ещё ${newMarks.size - 3}" else ""

        val title = if (newMarks.size == 1) {
            "Новая оценка: ${newMarks[0].first}"
        } else {
            "Новые оценки (${newMarks.size})"
        }

        val content = "$summaryText$countSuffix"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun notifyHomeworkReminder(
        context: Context,
        targetDayName: String,
        pendingCount: Int,
        subjects: List<String>
    ) {
        if (pendingCount <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Домашнее задание на $targetDayName"
        val subjectsList = subjects.take(4).joinToString(", ")
        val more = if (subjects.size > 4) " и др." else ""
        val content = "Осталось сделать: $pendingCount предм. ($subjectsList$more)"

        val notification = NotificationCompat.Builder(context, CHANNEL_HW_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_HW_ID, notification)
    }
}
