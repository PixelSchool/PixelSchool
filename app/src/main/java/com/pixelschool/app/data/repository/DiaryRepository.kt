package com.pixelschool.app.data.repository

import com.pixelschool.app.data.local.MockDataProvider
import com.pixelschool.app.data.local.SessionStore
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.Mark
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
        fallbackUserName: String? = null
    ): Result<UserSession> {
        val result = apiClient.resolveSession(auth, cookies, fallbackUserName)
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

    suspend fun getPeriods(): List<AcademicPeriod> {
        val currentSession = _session.value ?: return MockDataProvider.getAcademicPeriods()
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
        val currentSession = _session.value ?: return MockDataProvider.getWeekSchedule()
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
            cookies = sessionStore.getCookies()
        )

        return if (remoteResult.isSuccess) {
            remoteResult.getOrThrow()
        } else {
            throw remoteResult.exceptionOrNull()
                ?: Exception("Не удалось загрузить расписание из регионального дневника")
        }
    }

    suspend fun getHomework(weekOffset: Int = 0): List<Homework> {
        val currentSession = _session.value ?: return MockDataProvider.getHomeworkList()
        if (currentSession.isDemo) {
            return MockDataProvider.getHomeworkList()
        }

        val schedule = getSchedule(weekOffset)
        val hwList = mutableListOf<Homework>()
        var hwCounter = 1
        for (day in schedule) {
            for (lesson in day.lessons) {
                if (!lesson.homework.isNullOrBlank()) {
                    hwList.add(
                        Homework(
                            id = "hw_${day.date}_${lesson.num}_${hwCounter++}",
                            subject = lesson.subject,
                            text = lesson.homework,
                            date = day.date,
                            isCompleted = false
                        )
                    )
                }
            }
        }
        return hwList
    }

    suspend fun getMarks(days: String? = null): List<SubjectSummary> {
        val currentSession = _session.value ?: return MockDataProvider.getSubjectsSummary()
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

        // Try getting official term marks first
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

        // Fallback: aggregate marks from loaded schedule lessons
        val schedule = try { getSchedule() } catch (_: Exception) { emptyList() }
        val subjectMarksMap = mutableMapOf<String, MutableList<Mark>>()
        for (day in schedule) {
            for (lesson in day.lessons) {
                if (lesson.marks.isNotEmpty()) {
                    val list = subjectMarksMap.getOrPut(lesson.subject) { mutableListOf() }
                    list.addAll(lesson.marks)
                }
            }
        }

        if (subjectMarksMap.isNotEmpty()) {
            return subjectMarksMap.map { (subject, marks) ->
                val numericValues = marks.mapNotNull { it.numericValue }
                val avg = if (numericValues.isNotEmpty()) numericValues.average().toFloat() else 0f
                SubjectSummary(
                    subject = subject,
                    average = avg,
                    marks = marks
                )
            }.sortedByDescending { it.average }
        }

        // Fallback: web scraping
        if (!cookies.isNullOrEmpty()) {
            val webMarks = apiClient.scrapeMarksFromWeb(cookies, effectiveStudentId)
            if (webMarks.isSuccess && webMarks.getOrNull()?.isNotEmpty() == true) {
                return webMarks.getOrThrow()
            }
        }

        return emptyList()
    }
}
