package com.example.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.audio.SoundDownloadState
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ReaderThemeMode
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenBackupRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val soundState by viewModel.soundDownloadState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance & Language
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_section_appearance)
                ) {
                    Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            AppThemeMode.SYSTEM to R.string.settings_theme_system,
                            AppThemeMode.LIGHT to R.string.settings_theme_light,
                            AppThemeMode.DARK to R.string.settings_theme_dark,
                            AppThemeMode.AMOLED to R.string.settings_theme_amoled
                        ).forEach { (mode, resId) ->
                            FilterChip(
                                selected = prefs.appThemeMode == mode,
                                onClick = { viewModel.setAppThemeMode(mode) },
                                label = { Text(stringResource(resId), maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = prefs.appLanguage == "bn",
                            onClick = { viewModel.setLanguage("bn") },
                            label = { Text(stringResource(R.string.settings_language_bn)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_lang_bn")
                        )
                        FilterChip(
                            selected = prefs.appLanguage == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text(stringResource(R.string.settings_language_en)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_lang_en")
                        )
                    }
                }
            }

            // Reading Preferences & Page-Turn Sound
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.MenuBook,
                    title = stringResource(R.string.settings_section_reading)
                ) {
                    Text(stringResource(R.string.settings_default_reader_theme), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            ReaderThemeMode.LIGHT to R.string.reader_theme_light,
                            ReaderThemeMode.SEPIA to R.string.reader_theme_sepia,
                            ReaderThemeMode.DARK to R.string.reader_theme_dark,
                            ReaderThemeMode.AMOLED to R.string.reader_theme_amoled
                        ).forEach { (mode, resId) ->
                            FilterChip(
                                selected = prefs.readerThemeMode == mode,
                                onClick = { viewModel.setReaderThemeMode(mode) },
                                label = { Text(stringResource(resId), maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_show_progress), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = prefs.readerShowProgress, onCheckedChange = { viewModel.setShowProgress(it) })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.reader_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = prefs.readerKeepScreenOn, onCheckedChange = { viewModel.setKeepScreenOn(it) })
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // Page-Turn Sound Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_page_turn_sound), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = stringResource(R.string.settings_page_turn_sound_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (val st = soundState) {
                        SoundDownloadState.NotDownloaded -> {
                            Button(
                                onClick = { viewModel.downloadPageTurnSound() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_download_sound_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_download_sound))
                            }
                        }
                        is SoundDownloadState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { st.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.settings_downloading_sound, st.progressPercent),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        SoundDownloadState.Ready -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = stringResource(R.string.settings_sound_downloaded_ready),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.settings_sound_toggle), style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = prefs.pageTurnSoundEnabled,
                                    onCheckedChange = { viewModel.togglePageTurnSound(it) },
                                    modifier = Modifier.testTag("settings_sound_enable_switch")
                                )
                            }
                        }
                        is SoundDownloadState.Error -> {
                            Text(
                                text = stringResource(R.string.settings_sound_download_failed) + " (${st.message})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(
                                onClick = { viewModel.downloadPageTurnSound() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }

                    TextButton(onClick = { viewModel.resetReadingPreferences() }) {
                        Text(stringResource(R.string.action_reset))
                    }
                }
            }

            // Backup & Restore (Located ONLY inside Settings)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenBackupRestore)
                        .testTag("settings_backup_restore_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_section_backup),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.settings_backup_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            // Storage Management
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.settings_section_storage)
                ) {
                    Text(
                        text = stringResource(
                            R.string.settings_storage_library_info,
                            storageStats.totalBooks,
                            formatFileSize(storageStats.totalBookBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_storage_cache_info, formatFileSize(storageStats.cacheBytes)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (storageStats.missingFilesCount > 0) {
                        Text(
                            text = stringResource(R.string.settings_storage_missing_files, storageStats.missingFilesCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearAppCache() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_cache_button")
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_cache))
                    }
                }
            }

            // Privacy & Security
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.settings_section_privacy)
                ) {
                    Text(
                        text = stringResource(R.string.settings_privacy_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Developer Official Shop & Support Email
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Storefront,
                    title = stringResource(R.string.settings_section_developer)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://muhammedomor.supportkori.shop/"))
                                )
                            } catch (_: ActivityNotFoundException) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_no_browser_error))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_developer_shop_button")
                    ) {
                        Icon(Icons.Rounded.Storefront, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_official_shop))
                    }

                    OutlinedButton(
                        onClick = {
                            val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:muhammedomor.studio@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "MyBooks Support")
                            }
                            try {
                                context.startActivity(mailIntent)
                            } catch (_: ActivityNotFoundException) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_no_email_error))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_contact_support_button")
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_contact_support))
                    }
                }
            }

            // About
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_section_about)
                ) {
                    Text(stringResource(R.string.settings_about_developer), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_about_copyright), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
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
