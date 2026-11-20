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

@Serializable
data class UserPreferences(
    val appLanguage: String = "bn",
    val appThemeModeName: String = AppThemeMode.SYSTEM.name,
    val readerThemeModeName: String = ReaderThemeMode.LIGHT.name,
    val readerFontSizeSp: Float = 18f,
    val readerLineSpacing: Float = 1.55f,
    val readerMarginDp: Int = 20,
    val readerFontFamilyName: String = ReaderFontFamilyOption.BENGALI_SANS.name,
    val readerPdfFitModeName: String = PdfFitMode.FIT_PAGE.name,
    val readerKeepScreenOn: Boolean = false,
    val readerFullscreen: Boolean = false,
    val readerShowProgress: Boolean = true,
    val pageTurnSoundDownloaded: Boolean = false,
    val pageTurnSoundEnabled: Boolean = false
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
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_FULLSCREEN = booleanPreferencesKey("reader_fullscreen")
        val READER_SHOW_PROGRESS = booleanPreferencesKey("reader_show_progress")
        val SOUND_DOWNLOADED = booleanPreferencesKey("page_turn_sound_downloaded")
        val SOUND_ENABLED = booleanPreferencesKey("page_turn_sound_enabled")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                appLanguage = prefs[Keys.APP_LANGUAGE] ?: "bn",
                appThemeModeName = prefs[Keys.APP_THEME_MODE] ?: AppThemeMode.SYSTEM.name,
                readerThemeModeName = prefs[Keys.READER_THEME_MODE] ?: ReaderThemeMode.LIGHT.name,
                readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE] ?: 18f).coerceIn(12f, 32f),
                readerLineSpacing = (prefs[Keys.READER_LINE_SPACING] ?: 1.55f).coerceIn(1.1f, 2.2f),
                readerMarginDp = (prefs[Keys.READER_MARGIN_DP] ?: 20).coerceIn(8, 48),
                readerFontFamilyName = prefs[Keys.READER_FONT_FAMILY] ?: ReaderFontFamilyOption.BENGALI_SANS.name,
                readerPdfFitModeName = prefs[Keys.READER_PDF_FIT_MODE] ?: PdfFitMode.FIT_PAGE.name,
                readerKeepScreenOn = prefs[Keys.READER_KEEP_SCREEN_ON] ?: false,
                readerFullscreen = prefs[Keys.READER_FULLSCREEN] ?: false,
                readerShowProgress = prefs[Keys.READER_SHOW_PROGRESS] ?: true,
                pageTurnSoundDownloaded = prefs[Keys.SOUND_DOWNLOADED] ?: false,
                pageTurnSoundEnabled = prefs[Keys.SOUND_ENABLED] ?: false
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

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setFullscreen(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_FULLSCREEN] = enabled }
    }

    suspend fun setShowProgress(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_SHOW_PROGRESS] = enabled }
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
            it[Keys.READER_KEEP_SCREEN_ON] = false
            it[Keys.READER_FULLSCREEN] = false
            it[Keys.READER_SHOW_PROGRESS] = true
        }
    }

    suspend fun restoreFromBackup(prefs: UserPreferences, soundFileExists: Boolean) {
        dataStore.edit {
            it[Keys.APP_LANGUAGE] = if (prefs.appLanguage == "en") "en" else "bn"
            it[Keys.APP_THEME_MODE] = prefs.appThemeMode.name
            it[Keys.READER_THEME_MODE] = prefs.readerThemeMode.name
            it[Keys.READER_FONT_SIZE] = prefs.readerFontSizeSp.coerceIn(12f, 32f)
            it[Keys.READER_LINE_SPACING] = prefs.readerLineSpacing.coerceIn(1.1f, 2.2f)
            it[Keys.READER_MARGIN_DP] = prefs.readerMarginDp.coerceIn(8, 48)
            it[Keys.READER_FONT_FAMILY] = prefs.readerFontFamily.name
            it[Keys.READER_PDF_FIT_MODE] = prefs.readerPdfFitMode.name
            it[Keys.READER_KEEP_SCREEN_ON] = prefs.readerKeepScreenOn
            it[Keys.READER_FULLSCREEN] = prefs.readerFullscreen
            it[Keys.READER_SHOW_PROGRESS] = prefs.readerShowProgress
            it[Keys.SOUND_DOWNLOADED] = soundFileExists && prefs.pageTurnSoundDownloaded
            it[Keys.SOUND_ENABLED] = soundFileExists && prefs.pageTurnSoundDownloaded && prefs.pageTurnSoundEnabled
        }
    }
}
