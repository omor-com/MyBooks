package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.PageTurnSoundManager
import com.example.data.audio.SoundDownloadState
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupMode
import com.example.data.backup.DuplicatePolicy
import com.example.data.backup.InspectedBackup
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.preferences.PdfScrollMode
import com.example.data.preferences.ReaderFontFamilyOption
import com.example.data.preferences.ReflowablePaginationMode
import com.example.data.preferences.TapZoneMode
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.productivity.ReadingReminderScheduler
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ReadingStreakSummary
import com.example.data.repository.StorageBreakdown
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageStats(
    val totalBooks: Int = 0,
    val totalBookBytes: Long = 0L,
    val missingFilesCount: Int = 0,
    val breakdown: StorageBreakdown = StorageBreakdown(0L, 0L, 0L, 0L)
) {
    val cacheBytes: Long get() = breakdown.cacheBytes
}

data class FocusTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val breakIntervalMinutes: Int = 0,
    val breakAlertTriggered: Boolean = false,
    val activeBookId: String? = null,
    val activeBookTitle: String = ""
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val libraryRepository: LibraryRepository,
    private val soundManager: PageTurnSoundManager,
    private val backupManager: BackupManager
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val soundDownloadState: StateFlow<SoundDownloadState> = soundManager.downloadState

    val readingSessions: StateFlow<List<ReadingSessionEntity>> = libraryRepository.readingSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val streakSummary: StateFlow<ReadingStreakSummary> = combine(
        readingSessions,
        preferences
    ) { sessions, prefs ->
        LibraryRepository.calculateStreakSummary(sessions, prefs.dailyReadingGoalMinutes)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        ReadingStreakSummary(0, 0, 0, 0, emptyMap())
    )

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _focusTimerState = MutableStateFlow(FocusTimerState())
    val focusTimerState: StateFlow<FocusTimerState> = _focusTimerState.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _pendingRestore = MutableStateFlow<InspectedBackup?>(null)
    val pendingRestore: StateFlow<InspectedBackup?> = _pendingRestore.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var focusTimerJob: Job? = null
    private var focusStartedAtEpochMs: Long = 0L

    init {
        viewModelScope.launch {
            soundManager.verifyLocalSoundIntegrity()
        }
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            libraryRepository.allBooksWithDetails.collect { list ->
                val breakdown = withContext(Dispatchers.IO) {
                    libraryRepository.calculateStorageBreakdown()
                }
                _storageStats.value = StorageStats(
                    totalBooks = list.size,
                    totalBookBytes = list.sumOf { it.book.fileSizeBytes },
                    missingFilesCount = list.count { it.book.isFileMissing },
                    breakdown = breakdown
                )
            }
        }
    }

    fun setLanguage(code: String) = viewModelScope.launch { preferencesRepository.setLanguage(code) }
    fun setAppThemeMode(mode: AppThemeMode) = viewModelScope.launch { preferencesRepository.setAppThemeMode(mode) }
    fun setReaderThemeMode(mode: ReaderThemeMode) = viewModelScope.launch { preferencesRepository.setReaderThemeMode(mode) }
    fun setReaderFontSize(sizeSp: Float) = viewModelScope.launch { preferencesRepository.setReaderFontSize(sizeSp) }
    fun setReaderLineSpacing(spacing: Float) = viewModelScope.launch { preferencesRepository.setReaderLineSpacing(spacing) }
    fun setReaderMarginDp(marginDp: Int) = viewModelScope.launch { preferencesRepository.setReaderMarginDp(marginDp) }
    fun setReaderFontFamily(option: ReaderFontFamilyOption) = viewModelScope.launch { preferencesRepository.setReaderFontFamily(option) }
    fun setPdfScrollMode(mode: PdfScrollMode) = viewModelScope.launch { preferencesRepository.setReaderPdfScrollMode(mode) }
    fun setReflowableMode(mode: ReflowablePaginationMode) = viewModelScope.launch { preferencesRepository.setReaderReflowableMode(mode) }
    fun setTapZoneMode(mode: TapZoneMode) = viewModelScope.launch { preferencesRepository.setTapZoneMode(mode) }
    fun setReducedAnimation(reduced: Boolean) = viewModelScope.launch { preferencesRepository.setReducedAnimation(reduced) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    fun setFullscreen(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setFullscreen(enabled) }
    fun setShowProgress(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setShowProgress(enabled) }
    fun resetReadingPreferences() = viewModelScope.launch { preferencesRepository.resetReadingPreferences() }

    fun setDailyReadingGoalMinutes(minutes: Int) = viewModelScope.launch {
        preferencesRepository.setDailyReadingGoalMinutes(minutes)
    }

    fun setReadingSpeedWpm(wpm: Int) = viewModelScope.launch {
        preferencesRepository.setReadingSpeedWpm(wpm)
    }

    fun setBreakReminderMinutes(minutes: Int) = viewModelScope.launch {
        preferencesRepository.setBreakReminderMinutes(minutes)
        _focusTimerState.update { it.copy(breakIntervalMinutes = minutes) }
    }

    fun setReminderSettings(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesRepository.setReminderSettings(enabled, hour, minute)
            ReadingReminderScheduler.scheduleDailyReminder(context, enabled, hour, minute)
        }
    }

    fun startFocusTimer(bookId: String? = null, bookTitle: String = "") {
        val breakMinutes = preferences.value.breakReminderMinutes
        if (focusStartedAtEpochMs == 0L) {
            focusStartedAtEpochMs = System.currentTimeMillis()
        }
        _focusTimerState.value = FocusTimerState(
            isRunning = true,
            isPaused = false,
            elapsedSeconds = _focusTimerState.value.elapsedSeconds,
            breakIntervalMinutes = breakMinutes,
            breakAlertTriggered = false,
            activeBookId = bookId,
            activeBookTitle = bookTitle
        )
        launchTimerLoop()
    }

    fun pauseFocusTimer() {
        focusTimerJob?.cancel()
        _focusTimerState.update { it.copy(isRunning = false, isPaused = true) }
    }

    fun resumeFocusTimer() {
        if (!_focusTimerState.value.isPaused) return
        _focusTimerState.update { it.copy(isRunning = true, isPaused = false) }
        launchTimerLoop()
    }

    fun dismissBreakAlert() {
        _focusTimerState.update { it.copy(breakAlertTriggered = false) }
    }

    fun stopAndSaveFocusTimer() {
        focusTimerJob?.cancel()
        val snap = _focusTimerState.value
        val started = focusStartedAtEpochMs.takeIf { it > 0L }
            ?: (System.currentTimeMillis() - snap.elapsedSeconds * 1000L)
        focusStartedAtEpochMs = 0L
        _focusTimerState.value = FocusTimerState(breakIntervalMinutes = preferences.value.breakReminderMinutes)

        if (snap.elapsedSeconds >= 10) {
            viewModelScope.launch {
                libraryRepository.recordReadingSession(
                    bookId = snap.activeBookId,
                    bookTitle = snap.activeBookTitle.ifBlank { "Focus Session" },
                    durationSeconds = snap.elapsedSeconds,
                    startedAtEpochMs = started,
                    endedAtEpochMs = System.currentTimeMillis(),
                    isFocusTimer = true
                )
                val mins = (snap.elapsedSeconds / 60).coerceAtLeast(1)
                _messages.emit("Focus session saved ($mins min)")
            }
        }
    }

    private fun launchTimerLoop() {
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _focusTimerState.value
                if (!current.isRunning || current.isPaused) break
                val nextSeconds = current.elapsedSeconds + 1
                val breakMin = preferences.value.breakReminderMinutes
                val triggered = breakMin > 0 && nextSeconds > 0 && nextSeconds % (breakMin * 60) == 0
                _focusTimerState.update {
                    it.copy(
                        elapsedSeconds = nextSeconds,
                        breakIntervalMinutes = breakMin,
                        breakAlertTriggered = it.breakAlertTriggered || triggered
                    )
                }
            }
        }
    }

    fun downloadPageTurnSound() {
        soundManager.startDownload()
    }

    fun redownloadPageTurnSound() {
        soundManager.forceRedownload()
    }

    fun togglePageTurnSound(enabled: Boolean) {
        viewModelScope.launch {
            val valid = soundManager.verifyLocalSoundIntegrity()
            if (valid) {
                preferencesRepository.setPageTurnSoundEnabled(enabled)
                if (enabled) {
                    soundManager.playPageTurnIfEnabled(true)
                }
            } else {
                _messages.emit("Sound file missing. Please download it again.")
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val breakdown = withContext(Dispatchers.IO) {
                libraryRepository.clearAppCache()
                libraryRepository.calculateStorageBreakdown()
            }
            _storageStats.value = _storageStats.value.copy(
                breakdown = breakdown
            )
            _messages.emit("App cache cleared. Your books remain untouched.")
        }
    }

    fun createBackup(destinationUri: Uri, mode: BackupMode, selectedBookIds: Set<String>) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val res = backupManager.createBackup(
                destinationUri = destinationUri,
                mode = mode,
                selectedBookIds = selectedBookIds,
                preferences = preferences.value
            )
            _isBackupBusy.value = false
            res.onSuccess { summary ->
                val msg = if (summary.excludedFiles.isEmpty()) {
                    "Backup created (${summary.bookCount} book records)."
                } else {
                    "Backup created (${summary.bookCount} records). ${summary.excludedFiles.size} inaccessible file(s) excluded: ${summary.excludedFiles.take(3).joinToString()}"
                }
                _messages.emit(msg)
            }.onFailure { err ->
                _messages.emit(err.localizedMessage ?: "Backup failed")
            }
        }
    }

    fun inspectBackupForRestore(sourceUri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val res = backupManager.inspectAndValidateBackup(sourceUri)
            _isBackupBusy.value = false
            res.onSuccess { inspected ->
                _pendingRestore.value = inspected
            }.onFailure { err ->
                _messages.emit(err.localizedMessage ?: "Invalid or unsafe backup archive")
            }
        }
    }

    fun cancelPendingRestore() {
        _pendingRestore.value?.stagedFilesDir?.deleteRecursively()
        _pendingRestore.value = null
    }

    fun confirmRestore(policy: DuplicatePolicy) {
        val inspected = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            _isBackupBusy.value = true
            val res = backupManager.applyRestore(inspected, policy)
            _isBackupBusy.value = false
            res.onSuccess { summary ->
                _messages.emit(
                    "Restore complete: ${summary.booksProcessed} books, ${summary.bookmarksProcessed} bookmarks, ${summary.notesProcessed} notes (${summary.skippedDuplicates} skipped)."
                )
            }.onFailure { err ->
                _messages.emit(err.localizedMessage ?: "Restore failed; existing library preserved.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        focusTimerJob?.cancel()
    }
}
