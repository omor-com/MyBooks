package com.example.ui.screens.bookmarks

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.BookWithDetails
import com.example.data.local.entity.NoteEntity
import com.example.ui.viewmodel.AnnotationExportFormat
import com.example.ui.viewmodel.LibraryUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookmarksNotesScreen(
    state: LibraryUiState,
    onOpenBookAtLocation: (bookId: String, pageIndex: Int?, chapterIndex: Int?) -> Unit,
    onSaveNote: (id: String?, bookId: String?, bookTitle: String, title: String, content: String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onExportAnnotations: (Uri, AnnotationExportFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var filterBookId by remember { mutableStateOf<String?>(null) }
    var bookFilterExpanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf(AnnotationExportFormat.MARKDOWN) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showNewNoteDialog by remember { mutableStateOf(false) }

    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) onExportAnnotations(uri, AnnotationExportFormat.MARKDOWN)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportAnnotations(uri, AnnotationExportFormat.JSON)
    }

    val bookTitlesById = remember(state.allBooks) {
        state.allBooks.associate { it.book.id to it.book.title }
    }

    Scaffold(
        modifier = modifier.testTag("bookmarks_notes_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showNewNoteDialog = true },
                    modifier = Modifier.testTag("new_note_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bookmarks_new_note))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bookmarks_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        IconButton(
                            onClick = { exportMenuExpanded = true },
                            modifier = Modifier.testTag("bookmarks_export_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.bookmarks_export_button))
                        }
                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_export_md)) },
                                onClick = {
                                    exportMenuExpanded = false
                                    pendingExportFormat = AnnotationExportFormat.MARKDOWN
                                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                    exportMdLauncher.launch("MyBooks_Notes_$stamp.md")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_export_json)) },
                                onClick = {
                                    exportMenuExpanded = false
                                    pendingExportFormat = AnnotationExportFormat.JSON
                                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                    exportJsonLauncher.launch("MyBooks_Notes_$stamp.json")
                                }
                            )
                        }
                    }
                    Box {
                        FilterChip(
                            selected = filterBookId != null,
                            onClick = { bookFilterExpanded = true },
                            label = {
                                Text(
                                    text = filterBookId?.let { bookTitlesById[it] } ?: stringResource(R.string.bookmarks_filter_all_books),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = bookFilterExpanded,
                            onDismissRequest = { bookFilterExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_filter_all_books)) },
                                onClick = { filterBookId = null; bookFilterExpanded = false }
                            )
                            state.allBooks.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = { filterBookId = item.book.id; bookFilterExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.bookmarks_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${stringResource(R.string.bookmarks_tab_bookmarks)} (${state.bookmarks.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${stringResource(R.string.bookmarks_tab_notes)} (${state.notes.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("${stringResource(R.string.bookmarks_tab_highlights)} (${state.highlights.size})") }
                )
            }

            Spacer(Modifier.height(10.dp))

            when (selectedTab) {
                0 -> {
                    val filtered = state.bookmarks.filter { bm ->
                        val matchesBook = filterBookId == null || bm.bookId == filterBookId
                        val bookTitle = bookTitlesById[bm.bookId].orEmpty()
                        val matchesQuery = searchQuery.isBlank() ||
                            bm.title.contains(searchQuery, ignoreCase = true) ||
                            bm.locatorDisplay.contains(searchQuery, ignoreCase = true) ||
                            bookTitle.contains(searchQuery, ignoreCase = true)
                        matchesBook && matchesQuery
                    }
                    if (filtered.isEmpty()) {
                        EmptyAnnotationsState(stringResource(R.string.bookmarks_empty_bookmarks))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { bm ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenBookAtLocation(bm.bookId, bm.pageIndex, bm.chapterIndex) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bm.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = "${bookTitlesById[bm.bookId] ?: ""} • ${bm.locatorDisplay}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val filtered = state.notes.filter { note ->
                        val matchesBook = filterBookId == null || note.bookId == filterBookId
                        val matchesQuery = searchQuery.isBlank() ||
                            note.title.contains(searchQuery, ignoreCase = true) ||
                            note.content.contains(searchQuery, ignoreCase = true) ||
                            note.bookTitleSnapshot.contains(searchQuery, ignoreCase = true)
                        matchesBook && matchesQuery
                    }
                    if (filtered.isEmpty()) {
                        EmptyAnnotationsState(stringResource(R.string.bookmarks_empty_notes))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { note ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = note.bookId != null) {
                                            note.bookId?.let { id ->
                                                onOpenBookAtLocation(id, note.pageIndex, note.chapterIndex)
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Rounded.NoteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                                Text(
                                                    text = note.title.ifBlank { note.bookTitleSnapshot.ifBlank { stringResource(R.string.bookmarks_no_book_attached) } },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row {
                                                IconButton(onClick = { editingNote = note }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { onDeleteNote(note.id) }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                        Text(note.content, style = MaterialTheme.typography.bodyMedium)
                                        if (note.bookTitleSnapshot.isNotBlank() || note.locatorDisplay.isNotBlank()) {
                                            Text(
                                                text = listOf(note.bookTitleSnapshot, note.locatorDisplay).filter { it.isNotBlank() }.joinToString(" • "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val filtered = state.highlights.filter { hl ->
                        val matchesBook = filterBookId == null || hl.bookId == filterBookId
                        val matchesQuery = searchQuery.isBlank() ||
                            hl.selectedText.contains(searchQuery, ignoreCase = true) ||
                            hl.bookTitleSnapshot.contains(searchQuery, ignoreCase = true)
                        matchesBook && matchesQuery
                    }
                    if (filtered.isEmpty()) {
                        EmptyAnnotationsState(stringResource(R.string.bookmarks_empty_highlights))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { hl ->
                                val swatchColor = remember(hl.colorHex) {
                                    runCatching { Color(android.graphics.Color.parseColor(hl.colorHex)) }
                                        .getOrDefault(Color(0xFFFFB703))
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenBookAtLocation(hl.bookId, hl.pageIndex, hl.chapterIndex) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(6.dp)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(swatchColor)
                                        )
                                        Icon(Icons.Rounded.FormatQuote, contentDescription = null, tint = swatchColor)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(hl.selectedText, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                text = "${hl.bookTitleSnapshot} • ${hl.locatorDisplay}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onDeleteHighlight(hl.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewNoteDialog || editingNote != null) {
        NoteEditorDialog(
            existing = editingNote,
            books = state.allBooks,
            onDismiss = {
                showNewNoteDialog = false
                editingNote = null
            },
            onSave = { bookId, bookTitle, title, content ->
                onSaveNote(editingNote?.id, bookId, bookTitle, title, content)
                showNewNoteDialog = false
                editingNote = null
            }
        )
    }
}

@Composable
private fun EmptyAnnotationsState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoteEditorDialog(
    existing: NoteEntity?,
    books: List<BookWithDetails>,
    onDismiss: () -> Unit,
    onSave: (bookId: String?, bookTitle: String, title: String, content: String) -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember(existing) { mutableStateOf(existing?.content.orEmpty()) }
    var selectedBookId by remember(existing) { mutableStateOf(existing?.bookId) }
    var bookMenuExpanded by remember { mutableStateOf(false) }

    val selectedTitle = selectedBookId?.let { id ->
        books.firstOrNull { it.book.id == id }?.book?.title ?: existing?.bookTitleSnapshot
    } ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.bookmarks_new_note else R.string.bookmarks_edit_note))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { bookMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedTitle.ifBlank { stringResource(R.string.bookmarks_select_book) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = bookMenuExpanded, onDismissRequest = { bookMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookmarks_no_book_attached)) },
                            onClick = { selectedBookId = null; bookMenuExpanded = false }
                        )
                        books.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { selectedBookId = item.book.id; bookMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.bookmarks_note_title_hint)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.bookmarks_note_content_hint)) },
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedBookId, selectedTitle, title, content) },
                enabled = content.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
