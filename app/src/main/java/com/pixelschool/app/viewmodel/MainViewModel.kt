package com.pixelschool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
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

    private val _homework = MutableStateFlow<List<Homework>>(emptyList())
    val homework: StateFlow<List<Homework>> = _homework.asStateFlow()

    private val _marks = MutableStateFlow<List<SubjectSummary>>(emptyList())
    val marks: StateFlow<List<SubjectSummary>> = _marks.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _schedule.value = repository.getSchedule()
                _homework.value = repository.getHomework()
                _marks.value = repository.getMarks()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Ошибка загрузки данных"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectDay(index: Int) {
        if (index in 0 until _schedule.value.size) {
            _selectedDayIndex.value = index
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

    fun loginWithVToken(vToken: String, cookies: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val res = repository.loginWithVToken(vToken, cookies)
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
    }
}
