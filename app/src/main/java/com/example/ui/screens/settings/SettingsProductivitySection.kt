package com.example.ui.screens.settings

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.productivity.ReadingReminderScheduler
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ReadingStreakSummary
import com.example.ui.viewmodel.FocusTimerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
internal fun ReadingProductivitySection(
    prefs: UserPreferences,
    streakSummary: ReadingStreakSummary,
    sessions: List<ReadingSessionEntity>,
    focusTimerState: FocusTimerState,
    onSetDailyGoal: (Int) -> Unit,
    onSetReadingSpeedWpm: (Int) -> Unit,
    onSetBreakMinutes: (Int) -> Unit,
    onSetReminder: (Boolean, Int, Int) -> Unit,
    onPermissionDenied: () -> Unit,
    onStartFocus: () -> Unit,
    onPauseFocus: () -> Unit,
    onResumeFocus: () -> Unit,
    onStopFocus: () -> Unit
) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetReminder(true, prefs.reminderHour, prefs.reminderMinute)
        } else {
            onSetReminder(false, prefs.reminderHour, prefs.reminderMinute)
            onPermissionDenied()
        }
    }

    SettingsSectionCard(
        icon = Icons.Rounded.Timer,
        title = stringResource(R.string.settings_section_productivity)
    ) {
        // Daily Goal Chips
        Text(stringResource(R.string.settings_daily_goal_label), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(15, 30, 45, 60, 90).forEach { goal ->
                FilterChip(
                    selected = prefs.dailyReadingGoalMinutes == goal,
                    onClick = { onSetDailyGoal(goal) },
                    label = { Text("$goal m") }
                )
            }
        }

        // Reading Speed WPM Slider
        Text(
            text = stringResource(R.string.settings_reading_speed_wpm, prefs.readingSpeedWpm),
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = prefs.readingSpeedWpm.toFloat(),
            onValueChange = { onSetReadingSpeedWpm((it / 10f).roundToInt() * 10) },
            valueRange = 100f..400f
        )

        // Streak & 14-day Calendar Grid
        Text(
            text = stringResource(
                R.string.settings_streak_summary,
                streakSummary.currentStreakDays,
                streakSummary.bestStreakDays,
                streakSummary.totalMinutes
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.settings_14_day_calendar), style = MaterialTheme.typography.labelLarge)
        val todayEpochDay = LibraryRepository.localEpochDay()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (offset in 13 downTo 0) {
                val day = todayEpochDay - offset
                val mins = streakSummary.minutesByEpochDay[day] ?: 0
                val ratio = (mins.toFloat() / prefs.dailyReadingGoalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                val color = when {
                    mins <= 0 -> MaterialTheme.colorScheme.surfaceVariant
                    ratio >= 1f -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Focus Timer Controls + Break Reminder
        val isActive = focusTimerState.isRunning || focusTimerState.isPaused
        val mins = focusTimerState.elapsedSeconds / 60
        val secs = focusTimerState.elapsedSeconds % 60
        val timeDisplay = String.format(Locale.US, "%02d:%02d", mins, secs)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.home_focus_timer_title)} • $timeDisplay",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (!isActive) {
                Button(onClick = onStartFocus, modifier = Modifier.testTag("settings_focus_start")) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.home_focus_timer_start))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = if (focusTimerState.isPaused) onResumeFocus else onPauseFocus) {
                        Icon(
                            imageVector = if (focusTimerState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Button(onClick = onStopFocus) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Text(stringResource(R.string.settings_break_reminder), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0, 20, 30, 45, 60).forEach { interval ->
                FilterChip(
                    selected = prefs.breakReminderMinutes == interval,
                    onClick = { onSetBreakMinutes(interval) },
                    label = {
                        Text(
                            if (interval == 0) stringResource(R.string.settings_break_off)
                            else stringResource(R.string.settings_break_minutes, interval)
                        )
                    }
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Daily Reminder Notification Toggle + Time Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                Column {
                    Text(stringResource(R.string.settings_daily_reminder), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.settings_reminder_time, prefs.reminderHour, prefs.reminderMinute),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = prefs.reminderEnabled,
                onCheckedChange = { enable ->
                    if (!enable) {
                        onSetReminder(false, prefs.reminderHour, prefs.reminderMinute)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ReadingReminderScheduler.hasNotificationPermission(context)
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onSetReminder(true, prefs.reminderHour, prefs.reminderMinute)
                    }
                },
                modifier = Modifier.testTag("settings_reminder_switch")
            )
        }
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        onSetReminder(prefs.reminderEnabled, hourOfDay, minute)
                    },
                    prefs.reminderHour,
                    prefs.reminderMinute,
                    false
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_reminder_time, prefs.reminderHour, prefs.reminderMinute))
        }

        // Recent Reading Sessions
        Text(stringResource(R.string.settings_recent_sessions), style = MaterialTheme.typography.labelLarge)
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_no_sessions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            sessions.take(5).forEach { s ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.bookTitleSnapshot.ifBlank { stringResource(R.string.home_focus_timer_title) },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = fmt.format(Date(s.endedAtEpochMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${(s.durationSeconds / 60).coerceAtLeast(1)}m",
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

@Composable
internal fun PrivacyDetailsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_privacy_dialog_title)) },
        text = {
            Text(
                text = stringResource(R.string.settings_privacy_dialog_body),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
