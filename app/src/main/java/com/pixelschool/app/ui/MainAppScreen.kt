package com.pixelschool.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Switch
import com.pixelschool.app.data.local.AppThemePalette
import com.pixelschool.app.data.local.SettingsStore
import com.pixelschool.app.data.model.ProfileUiState
import com.pixelschool.app.data.model.UserSession
import com.pixelschool.app.notification.MarksSyncWorker
import com.pixelschool.app.ui.navigation.Screen
import com.pixelschool.app.ui.screens.AuthScreen
import com.pixelschool.app.ui.screens.HomeworkScreen
import com.pixelschool.app.ui.screens.MarksScreen
import com.pixelschool.app.ui.screens.ScheduleScreen
import com.pixelschool.app.ui.screens.SettingsScreen
import com.pixelschool.app.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    settingsStore: SettingsStore? = null,
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
        var showSettingsScreen by remember { mutableStateOf(false) }
        val navItems = listOf(Screen.Schedule, Screen.Homework, Screen.Marks, Screen.Profile)

        if (showSettingsScreen) {
            val context = LocalContext.current
            val safeSettingsStore = settingsStore ?: remember(context) { SettingsStore(context) }
            val updateInfo by viewModel.updateInfo.collectAsState()
            SettingsScreen(
                settingsStore = safeSettingsStore,
                updateInfo = updateInfo,
                onCheckUpdates = { viewModel.checkForUpdates() },
                onBack = { showSettingsScreen = false }
            )
        } else {
            Scaffold(
            topBar = {
                AppTopBar(
                    title = currentScreen.title
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

                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val pullToRefreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshData() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Crossfade(
                        targetState = currentScreen,
                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                        modifier = Modifier.fillMaxSize(),
                        label = "TabCrossfade"
                    ) { screen ->
                    when (screen) {
                    Screen.Schedule -> {
                        val schedule by viewModel.schedule.collectAsState()
                        val homework by viewModel.homework.collectAsState()
                        val selectedDayIndex by viewModel.selectedDayIndex.collectAsState()
                        val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
                        val currentPalette by (settingsStore?.themePalette ?: remember { MutableStateFlow(AppThemePalette.DYNAMIC) }).collectAsState()
                        ScheduleScreen(
                            schedule = schedule,
                            selectedDayIndex = selectedDayIndex,
                            onSelectDay = { viewModel.selectDay(it) },
                            weekOffset = currentWeekOffset,
                            onChangeWeekOffset = { viewModel.changeWeekOffset(it) },
                            onResetWeekOffset = { viewModel.resetWeekOffset() },
                            homeworkList = homework,
                            palette = currentPalette
                        )
                    }
                    Screen.Homework -> {
                        val homework by viewModel.homework.collectAsState()
                        val schedule by viewModel.schedule.collectAsState()
                        val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
                        val currentPalette by (settingsStore?.themePalette ?: remember { MutableStateFlow(AppThemePalette.DYNAMIC) }).collectAsState()
                        HomeworkScreen(
                            homeworkList = homework,
                            onToggleCompleted = { viewModel.toggleHomework(it) },
                            weekOffset = currentWeekOffset,
                            onChangeWeekOffset = { viewModel.changeWeekOffset(it) },
                            onResetWeekOffset = { viewModel.resetWeekOffset() },
                            schedule = schedule,
                            palette = currentPalette
                        )
                    }
                    Screen.Marks -> {
                        val marks by viewModel.marks.collectAsState()
                        val periods by viewModel.periods.collectAsState()
                        val selectedPeriodIndex by viewModel.selectedPeriodIndex.collectAsState()
                        val schedule by viewModel.schedule.collectAsState()
                        val allDays = remember(schedule) { viewModel.getAllKnownDays() }
                        MarksScreen(
                            subjects = marks,
                            periods = periods,
                            selectedPeriodIndex = selectedPeriodIndex,
                            onSelectPeriod = { viewModel.selectPeriod(it) },
                            allDays = allDays,
                            settingsStore = settingsStore
                        )
                    }
                    Screen.Profile -> {
                        val updateInfo by viewModel.updateInfo.collectAsState()
                        ProfileScreen(
                            profile = profileState,
                            updateInfo = updateInfo,
                            onOpenSettings = { showSettingsScreen = true },
                            onCheckUpdates = { viewModel.checkForUpdates() },
                            onLogout = { viewModel.logout() }
                        )
                    }
                }
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
    updateInfo: com.pixelschool.app.data.remote.AppUpdateInfo? = null,
    onOpenSettings: () -> Unit,
    onCheckUpdates: () -> Unit = {},
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
            .verticalScroll(rememberScrollState())
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

        Spacer(modifier = Modifier.height(28.dp))

        // Settings Button Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Настройки приложения",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Тема, уведомления, ИИ, напоминания о ДЗ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Update Card
        val context = LocalContext.current
        val appVersion = remember(context) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: com.pixelschool.app.BuildConfig.VERSION_NAME
            } catch (_: Exception) {
                com.pixelschool.app.BuildConfig.VERSION_NAME
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (updateInfo?.isUpdateAvailable == true)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (updateInfo?.isUpdateAvailable == true)
                            "Доступно обновление v${updateInfo.latestVersion}"
                        else
                            "Версия приложения: $appVersion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (updateInfo?.isUpdateAvailable == true)
                            "Новый стабильный релиз на GitHub"
                        else
                            "Установлена актуальная версия",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (updateInfo?.isUpdateAvailable == true) {
                    Button(
                        onClick = {
                            val url = updateInfo.apkDownloadUrl ?: updateInfo.releaseUrl
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Обновить",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    IconButton(onClick = onCheckUpdates) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Проверить",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = TopAppBarDefaults.windowInsets
    )
}
