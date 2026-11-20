package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.PageTurnSoundManager
import com.example.data.audio.SoundDownloadState
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupMode
import com.example.data.backup.DuplicatePolicy
import com.example.data.backup.InspectedBackup
import com.example.data.preferences.ReaderFontFamilyOption
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LibraryRepository
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageStats(
    val totalBooks: Int = 0,
    val totalBookBytes: Long = 0L,
    val missingFilesCount: Int = 0,
    val cacheBytes: Long = 0L
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

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _pendingRestore = MutableStateFlow<InspectedBackup?>(null)
    val pendingRestore: StateFlow<InspectedBackup?> = _pendingRestore.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            soundManager.verifyLocalSoundIntegrity()
        }
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            libraryRepository.allBooksWithDetails.collect { list ->
                _storageStats.value = StorageStats(
                    totalBooks = list.size,
                    totalBookBytes = list.sumOf { it.book.fileSizeBytes },
                    missingFilesCount = list.count { it.book.isFileMissing },
                    cacheBytes = libraryRepository.calculateAppCacheSizeBytes()
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
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    fun setFullscreen(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setFullscreen(enabled) }
    fun setShowProgress(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setShowProgress(enabled) }
    fun resetReadingPreferences() = viewModelScope.launch { preferencesRepository.resetReadingPreferences() }

    fun downloadPageTurnSound() {
        soundManager.startDownload()
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
            libraryRepository.clearAppCache()
            _storageStats.value = _storageStats.value.copy(
                cacheBytes = libraryRepository.calculateAppCacheSizeBytes()
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
}
