package com.pixelschool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
import com.pixelschool.app.data.remote.AuthCredentials
import com.pixelschool.app.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: DiaryRepository
) : ViewModel() {

    val session: StateFlow<UserSession?> = repository.session

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _schedule = MutableStateFlow<List<DaySchedule>>(emptyList())
    val schedule: StateFlow<List<DaySchedule>> = _schedule.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(0)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _currentWeekOffset = MutableStateFlow(0)
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
        if (session.value != null) {
            loadData()
        }
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
            var errorToDisplay: String? = null

            try {
                _schedule.value = repository.getSchedule(_currentWeekOffset.value)
                _homework.value = repository.getHomework(_currentWeekOffset.value)
            } catch (e: Exception) {
                if (!curSession.isDemo) {
                    _schedule.value = emptyList()
                    _homework.value = emptyList()
                }
                errorToDisplay = e.message ?: "Не удалось загрузить дневник из региональной системы"
            }

            try {
                val resolvedPeriods = repository.getPeriods()
                _periods.value = resolvedPeriods
                val activeIdx = resolvedPeriods.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
                _selectedPeriodIndex.value = activeIdx

                val activeRange = resolvedPeriods.getOrNull(activeIdx)?.rangeDays
                _marks.value = repository.getMarks(activeRange)
            } catch (e: Exception) {
                if (!curSession.isDemo) {
                    _marks.value = emptyList()
                }
                if (errorToDisplay == null) {
                    errorToDisplay = e.message ?: "Не удалось загрузить оценки"
                }
            }

            _errorMessage.value = errorToDisplay
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
        _currentWeekOffset.value = newOffset
        _selectedDayIndex.value = 0
        viewModelScope.launch {
            val curSession = session.value ?: return@launch
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _schedule.value = repository.getSchedule(newOffset)
                _homework.value = repository.getHomework(newOffset)
            } catch (e: Exception) {
                if (!curSession.isDemo) {
                    _schedule.value = emptyList()
                    _homework.value = emptyList()
                }
                _errorMessage.value = e.message ?: "Не удалось загрузить расписание"
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
                try {
                    val p = _periods.value[index]
                    _marks.value = repository.getMarks(p.rangeDays)
                } catch (e: Exception) {
                    if (!curSession.isDemo) {
                        _marks.value = emptyList()
                    }
                    _errorMessage.value = e.message ?: "Не удалось загрузить оценки"
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

    fun loginWithSession(auth: AuthCredentials?, cookies: String?, userName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _schedule.value = emptyList()
            _homework.value = emptyList()
            _marks.value = emptyList()
            _periods.value = emptyList()

            val res = repository.loginWithSession(auth, cookies, userName)
            if (res.isSuccess) {
                loadData()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Не удалось авторизоваться"
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
        _currentWeekOffset.value = 0
    }
}
