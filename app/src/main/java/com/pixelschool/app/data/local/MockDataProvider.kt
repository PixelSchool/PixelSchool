package com.pixelschool.app.data.local

import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.Lesson
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession

object MockDataProvider {

    val demoSession = UserSession(
        token = "demo_token_12345678",
        vendor = "edu",
        login = "student_krym",
        userTitle = "Алексей Смирнов (10-А класс)",
        schoolTitle = "МБОУ СОШ №1 г. Симферополь",
        isDemo = true
    )

    fun getWeekSchedule(): List<DaySchedule> {
        return listOf(
            DaySchedule(
                date = "12.10.2026",
                dayOfWeek = "Понедельник",
                lessons = listOf(
                    Lesson(
                        num = 1,
                        startTime = "08:30",
                        endTime = "09:15",
                        subject = "Алгебра и начала анализа",
                        room = "Каб. 24",
                        teacher = "Соколова Е. В.",
                        topic = "Тригонометрические уравнения и неравенства",
                        homework = "№ 142, 145 (а, б)",
                        marks = listOf(Mark(value = "5", weight = 1, date = "12.10", comment = "Ответ у доски"))
                    ),
                    Lesson(
                        num = 2,
                        startTime = "09:25",
                        endTime = "10:10",
                        subject = "Физика",
                        room = "Каб. 31",
                        teacher = "Петров А. Н.",
                        topic = "Законы сохранения в механике",
                        homework = "Параграф 14, вопросы 1-5",
                        marks = emptyList()
                    ),
                    Lesson(
                        num = 3,
                        startTime = "10:30",
                        endTime = "11:15",
                        subject = "Информатика",
                        room = "Каб. 12",
                        teacher = "Ковалев Д. И.",
                        topic = "Динамическое программирование",
                        homework = "Решить задачу на степы",
                        marks = listOf(Mark(value = "5", weight = 2, date = "12.10", comment = "Практическая работа", isControl = true))
                    ),
                    Lesson(
                        num = 4,
                        startTime = "11:35",
                        endTime = "12:20",
                        subject = "Русский язык",
                        room = "Каб. 18",
                        teacher = "Морозова О. А.",
                        topic = "Сложноподчиненные предложения",
                        homework = "Упр. 210 по заданию",
                        marks = listOf(Mark(value = "4", weight = 1, date = "12.10"))
                    ),
                    Lesson(
                        num = 5,
                        startTime = "12:35",
                        endTime = "13:20",
                        subject = "История Крыма",
                        room = "Каб. 22",
                        teacher = "Воронов С. П.",
                        topic = "Крым в эпоху Средневековья",
                        homework = "Конспект параграфа 8",
                        marks = emptyList()
                    )
                )
            ),
            DaySchedule(
                date = "13.10.2026",
                dayOfWeek = "Вторник",
                lessons = listOf(
                    Lesson(
                        num = 1,
                        startTime = "08:30",
                        endTime = "09:15",
                        subject = "Геометрия",
                        room = "Каб. 24",
                        teacher = "Соколова Е. В.",
                        topic = "Аксиомы стереометрии",
                        homework = "Стр. 45 № 3, 5"
                    ),
                    Lesson(
                        num = 2,
                        startTime = "09:25",
                        endTime = "10:10",
                        subject = "Литература",
                        room = "Каб. 18",
                        teacher = "Морозова О. А.",
                        topic = "Поэзия Серебряного века",
                        homework = "Выучить стихотворение наизусть",
                        marks = listOf(Mark(value = "5", weight = 1, date = "13.10", comment = "Чтение наизусть"))
                    ),
                    Lesson(
                        num = 3,
                        startTime = "10:30",
                        endTime = "11:15",
                        subject = "Иностранный язык (Английский)",
                        room = "Каб. 35",
                        teacher = "Браун М. В.",
                        topic = "Scientific Discoveries",
                        homework = "Workbook p. 28 ex. 1-4",
                        marks = listOf(Mark(value = "4", weight = 1, date = "13.10"))
                    ),
                    Lesson(
                        num = 4,
                        startTime = "11:35",
                        endTime = "12:20",
                        subject = "Химия",
                        room = "Каб. 29",
                        teacher = "Громова Т. М.",
                        topic = "Окислительно-восстановительные реакции",
                        homework = "Составить 3 уравнения ОВР"
                    ),
                    Lesson(
                        num = 5,
                        startTime = "12:35",
                        endTime = "13:20",
                        subject = "Физическая культура",
                        room = "Спортзал",
                        teacher = "Сидоров В. К.",
                        topic = "Легкая атлетика"
                    )
                )
            ),
            DaySchedule(
                date = "14.10.2026",
                dayOfWeek = "Среда",
                lessons = listOf(
                    Lesson(
                        num = 1,
                        startTime = "08:30",
                        endTime = "09:15",
                        subject = "Обществознание",
                        room = "Каб. 22",
                        teacher = "Воронов С. П.",
                        topic = "Экономика и государство"
                    ),
                    Lesson(
                        num = 2,
                        startTime = "09:25",
                        endTime = "10:10",
                        subject = "Алгебра и начала анализа",
                        room = "Каб. 24",
                        teacher = "Соколова Е. В.",
                        topic = "Методы решения уравнений",
                        marks = listOf(Mark(value = "4", weight = 1, date = "14.10"))
                    ),
                    Lesson(
                        num = 3,
                        startTime = "10:30",
                        endTime = "11:15",
                        subject = "Биология",
                        room = "Каб. 27",
                        teacher = "Лебедева Н. А.",
                        topic = "Генетика и наследственность"
                    ),
                    Lesson(
                        num = 4,
                        startTime = "11:35",
                        endTime = "12:20",
                        subject = "География",
                        room = "Каб. 19",
                        teacher = "Орлова Ю. В.",
                        topic = "Мировые природные ресурсы"
                    )
                )
            ),
            DaySchedule(
                date = "15.10.2026",
                dayOfWeek = "Четверг",
                lessons = listOf(
                    Lesson(
                        num = 1,
                        startTime = "08:30",
                        endTime = "09:15",
                        subject = "Физика",
                        room = "Каб. 31",
                        teacher = "Петров А. Н.",
                        topic = "Лабораторная работа №2",
                        marks = listOf(Mark(value = "5", weight = 2, isControl = true, date = "15.10"))
                    ),
                    Lesson(
                        num = 2,
                        startTime = "09:25",
                        endTime = "10:10",
                        subject = "Информатика",
                        room = "Каб. 12",
                        teacher = "Ковалев Д. И.",
                        topic = "Графы и алгоритмы на графах"
                    ),
                    Lesson(
                        num = 3,
                        startTime = "10:30",
                        endTime = "11:15",
                        subject = "Геометрия",
                        room = "Каб. 24",
                        teacher = "Соколова Е. В."
                    )
                )
            ),
            DaySchedule(
                date = "16.10.2026",
                dayOfWeek = "Пятница",
                lessons = listOf(
                    Lesson(
                        num = 1,
                        startTime = "08:30",
                        endTime = "09:15",
                        subject = "Литература",
                        room = "Каб. 18",
                        teacher = "Морозова О. А."
                    ),
                    Lesson(
                        num = 2,
                        startTime = "09:25",
                        endTime = "10:10",
                        subject = "Иностранный язык (Английский)",
                        room = "Каб. 35",
                        teacher = "Браун М. В."
                    ),
                    Lesson(
                        num = 3,
                        startTime = "10:30",
                        endTime = "11:15",
                        subject = "ОБЖ",
                        room = "Каб. 14",
                        teacher = "Сергеев В. И."
                    )
                )
            ),
            DaySchedule(
                date = "17.10.2026",
                dayOfWeek = "Суббота",
                isHoliday = true,
                holidayName = "Выходной день"
            )
        )
    }

    fun getHomeworkList(): List<Homework> {
        return listOf(
            Homework("hw_1", "Алгебра и начала анализа", "№ 142, 145 (а, б) — подготовиться к самостоятельной", "13.10.2026", false),
            Homework("hw_2", "Физика", "Параграф 14, ответить на вопросы 1-5 в конце параграфа", "13.10.2026", true),
            Homework("hw_3", "Информатика", "Решить задачу на поиск кратчайшего пути в графе", "14.10.2026", false),
            Homework("hw_4", "Литература", "Выучить стихотворение Серебряного века наизусть", "14.10.2026", false),
            Homework("hw_5", "История Крыма", "Составить конспект параграфа 8, выписать ключевые даты", "15.10.2026", false),
            Homework("hw_6", "Английский язык", "Workbook p. 28 ex. 1-4 (Vocabulary check)", "15.10.2026", true)
        )
    }

    fun getSubjectsSummary(): List<SubjectSummary> {
        return listOf(
            SubjectSummary(
                subject = "Алгебра и начала анализа",
                average = 4.67f,
                marks = listOf(
                    Mark("5", 1, "02.10"),
                    Mark("4", 1, "05.10"),
                    Mark("5", 2, "09.10", isControl = true),
                    Mark("5", 1, "12.10"),
                    Mark("4", 1, "14.10")
                )
            ),
            SubjectSummary(
                subject = "Информатика",
                average = 5.00f,
                marks = listOf(
                    Mark("5", 1, "03.10"),
                    Mark("5", 2, "10.10", isControl = true),
                    Mark("5", 2, "12.10", isControl = true)
                )
            ),
            SubjectSummary(
                subject = "Физика",
                average = 4.80f,
                marks = listOf(
                    Mark("5", 1, "01.10"),
                    Mark("4", 1, "06.10"),
                    Mark("5", 1, "08.10"),
                    Mark("5", 2, "15.10", isControl = true)
                )
            ),
            SubjectSummary(
                subject = "Геометрия",
                average = 4.33f,
                marks = listOf(
                    Mark("4", 1, "03.10"),
                    Mark("4", 1, "07.10"),
                    Mark("5", 1, "10.10")
                )
            ),
            SubjectSummary(
                subject = "Литература",
                average = 4.75f,
                marks = listOf(
                    Mark("5", 1, "04.10"),
                    Mark("4", 1, "08.10"),
                    Mark("5", 1, "13.10")
                )
            ),
            SubjectSummary(
                subject = "Русский язык",
                average = 4.25f,
                marks = listOf(
                    Mark("4", 1, "02.10"),
                    Mark("4", 1, "07.10"),
                    Mark("5", 1, "09.10"),
                    Mark("4", 1, "12.10")
                )
            ),
            SubjectSummary(
                subject = "История Крыма",
                average = 5.00f,
                marks = listOf(
                    Mark("5", 1, "05.10"),
                    Mark("5", 1, "12.10")
                )
            ),
            SubjectSummary(
                subject = "Иностранный язык (Английский)",
                average = 4.50f,
                marks = listOf(
                    Mark("5", 1, "03.10"),
                    Mark("4", 1, "08.10"),
                    Mark("4", 1, "13.10"),
                    Mark("5", 1, "15.10")
                )
            )
        )
    }

    fun getAcademicPeriods(): List<AcademicPeriod> {
        return listOf(
            AcademicPeriod("1", "1 четверть", "20260901", "20261026", "20260901-20261026", isActive = true),
            AcademicPeriod("2", "2 четверть", "20261105", "20261228", "20261105-20261228"),
            AcademicPeriod("3", "3 четверть", "20270111", "20270322", "20270111-20270322"),
            AcademicPeriod("4", "4 четверть", "20270401", "20270525", "20270401-20270525")
        )
    }
}
