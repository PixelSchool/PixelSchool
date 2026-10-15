package com.pixelschool.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import kotlin.math.ceil
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.model.PeriodMarks
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.local.SettingsStore
import com.pixelschool.app.data.ai.AiStudyAssistant
import com.pixelschool.app.data.ai.AiStudyResult
import com.pixelschool.app.data.ai.ProblemLesson
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import com.pixelschool.app.ui.components.ExpressiveGradeBadge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksScreen(
    subjects: List<SubjectSummary>,
    periods: List<AcademicPeriod> = emptyList(),
    selectedPeriodIndex: Int = 0,
    onSelectPeriod: (Int) -> Unit = {},
    allDays: List<DaySchedule> = emptyList(),
    settingsStore: SettingsStore? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedSubject by remember { mutableStateOf<SubjectSummary?>(null) }
    var showGpaAnalysis by remember { mutableStateOf(false) }
    var studySubject by remember { mutableStateOf<SubjectSummary?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val periodTitles = remember(periods) {
        if (periods.isNotEmpty()) {
            periods.map { it.title }
        } else {
            listOf("1 четверть", "2 четверть", "3 четверть", "4 четверть")
        }
    }
    val allTabs = remember(periodTitles) { periodTitles + "За год" }

    var selectedTabIndex by remember {
        mutableIntStateOf(selectedPeriodIndex.coerceIn(0, allTabs.lastIndex))
    }

    val isYearTab = selectedTabIndex == allTabs.lastIndex

    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex.coerceIn(0, allTabs.lastIndex)
    ) { allTabs.size }

    LaunchedEffect(selectedPeriodIndex) {
        if (selectedPeriodIndex in periodTitles.indices && selectedTabIndex != selectedPeriodIndex && !pagerState.isScrollInProgress) {
            selectedTabIndex = selectedPeriodIndex
            pagerState.scrollToPage(selectedPeriodIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (selectedTabIndex != settled) {
                selectedTabIndex = settled
                if (settled < periodTitles.size) {
                    onSelectPeriod(settled)
                }
            }
        }
    }

    fun getSubjectsForTab(tabIndex: Int): List<SubjectSummary> {
        val isYear = tabIndex == allTabs.lastIndex
        return if (isYear) {
            subjects.map { s ->
                s.copy(
                    marks = s.marks.sortedOldestFirst(),
                    periodMarks = s.periodMarks.map { pm ->
                        pm.copy(marks = pm.marks.sortedOldestFirst())
                    }
                )
            }
        } else {
            subjects.map { s ->
                if (s.periodMarks.isNotEmpty() && tabIndex < s.periodMarks.size) {
                    val pm = s.periodMarks[tabIndex]
                    s.copy(
                        average = pm.average,
                        marks = pm.marks.sortedOldestFirst(),
                        finalMark = pm.finalMark,
                        periodMarks = s.periodMarks.map { it.copy(marks = it.marks.sortedOldestFirst()) }
                    )
                } else {
                    s.copy(
                        average = 0f,
                        marks = emptyList(),
                        finalMark = null,
                        periodMarks = s.periodMarks.map { it.copy(marks = it.marks.sortedOldestFirst()) }
                    )
                }
            }
        }
    }

    val currentDisplayedSubjects = remember(subjects, selectedTabIndex) {
        getSubjectsForTab(selectedTabIndex)
    }

    val currentOverallAverage = remember(currentDisplayedSubjects) {
        val avgs = currentDisplayedSubjects.map { it.average }.filter { it > 0 }
        if (avgs.isNotEmpty()) avgs.average().toFloat() else 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp)
    ) {
        // Canonical M3 Grades Tabs with edgePadding = 16.dp
        GradesTabRow(
            selectedTabIndex = selectedTabIndex,
            tabs = allTabs,
            onTabSelected = { index ->
                selectedTabIndex = index
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
                if (index < periodTitles.size) {
                    onSelectPeriod(index)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageSubjects = remember(subjects, pageIndex) { getSubjectsForTab(pageIndex) }
            val pageIsYear = pageIndex == allTabs.lastIndex
            val pageAverage = remember(pageSubjects) {
                val avgs = pageSubjects.map { it.average }.filter { it > 0 }
                if (avgs.isNotEmpty()) avgs.average().toFloat() else 0f
            }
            val pageMarksCount = remember(pageSubjects) { pageSubjects.sumOf { it.marks.size } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Hero Overall GPA Card (Clickable -> Shows "Что надо подтянуть")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showGpaAnalysis = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (pageIsYear) "Годовой средний балл" else "Средний балл",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• нажми для анализа",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (pageAverage > 0f) String.format(Locale.US, "%.2f", pageAverage) else "—",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Всего оценок: $pageMarksCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.height(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (pageAverage >= 4.5f) "Отлично" else if (pageAverage >= 3.5f) "Хорошо" else if (pageAverage > 0f) "Внимание" else "Нет данных",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (pageIsYear) "Ведомость за год" else "Предметы",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (pageSubjects.isEmpty() || pageSubjects.all { it.marks.isEmpty() && it.average == 0f && it.finalMark == null }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .height(40.dp)
                                        .width(40.dp)
                                )
                            }
                            Text(
                                text = "В этом периоде пока нет оценок",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Оценки появятся, как только учителя выставят их в журнал",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pageIsYear) {
                            items(pageSubjects, key = { it.subject }) { item ->
                                YearlySubjectCard(
                                    summary = item,
                                    onClick = { selectedSubject = item }
                                )
                            }
                        } else {
                            items(pageSubjects, key = { it.subject }) { item ->
                                SubjectMarkCard(
                                    summary = item,
                                    onClick = { selectedSubject = item }
                                )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    if (showGpaAnalysis) {
        GpaAnalysisBottomSheet(
            subjects = currentDisplayedSubjects,
            overallAverage = currentOverallAverage,
            isYearTab = selectedTabIndex == allTabs.lastIndex,
            onSelectSubject = { sub ->
                studySubject = sub
            },
            onDismiss = { showGpaAnalysis = false }
        )
    }

    studySubject?.let { sub ->
        SubjectStudyBottomSheet(
            subject = sub,
            allDays = allDays,
            settingsStore = settingsStore,
            onDismiss = { studySubject = null }
        )
    }

    selectedSubject?.let { subject ->
        ModalBottomSheet(
            onDismissRequest = { selectedSubject = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject.subject,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (subject.average > 0f) "Средний: ${String.format(Locale.US, "%.2f", subject.average)}" else "Оценок нет",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (subject.average > 0f) {
                        ExpressiveGradeBadge(
                            value = if (subject.average >= 4.5f) "5" else if (subject.average >= 3.5f) "4" else if (subject.average >= 2.5f) "3" else "2",
                            isLarge = true
                        )
                    }
                }

                if (!isYearTab && subject.marks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    com.pixelschool.app.ui.components.GradeCalculatorCard(
                        marks = subject.marks,
                        currentAverage = subject.average
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Text(
                    text = if (isYearTab) "Оценки по четвертям:" else "Все оценки за период:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isYearTab && subject.periodMarks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        subject.periodMarks.forEach { pm ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pm.periodName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (pm.average > 0f) {
                                            Text(
                                                text = "ср. ${String.format(Locale.US, "%.2f", pm.average)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (pm.marks.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            pm.marks.forEach { mark ->
                                                ExpressiveGradeBadge(value = mark.value)
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Нет оценок",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (subject.marks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        subject.marks.sortedNewestFirst().forEach { mark ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ExpressiveGradeBadge(value = mark.value)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                text = mark.comment?.takeIf { it.isNotBlank() }
                                                    ?: if (mark.isControl) "Контрольная работа" else "Ответ на уроке",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (mark.weight > 1) {
                                                Text(
                                                    text = "Вес оценки: x${mark.weight}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                    if (mark.date.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = mark.date,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Оценки отсутствуют",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun YearlySubjectCard(
    summary: SubjectSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Subject name & Yearly average / final mark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!summary.finalMark.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Итог: ${summary.finalMark}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (summary.average > 0f) String.format(Locale.US, "%.2f", summary.average) else "—",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val quarters = if (summary.periodMarks.isNotEmpty()) {
                summary.periodMarks
            } else {
                listOf(
                    PeriodMarks("1", "1 четверть", summary.marks, summary.average)
                )
            }

            quarters.forEachIndexed { qIndex, period ->
                if (qIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = period.periodName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (period.marks.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                period.marks.forEach { mark ->
                                    ExpressiveGradeBadge(value = mark.value)
                                }
                            }
                        } else {
                            Text(
                                text = "Нет оценок",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (period.average > 0f) {
                        Text(
                            text = "ср. ${String.format(Locale.US, "%.2f", period.average)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectMarkCard(
    summary: SubjectSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (summary.average > 0f) String.format(Locale.US, "%.2f", summary.average) else "—",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (summary.marks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.marks.forEach { mark ->
                        ExpressiveGradeBadge(value = mark.value)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нет оценок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 16.dp,
        divider = {},
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                width = Dp.Unspecified,
                height = 3.dp,
                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}

private fun parseAcademicDateWeight(dateStr: String): Int {
    val clean = dateStr.trim().replace("-", "").replace("/", ".")
    val parts = clean.split(".")
    if (parts.size >= 2) {
        val day = parts[0].toIntOrNull() ?: 0
        val month = parts[1].toIntOrNull() ?: 0
        if (month in 1..12) {
            val academicMonth = if (month >= 8) month else month + 12
            return academicMonth * 100 + day
        }
    }
    return 0
}

private fun List<Mark>.sortedOldestFirst(): List<Mark> {
    if (size <= 1) return this
    return this.sortedBy { parseAcademicDateWeight(it.date) }
}

private fun List<Mark>.sortedNewestFirst(): List<Mark> {
    if (size <= 1) return this
    return this.reversed().sortedByDescending { parseAcademicDateWeight(it.date) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaAnalysisBottomSheet(
    subjects: List<SubjectSummary>,
    overallAverage: Float,
    isYearTab: Boolean,
    onSelectSubject: (SubjectSummary) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Что надо подтянуть",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isYearTab) "Анализ годовых оценок" else "Анализ оценок за период",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (overallAverage > 0f) String.format(Locale.US, "%.2f", overallAverage) else "—",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val validSubjects = subjects.filter { it.average > 0f }
            val needAttention = validSubjects.filter { it.average < 3.50f }
            val closeToFive = validSubjects.filter { it.average in 3.50f..4.49f }
            val solidFives = validSubjects.filter { it.average >= 4.50f }

            if (needAttention.isEmpty() && closeToFive.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎉 Отличный результат!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Все ваши предметы идут на «отлично»! Продолжайте в том же духе.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (needAttention.isNotEmpty()) {
                Text(
                    text = "⚠️ Требуют внимания (< 3.50)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))

                needAttention.forEach { sub ->
                    val neededForFour = calculateNeededMarks(sub.marks, 3.50)
                    val neededForFive = calculateNeededMarks(sub.marks, 4.50)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectSubject(sub) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.subject,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                ExpressiveGradeBadge(
                                    value = String.format(Locale.US, "%.2f", sub.average)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• До 4 (хорошо): нужно получить $neededForFour ${pluralMarks(neededForFour)} «5»",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• До 5 (отлично): нужно получить $neededForFive ${pluralMarks(neededForFive)} «5»",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Анализ тем с ИИ ›",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (closeToFive.isNotEmpty()) {
                Text(
                    text = "🎯 Близко к пятёрке (3.50 — 4.49)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                closeToFive.forEach { sub ->
                    val neededForFive = calculateNeededMarks(sub.marks, 4.50)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectSubject(sub) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.subject,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Осталось: $neededForFive ${pluralMarks(neededForFive)} «5» до оценки 5",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Нажми для разбора тем с ИИ ›",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            ExpressiveGradeBadge(
                                value = String.format(Locale.US, "%.2f", sub.average)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (solidFives.isNotEmpty()) {
                Text(
                    text = "⭐ Отличные предметы (${solidFives.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    solidFives.forEach { sub ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = sub.subject,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f", sub.average),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateNeededMarks(marks: List<Mark>, targetAverage: Double): Int {
    if (marks.isEmpty()) return 1
    var currentWeightedSum = 0.0
    var totalWeight = 0.0
    for (m in marks) {
        val v = m.value.firstOrNull()?.toString()?.toDoubleOrNull() ?: continue
        val w = if (m.weight > 0) m.weight.toDouble() else 1.0
        currentWeightedSum += v * w
        totalWeight += w
    }
    if (totalWeight == 0.0) return 1
    val currentAvg = currentWeightedSum / totalWeight
    if (currentAvg >= targetAverage) return 0
    val diff = 5.0 - targetAverage
    if (diff <= 0.0) return 0
    val neededK = (targetAverage * totalWeight - currentWeightedSum) / diff
    return ceil(neededK).toInt().coerceAtLeast(1)
}

private fun pluralMarks(n: Int): String {
    val remainder100 = n % 100
    val remainder10 = n % 10
    if (remainder100 in 11..19) return "оценок"
    return when (remainder10) {
        1 -> "оценку"
        2, 3, 4 -> "оценки"
        else -> "оценок"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectStudyBottomSheet(
    subject: SubjectSummary,
    allDays: List<DaySchedule>,
    settingsStore: SettingsStore?,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val geminiKey = settingsStore?.geminiApiKey?.collectAsState()?.value ?: ""

    var studyResult by remember(subject, geminiKey) { mutableStateOf<AiStudyResult?>(null) }
    var isLoading by remember(subject, geminiKey) { mutableStateOf(true) }

    LaunchedEffect(subject, geminiKey) {
        isLoading = true
        studyResult = if (geminiKey.isNotBlank()) {
            AiStudyAssistant.analyzeWithGemini(geminiKey, subject.subject, subject.average, subject.marks, allDays)
        } else {
            AiStudyAssistant.generateLocalAnalysis(subject.subject, subject.average, subject.marks, allDays)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 48.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.subject,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Разбор успеваемости и тем уроков",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpressiveGradeBadge(
                    value = String.format(Locale.US, "%.2f", subject.average),
                    isLarge = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Goal & Fives calculation card (Cutoff is strictly .50)
            val targetCutoff = if (subject.average < 3.50f) 3.50 else 4.50
            val neededFives = studyResult?.neededFivesForGoal ?: AiStudyAssistant.calculateNeededFives(subject.marks, targetCutoff)
            val goalText = if (subject.average < 3.50f) "«4» (Хорошо)" else "«5» (Отлично)"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Цель: $goalText",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (neededFives > 0)
                                "Нужно ещё $neededFives ${pluralMarks(neededFives)} «5» с весом 1"
                            else
                                "Балл уже на уровне отличной оценки!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI / Local Study Recommendation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Рекомендации помощника",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (studyResult?.isAiGenerated == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (studyResult?.isAiGenerated == true) "🤖 Gemini AI" else "⚡ Встроенный анализ",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (studyResult?.isAiGenerated == true)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Анализируем уроки и темы...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        studyResult?.errorMessage?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Text(
                                    text = err,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Text(
                            text = studyResult?.studySummary ?: "Нет данных для анализа",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoading = true
                                        studyResult = if (geminiKey.isNotBlank()) {
                                            AiStudyAssistant.analyzeWithGemini(geminiKey, subject.subject, subject.average, subject.marks, allDays)
                                        } else {
                                            AiStudyAssistant.generateLocalAnalysis(subject.subject, subject.average, subject.marks, allDays)
                                        }
                                        isLoading = false
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Обновить анализ",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Problem Lessons Journal
            val problems = studyResult?.problemLessons ?: AiStudyAssistant.extractProblemLessons(subject.subject, subject.marks, allDays)

            Text(
                text = "Уроки с оценками 2 и 3 (${problems.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (problems.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Отлично! В журнале нет зафиксированных двоек и троек по этому предмету.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    problems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ExpressiveGradeBadge(value = item.mark.value)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.date,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (item.isControl) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = "Контрольная",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Тема: ${item.topic}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (!item.comment.isNullOrBlank() && item.comment != item.topic) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "За что: ${item.comment}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!item.homework.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "ДЗ: ${item.homework}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (!item.teacher.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Учитель: ${item.teacher}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
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
