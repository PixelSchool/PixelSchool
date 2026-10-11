package com.pixelschool.app.data.model

data class UserSession(
    val token: String,
    val vendor: String = "edu",
    val login: String = "",
    val userTitle: String = "",
    val schoolTitle: String = "",
    val isDemo: Boolean = false,
    val studentId: String = ""
)

enum class AttachmentType {
    FILE, LINK
}

data class Attachment(
    val title: String,
    val url: String,
    val type: AttachmentType
)

data class DaySchedule(
    val date: String,            // "12.10.2026"
    val dayOfWeek: String,       // "Понедельник"
    val lessons: List<Lesson> = emptyList(),
    val isHoliday: Boolean = false,
    val holidayName: String? = null
)

data class Lesson(
    val num: Int,
    val startTime: String,       // "08:30"
    val endTime: String,         // "09:15"
    val subject: String,         // "Алгебра"
    val room: String = "",       // "Каб. 24"
    val teacher: String = "",    // "Иванова А. С."
    val topic: String? = null,
    val homework: String? = null,
    val marks: List<Mark> = emptyList(),
    val attachments: List<Attachment> = emptyList()
)

data class Mark(
    val value: String,           // "5", "4+", "Н"
    val weight: Int = 1,
    val date: String = "",
    val comment: String? = null,
    val isControl: Boolean = false
) {
    val numericValue: Float?
        get() = value.take(1).toFloatOrNull()
}

data class Homework(
    val id: String,
    val subject: String,
    val text: String,
    val date: String,            // "13.10.2026"
    val isCompleted: Boolean = false,
    val attachments: List<Attachment> = emptyList()
)

data class PeriodMarks(
    val periodId: String,        // "1", "2", "3", "4"
    val periodName: String,      // "1 четверть", "2 четверть"
    val marks: List<Mark> = emptyList(),
    val average: Float = 0f,
    val finalMark: String? = null
)

data class SubjectSummary(
    val subject: String,
    val average: Float,          // 4.67
    val marks: List<Mark> = emptyList(),
    val finalMark: String? = null,
    val periodMarks: List<PeriodMarks> = emptyList()
)

data class AcademicPeriod(
    val id: String,              // "1"
    val title: String,           // "1 четверть"
    val start: String,           // "20260901"
    val end: String,             // "20261026"
    val rangeDays: String,       // "20260901-20261026"
    val isActive: Boolean = false
)
