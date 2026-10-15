package com.pixelschool.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.pixelschool.app.data.local.AppThemePalette
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.Lesson
import java.io.File
import java.io.FileOutputStream

object ScheduleShareHelper {

    fun generateShareText(
        day: DaySchedule,
        homeworkList: List<Homework>? = null
    ): String {
        val sb = StringBuilder()
        sb.appendLine("📅 ${day.dayOfWeek}, ${day.date}")
        sb.appendLine("═".repeat(24))

        if (day.lessons.isEmpty()) {
            sb.appendLine(day.holidayName ?: "В этот день уроков нет")
        } else {
            day.lessons.forEach { lesson ->
                val hw = resolveHomework(lesson, day.date, homeworkList)
                sb.append("${lesson.num}. ${lesson.subject}")
                if (lesson.startTime.isNotBlank()) {
                    sb.append(" (${lesson.startTime}–${lesson.endTime})")
                }
                if (lesson.room.isNotBlank()) {
                    sb.append(", ${lesson.room}")
                }
                sb.appendLine()
                if (!hw.isNullOrBlank()) {
                    sb.appendLine("   📚 ДЗ: $hw")
                }
                sb.appendLine()
            }
        }

        sb.appendLine("PixelSchool")
        return sb.toString().trimEnd()
    }

    fun resolveHomework(
        lesson: Lesson,
        date: String,
        homeworkList: List<Homework>?
    ): String? {
        if (!lesson.homework.isNullOrBlank()) {
            return lesson.homework
        }
        val match = homeworkList?.firstOrNull { hw ->
            hw.subject.equals(lesson.subject, ignoreCase = true) &&
                    (hw.date == date || hw.date.startsWith(date.take(5)))
        }
        return match?.text
    }

    fun generateShareBitmap(
        day: DaySchedule,
        homeworkList: List<Homework>? = null,
        palette: AppThemePalette = AppThemePalette.DYNAMIC
    ): Bitmap {
        val width = 1080
        val padding = 54f
        val innerWidth = width - (padding * 2)

        // Palette accent color conversion
        val accentColor = when (palette) {
            AppThemePalette.DYNAMIC -> 0xFF6750A4.toInt()
            AppThemePalette.COBALT -> 0xFF1E56A0.toInt()
            AppThemePalette.EMERALD -> 0xFF00796B.toInt()
            AppThemePalette.VIOLET -> 0xFF7C3AED.toInt()
            AppThemePalette.AMBER -> 0xFFD97706.toInt()
            AppThemePalette.ROSE -> 0xFFBE185D.toInt()
        }

        // Paints
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF131418.toInt() }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2028.toInt() }
        val hwCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF282A34.toInt() }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9E9FA9.toInt()
            textSize = 28f
            typeface = Typeface.DEFAULT
        }
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subjectPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0A1AB.toInt()
            textSize = 26f
            typeface = Typeface.DEFAULT
        }
        val hwTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E3E8.toInt()
            textSize = 26f
            typeface = Typeface.DEFAULT
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF70717C.toInt()
            textSize = 22f
            typeface = Typeface.DEFAULT
        }

        // Measure heights of lessons
        class LessonLayoutInfo(
            val lesson: Lesson,
            val hw: String?,
            val hwLayout: StaticLayout?,
            val totalHeight: Float
        )

        val layoutList = mutableListOf<LessonLayoutInfo>()
        val hwInnerWidth = (innerWidth - 48f).toInt()

        for (lesson in day.lessons) {
            val hw = resolveHomework(lesson, day.date, homeworkList)
            val hwLayout = if (!hw.isNullOrBlank()) {
                val formattedHw = "📚 ДЗ: $hw"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(formattedHw, 0, formattedHw.length, hwTextPaint, hwInnerWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(6f, 1f)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(formattedHw, hwTextPaint, hwInnerWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 6f, true)
                }
            } else null

            var itemH = 110f // base card height
            if (lesson.room.isNotBlank() || lesson.teacher.isNotBlank()) {
                itemH += 36f
            }
            if (hwLayout != null) {
                itemH += hwLayout.height + 34f
            }
            layoutList.add(LessonLayoutInfo(lesson, hw, hwLayout, itemH))
        }

        val headerHeight = 170f
        val footerHeight = 80f
        val lessonSpacing = 20f
        val lessonsTotalHeight = if (layoutList.isEmpty()) {
            200f
        } else {
            layoutList.sumOf { it.totalHeight.toDouble() }.toFloat() + ((layoutList.size - 1) * lessonSpacing)
        }

        val totalHeight = (padding + headerHeight + lessonsTotalHeight + footerHeight + padding).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Draw Top Accent Stripe
        canvas.drawRect(0f, 0f, width.toFloat(), 10f, accentPaint)

        var y = padding + 20f

        // Draw Header Badge "PixelSchool"
        val pillRect = RectF(padding, y, padding + 160f, y + 42f)
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF282A34.toInt() }
        canvas.drawRoundRect(pillRect, 21f, 21f, pillBgPaint)
        canvas.drawCircle(padding + 22f, y + 21f, 7f, accentPaint)
        canvas.drawText("PixelSchool", padding + 38f, y + 29f, badgeTextPaint)

        y += 72f
        canvas.drawText("${day.dayOfWeek}", padding, y, titlePaint)
        y += 42f
        canvas.drawText("${day.date} • ${day.lessons.size} уроков", padding, y, subtitlePaint)
        y += 50f

        // Draw Lessons
        if (layoutList.isEmpty()) {
            val emptyRect = RectF(padding, y, width - padding, y + 160f)
            canvas.drawRoundRect(emptyRect, 24f, 24f, cardPaint)
            val emptyMsg = day.holidayName ?: "В этот день уроков нет"
            canvas.drawText(emptyMsg, padding + 36f, y + 90f, subjectPaint)
            y += 180f
        } else {
            for (info in layoutList) {
                val cardRect = RectF(padding, y, width - padding, y + info.totalHeight)
                canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

                // Accent tag on left of card
                val tagRect = RectF(padding, y + 18f, padding + 8f, y + info.totalHeight - 18f)
                canvas.drawRoundRect(tagRect, 4f, 4f, accentPaint)

                var cardY = y + 42f
                val numTimeStr = "${info.lesson.num} • ${info.lesson.startTime}–${info.lesson.endTime}".trim('–', ' ')
                canvas.drawText(numTimeStr, padding + 28f, cardY, metaPaint)

                cardY += 40f
                canvas.drawText(info.lesson.subject, padding + 28f, cardY, subjectPaint)

                if (info.lesson.room.isNotBlank() || info.lesson.teacher.isNotBlank()) {
                    cardY += 34f
                    val roomTeacher = listOf(info.lesson.room, info.lesson.teacher)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    canvas.drawText(roomTeacher, padding + 28f, cardY, metaPaint)
                }

                if (info.hwLayout != null) {
                    cardY += 16f
                    val hwBoxTop = cardY
                    val hwBoxBottom = cardY + info.hwLayout.height + 24f
                    val hwBoxRect = RectF(padding + 24f, hwBoxTop, width - padding - 24f, hwBoxBottom)
                    canvas.drawRoundRect(hwBoxRect, 16f, 16f, hwCardPaint)

                    canvas.save()
                    canvas.translate(padding + 38f, hwBoxTop + 14f)
                    info.hwLayout.draw(canvas)
                    canvas.restore()
                }

                y += info.totalHeight + lessonSpacing
            }
        }

        // Draw Footer
        y += 20f
        canvas.drawText("PixelSchool • ЭлЖур", padding, y, footerPaint)

        return bitmap
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String = "Schedule"): Boolean {
        return try {
            val fullFileName = "${fileName}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fullFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PixelSchool")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false

                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Toast.makeText(context, "Сохранено в галерею (Pictures/PixelSchool)", Toast.LENGTH_SHORT).show()
                true
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val pixelDir = File(picturesDir, "PixelSchool")
                if (!pixelDir.exists()) pixelDir.mkdirs()
                val imageFile = File(pixelDir, fullFileName)
                FileOutputStream(imageFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(context, "Сохранено в галерею", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareImage(context: Context, bitmap: Bitmap, title: String = "Расписание") {
        try {
            val cacheImagesDir = File(context.cacheDir, "images")
            if (!cacheImagesDir.exists()) cacheImagesDir.mkdirs()
            val tempFile = File(cacheImagesDir, "schedule_share.png")
            FileOutputStream(tempFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Поделиться расписанием"))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось отправить: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String, title: String = "Расписание") {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Поделиться расписанием"))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось отправить: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
