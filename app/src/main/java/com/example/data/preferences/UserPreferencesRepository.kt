package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException

private val Context.myBooksDataStore: DataStore<Preferences> by preferencesDataStore(name = "mybooks_preferences")

enum class ReaderFontFamilyOption {
    DEFAULT, BENGALI_SANS, SERIF, SANS
}

enum class PdfFitMode {
    FIT_PAGE, FIT_WIDTH
}

enum class PdfScrollMode {
    SINGLE_PAGE, CONTINUOUS_VERTICAL
}

enum class ReflowablePaginationMode {
    PAGINATED, CONTINUOUS_SCROLL
}

enum class TapZoneMode {
    STANDARD, LEFT_HANDED, SIMPLE_CENTER, OFF
}

enum class ReadingProfilePreset {
    COMFORTABLE, NIGHT, STUDY
}

@Serializable
data class UserPreferences(
    val appLanguage: String = "en",
    val appThemeModeName: String = AppThemeMode.SYSTEM.name,
    val readerThemeModeName: String = ReaderThemeMode.LIGHT.name,
    val readerFontSizeSp: Float = 18f,
    val readerLineSpacing: Float = 1.55f,
    val readerMarginDp: Int = 20,
    val readerFontFamilyName: String = ReaderFontFamilyOption.BENGALI_SANS.name,
    val readerPdfFitModeName: String = PdfFitMode.FIT_PAGE.name,
    val readerPdfScrollModeName: String = PdfScrollMode.SINGLE_PAGE.name,
    val readerReflowableModeName: String = ReflowablePaginationMode.PAGINATED.name,
    val readerTapZoneModeName: String = TapZoneMode.STANDARD.name,
    val readerReducedAnimation: Boolean = false,
    val readerKeepScreenOn: Boolean = false,
    val readerFullscreen: Boolean = false,
    val readerShowProgress: Boolean = true,
    val pageTurnSoundDownloaded: Boolean = false,
    val pageTurnSoundEnabled: Boolean = false,
    val dailyReadingGoalMinutes: Int = 30,
    val readingSpeedWpm: Int = 200,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val breakReminderMinutes: Int = 0,
    val lastBackupEpochMs: Long = 0L,
    val lastBackupSummary: String = "",
    val searchHistory: List<String> = emptyList()
) {
    val appThemeMode: AppThemeMode
        get() = enumValues<AppThemeMode>().firstOrNull { it.name == appThemeModeName } ?: AppThemeMode.SYSTEM

    val readerThemeMode: ReaderThemeMode
        get() = enumValues<ReaderThemeMode>().firstOrNull { it.name == readerThemeModeName } ?: ReaderThemeMode.LIGHT

    val readerFontFamily: ReaderFontFamilyOption
        get() = enumValues<ReaderFontFamilyOption>().firstOrNull { it.name == readerFontFamilyName }
            ?: ReaderFontFamilyOption.BENGALI_SANS

    val readerPdfFitMode: PdfFitMode
        get() = enumValues<PdfFitMode>().firstOrNull { it.name == readerPdfFitModeName }
            ?: PdfFitMode.FIT_PAGE

    val readerPdfScrollMode: PdfScrollMode
        get() = enumValues<PdfScrollMode>().firstOrNull { it.name == readerPdfScrollModeName }
            ?: PdfScrollMode.SINGLE_PAGE

    val readerReflowableMode: ReflowablePaginationMode
        get() = enumValues<ReflowablePaginationMode>().firstOrNull { it.name == readerReflowableModeName }
            ?: ReflowablePaginationMode.PAGINATED

    val readerTapZoneMode: TapZoneMode
        get() = enumValues<TapZoneMode>().firstOrNull { it.name == readerTapZoneModeName }
            ?: TapZoneMode.STANDARD
}

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.myBooksDataStore)

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val READER_THEME_MODE = stringPreferencesKey("reader_theme_mode")
        val READER_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val READER_LINE_SPACING = floatPreferencesKey("reader_line_spacing")
        val READER_MARGIN_DP = intPreferencesKey("reader_margin_dp")
        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val READER_PDF_FIT_MODE = stringPreferencesKey("reader_pdf_fit_mode")
        val READER_PDF_SCROLL_MODE = stringPreferencesKey("reader_pdf_scroll_mode")
        val READER_REFLOWABLE_MODE = stringPreferencesKey("reader_reflowable_mode")
        val READER_TAP_ZONE_MODE = stringPreferencesKey("reader_tap_zone_mode")
        val READER_REDUCED_ANIMATION = booleanPreferencesKey("reader_reduced_animation")
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_FULLSCREEN = booleanPreferencesKey("reader_fullscreen")
        val READER_SHOW_PROGRESS = booleanPreferencesKey("reader_show_progress")
        val SOUND_DOWNLOADED = booleanPreferencesKey("page_turn_sound_downloaded")
        val SOUND_ENABLED = booleanPreferencesKey("page_turn_sound_enabled")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_reading_goal_minutes")
        val READING_SPEED_WPM = intPreferencesKey("reading_speed_wpm")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val BREAK_REMINDER_MINUTES = intPreferencesKey("break_reminder_minutes")
        val LAST_BACKUP_EPOCH_MS = androidx.datastore.preferences.core.longPreferencesKey("last_backup_epoch_ms")
        val LAST_BACKUP_SUMMARY = stringPreferencesKey("last_backup_summary")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                appLanguage = prefs[Keys.APP_LANGUAGE] ?: "en",
                appThemeModeName = prefs[Keys.APP_THEME_MODE] ?: AppThemeMode.SYSTEM.name,
                readerThemeModeName = prefs[Keys.READER_THEME_MODE] ?: ReaderThemeMode.LIGHT.name,
                readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE] ?: 18f).coerceIn(12f, 32f),
                readerLineSpacing = (prefs[Keys.READER_LINE_SPACING] ?: 1.55f).coerceIn(1.1f, 2.2f),
                readerMarginDp = (prefs[Keys.READER_MARGIN_DP] ?: 20).coerceIn(8, 48),
                readerFontFamilyName = prefs[Keys.READER_FONT_FAMILY] ?: ReaderFontFamilyOption.BENGALI_SANS.name,
                readerPdfFitModeName = prefs[Keys.READER_PDF_FIT_MODE] ?: PdfFitMode.FIT_PAGE.name,
                readerPdfScrollModeName = prefs[Keys.READER_PDF_SCROLL_MODE] ?: PdfScrollMode.SINGLE_PAGE.name,
                readerReflowableModeName = prefs[Keys.READER_REFLOWABLE_MODE] ?: ReflowablePaginationMode.PAGINATED.name,
                readerTapZoneModeName = prefs[Keys.READER_TAP_ZONE_MODE] ?: TapZoneMode.STANDARD.name,
                readerReducedAnimation = prefs[Keys.READER_REDUCED_ANIMATION] ?: false,
                readerKeepScreenOn = prefs[Keys.READER_KEEP_SCREEN_ON] ?: false,
                readerFullscreen = prefs[Keys.READER_FULLSCREEN] ?: false,
                readerShowProgress = prefs[Keys.READER_SHOW_PROGRESS] ?: true,
                pageTurnSoundDownloaded = prefs[Keys.SOUND_DOWNLOADED] ?: false,
                pageTurnSoundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
                dailyReadingGoalMinutes = (prefs[Keys.DAILY_GOAL_MINUTES] ?: 30).coerceIn(5, 300),
                readingSpeedWpm = (prefs[Keys.READING_SPEED_WPM] ?: 200).coerceIn(80, 600),
                reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
                reminderHour = (prefs[Keys.REMINDER_HOUR] ?: 20).coerceIn(0, 23),
                reminderMinute = (prefs[Keys.REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
                breakReminderMinutes = (prefs[Keys.BREAK_REMINDER_MINUTES] ?: 0).coerceIn(0, 120),
                lastBackupEpochMs = prefs[Keys.LAST_BACKUP_EPOCH_MS] ?: 0L,
                lastBackupSummary = prefs[Keys.LAST_BACKUP_SUMMARY] ?: "",
                searchHistory = prefs[Keys.SEARCH_HISTORY]
                    ?.split('\n')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    ?.take(10)
                    ?: emptyList()
            )
        }

    suspend fun setLanguage(languageCode: String) {
        val safe = if (languageCode == "en") "en" else "bn"
        dataStore.edit { it[Keys.APP_LANGUAGE] = safe }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.APP_THEME_MODE] = mode.name }
    }

    suspend fun setReaderThemeMode(mode: ReaderThemeMode) {
        dataStore.edit { it[Keys.READER_THEME_MODE] = mode.name }
    }

    suspend fun setReaderFontSize(sizeSp: Float) {
        dataStore.edit { it[Keys.READER_FONT_SIZE] = sizeSp.coerceIn(12f, 32f) }
    }

    suspend fun setReaderLineSpacing(spacing: Float) {
        dataStore.edit { it[Keys.READER_LINE_SPACING] = spacing.coerceIn(1.1f, 2.2f) }
    }

    suspend fun setReaderMarginDp(marginDp: Int) {
        dataStore.edit { it[Keys.READER_MARGIN_DP] = marginDp.coerceIn(8, 48) }
    }

    suspend fun setReaderFontFamily(option: ReaderFontFamilyOption) {
        dataStore.edit { it[Keys.READER_FONT_FAMILY] = option.name }
    }

    suspend fun setReaderPdfFitMode(mode: PdfFitMode) {
        dataStore.edit { it[Keys.READER_PDF_FIT_MODE] = mode.name }
    }

    suspend fun setReaderPdfScrollMode(mode: PdfScrollMode) {
        dataStore.edit { it[Keys.READER_PDF_SCROLL_MODE] = mode.name }
    }

    suspend fun setReaderReflowableMode(mode: ReflowablePaginationMode) {
        dataStore.edit { it[Keys.READER_REFLOWABLE_MODE] = mode.name }
    }

    suspend fun setTapZoneMode(mode: TapZoneMode) {
        dataStore.edit { it[Keys.READER_TAP_ZONE_MODE] = mode.name }
    }

    suspend fun setReducedAnimation(reduced: Boolean) {
        dataStore.edit { it[Keys.READER_REDUCED_ANIMATION] = reduced }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setFullscreen(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_FULLSCREEN] = enabled }
    }

    suspend fun setShowProgress(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_SHOW_PROGRESS] = enabled }
    }

    suspend fun applyReadingProfile(preset: ReadingProfilePreset) {
        dataStore.edit { prefs ->
            when (preset) {
                ReadingProfilePreset.COMFORTABLE -> {
                    prefs[Keys.READER_THEME_MODE] = ReaderThemeMode.SEPIA.name
                    prefs[Keys.READER_FONT_SIZE] = 19f
                    prefs[Keys.READER_LINE_SPACING] = 1.65f
                    prefs[Keys.READER_MARGIN_DP] = 24
                    prefs[Keys.READER_REDUCED_ANIMATION] = false
                }
                ReadingProfilePreset.NIGHT -> {
                    prefs[Keys.READER_THEME_MODE] = ReaderThemeMode.DARK.name
                    prefs[Keys.READER_FONT_SIZE] = 18f
                    prefs[Keys.READER_LINE_SPACING] = 1.55f
                    prefs[Keys.READER_MARGIN_DP] = 20
                    prefs[Keys.READER_KEEP_SCREEN_ON] = false
                }
                ReadingProfilePreset.STUDY -> {
                    prefs[Keys.READER_THEME_MODE] = ReaderThemeMode.LIGHT.name
                    prefs[Keys.READER_FONT_SIZE] = 16f
                    prefs[Keys.READER_LINE_SPACING] = 1.4f
                    prefs[Keys.READER_MARGIN_DP] = 14
                    prefs[Keys.READER_SHOW_PROGRESS] = true
                    prefs[Keys.READER_KEEP_SCREEN_ON] = true
                }
            }
        }
    }

    suspend fun setDailyReadingGoalMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DAILY_GOAL_MINUTES] = minutes.coerceIn(5, 300) }
    }

    suspend fun setReadingSpeedWpm(wpm: Int) {
        dataStore.edit { it[Keys.READING_SPEED_WPM] = wpm.coerceIn(80, 600) }
    }

    suspend fun setReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setBreakReminderMinutes(minutes: Int) {
        dataStore.edit { it[Keys.BREAK_REMINDER_MINUTES] = minutes.coerceIn(0, 120) }
    }

    suspend fun recordLastBackup(epochMs: Long, summary: String) {
        dataStore.edit {
            it[Keys.LAST_BACKUP_EPOCH_MS] = epochMs
            it[Keys.LAST_BACKUP_SUMMARY] = summary.take(200)
        }
    }

    suspend fun addSearchHistory(query: String) {
        val clean = query.replace("\n", " ").trim()
        if (clean.length < 2) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_HISTORY]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
                ?: emptyList()
            val updated = (listOf(clean) + current).take(10)
            prefs[Keys.SEARCH_HISTORY] = updated.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    suspend fun setPageTurnSoundDownloaded(downloaded: Boolean) {
        dataStore.edit {
            it[Keys.SOUND_DOWNLOADED] = downloaded
            if (!downloaded) {
                it[Keys.SOUND_ENABLED] = false
            }
        }
    }

    suspend fun setPageTurnSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val isDownloaded = prefs[Keys.SOUND_DOWNLOADED] ?: false
            prefs[Keys.SOUND_ENABLED] = enabled && isDownloaded
        }
    }

    suspend fun resetReadingPreferences() {
        dataStore.edit {
            it[Keys.READER_THEME_MODE] = ReaderThemeMode.LIGHT.name
            it[Keys.READER_FONT_SIZE] = 18f
            it[Keys.READER_LINE_SPACING] = 1.55f
            it[Keys.READER_MARGIN_DP] = 20
            it[Keys.READER_FONT_FAMILY] = ReaderFontFamilyOption.BENGALI_SANS.name
            it[Keys.READER_PDF_FIT_MODE] = PdfFitMode.FIT_PAGE.name
            it[Keys.READER_PDF_SCROLL_MODE] = PdfScrollMode.SINGLE_PAGE.name
            it[Keys.READER_REFLOWABLE_MODE] = ReflowablePaginationMode.PAGINATED.name
            it[Keys.READER_TAP_ZONE_MODE] = TapZoneMode.STANDARD.name
            it[Keys.READER_REDUCED_ANIMATION] = false
            it[Keys.READER_KEEP_SCREEN_ON] = false
            it[Keys.READER_FULLSCREEN] = false
            it[Keys.READER_SHOW_PROGRESS] = true
        }
    }

    suspend fun restoreFromBackup(prefs: UserPreferences, soundFileExists: Boolean) {
        dataStore.edit {
            it[Keys.APP_LANGUAGE] = if (prefs.appLanguage == "bn") "bn" else "en"
            it[Keys.APP_THEME_MODE] = prefs.appThemeMode.name
            it[Keys.READER_THEME_MODE] = prefs.readerThemeMode.name
            it[Keys.READER_FONT_SIZE] = prefs.readerFontSizeSp.coerceIn(12f, 32f)
            it[Keys.READER_LINE_SPACING] = prefs.readerLineSpacing.coerceIn(1.1f, 2.2f)
            it[Keys.READER_MARGIN_DP] = prefs.readerMarginDp.coerceIn(8, 48)
            it[Keys.READER_FONT_FAMILY] = prefs.readerFontFamily.name
            it[Keys.READER_PDF_FIT_MODE] = prefs.readerPdfFitMode.name
            it[Keys.READER_PDF_SCROLL_MODE] = prefs.readerPdfScrollMode.name
            it[Keys.READER_REFLOWABLE_MODE] = prefs.readerReflowableMode.name
            it[Keys.READER_TAP_ZONE_MODE] = prefs.readerTapZoneMode.name
            it[Keys.READER_REDUCED_ANIMATION] = prefs.readerReducedAnimation
            it[Keys.READER_KEEP_SCREEN_ON] = prefs.readerKeepScreenOn
            it[Keys.READER_FULLSCREEN] = prefs.readerFullscreen
            it[Keys.READER_SHOW_PROGRESS] = prefs.readerShowProgress
            it[Keys.DAILY_GOAL_MINUTES] = prefs.dailyReadingGoalMinutes.coerceIn(5, 300)
            it[Keys.READING_SPEED_WPM] = prefs.readingSpeedWpm.coerceIn(80, 600)
            it[Keys.SOUND_DOWNLOADED] = soundFileExists && prefs.pageTurnSoundDownloaded
            it[Keys.SOUND_ENABLED] = soundFileExists && prefs.pageTurnSoundDownloaded && prefs.pageTurnSoundEnabled
        }
    }
}
