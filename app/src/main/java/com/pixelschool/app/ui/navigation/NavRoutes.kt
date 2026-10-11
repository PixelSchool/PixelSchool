package com.pixelschool.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    Schedule("Расписание", Icons.Rounded.CalendarMonth),
    Homework("Задания", Icons.AutoMirrored.Rounded.Assignment),
    Marks("Оценки", Icons.Rounded.Grade),
    Profile("Профиль", Icons.Rounded.Person)
}

