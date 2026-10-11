package com.pixelschool.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixelschool.app.data.model.ProfileUiState
import com.pixelschool.app.data.model.UserSession
import com.pixelschool.app.ui.navigation.Screen
import com.pixelschool.app.ui.screens.AuthScreen
import com.pixelschool.app.ui.screens.HomeworkScreen
import com.pixelschool.app.ui.screens.MarksScreen
import com.pixelschool.app.ui.screens.ScheduleScreen
import com.pixelschool.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.session.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()

    if (session == null) {
        AuthScreen(
            onLoginDemo = { viewModel.loginDemo() },
            onLoginWithSession = { auth, cookies, user, school -> viewModel.loginWithSession(auth, cookies, user, school) },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    } else {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Schedule) }
        val navItems = listOf(Screen.Schedule, Screen.Homework, Screen.Marks, Screen.Profile)

        Scaffold(
            topBar = {
                AppTopBar(
                    title = currentScreen.title,
                    onRefresh = if (currentScreen != Screen.Profile) { { viewModel.loadData() } } else null
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    navItems.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (errorState != null || !errorMessage.isNullOrBlank()) {
                    val errMessage = errorState?.message ?: errorMessage ?: "Ошибка загрузки данных"
                    val errCode = errorState?.errorCode
                    val errDetails = errorState?.errorDetails

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }

                                if (!errCode.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = errCode,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onError,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (!errDetails.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errDetails,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                Crossfade(
                    targetState = currentScreen,
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                when (screen) {
                    Screen.Schedule -> {
                        val schedule by viewModel.schedule.collectAsState()
                        val selectedDayIndex by viewModel.selectedDayIndex.collectAsState()
                        val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
                        ScheduleScreen(
                            schedule = schedule,
                            selectedDayIndex = selectedDayIndex,
                            onSelectDay = { viewModel.selectDay(it) },
                            weekOffset = currentWeekOffset,
                            onChangeWeekOffset = { viewModel.changeWeekOffset(it) },
                            onResetWeekOffset = { viewModel.resetWeekOffset() }
                        )
                    }
                    Screen.Homework -> {
                        val homework by viewModel.homework.collectAsState()
                        val schedule by viewModel.schedule.collectAsState()
                        val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
                        HomeworkScreen(
                            homeworkList = homework,
                            onToggleCompleted = { viewModel.toggleHomework(it) },
                            weekOffset = currentWeekOffset,
                            onChangeWeekOffset = { viewModel.changeWeekOffset(it) },
                            onResetWeekOffset = { viewModel.resetWeekOffset() },
                            schedule = schedule
                        )
                    }
                    Screen.Marks -> {
                        val marks by viewModel.marks.collectAsState()
                        val periods by viewModel.periods.collectAsState()
                        val selectedPeriodIndex by viewModel.selectedPeriodIndex.collectAsState()
                        MarksScreen(
                            subjects = marks,
                            periods = periods,
                            selectedPeriodIndex = selectedPeriodIndex,
                            onSelectPeriod = { viewModel.selectPeriod(it) }
                        )
                    }
                    Screen.Profile -> {
                        ProfileScreen(
                            profile = profileState,
                            onLogout = { viewModel.logout() }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun ProfileScreen(
    profile: ProfileUiState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = profile.fullName.ifBlank {
        if (profile.lastName.isNotBlank() || profile.firstName.isNotBlank()) {
            "${profile.lastName} ${profile.firstName}".trim()
        } else {
            "Ученик"
        }
    }
    val displaySchool = profile.schoolName.ifBlank { "Республика Крым" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = displaySchool,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (profile.isDemo) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "Активен демо-режим",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "О системе",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ГИС в сфере образования (ЭлЖур Крым)\nedu.rk.gov.ru",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Дизайн: Material 3 Expressive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Выйти из аккаунта",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onRefresh: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = TopAppBarDefaults.windowInsets
    )
}
