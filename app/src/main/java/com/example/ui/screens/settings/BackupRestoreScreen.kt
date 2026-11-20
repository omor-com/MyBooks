package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.backup.BackupMode
import com.example.data.backup.DuplicatePolicy
import com.example.data.local.entity.BookWithDetails
import com.example.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    allBooks: List<BookWithDetails>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val isBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedBackupMode by remember { mutableStateOf(BackupMode.METADATA_ONLY) }
    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }
    var selectedDuplicatePolicy by remember { mutableStateOf(DuplicatePolicy.SKIP_DUPLICATES) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.createBackup(uri, selectedBackupMode, selectedBookIds)
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupForRestore(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("backup_restore_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("backup_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.backup_community_note),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Create Backup Card
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.backup_create_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        listOf(
                            Triple(BackupMode.METADATA_ONLY, R.string.backup_mode_metadata, R.string.backup_mode_metadata_desc),
                            Triple(BackupMode.SELECTED_BOOKS, R.string.backup_mode_selected, R.string.backup_mode_selected_desc),
                            Triple(BackupMode.FULL_LIBRARY, R.string.backup_mode_full, R.string.backup_mode_full_desc)
                        ).forEach { (mode, titleRes, descRes) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBackupMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(selected = selectedBackupMode == mode, onClick = { selectedBackupMode = mode })
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (selectedBackupMode == BackupMode.SELECTED_BOOKS) {
                            if (allBooks.isEmpty()) {
                                Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.bodySmall)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    allBooks.forEach { item ->
                                        val checked = item.book.id in selectedBookIds
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedBookIds = if (checked) selectedBookIds - item.book.id else selectedBookIds + item.book.id
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { isChecked ->
                                                    selectedBookIds = if (isChecked) selectedBookIds + item.book.id else selectedBookIds - item.book.id
                                                }
                                            )
                                            Text(
                                                text = item.book.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                createBackupLauncher.launch("MyBooks_Backup_$stamp.zip")
                            },
                            enabled = !isBusy && (selectedBackupMode != BackupMode.SELECTED_BOOKS || selectedBookIds.isNotEmpty()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_create_zip_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Rounded.Archive, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.backup_create_button))
                        }
                    }
                }
            }

            // Restore Backup Card
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.backup_restore_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.backup_duplicate_policy), style = MaterialTheme.typography.labelLarge)

                        listOf(
                            DuplicatePolicy.SKIP_DUPLICATES to R.string.backup_policy_skip,
                            DuplicatePolicy.KEEP_BOTH to R.string.backup_policy_keep_both,
                            DuplicatePolicy.OVERWRITE_MATCHES to R.string.backup_policy_overwrite
                        ).forEach { (policy, resId) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDuplicatePolicy = policy },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedDuplicatePolicy == policy, onClick = { selectedDuplicatePolicy = policy })
                                Text(stringResource(resId), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                openRestoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_select_zip_restore_button")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_restore_button))
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { inspected ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_confirm_message,
                        inspected.manifest.bookCount,
                        inspected.manifest.noteCount,
                        inspected.manifest.bookmarkCount
                    )
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRestore(selectedDuplicatePolicy) }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingRestore() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
