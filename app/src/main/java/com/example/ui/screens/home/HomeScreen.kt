package com.example.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.BookWithDetails
import com.example.data.reader.DocumentFormat
import com.example.ui.components.BookCoverThumbnail
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandPurpleDeep
import com.example.ui.theme.BrandPurplePrimary
import com.example.ui.viewmodel.FocusTimerState
import com.example.ui.viewmodel.LibraryUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: LibraryUiState,
    focusTimerState: FocusTimerState,
    currentLanguage: String,
    onToggleLanguage: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBook: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onStartFocusTimer: (String?, String) -> Unit,
    onPauseFocusTimer: () -> Unit,
    onResumeFocusTimer: () -> Unit,
    onStopFocusTimer: () -> Unit,
    onDismissBreakAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeBrandHeader(
                currentLanguage = currentLanguage,
                onToggleLanguage = onToggleLanguage,
                onImportClick = onImportClick
            )
        }

        // Daily Reading Goal & Streak Card
        item {
            ReadingGoalAndStreakCard(
                todayMinutes = state.streakSummary.todayMinutes,
                goalMinutes = state.dailyReadingGoalMinutes,
                currentStreakDays = state.streakSummary.currentStreakDays,
                bestStreakDays = state.streakSummary.bestStreakDays
            )
        }

        // Compact Focus Timer Card
        item {
            val activeBook = state.continueReadingBook?.book
            FocusTimerCompactCard(
                timerState = focusTimerState,
                suggestedBookTitle = activeBook?.title.orEmpty(),
                onStart = { onStartFocusTimer(activeBook?.id, activeBook?.title.orEmpty()) },
                onPause = onPauseFocusTimer,
                onResume = onResumeFocusTimer,
                onStop = onStopFocusTimer
            )
        }

        if (state.allBooks.isEmpty()) {
            item {
                EmptyLibraryDashboardCard(onImportClick = onImportClick)
            }
        } else {
            state.continueReadingBook?.let { current ->
                item {
                    ContinueReadingHeroCard(
                        bookWithDetails = current,
                        readingSpeedWpm = state.readingSpeedWpm,
                        onOpenBook = { onOpenBook(current.book.id) }
                    )
                }
            }

            item {
                val completedCount = state.allBooks.count { it.book.readingProgress >= 0.99f }
                val annotationsCount = state.bookmarks.size + state.notes.size + state.highlights.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatTile(
                        icon = Icons.Rounded.AutoStories,
                        value = state.allBooks.size.toString(),
                        label = stringResource(R.string.home_stats_books),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Rounded.CheckCircle,
                        value = completedCount.toString(),
                        label = stringResource(R.string.home_stats_completed),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Rounded.Bookmark,
                        value = annotationsCount.toString(),
                        label = stringResource(R.string.home_stats_notes),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.recentlyOpenedBooks.isNotEmpty()) {
                item {
                    HomeBookShelf(
                        title = stringResource(R.string.home_recently_opened),
                        books = state.recentlyOpenedBooks,
                        onOpenBook = onOpenBook,
                        onSeeAll = onNavigateToLibrary
                    )
                }
            }

            if (state.recentlyAddedBooks.isNotEmpty()) {
                item {
                    HomeBookShelf(
                        title = stringResource(R.string.home_recently_added),
                        books = state.recentlyAddedBooks,
                        onOpenBook = onOpenBook,
                        onSeeAll = onNavigateToLibrary
                    )
                }
            }

            if (state.favoriteBooks.isNotEmpty()) {
                item {
                    HomeBookShelf(
                        title = stringResource(R.string.home_favorites),
                        icon = Icons.Rounded.Favorite,
                        books = state.favoriteBooks,
                        onOpenBook = onOpenBook,
                        onSeeAll = onNavigateToLibrary
                    )
                }
            }
        }
    }

    if (focusTimerState.breakAlertTriggered) {
        AlertDialog(
            onDismissRequest = onDismissBreakAlert,
            title = { Text(stringResource(R.string.home_break_alert_title)) },
            text = { Text(stringResource(R.string.home_break_alert_message)) },
            confirmButton = {
                Button(onClick = onDismissBreakAlert) {
                    Text(stringResource(R.string.home_break_dismiss))
                }
            }
        )
    }
}

@Composable
private fun ReadingGoalAndStreakCard(
    todayMinutes: Int,
    goalMinutes: Int,
    currentStreakDays: Int,
    bestStreakDays: Int
) {
    val safeGoal = goalMinutes.coerceAtLeast(1)
    val fraction = (todayMinutes.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_goal_streak_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_daily_goal_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${stringResource(R.string.home_streak_days, currentStreakDays)} • ${stringResource(R.string.home_streak_best, bestStreakDays)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Text(
                text = stringResource(R.string.home_daily_goal_progress, todayMinutes, safeGoal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusTimerCompactCard(
    timerState: FocusTimerState,
    suggestedBookTitle: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val active = timerState.isRunning || timerState.isPaused
    val mins = timerState.elapsedSeconds / 60
    val secs = timerState.elapsedSeconds % 60
    val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_focus_timer_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (active) {
                        "${stringResource(R.string.home_focus_timer_title)} • $formatted"
                    } else {
                        stringResource(R.string.home_focus_timer_title)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                val sub = if (active && timerState.activeBookTitle.isNotBlank()) {
                    timerState.activeBookTitle
                } else {
                    suggestedBookTitle
                }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!active) {
                FilledTonalButton(
                    onClick = onStart,
                    modifier = Modifier.testTag("home_focus_start_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.home_focus_timer_start), maxLines = 1)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = if (timerState.isPaused) onResume else onPause,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("home_focus_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (timerState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = stringResource(
                                if (timerState.isPaused) R.string.home_focus_timer_resume else R.string.home_focus_timer_pause
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Button(
                        onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("home_focus_stop_button")
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.home_focus_timer_stop), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBrandHeader(
    currentLanguage: String,
    onToggleLanguage: () -> Unit,
    onImportClick: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val nextLangLabel = stringResource(
        if (currentLanguage == "bn") R.string.action_switch_to_en else R.string.action_switch_to_bn
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_brand_header")
    ) {
        val stackHeader = maxWidth < 400.dp || fontScale > 1.25f
        if (stackHeader) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BrandIdentityRow(modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onToggleLanguage,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_language_toggle")
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = nextLangLabel,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(
                        onClick = onImportClick,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_quick_add_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_add_book),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandIdentityRow(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onToggleLanguage,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_language_toggle")
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = nextLangLabel,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onImportClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_quick_add_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_add_book),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandIdentityRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.mybooks_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyLibraryDashboardCard(onImportClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BrandPurpleDeep,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = BrandGold,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_empty_import_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_empty_cta))
            }
        }
    }
}

@Composable
private fun ContinueReadingHeroCard(
    bookWithDetails: BookWithDetails,
    readingSpeedWpm: Int,
    onOpenBook: () -> Unit
) {
    val book = bookWithDetails.book
    val progressPct = (book.readingProgress * 100f).roundToInt().coerceIn(0, 100)
    val format = DocumentFormat.fromName(book.formatName)

    // Clearly labeled estimate of remaining reading time based on remaining pages/chapters & reading speed
    val estMinutesLeft = estimateRemainingMinutes(
        bookWithDetails = bookWithDetails,
        readingSpeedWpm = readingSpeedWpm
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBook)
            .testTag("continue_reading_card"),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(colors = listOf(BrandPurpleDeep, BrandPurplePrimary))
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverThumbnail(
                    title = book.title,
                    formatLabel = format.displayLabel,
                    coverPath = book.coverUri,
                    modifier = Modifier.width(78.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_continue_reading),
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.author.isNotBlank()) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LinearProgressIndicator(
                        progress = { book.readingProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BrandGold,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.home_progress_percent, progressPct),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            if (estMinutesLeft > 0) {
                                Text(
                                    text = stringResource(R.string.home_est_minutes_left, estMinutesLeft),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandGold.copy(alpha = 0.9f)
                                )
                            }
                        }
                        Button(
                            onClick = onOpenBook,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGold,
                                contentColor = Color(0xFF231400)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("resume_reading_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.home_resume_reading),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun estimateRemainingMinutes(
    bookWithDetails: BookWithDetails,
    readingSpeedWpm: Int
): Int {
    val book = bookWithDetails.book
    val remainingFraction = (1f - book.readingProgress.coerceIn(0f, 1f))
    if (remainingFraction <= 0.01f) return 0
    val safeWpm = readingSpeedWpm.coerceIn(80, 600)
    if (book.estimatedTotalMinutes > 0) {
        return (book.estimatedTotalMinutes * remainingFraction).roundToInt().coerceAtLeast(1)
    }
    val progress = bookWithDetails.progress
    val totalMinutesEstimate = when {
        progress != null && progress.totalPages > 1 -> (progress.totalPages * 250f) / safeWpm
        progress != null && progress.totalChapters > 1 -> (progress.totalChapters * 1400f) / safeWpm
        book.fileSizeBytes > 0L -> ((book.fileSizeBytes / 1800L).coerceIn(5L, 600L)).toFloat() * (200f / safeWpm)
        else -> 0f
    }
    return (totalMinutesEstimate * remainingFraction).roundToInt().coerceAtLeast(1)
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeBookShelf(
    title: String,
    books: List<BookWithDetails>,
    onOpenBook: (String) -> Unit,
    onSeeAll: () -> Unit,
    icon: ImageVector? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.nav_library),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books, key = { it.book.id }) { item ->
                val format = DocumentFormat.fromName(item.book.formatName)
                Column(
                    modifier = Modifier
                        .width(114.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenBook(item.book.id) }
                        .padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BookCoverThumbnail(
                        title = item.book.title,
                        formatLabel = format.displayLabel,
                        coverPath = item.book.coverUri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = item.book.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val pct = (item.book.readingProgress * 100f).roundToInt().coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { item.book.readingProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
