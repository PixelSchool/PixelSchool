package com.pixelschool.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "Расписание", Icons.Rounded.CalendarMonth)
    object Homework : Screen("homework", "Задания", Icons.Rounded.Assignment)
    object Marks : Screen("marks", "Оценки", Icons.Rounded.Grade)
    object Profile : Screen("profile", "Профиль", Icons.Rounded.Person)
}
