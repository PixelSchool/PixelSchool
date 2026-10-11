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
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            var errorToDisplay: String? = null

            try {
                _schedule.value = repository.getSchedule(_currentWeekOffset.value)
            } catch (e: Exception) {
                errorToDisplay = e.message
            }

            try {
                _homework.value = repository.getHomework(_currentWeekOffset.value)
            } catch (e: Exception) {
                if (errorToDisplay == null) errorToDisplay = e.message
            }

            try {
                val resolvedPeriods = repository.getPeriods()
                _periods.value = resolvedPeriods
                val activeIdx = resolvedPeriods.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
                _selectedPeriodIndex.value = activeIdx

                val activeRange = resolvedPeriods.getOrNull(activeIdx)?.rangeDays
                _marks.value = repository.getMarks(activeRange)
            } catch (e: Exception) {
                if (errorToDisplay == null) errorToDisplay = e.message
            }

            if (_schedule.value.isEmpty() && _homework.value.isEmpty() && _marks.value.isEmpty()) {
                _errorMessage.value = errorToDisplay ?: "Не удалось загрузить данные из региональной системы"
            } else {
                _errorMessage.value = null
            }
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
            _isLoading.value = true
            try {
                _schedule.value = repository.getSchedule(newOffset)
                _homework.value = repository.getHomework(newOffset)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectPeriod(index: Int) {
        if (index in 0 until _periods.value.size) {
            _selectedPeriodIndex.value = index
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val p = _periods.value[index]
                    _marks.value = repository.getMarks(p.rangeDays)
                } catch (e: Exception) {
                    _errorMessage.value = e.message
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
