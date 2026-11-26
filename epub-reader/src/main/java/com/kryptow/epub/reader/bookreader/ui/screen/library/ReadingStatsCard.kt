package com.kryptow.epub.reader.bookreader.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.domain.model.ReadingStats

/** Günlük hedef seçenekleri (dakika). 0 = kapalı. */
val DAILY_GOAL_OPTIONS = listOf(0, 10, 15, 20, 30, 45, 60, 90)

/**
 * Kütüphanenin üstündeki okuma özeti: bugünkü süre / hedef halkası, seri ve son 7 gün.
 * Dokununca [onClick] (genellikle hedef seçme diyaloğu) çağrılır.
 */
@Composable
fun ReadingStatsCard(
    stats: ReadingStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Hedef halkası
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                CircularProgressIndicator(
                    progress = { if (stats.goalMinutes > 0) stats.goalProgress else 0f },
                    modifier = Modifier.size(52.dp),
                    strokeWidth = 5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                )
                Text(
                    text = stats.todayMinutes.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        stats.isGoalReached -> stringResource(R.string.stats_goal_reached)
                        stats.goalMinutes > 0 -> stringResource(R.string.stats_today) + " · " +
                            stringResource(R.string.stats_goal_progress, stats.todayMinutes, stats.goalMinutes)
                        else -> stringResource(R.string.stats_today) + " · " +
                            stringResource(R.string.minutes_short, stats.todayMinutes)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stats.streakDays > 0) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (stats.streakDays > 0)
                            pluralStringResource(R.plurals.stats_streak_days, stats.streakDays, stats.streakDays)
                        else stringResource(R.string.stats_no_streak),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                if (stats.totalSeconds > 0) {
                    Text(
                        text = stringResource(R.string.stats_total, formatDuration(stats.totalSeconds)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            WeekBars(stats)
        }
    }
}

/** Son 7 günün mini çubuk grafiği — bugün vurgulu. */
@Composable
private fun WeekBars(stats: ReadingStats) {
    val max = (stats.last7Days.maxOrNull() ?: 0L)
        .coerceAtLeast(if (stats.goalMinutes > 0) stats.goalMinutes * 60L else 60L)
    val label = stringResource(R.string.stats_last_7_days) + ": " +
        stats.last7Days.joinToString(", ") { stringResourceMinutes(it) }
    Row(
        modifier = Modifier
            .height(40.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        stats.last7Days.forEachIndexed { i, secs ->
            val isToday = i == stats.last7Days.lastIndex
            val fraction = (secs.toFloat() / max).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(
                            alpha = if (secs > 0) 0.45f else 0.15f
                        )
                    ),
            )
        }
    }
}

private fun stringResourceMinutes(seconds: Long): String = "${seconds / 60}"

@Composable
private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val h = (totalMinutes / 60).toInt()
    val m = (totalMinutes % 60).toInt()
    return if (h > 0) stringResource(R.string.hours_minutes_short, h, m)
    else stringResource(R.string.minutes_short, m)
}

/** Günlük hedef seçme diyaloğu. */
@Composable
fun DailyGoalDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_daily_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.stats_goal_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // 4'lü iki satır
                DAILY_GOAL_OPTIONS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            FilterChip(
                                selected = currentMinutes == minutes,
                                onClick = { onSelect(minutes) },
                                label = {
                                    Text(
                                        if (minutes == 0) stringResource(R.string.off)
                                        else stringResource(R.string.minutes_short, minutes)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}
