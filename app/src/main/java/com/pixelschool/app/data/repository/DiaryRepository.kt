package com.pixelschool.app.data.repository

import com.pixelschool.app.data.local.MockDataProvider
import com.pixelschool.app.data.local.SessionStore
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.model.PeriodMarks
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
import com.pixelschool.app.data.remote.AuthCredentials
import com.pixelschool.app.data.remote.EljurApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiaryRepository(
    private val sessionStore: SessionStore,
    private val apiClient: EljurApiClient = EljurApiClient()
) {

    private val _session = MutableStateFlow<UserSession?>(sessionStore.getSession())
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    fun loginAsDemo(): UserSession {
        val demo = MockDataProvider.demoSession
        sessionStore.saveSession(demo)
        _session.value = demo
        return demo
    }

    suspend fun loginWithSession(
        auth: AuthCredentials?,
        cookies: String?,
        fallbackUserName: String? = null,
        fallbackSchoolName: String? = null
    ): Result<UserSession> {
        val result = apiClient.resolveSession(auth, cookies, fallbackUserName, fallbackSchoolName)
        if (result.isSuccess) {
            val s = result.getOrThrow()
            sessionStore.saveSession(s, cookies)
            _session.value = s
        }
        return result
    }

    fun logout() {
        sessionStore.clear()
        _session.value = null
    }

    suspend fun ensureProfileLoaded() {
        val currentSession = _session.value ?: return
        if (currentSession.isDemo) return
        val cookies = sessionStore.getCookies()
        if (cookies.isNullOrBlank()) return

        if (currentSession.userTitle.isBlank() ||
            currentSession.userTitle == "Ученик" ||
            currentSession.firstName.isBlank() ||
            currentSession.schoolTitle.isBlank() ||
            currentSession.schoolTitle == "ГИС РК «Образование»" ||
            currentSession.schoolTitle == "Республика Крым" ||
            currentSession.schoolTitle.contains("·") ||
            (!currentSession.schoolTitle.contains("школ", ignoreCase = true) &&
             !currentSession.schoolTitle.contains("гимназ", ignoreCase = true) &&
             !currentSession.schoolTitle.contains("лицей", ignoreCase = true) &&
             !currentSession.schoolTitle.contains("СОШ", ignoreCase = true) &&
             !currentSession.schoolTitle.contains("МБОУ", ignoreCase = true)) ||
            currentSession.studentId.isBlank()
        ) {
            try {
                val profile = apiClient.scrapeUserProfile(cookies)
                var updated = currentSession
                if (profile.name.isNotBlank() && profile.name != "Ученик") {
                    updated = updated.copy(
                        userTitle = profile.name,
                        firstName = profile.firstName.ifBlank { updated.firstName },
                        lastName = profile.lastName.ifBlank { updated.lastName }
                    )
                }
                if (profile.school.isNotBlank() && profile.school != "ГИС РК «Образование»" && profile.school != "Республика Крым") {
                    updated = updated.copy(
                        schoolTitle = profile.school,
                        schoolName = profile.school
                    )
                }
                if (profile.studentId.isNotBlank() && updated.studentId.isBlank()) {
                    updated = updated.copy(studentId = profile.studentId)
                }
                if (updated != currentSession) {
                    sessionStore.saveSession(updated, cookies)
                    _session.value = updated
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun getPeriods(): List<AcademicPeriod> {
        val currentSession = _session.value ?: return emptyList()
        if (currentSession.isDemo) {
            return MockDataProvider.getAcademicPeriods()
        }

        var activeSession = currentSession
        if (activeSession.studentId.isBlank()) {
            val enriched = apiClient.enrichWithRules(activeSession, sessionStore.getCookies())
            if (enriched.studentId.isNotBlank() || (enriched.token.isNotBlank() && enriched.token != activeSession.token)) {
                sessionStore.saveSession(enriched)
                _session.value = enriched
                activeSession = enriched
            }
        }

        val res = apiClient.fetchPeriods(
            token = activeSession.token,
            vendor = activeSession.vendor,
            studentId = activeSession.studentId.ifBlank { null },
            cookies = sessionStore.getCookies()
        )
        return res.getOrDefault(apiClient.generateDefaultAcademicPeriods())
    }

    suspend fun getSchedule(weekOffset: Int = 0): List<DaySchedule> {
        val currentSession = _session.value
            ?: throw IllegalStateException("Сессия не найдена. Требуется авторизация.")
        if (currentSession.isDemo) {
            return MockDataProvider.getWeekSchedule()
        }

        var activeSession = currentSession
        if (activeSession.studentId.isBlank()) {
            val enriched = apiClient.enrichWithRules(activeSession, sessionStore.getCookies())
            if (enriched.studentId.isNotBlank() || (enriched.token.isNotBlank() && enriched.token != activeSession.token)) {
                sessionStore.saveSession(enriched)
                _session.value = enriched
                activeSession = enriched
            }
        }

        val weekRange = apiClient.getWeekRangeForOffset(weekOffset)

        val remoteResult = apiClient.fetchDiary(
            token = activeSession.token,
            vendor = activeSession.vendor,
            studentId = activeSession.studentId.ifBlank { null },
            days = weekRange,
            cookies = sessionStore.getCookies(),
            weekOffset = weekOffset
        )

        return if (remoteResult.isSuccess) {
            remoteResult.getOrThrow()
        } else {
            throw remoteResult.exceptionOrNull()
                ?: Exception("Не удалось загрузить расписание из регионального дневника")
        }
    }

    suspend fun getHomework(weekOffset: Int = 0): List<Homework> {
        val currentSession = _session.value
            ?: throw IllegalStateException("Сессия не найдена. Требуется авторизация.")
        if (currentSession.isDemo) {
            return MockDataProvider.getHomeworkList()
        }

        val schedule = getSchedule(weekOffset)
        val hwList = mutableListOf<Homework>()
        var hwCounter = 1
        for (day in schedule) {
            for (lesson in day.lessons) {
                if (!lesson.homework.isNullOrBlank() || lesson.attachments.isNotEmpty()) {
                    hwList.add(
                        Homework(
                            id = "hw_${day.date}_${lesson.num}_${hwCounter++}",
                            subject = lesson.subject,
                            text = lesson.homework ?: "Материалы к уроку",
                            date = day.date,
                            isCompleted = false,
                            attachments = lesson.attachments
                        )
                    )
                }
            }
        }
        return hwList
    }

    suspend fun getMarks(days: String? = null): List<SubjectSummary> {
        val currentSession = _session.value
            ?: throw IllegalStateException("Сессия не найдена. Требуется авторизация.")
        if (currentSession.isDemo) {
            return MockDataProvider.getSubjectsSummary()
        }

        var activeSession = currentSession
        if (activeSession.studentId.isBlank()) {
            val enriched = apiClient.enrichWithRules(activeSession, sessionStore.getCookies())
            if (enriched.studentId.isNotBlank() || (enriched.token.isNotBlank() && enriched.token != activeSession.token)) {
                sessionStore.saveSession(enriched)
                _session.value = enriched
                activeSession = enriched
            }
        }

        var targetDays = days
        if (targetDays.isNullOrBlank()) {
            val periods = getPeriods()
            targetDays = periods.firstOrNull { it.isActive }?.rangeDays
                ?: periods.firstOrNull()?.rangeDays
        }

        val cookies = sessionStore.getCookies()
        val effectiveStudentId = activeSession.studentId.ifBlank { null }

        // 1. Primary: Scrape full report card from journal-student-grades-action (SSR)
        var lastError: Throwable? = null
        if (!cookies.isNullOrEmpty()) {
            val webMarks = apiClient.scrapeMarksFromWeb(cookies, effectiveStudentId)
            val updatedS = sessionStore.getSession()
            if (updatedS != null && updatedS != _session.value) {
                _session.value = updatedS
            }
            if (webMarks.isSuccess && webMarks.getOrNull()?.isNotEmpty() == true) {
                return webMarks.getOrThrow()
            }
            if (webMarks.exceptionOrNull() is com.pixelschool.app.data.remote.SessionExpiredException) {
                throw webMarks.exceptionOrNull()!!
            }
            lastError = webMarks.exceptionOrNull()
        }

        // 2. Secondary: Official API v3 if authentic token is available
        val marksResult = apiClient.fetchMarks(
            token = activeSession.token,
            vendor = activeSession.vendor,
            studentId = effectiveStudentId,
            days = targetDays,
            cookies = cookies
        )

        if (marksResult.isSuccess && marksResult.getOrNull()?.isNotEmpty() == true) {
            return marksResult.getOrThrow()
        }
        if (marksResult.exceptionOrNull() is com.pixelschool.app.data.remote.SessionExpiredException) {
            throw marksResult.exceptionOrNull()!!
        }

        // 3. Fallback: Aggregate marks from timetable schedule across quarter weeks if web scraping & API v3 both fail/empty
        try {
            val allLessons = mutableListOf<com.pixelschool.app.data.model.Lesson>()
            for (offset in 0..6) {
                try {
                    val weekSched = getSchedule(offset)
                    allLessons.addAll(weekSched.flatMap { it.lessons })
                } catch (_: Exception) {}
            }
            val marksBySubject = allLessons
                .filter { it.marks.isNotEmpty() }
                .groupBy { it.subject }

            if (marksBySubject.isNotEmpty()) {
                val summaries = marksBySubject.map { (subj, lessons) ->
                    val marks = lessons.flatMap { it.marks }.distinctBy { "${it.date}_${it.value}_${it.weight}" }
                    val num = marks.mapNotNull { it.numericValue }
                    val avg = if (num.isNotEmpty()) num.average().toFloat() else 0f
                    SubjectSummary(
                        subject = subj,
                        average = avg,
                        marks = marks,
                        periodMarks = apiClient.groupMarksIntoPeriods(marks)
                    )
                }.sortedByDescending { it.average }

                if (summaries.isNotEmpty()) {
                    return summaries
                }
            }
        } catch (_: Exception) {}

        throw marksResult.exceptionOrNull()
            ?: lastError
            ?: Exception("Не удалось загрузить сводную ведомость оценок")
    }
}
