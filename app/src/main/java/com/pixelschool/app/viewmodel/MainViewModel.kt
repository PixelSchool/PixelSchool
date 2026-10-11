package com.pixelschool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.ErrorUiState
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.ProfileUiState
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
import com.pixelschool.app.data.remote.AuthCredentials
import com.pixelschool.app.data.remote.SessionExpiredException
import com.pixelschool.app.data.repository.DiaryRepository
import com.pixelschool.app.ui.screens.HomeworkDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(
    private val repository: DiaryRepository
) : ViewModel() {

    val session: StateFlow<UserSession?> = repository.session

    private val _profileState = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _errorState = MutableStateFlow<ErrorUiState?>(null)
    val errorState: StateFlow<ErrorUiState?> = _errorState.asStateFlow()

    private val _schedule = MutableStateFlow<List<DaySchedule>>(emptyList())
    val schedule: StateFlow<List<DaySchedule>> = _schedule.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(HomeworkDateUtils.calculateDefaultDayIndex())
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _currentWeekOffset = MutableStateFlow(HomeworkDateUtils.calculateDefaultWeekOffset())
    val currentWeekOffset: StateFlow<Int> = _currentWeekOffset.asStateFlow()

    private val _homework = MutableStateFlow<List<Homework>>(emptyList())
    val homework: StateFlow<List<Homework>> = _homework.asStateFlow()

    private val _periods = MutableStateFlow<List<AcademicPeriod>>(emptyList())
    val periods: StateFlow<List<AcademicPeriod>> = _periods.asStateFlow()

    private val _selectedPeriodIndex = MutableStateFlow(0)
    val selectedPeriodIndex: StateFlow<Int> = _selectedPeriodIndex.asStateFlow()

    private val _marks = MutableStateFlow<List<SubjectSummary>>(emptyList())
    val marks: StateFlow<List<SubjectSummary>> = _marks.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { s ->
                updateProfile(s)
            }
        }
        if (session.value != null) {
            loadData()
        }
    }

    private fun updateProfile(s: UserSession?) {
        if (s == null) {
            _profileState.value = ProfileUiState()
        } else {
            val full = s.userTitle.takeIf { it.isNotBlank() && it != "Ученик" }
                ?: if (s.lastName.isNotBlank() || s.firstName.isNotBlank()) "${s.lastName} ${s.firstName}".trim() else "Ученик"
            val school = s.schoolName.takeIf { it.isNotBlank() && it != "Республика Крым" && it != "ГИС РК «Образование»" }
                ?: s.schoolTitle.takeIf { it.isNotBlank() && it != "ГИС РК «Образование»" }
                ?: "Республика Крым"
            _profileState.value = ProfileUiState(
                fullName = full,
                firstName = s.firstName,
                lastName = s.lastName,
                schoolName = school,
                studentId = s.studentId,
                login = s.login,
                isDemo = s.isDemo
            )
        }
    }

    fun clearError() {
        _errorState.value = null
        _errorMessage.value = null
    }

    private fun Throwable.toErrorUiState(fallbackMessage: String): ErrorUiState {
        val msg = this.message ?: fallbackMessage
        val code = when {
            msg.contains("HTTP 404") -> "HTTP 404"
            msg.contains("HTTP 401") -> "HTTP 401"
            msg.contains("HTTP 403") -> "HTTP 403"
            msg.contains("HTTP 500") -> "HTTP 500"
            msg.contains("HTTP 502") -> "HTTP 502"
            msg.contains("HTTP 503") -> "HTTP 503"
            msg.contains("HTTP ") -> Regex("""HTTP \d+""").find(msg)?.value
            else -> null
        }
        val details = when (this) {
            is java.net.SocketTimeoutException -> "java.net.SocketTimeoutException: Превышено время ожидания сервера"
            is java.net.UnknownHostException -> "java.net.UnknownHostException: Нет соединения с сетью"
            is java.io.IOException -> "java.io.IOException: Ошибка сетевого соединения"
            else -> this::class.java.simpleName + (if (!this.message.isNullOrBlank()) ": ${this.message}" else "")
        }
        return ErrorUiState(
            message = fallbackMessage.ifBlank { msg },
            errorCode = code,
            errorDetails = details
        )
    }

    fun loadData() {
        viewModelScope.launch {
            val curSession = session.value
            if (curSession == null) {
                _schedule.value = emptyList()
                _homework.value = emptyList()
                _marks.value = emptyList()
                _periods.value = emptyList()
                _isLoading.value = false
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null
            _errorState.value = null
            var errorToDisplay: String? = null
            var errorUiToDisplay: ErrorUiState? = null

            // Restore/verify student profile (name and school) if not fully loaded
            try {
                repository.ensureProfileLoaded()
            } catch (_: Exception) {}

            try {
                _schedule.value = repository.getSchedule(_currentWeekOffset.value)
                _homework.value = repository.getHomework(_currentWeekOffset.value)
            } catch (e: Exception) {
                if (e is SessionExpiredException) {
                    repository.logout()
                    _errorMessage.value = e.message ?: "Сессия устарела. Требуется повторный вход."
                    _errorState.value = e.toErrorUiState("Сессия устарела. Требуется повторный вход.")
                    _isLoading.value = false
                    return@launch
                }
                if (!curSession.isDemo) {
                    _schedule.value = emptyList()
                    _homework.value = emptyList()
                }
                errorToDisplay = e.message ?: "Не удалось загрузить дневник из региональной системы"
                errorUiToDisplay = e.toErrorUiState("Не удалось загрузить дневник")
            }

            try {
                val resolvedPeriods = repository.getPeriods()
                _periods.value = resolvedPeriods

                val nowMonth = Calendar.getInstance().get(Calendar.MONTH) // 8=Sep, 9=Oct, 10=Nov
                val defaultIdx = if (nowMonth in 8..10) 0 else {
                    resolvedPeriods.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
                }
                _selectedPeriodIndex.value = defaultIdx

                val activeRange = resolvedPeriods.getOrNull(defaultIdx)?.rangeDays
                _marks.value = repository.getMarks(activeRange)
            } catch (e: Exception) {
                if (e is SessionExpiredException) {
                    repository.logout()
                    _errorMessage.value = e.message ?: "Сессия устарела. Требуется повторный вход."
                    _errorState.value = e.toErrorUiState("Сессия устарела. Требуется повторный вход.")
                    _isLoading.value = false
                    return@launch
                }
                if (!curSession.isDemo) {
                    _marks.value = emptyList()
                }
                if (errorToDisplay == null) {
                    errorToDisplay = e.message ?: "Не удалось загрузить оценки"
                    errorUiToDisplay = e.toErrorUiState("Не удалось загрузить оценки")
                }
            }

            _errorMessage.value = errorToDisplay
            _errorState.value = errorUiToDisplay
            _isLoading.value = false
        }
    }

    fun selectDay(index: Int) {
        if (index in 0 until _schedule.value.size) {
            _selectedDayIndex.value = index
        }
    }

    fun changeWeekOffset(offsetDelta: Int) {
        val newOffset = _currentWeekOffset.value + offsetDelta
        if (offsetDelta < 0) {
            _selectedDayIndex.value = 0
        }
        setWeekOffset(newOffset)
    }

    fun resetWeekOffset() {
        val defaultOffset = HomeworkDateUtils.calculateDefaultWeekOffset()
        val defaultDay = HomeworkDateUtils.calculateDefaultDayIndex()
        _selectedDayIndex.value = defaultDay
        if (_currentWeekOffset.value != defaultOffset) {
            setWeekOffset(defaultOffset)
        }
    }

    fun setWeekOffset(newOffset: Int) {
        _currentWeekOffset.value = newOffset
        viewModelScope.launch {
            val curSession = session.value ?: return@launch
            _isLoading.value = true
            _errorMessage.value = null
            _errorState.value = null
            try {
                _schedule.value = repository.getSchedule(newOffset)
                _homework.value = repository.getHomework(newOffset)
            } catch (e: Exception) {
                if (e is SessionExpiredException) {
                    repository.logout()
                    _errorMessage.value = e.message ?: "Сессия устарела. Требуется повторный вход."
                    _errorState.value = e.toErrorUiState("Сессия устарела. Требуется повторный вход.")
                    return@launch
                }
                if (!curSession.isDemo) {
                    _schedule.value = emptyList()
                    _homework.value = emptyList()
                }
                _errorMessage.value = e.message ?: "Не удалось загрузить данные"
                _errorState.value = e.toErrorUiState("Не удалось загрузить данные")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectPeriod(index: Int) {
        if (index in 0 until _periods.value.size) {
            _selectedPeriodIndex.value = index
            viewModelScope.launch {
                val curSession = session.value ?: return@launch
                _isLoading.value = true
                _errorMessage.value = null
                _errorState.value = null
                try {
                    val p = _periods.value[index]
                    _marks.value = repository.getMarks(p.rangeDays)
                } catch (e: Exception) {
                    if (e is SessionExpiredException) {
                        repository.logout()
                        _errorMessage.value = e.message ?: "Сессия устарела. Требуется повторный вход."
                        _errorState.value = e.toErrorUiState("Сессия устарела. Требуется повторный вход.")
                        return@launch
                    }
                    if (!curSession.isDemo) {
                        _marks.value = emptyList()
                    }
                    _errorMessage.value = e.message ?: "Не удалось загрузить оценки"
                    _errorState.value = e.toErrorUiState("Не удалось загрузить оценки")
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    fun toggleHomework(id: String) {
        _homework.value = _homework.value.map { hw ->
            if (hw.id == id) hw.copy(isCompleted = !hw.isCompleted) else hw
        }
    }

    fun loginDemo() {
        repository.loginAsDemo()
        loadData()
    }

    fun loginWithSession(
        auth: AuthCredentials?,
        cookies: String?,
        userName: String? = null,
        schoolName: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _errorState.value = null
            _schedule.value = emptyList()
            _homework.value = emptyList()
            _marks.value = emptyList()
            _periods.value = emptyList()

            val res = repository.loginWithSession(auth, cookies, userName, schoolName)
            if (res.isSuccess) {
                loadData()
            } else {
                val ex = res.exceptionOrNull() ?: Exception("Не удалось авторизоваться")
                _errorMessage.value = ex.message ?: "Не удалось авторизоваться"
                _errorState.value = ex.toErrorUiState("Не удалось авторизоваться")
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        repository.logout()
        _schedule.value = emptyList()
        _homework.value = emptyList()
        _marks.value = emptyList()
        _periods.value = emptyList()
        _currentWeekOffset.value = HomeworkDateUtils.calculateDefaultWeekOffset()
        _selectedDayIndex.value = HomeworkDateUtils.calculateDefaultDayIndex()
    }
}
