package com.pixelschool.app.data.ai

import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.remote.RussianTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ProblemLesson(
    val date: String,
    val mark: Mark,
    val topic: String,
    val homework: String?,
    val comment: String?,
    val teacher: String?,
    val isControl: Boolean
)

data class AiStudyResult(
    val subject: String,
    val currentAverage: Float,
    val neededFivesForGoal: Int,
    val goalGrade: String,
    val problemLessons: List<ProblemLesson>,
    val studySummary: String,
    val recommendedTopics: List<String>,
    val isAiGenerated: Boolean,
    val aiModelName: String? = null,
    val errorMessage: String? = null
)

object AiStudyAssistant {

    private val client by lazy { RussianTrustManager.defaultOkHttpClient }

    fun extractProblemLessons(
        subjectName: String,
        marks: List<Mark>,
        allDays: List<DaySchedule>
    ): List<ProblemLesson> {
        val result = mutableListOf<ProblemLesson>()

        val strugglingMarks = marks.filter { mark ->
            val num = mark.numericValue
            (num != null && num < 4.0f) || (mark.value.contains("Н", ignoreCase = true) && mark.isControl)
        }

        for (mark in strugglingMarks) {
            val markDateNorm = mark.date.trim()
            val day = allDays.firstOrNull { d ->
                val dDate = d.date.trim()
                dDate == markDateNorm ||
                (dDate.length >= 5 && markDateNorm.length >= 5 && dDate.take(5) == markDateNorm.take(5))
            }

            val lesson = day?.lessons?.firstOrNull { l ->
                l.subject.equals(subjectName, ignoreCase = true) ||
                l.subject.contains(subjectName, ignoreCase = true) ||
                subjectName.contains(l.subject, ignoreCase = true)
            }

            val resolvedTopic = when {
                !lesson?.topic.isNullOrBlank() -> lesson!!.topic!!.trim()
                !mark.comment.isNullOrBlank() -> mark.comment.trim()
                !lesson?.homework.isNullOrBlank() -> "ДЗ: ${lesson!!.homework!!.trim()}"
                else -> "Тема урока не указана в журнале"
            }

            result.add(
                ProblemLesson(
                    date = if (mark.date.isNotBlank()) mark.date else (day?.date ?: ""),
                    mark = mark,
                    topic = resolvedTopic,
                    homework = lesson?.homework?.takeIf { it.isNotBlank() },
                    comment = mark.comment?.takeIf { it.isNotBlank() },
                    teacher = lesson?.teacher?.takeIf { it.isNotBlank() },
                    isControl = mark.isControl || (mark.comment?.contains("контроль", ignoreCase = true) == true)
                )
            )
        }

        return result.distinctBy { "${it.date}_${it.mark.value}_${it.topic}" }
            .sortedByDescending { it.date }
    }

    fun calculateNeededFives(marks: List<Mark>, targetAverage: Double): Int {
        val validMarks = marks.mapNotNull { m ->
            m.numericValue?.let { it.toDouble() to (if (m.weight > 0) m.weight else 1) }
        }
        if (validMarks.isEmpty()) return 1

        val currentWeightSum = validMarks.sumOf { it.second }.toDouble()
        val currentProductSum = validMarks.sumOf { it.first * it.second }
        val currentAverage = currentProductSum / currentWeightSum

        if (currentAverage >= targetAverage) return 0

        var addedFives = 0
        var simProduct = currentProductSum
        var simWeight = currentWeightSum

        while (addedFives < 30) {
            addedFives++
            simProduct += 5.0 * 1.0
            simWeight += 1.0
            if (simProduct / simWeight >= targetAverage) {
                return addedFives
            }
        }
        return addedFives
    }

    fun generateLocalAnalysis(
        subjectName: String,
        currentAverage: Float,
        marks: List<Mark>,
        allDays: List<DaySchedule>
    ): AiStudyResult {
        val problems = extractProblemLessons(subjectName, marks, allDays)
        val target = if (currentAverage < 3.50f) 3.50 else 4.50
        val goalLabel = if (currentAverage < 3.50f) "«4» (Хорошо)" else "«5» (Отлично)"
        val neededFives = calculateNeededFives(marks, target)

        val meaningfulTopics = problems
            .map { it.topic }
            .filter { it.isNotBlank() && !it.contains("не указана", ignoreCase = true) }
            .distinct()

        val summary = buildString {
            appendLine("Встроенный локальный анализ предмета «$subjectName»:")
            appendLine("Текущий средний балл: ${String.format(Locale.US, "%.2f", currentAverage)}.")
            appendLine("Цель: $goalLabel. Требуется получить ещё $neededFives ${pluralMarks(neededFives)} «5» с весом 1.")
            appendLine()

            if (meaningfulTopics.isNotEmpty()) {
                appendLine("📚 Темы, где были получены оценки 2 или 3:")
                meaningfulTopics.take(5).forEachIndexed { idx, t ->
                    appendLine("  ${idx + 1}. $t")
                }
                appendLine()
                appendLine("💡 Рекомендации по подготовке:")
                appendLine("• Повторите теоретические правила и конспекты по перечисленным темам.")
                val hwCount = problems.count { !it.homework.isNullOrBlank() }
                if (hwCount > 0) {
                    appendLine("• Прорешайте домашние задания и упражнения за эти даты ($hwCount ${pluralHomework(hwCount)}).")
                }
                val controlCount = problems.count { it.isControl }
                if (controlCount > 0) {
                    appendLine("• ⚠️ Выявлено $controlCount ${pluralControls(controlCount)} — обязательно подготовьтесь к закрытию контрольных точек.")
                }
                appendLine("• Попросите учителя ответить у доски или сдать дополнительный проект для исправления.")
            } else {
                appendLine("Зафиксировано ${problems.size} ${pluralMarks(problems.size)} ниже четвёрки.")
                appendLine("Темы уроков в дневнике не были детализированы учителем. Рекомендуется повторить пройденные за последние недели главы учебника.")
            }
            appendLine()
            append("ℹ️ Совет: в настройках можно добавить бесплатный API-ключ Gemini для генерации персональных шпаргалок и формул с ИИ.")
        }

        return AiStudyResult(
            subject = subjectName,
            currentAverage = currentAverage,
            neededFivesForGoal = neededFives,
            goalGrade = goalLabel,
            problemLessons = problems,
            studySummary = summary,
            recommendedTopics = meaningfulTopics,
            isAiGenerated = false
        )
    }

    suspend fun analyzeWithGemini(
        apiKey: String,
        subjectName: String,
        currentAverage: Float,
        marks: List<Mark>,
        allDays: List<DaySchedule>
    ): AiStudyResult = withContext(Dispatchers.IO) {
        val problems = extractProblemLessons(subjectName, marks, allDays)
        val target = if (currentAverage < 3.50f) 3.50 else 4.50
        val goalLabel = if (currentAverage < 3.50f) "«4» (Хорошо)" else "«5» (Отлично)"
        val neededFives = calculateNeededFives(marks, target)

        if (apiKey.isBlank()) {
            return@withContext generateLocalAnalysis(subjectName, currentAverage, marks, allDays)
        }

        val meaningfulTopics = problems
            .map { it.topic }
            .filter { it.isNotBlank() && !it.contains("не указана", ignoreCase = true) }
            .distinct()

        val promptText = buildString {
            appendLine("Ты — опытный и поддерживающий персональный школьный репетитор по предмету «$subjectName».")
            appendLine("Ученик стремится исправить текущий средний балл с ${String.format(Locale.US, "%.2f", currentAverage)} до цели: $goalLabel.")
            appendLine("Ему нужно получить ещё $neededFives оценок «5».")
            appendLine()
            if (problems.isNotEmpty()) {
                appendLine("Список уроков и проверочных работ, за которые были получены оценки 2 или 3:")
                problems.forEach { p ->
                    appendLine("- Дата: ${p.date}, Оценка: ${p.mark.value}${if (p.isControl) " (Контрольная работа)" else ""}. Тема: ${p.topic}${if (!p.homework.isNullOrBlank()) ", ДЗ: ${p.homework}" else ""}")
                }
            } else {
                appendLine("Конкретных двоек/троек мало, но средний балл требует улучшения.")
            }
            appendLine()
            appendLine("Сформируй понятный, мотивирующий и практичный разбор для школьника на русском языке:")
            appendLine("1. 🎯 Какие 2-3 ключевые темы нужно выучить и подтянуть в первую очередь (объясни их простыми словами).")
            appendLine("2. 💡 Шпаргалка: главные правила, законы или формулы по этим темам, которые обязательно спросят.")
            appendLine("3. ❓ 1-2 примера типовых вопросов или задач, которые учитель чаще всего даёт для исправления оценки.")
            appendLine("Пиши чётко, структурированно, без шаблонной «воды», используй эмодзи и списки.")
        }

        val jsonBody = JSONObject().apply {
            val partsArr = JSONArray().apply {
                put(JSONObject().apply { put("text", promptText) })
            }
            val contentsArr = JSONArray().apply {
                put(JSONObject().apply { put("parts", partsArr) })
            }
            put("contents", contentsArr)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 1200)
            })
        }

        val modelsToTry = listOf("gemini-2.0-flash", "gemini-1.5-flash")
        var lastException: Exception? = null

        for (model in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val respStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val respJson = JSONObject(respStr)
                        val candidates = respJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val text = parts?.optJSONObject(0)?.optString("text")

                            if (!text.isNullOrBlank()) {
                                return@withContext AiStudyResult(
                                    subject = subjectName,
                                    currentAverage = currentAverage,
                                    neededFivesForGoal = neededFives,
                                    goalGrade = goalLabel,
                                    problemLessons = problems,
                                    studySummary = text.trim(),
                                    recommendedTopics = meaningfulTopics,
                                    isAiGenerated = true,
                                    aiModelName = model
                                )
                            }
                        }
                    } else {
                        val errMsg = try {
                            JSONObject(respStr).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        } catch (_: Exception) {
                            "HTTP ${response.code}"
                        }
                        lastException = Exception("Gemini API ($model): $errMsg")
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        // If Gemini failed (bad key, no network, etc.), fallback to local analysis
        val local = generateLocalAnalysis(subjectName, currentAverage, marks, allDays)
        return@withContext local.copy(
            errorMessage = "Не удалось связаться с Gemini (${lastException?.message ?: "ошибка сети"}). Показан встроенный анализ."
        )
    }

    private fun pluralMarks(count: Int): String {
        val rem10 = count % 10
        val rem100 = count % 100
        return when {
            rem100 in 11..19 -> "оценок"
            rem10 == 1 -> "оценку"
            rem10 in 2..4 -> "оценки"
            else -> "оценок"
        }
    }

    private fun pluralHomework(count: Int): String {
        val rem10 = count % 10
        val rem100 = count % 100
        return when {
            rem100 in 11..19 -> "заданий"
            rem10 == 1 -> "задание"
            rem10 in 2..4 -> "задания"
            else -> "заданий"
        }
    }

    private fun pluralControls(count: Int): String {
        val rem10 = count % 10
        val rem100 = count % 100
        return when {
            rem100 in 11..19 -> "проверочных работ"
            rem10 == 1 -> "проверочная работа"
            rem10 in 2..4 -> "проверочные работы"
            else -> "проверочных работ"
        }
    }
}
