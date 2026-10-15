package com.pixelschool.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.Attachment
import com.pixelschool.app.data.model.AttachmentType
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.Lesson
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.model.PeriodMarks
import com.pixelschool.app.data.model.SubjectSummary
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight persistent cache for offline mode and completed homework state.
 * Uses native SharedPreferences and org.json with zero external bloat.
 */
class CacheStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_school_cache", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COMPLETED_HOMEWORK_IDS = "key_completed_homework_ids"
        private const val PREFIX_SCHEDULE = "cache_schedule_week_"
        private const val KEY_HOMEWORK = "cache_homework_list"
        private const val KEY_GRADES = "cache_grades_summary"
        private const val KEY_PERIODS = "cache_academic_periods"
        private const val KEY_LAST_SYNC_TIME = "cache_last_sync_timestamp"
        private const val KEY_KNOWN_MARKS = "key_known_marks_set"
        private const val KEY_BASELINE_INITIALIZED = "key_marks_baseline_initialized"
    }

    // --- New Marks Notification Baseline ---

    fun isMarksBaselineInitialized(): Boolean {
        return prefs.getBoolean(KEY_BASELINE_INITIALIZED, false)
    }

    fun getKnownMarkKeys(): Set<String> {
        return prefs.getStringSet(KEY_KNOWN_MARKS, emptySet()) ?: emptySet()
    }

    fun saveKnownMarkKeys(keys: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_KNOWN_MARKS, HashSet(keys))
            .putBoolean(KEY_BASELINE_INITIALIZED, true)
            .apply()
    }

    fun addKnownMarkKeys(newKeys: Set<String>) {
        val current = HashSet(getKnownMarkKeys())
        current.addAll(newKeys)
        prefs.edit()
            .putStringSet(KEY_KNOWN_MARKS, current)
            .putBoolean(KEY_BASELINE_INITIALIZED, true)
            .apply()
    }

    // --- Completed Homework State ---

    fun getCompletedHomeworkIds(): Set<String> {
        return prefs.getStringSet(KEY_COMPLETED_HOMEWORK_IDS, emptySet()) ?: emptySet()
    }

    fun isHomeworkCompleted(id: String): Boolean {
        return getCompletedHomeworkIds().contains(id)
    }

    fun toggleHomeworkCompleted(id: String): Boolean {
        val current = getCompletedHomeworkIds().toMutableSet()
        val newState = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet(KEY_COMPLETED_HOMEWORK_IDS, current).apply()
        return newState
    }

    fun setHomeworkCompleted(id: String, completed: Boolean) {
        val current = getCompletedHomeworkIds().toMutableSet()
        if (completed) {
            current.add(id)
        } else {
            current.remove(id)
        }
        prefs.edit().putStringSet(KEY_COMPLETED_HOMEWORK_IDS, current).apply()
    }

    // --- Last Sync Timestamp ---

    fun saveLastSyncTime(timeMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timeMillis).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    // --- Schedule Cache ---

    fun saveScheduleCache(weekOffset: Int, schedule: List<DaySchedule>) {
        try {
            val array = JSONArray()
            schedule.forEach { day ->
                val dayObj = JSONObject().apply {
                    put("date", day.date)
                    put("dayOfWeek", day.dayOfWeek)
                    put("isHoliday", day.isHoliday)
                    put("holidayName", day.holidayName ?: "")
                    val lessonsArr = JSONArray()
                    day.lessons.forEach { lesson ->
                        lessonsArr.put(serializeLesson(lesson))
                    }
                    put("lessons", lessonsArr)
                }
                array.put(dayObj)
            }
            prefs.edit()
                .putString("$PREFIX_SCHEDULE$weekOffset", array.toString())
                .apply()
        } catch (_: Exception) {}
    }

    fun getScheduleCache(weekOffset: Int): List<DaySchedule>? {
        val json = prefs.getString("$PREFIX_SCHEDULE$weekOffset", null) ?: return null
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<DaySchedule>()
            for (i in 0 until array.length()) {
                val dayObj = array.getJSONObject(i)
                val lessonsArr = dayObj.optJSONArray("lessons") ?: JSONArray()
                val lessons = mutableListOf<Lesson>()
                for (j in 0 until lessonsArr.length()) {
                    lessons.add(deserializeLesson(lessonsArr.getJSONObject(j)))
                }
                list.add(
                    DaySchedule(
                        date = dayObj.optString("date"),
                        dayOfWeek = dayObj.optString("dayOfWeek"),
                        isHoliday = dayObj.optBoolean("isHoliday", false),
                        holidayName = dayObj.optString("holidayName").ifEmpty { null },
                        lessons = lessons
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (_: Exception) {
            null
        }
    }

    fun getAllCachedDays(): List<DaySchedule> {
        val result = mutableListOf<DaySchedule>()
        try {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(PREFIX_SCHEDULE) && value is String) {
                    val array = JSONArray(value)
                    for (i in 0 until array.length()) {
                        val dayObj = array.getJSONObject(i)
                        val lessonsArr = dayObj.optJSONArray("lessons") ?: JSONArray()
                        val lessons = mutableListOf<Lesson>()
                        for (j in 0 until lessonsArr.length()) {
                            lessons.add(deserializeLesson(lessonsArr.getJSONObject(j)))
                        }
                        result.add(
                            DaySchedule(
                                date = dayObj.optString("date"),
                                dayOfWeek = dayObj.optString("dayOfWeek"),
                                isHoliday = dayObj.optBoolean("isHoliday", false),
                                holidayName = dayObj.optString("holidayName").ifEmpty { null },
                                lessons = lessons
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result.distinctBy { it.date }
    }

    // --- Homework Cache ---

    fun saveHomeworkCache(homeworkList: List<Homework>) {
        try {
            val array = JSONArray()
            homeworkList.forEach { hw ->
                val obj = JSONObject().apply {
                    put("id", hw.id)
                    put("subject", hw.subject)
                    put("text", hw.text)
                    put("date", hw.date)
                    val attachArr = JSONArray()
                    hw.attachments.forEach { a ->
                        attachArr.put(JSONObject().apply {
                            put("title", a.title)
                            put("url", a.url)
                            put("type", a.type.name)
                        })
                    }
                    put("attachments", attachArr)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_HOMEWORK, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getHomeworkCache(): List<Homework>? {
        val json = prefs.getString(KEY_HOMEWORK, null) ?: return null
        return try {
            val array = JSONArray(json)
            val completedIds = getCompletedHomeworkIds()
            val list = mutableListOf<Homework>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                val attachArr = obj.optJSONArray("attachments") ?: JSONArray()
                val attachments = mutableListOf<Attachment>()
                for (j in 0 until attachArr.length()) {
                    val aObj = attachArr.getJSONObject(j)
                    attachments.add(
                        Attachment(
                            title = aObj.optString("title"),
                            url = aObj.optString("url"),
                            type = try {
                                AttachmentType.valueOf(aObj.optString("type"))
                            } catch (_: Exception) {
                                AttachmentType.LINK
                            }
                        )
                    )
                }
                list.add(
                    Homework(
                        id = id,
                        subject = obj.optString("subject"),
                        text = obj.optString("text"),
                        date = obj.optString("date"),
                        isCompleted = completedIds.contains(id),
                        attachments = attachments
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (_: Exception) {
            null
        }
    }

    // --- Grades Cache ---

    fun saveGradesCache(grades: List<SubjectSummary>, periods: List<AcademicPeriod>) {
        try {
            val gradesArr = JSONArray()
            grades.forEach { s ->
                val obj = JSONObject().apply {
                    put("subject", s.subject)
                    put("average", s.average.toDouble())
                    put("finalMark", s.finalMark ?: "")
                    val marksArr = JSONArray()
                    s.marks.forEach { m -> marksArr.put(serializeMark(m)) }
                    put("marks", marksArr)

                    val periodsArr = JSONArray()
                    s.periodMarks.forEach { pm ->
                        periodsArr.put(JSONObject().apply {
                            put("periodId", pm.periodId)
                            put("periodName", pm.periodName)
                            put("average", pm.average.toDouble())
                            put("finalMark", pm.finalMark ?: "")
                            val pmMarks = JSONArray()
                            pm.marks.forEach { m -> pmMarks.put(serializeMark(m)) }
                            put("marks", pmMarks)
                        })
                    }
                    put("periodMarks", periodsArr)
                }
                gradesArr.put(obj)
            }

            val periodsArr = JSONArray()
            periods.forEach { p ->
                periodsArr.put(JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("start", p.start)
                    put("end", p.end)
                    put("rangeDays", p.rangeDays)
                    put("isActive", p.isActive)
                })
            }

            prefs.edit()
                .putString(KEY_GRADES, gradesArr.toString())
                .putString(KEY_PERIODS, periodsArr.toString())
                .apply()
        } catch (_: Exception) {}
    }

    fun getGradesCache(): Pair<List<SubjectSummary>, List<AcademicPeriod>>? {
        val gradesJson = prefs.getString(KEY_GRADES, null) ?: return null
        val periodsJson = prefs.getString(KEY_PERIODS, null) ?: return null
        return try {
            val gradesArr = JSONArray(gradesJson)
            val periodsArr = JSONArray(periodsJson)

            val gradesList = mutableListOf<SubjectSummary>()
            for (i in 0 until gradesArr.length()) {
                val obj = gradesArr.getJSONObject(i)
                val marksArr = obj.optJSONArray("marks") ?: JSONArray()
                val marks = mutableListOf<Mark>()
                for (j in 0 until marksArr.length()) {
                    marks.add(deserializeMark(marksArr.getJSONObject(j)))
                }

                val pMarksArr = obj.optJSONArray("periodMarks") ?: JSONArray()
                val periodMarks = mutableListOf<PeriodMarks>()
                for (j in 0 until pMarksArr.length()) {
                    val pmObj = pMarksArr.getJSONObject(j)
                    val pmMarksArr = pmObj.optJSONArray("marks") ?: JSONArray()
                    val pmMarks = mutableListOf<Mark>()
                    for (k in 0 until pmMarksArr.length()) {
                        pmMarks.add(deserializeMark(pmMarksArr.getJSONObject(k)))
                    }
                    periodMarks.add(
                        PeriodMarks(
                            periodId = pmObj.optString("periodId"),
                            periodName = pmObj.optString("periodName"),
                            average = pmObj.optDouble("average", 0.0).toFloat(),
                            finalMark = pmObj.optString("finalMark").ifEmpty { null },
                            marks = pmMarks
                        )
                    )
                }

                gradesList.add(
                    SubjectSummary(
                        subject = obj.optString("subject"),
                        average = obj.optDouble("average", 0.0).toFloat(),
                        marks = marks,
                        finalMark = obj.optString("finalMark").ifEmpty { null },
                        periodMarks = periodMarks
                    )
                )
            }

            val periodsList = mutableListOf<AcademicPeriod>()
            for (i in 0 until periodsArr.length()) {
                val pObj = periodsArr.getJSONObject(i)
                periodsList.add(
                    AcademicPeriod(
                        id = pObj.optString("id"),
                        title = pObj.optString("title"),
                        start = pObj.optString("start"),
                        end = pObj.optString("end"),
                        rangeDays = pObj.optString("rangeDays"),
                        isActive = pObj.optBoolean("isActive", false)
                    )
                )
            }

            if (gradesList.isNotEmpty()) Pair(gradesList, periodsList) else null
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // --- Private JSON Helpers ---

    private fun serializeLesson(lesson: Lesson): JSONObject {
        return JSONObject().apply {
            put("num", lesson.num)
            put("startTime", lesson.startTime)
            put("endTime", lesson.endTime)
            put("subject", lesson.subject)
            put("room", lesson.room)
            put("teacher", lesson.teacher)
            put("topic", lesson.topic ?: "")
            put("homework", lesson.homework ?: "")
            val marksArr = JSONArray()
            lesson.marks.forEach { marksArr.put(serializeMark(it)) }
            put("marks", marksArr)
            val attachArr = JSONArray()
            lesson.attachments.forEach { a ->
                attachArr.put(JSONObject().apply {
                    put("title", a.title)
                    put("url", a.url)
                    put("type", a.type.name)
                })
            }
            put("attachments", attachArr)
        }
    }

    private fun deserializeLesson(obj: JSONObject): Lesson {
        val marksArr = obj.optJSONArray("marks") ?: JSONArray()
        val marks = mutableListOf<Mark>()
        for (i in 0 until marksArr.length()) {
            marks.add(deserializeMark(marksArr.getJSONObject(i)))
        }

        val attachArr = obj.optJSONArray("attachments") ?: JSONArray()
        val attachments = mutableListOf<Attachment>()
        for (i in 0 until attachArr.length()) {
            val aObj = attachArr.getJSONObject(i)
            attachments.add(
                Attachment(
                    title = aObj.optString("title"),
                    url = aObj.optString("url"),
                    type = try {
                        AttachmentType.valueOf(aObj.optString("type"))
                    } catch (_: Exception) {
                        AttachmentType.LINK
                    }
                )
            )
        }

        return Lesson(
            num = obj.optInt("num"),
            startTime = obj.optString("startTime"),
            endTime = obj.optString("endTime"),
            subject = obj.optString("subject"),
            room = obj.optString("room"),
            teacher = obj.optString("teacher"),
            topic = obj.optString("topic").ifEmpty { null },
            homework = obj.optString("homework").ifEmpty { null },
            marks = marks,
            attachments = attachments
        )
    }

    private fun serializeMark(mark: Mark): JSONObject {
        return JSONObject().apply {
            put("value", mark.value)
            put("weight", mark.weight)
            put("date", mark.date)
            put("comment", mark.comment ?: "")
            put("isControl", mark.isControl)
        }
    }

    private fun deserializeMark(obj: JSONObject): Mark {
        return Mark(
            value = obj.optString("value"),
            weight = obj.optInt("weight", 1),
            date = obj.optString("date"),
            comment = obj.optString("comment").ifEmpty { null },
            isControl = obj.optBoolean("isControl", false)
        )
    }
}
