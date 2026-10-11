package com.pixelschool.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelschool.app.data.model.Mark
import java.util.Locale
import kotlin.math.ceil

@Composable
fun GradeCalculatorCard(
    marks: List<Mark>,
    currentAverage: Float,
    modifier: Modifier = Modifier
) {
    val numericMarks = remember(marks) {
        marks.mapNotNull { it.numericValue?.toInt() }
    }

    // Selected target goal: 4.60 (for 5), 3.60 (for 4), 2.60 (for 3)
    var targetGrade by remember { mutableFloatStateOf(4.60f) }

    // Simulated additional marks added by the user
    val simulatedMarks = remember { mutableStateListOf<Int>() }

    val simulatedAverage = remember(numericMarks, simulatedMarks.toList()) {
        val totalList = numericMarks + simulatedMarks
        if (totalList.isNotEmpty()) {
            totalList.sum().toFloat() / totalList.size
        } else {
            currentAverage
        }
    }

    val neededFives = remember(numericMarks, targetGrade) {
        calculateNeededMarks(numericMarks, targetGrade, desiredMark = 5)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Калькулятор среднего балла",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Прогноз и расчёт нужных оценок",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (simulatedMarks.isNotEmpty()) {
                    IconButton(
                        onClick = { simulatedMarks.clear() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Сброс",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Goal selection
            Text(
                text = "Желаемая оценка за четверть:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoalChip(
                    label = "На «5» (от 4.60)",
                    selected = targetGrade == 4.60f,
                    onClick = { targetGrade = 4.60f },
                    modifier = Modifier.weight(1f)
                )
                GoalChip(
                    label = "На «4» (от 3.60)",
                    selected = targetGrade == 3.60f,
                    onClick = { targetGrade = 3.60f },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recommendation result
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = if (neededFives == 0) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.TrendingUp
                    val tint = if (neededFives == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            numericMarks.isEmpty() -> "Нет выставленных оценок для расчёта"
                            neededFives == 0 -> "Текущий балл уже выше цели! Отличный результат 🎯"
                            neededFives != null -> "Нужно получить ещё $neededFives ${pluralizeFives(neededFives)} «5»"
                            else -> "Цель недостижима с текущим количеством оценок"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Simulator
            Text(
                text = "Симулятор («Что, если получить...»):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 4, 3, 2).forEach { markValue ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { simulatedMarks.add(markValue) }
                    ) {
                        Text(
                            text = "+$markValue",
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(visible = simulatedMarks.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Добавлено: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = simulatedMarks.joinToString(", ") { "+$it" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "${String.format(Locale.US, "%.2f", currentAverage)} ➔ ${String.format(Locale.US, "%.2f", simulatedAverage)}",
                                style = MaterialTheme.typography.titleSmall,
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

@Composable
private fun Box(
    contentAlignment: Alignment,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        contentAlignment = contentAlignment,
        content = { content() }
    )
}

@Composable
private fun GoalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

private fun calculateNeededMarks(marks: List<Int>, targetAverage: Float, desiredMark: Int): Int? {
    if (marks.isEmpty()) return null
    val currentSum = marks.sum()
    val currentCount = marks.size
    if (currentSum.toFloat() / currentCount >= targetAverage) return 0
    if (desiredMark <= targetAverage) return null
    val numerator = targetAverage * currentCount - currentSum
    val denominator = desiredMark - targetAverage
    return ceil(numerator / denominator).toInt().coerceAtLeast(1)
}

private fun pluralizeFives(count: Int): String {
    val remainder10 = count % 10
    val remainder100 = count % 100
    return when {
        remainder100 in 11..19 -> "пятёрок"
        remainder10 == 1 -> "пятёрку"
        remainder10 in 2..4 -> "пятёрки"
        else -> "пятёрок"
    }
}
