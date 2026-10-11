package com.pixelschool.app.data.repository

import com.pixelschool.app.data.local.MockDataProvider
import com.pixelschool.app.data.local.SessionStore
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
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

    suspend fun loginWithWebSession(
        tokenOrVToken: String?,
        cookies: String?,
        fallbackUserName: String? = null
    ): Result<UserSession> {
        val result = apiClient.resolveSession(tokenOrVToken, cookies, fallbackUserName)
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

    suspend fun getSchedule(): List<DaySchedule> {
        val currentSession = _session.value ?: return MockDataProvider.getWeekSchedule()
        if (currentSession.isDemo) {
            return MockDataProvider.getWeekSchedule()
        }

        val remoteResult = apiClient.fetchDiary(
            token = currentSession.token,
            vendor = currentSession.vendor,
            cookies = sessionStore.getCookies()
        )

        return if (remoteResult.isSuccess && remoteResult.getOrNull()?.isNotEmpty() == true) {
            remoteResult.getOrThrow()
        } else {
            MockDataProvider.getWeekSchedule()
        }
    }

    suspend fun getHomework(): List<Homework> {
        val currentSession = _session.value ?: return MockDataProvider.getHomeworkList()
        if (currentSession.isDemo) {
            return MockDataProvider.getHomeworkList()
        }

        val schedule = getSchedule()
        val hwList = mutableListOf<Homework>()
        var hwCounter = 1
        for (day in schedule) {
            for (lesson in day.lessons) {
                if (!lesson.homework.isNullOrBlank()) {
                    hwList.add(
                        Homework(
                            id = "hw_${hwCounter++}",
                            subject = lesson.subject,
                            text = lesson.homework,
                            date = day.date,
                            isCompleted = false
                        )
                    )
                }
            }
        }
        return if (hwList.isNotEmpty()) hwList else MockDataProvider.getHomeworkList()
    }

    suspend fun getMarks(): List<SubjectSummary> {
        val currentSession = _session.value ?: return MockDataProvider.getSubjectsSummary()
        if (currentSession.isDemo) {
            return MockDataProvider.getSubjectsSummary()
        }

        val schedule = getSchedule()
        val subjectMarksMap = mutableMapOf<String, MutableList<com.pixelschool.app.data.model.Mark>>()
        for (day in schedule) {
            for (lesson in day.lessons) {
                if (lesson.marks.isNotEmpty()) {
                    val list = subjectMarksMap.getOrPut(lesson.subject) { mutableListOf() }
                    list.addAll(lesson.marks)
                }
            }
        }

        if (subjectMarksMap.isEmpty()) {
            return MockDataProvider.getSubjectsSummary()
        }

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
}
