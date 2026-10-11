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
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        return if (now.dayOfWeek == DayOfWeek.SUNDAY) -1 else 0
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
        return currentMonday.plusWeeks(-weekOffset.toLong())
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
            .padding(top = 12.dp)
    ) {
        // Week Navigator Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onChangeWeekOffset(1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "Предыдущая неделя",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .height(30.dp)
                    .clickable {
                        onResetWeekOffset()
                        selectedDayIndex = HomeworkDateUtils.calculateDefaultDayIndex()
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = HomeworkDateUtils.formatWeekRange(monday),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = {
                    onChangeWeekOffset(-1)
                    selectedDayIndex = 0
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = "Следующая неделя",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Expressive Day Selector Tabs (Mon-Sat, strictly one day focused)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (0..5).forEach { index ->
                val isSelected = index == selectedDayIndex
                val dayDate = monday.plusDays(index.toLong())
                val countForDay = homeworkList.count { HomeworkDateUtils.matchesDay(it, index, monday, schedule) }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .height(48.dp)
                        .clickable { selectedDayIndex = index }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = HomeworkDateUtils.getDayShortLabel(index),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${dayDate.dayOfMonth}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        if (countForDay > 0) {
                            Badge(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "$countForDay",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Progress Line for selected day
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$selectedDayFullName, ${selectedDayDate.dayOfMonth} ${HomeworkDateUtils.getMonthGenitive(selectedDayDate.monthValue)} • $completedCount из $totalCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // M3 Canonical SingleChoiceSegmentedButtonRow
        val filterOptions = listOf(
            "Все ($totalCount)",
            "Осталось (${totalCount - completedCount})",
            "Сделано ($completedCount)"
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            filterOptions.forEachIndexed { index, label ->
                val filter = when (index) {
                    0 -> HomeworkStatusFilter.ALL
                    1 -> HomeworkStatusFilter.PENDING
                    else -> HomeworkStatusFilter.COMPLETED
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filterOptions.size),
                    onClick = { statusFilter = filter },
                    selected = statusFilter == filter,
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
