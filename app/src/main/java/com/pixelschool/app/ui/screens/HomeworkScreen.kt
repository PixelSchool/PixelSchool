package com.pixelschool.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
import com.pixelschool.app.ui.components.HomeworkExpressiveCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class HomeworkStatusFilter {
    ALL, PENDING, COMPLETED
}

object HomeworkDateUtils {
    val russianMonthNamesGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    fun calculateDefaultWeekOffset(now: LocalDate = LocalDate.now()): Int {
        return if (now.dayOfWeek == DayOfWeek.SUNDAY) 1 else 0
    }

    fun calculateDefaultDayIndex(now: LocalDate = LocalDate.now()): Int {
        return when (now.dayOfWeek) {
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.FRIDAY -> 5
            else -> (now.plusDays(1).dayOfWeek.value - 1).coerceIn(0, 5)
        }
    }

    fun getMondayForOffset(weekOffset: Int, now: LocalDate = LocalDate.now()): LocalDate {
        val currentMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return currentMonday.plusWeeks(weekOffset.toLong())
    }

    fun formatWeekRange(monday: LocalDate): String {
        val saturday = monday.plusDays(5)
        val mStart = monday.monthValue - 1
        val mEnd = saturday.monthValue - 1
        return if (monday.monthValue == saturday.monthValue) {
            "${monday.dayOfMonth} — ${saturday.dayOfMonth} ${russianMonthNamesGenitive[mEnd]}"
        } else {
            "${monday.dayOfMonth} ${russianMonthNamesGenitive[mStart]} — ${saturday.dayOfMonth} ${russianMonthNamesGenitive[mEnd]}"
        }
    }

    fun getDayShortLabel(index: Int): String = when (index) {
        0 -> "Пн"
        1 -> "Вт"
        2 -> "Ср"
        3 -> "Чт"
        4 -> "Пт"
        5 -> "Сб"
        else -> ""
    }

    fun getDayFullLabel(index: Int): String = when (index) {
        0 -> "Понедельник"
        1 -> "Вторник"
        2 -> "Среда"
        3 -> "Четверг"
        4 -> "Пятница"
        5 -> "Суббота"
        else -> ""
    }

    fun getMonthGenitive(month: Int): String {
        return russianMonthNamesGenitive.getOrElse(month - 1) { "" }
    }

    fun matchesDay(hw: Homework, index: Int, monday: LocalDate, schedule: List<DaySchedule>? = null): Boolean {
        val dayDate = monday.plusDays(index.toLong())
        val dateFull = String.format(Locale.US, "%02d.%02d.%04d", dayDate.dayOfMonth, dayDate.monthValue, dayDate.year)
        val dateShort = String.format(Locale.US, "%02d.%02d", dayDate.dayOfMonth, dayDate.monthValue)
        val dateIso = dayDate.toString()
        val scheduleDate = schedule?.getOrNull(index)?.date

        val hwClean = hw.date.trim()
        if (!scheduleDate.isNullOrBlank()) {
            if (hwClean == scheduleDate || hwClean.startsWith(scheduleDate.take(5))) {
                return true
            }
        }

        return hwClean == dateFull ||
                hwClean.startsWith(dateShort) ||
                hwClean == dateIso ||
                (hwClean.contains(".") && hwClean.take(5) == dateShort)
    }
}

@Composable
fun HomeworkScreen(
    homeworkList: List<Homework>,
    onToggleCompleted: (String) -> Unit,
    weekOffset: Int = 0,
    onChangeWeekOffset: (Int) -> Unit = {},
    onResetWeekOffset: () -> Unit = {},
    schedule: List<DaySchedule> = emptyList(),
    modifier: Modifier = Modifier
) {
    val monday = remember(weekOffset) {
        HomeworkDateUtils.getMondayForOffset(weekOffset)
    }

    var selectedDayIndex by remember {
        mutableIntStateOf(HomeworkDateUtils.calculateDefaultDayIndex())
    }

    var statusFilter by remember { mutableStateOf(HomeworkStatusFilter.ALL) }

    val dayHomeworkList = remember(homeworkList, selectedDayIndex, monday, schedule) {
        homeworkList.filter { HomeworkDateUtils.matchesDay(it, selectedDayIndex, monday, schedule) }
    }

    val finalFilteredList = when (statusFilter) {
        HomeworkStatusFilter.ALL -> dayHomeworkList
        HomeworkStatusFilter.PENDING -> dayHomeworkList.filter { !it.isCompleted }
        HomeworkStatusFilter.COMPLETED -> dayHomeworkList.filter { it.isCompleted }
    }

    val completedCount = dayHomeworkList.count { it.isCompleted }
    val totalCount = dayHomeworkList.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    val selectedDayDate = monday.plusDays(selectedDayIndex.toLong())
    val selectedDayFullName = HomeworkDateUtils.getDayFullLabel(selectedDayIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Week Navigator Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onChangeWeekOffset(-1) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "Предыдущая неделя",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    onResetWeekOffset()
                    selectedDayIndex = HomeworkDateUtils.calculateDefaultDayIndex()
                }
            ) {
                Text(
                    text = HomeworkDateUtils.formatWeekRange(monday),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (weekOffset) {
                        0 -> "Текущая неделя"
                        1 -> "Следующая неделя"
                        -1 -> "Предыдущая неделя"
                        else -> if (weekOffset > 0) "+$weekOffset нед." else "$weekOffset нед."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultOffset = HomeworkDateUtils.calculateDefaultWeekOffset()
                val isDefaultActive = weekOffset == defaultOffset && selectedDayIndex == HomeworkDateUtils.calculateDefaultDayIndex()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDefaultActive)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        onResetWeekOffset()
                        selectedDayIndex = HomeworkDateUtils.calculateDefaultDayIndex()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Today,
                            contentDescription = null,
                            tint = if (isDefaultActive)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Сегодня",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDefaultActive)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                IconButton(
                    onClick = { onChangeWeekOffset(1) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Следующая неделя",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Expressive Day Selector Tabs (Mon-Sat, strictly one day focused)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            (0..5).forEach { index ->
                val isSelected = index == selectedDayIndex
                val dayDate = monday.plusDays(index.toLong())
                val countForDay = homeworkList.count { HomeworkDateUtils.matchesDay(it, index, monday, schedule) }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.clickable { selectedDayIndex = index }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = HomeworkDateUtils.getDayShortLabel(index),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${dayDate.dayOfMonth}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            if (countForDay > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                    else
                                        MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "$countForDay",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Summary Card for selected day
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedDayFullName, ${selectedDayDate.dayOfMonth} ${HomeworkDateUtils.getMonthGenitive(selectedDayDate.monthValue)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$completedCount из $totalCount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = statusFilter == HomeworkStatusFilter.ALL,
                onClick = { statusFilter = HomeworkStatusFilter.ALL },
                label = { Text("Все ($totalCount)") },
                shape = RoundedCornerShape(14.dp)
            )
            FilterChip(
                selected = statusFilter == HomeworkStatusFilter.PENDING,
                onClick = { statusFilter = HomeworkStatusFilter.PENDING },
                label = { Text("Осталось (${totalCount - completedCount})") },
                shape = RoundedCornerShape(14.dp)
            )
            FilterChip(
                selected = statusFilter == HomeworkStatusFilter.COMPLETED,
                onClick = { statusFilter = HomeworkStatusFilter.COMPLETED },
                label = { Text("Сделано ($completedCount)") },
                shape = RoundedCornerShape(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Homework list for the strictly selected day
        AnimatedContent(
            targetState = finalFilteredList,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
            label = "homework_day_list_anim"
        ) { itemsList ->
            if (itemsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AssignmentTurnedIn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(20.dp)
                                    .size(48.dp)
                            )
                        }
                        Text(
                            text = if (totalCount == 0) "На этот день заданий нет" else "Все задания выполнены!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (totalCount == 0) "Отличная работа! Можно отдохнуть" else "Вы справились со всеми заданиями на этот день",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(itemsList, key = { it.id }) { hw ->
                        HomeworkExpressiveCard(
                            homework = hw,
                            onToggleCompleted = onToggleCompleted
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
