package com.example.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.BookWithDetails
import com.example.data.local.entity.CollectionEntity
import com.example.data.reader.DocumentFormat
import com.example.ui.components.BookCoverThumbnail
import com.example.ui.components.formatFileSize
import com.example.ui.viewmodel.DuplicatePromptState
import com.example.ui.viewmodel.LibraryFilterChip
import com.example.ui.viewmodel.LibrarySortOption
import com.example.ui.viewmodel.LibraryUiState
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSearchChange: (String) -> Unit,
    onToggleGridView: () -> Unit,
    onSelectFilterChip: (LibraryFilterChip) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onSelectCollection: (String?) -> Unit,
    onSelectSort: (LibrarySortOption) -> Unit,
    onImportClick: () -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (BookWithDetails) -> Unit,
    onRelocateFile: (String, Uri) -> Unit,
    onUpdateMetadata: (
        bookId: String,
        title: String,
        author: String,
        description: String,
        publisher: String,
        language: String,
        categoryName: String?,
        tagsCsv: String,
        collectionIds: List<String>
    ) -> Unit,
    onSetBookCover: (String, Uri) -> Unit,
    onCreateCollection: (String) -> Unit,
    onDeleteBook: (String, Boolean) -> Unit,
    onDuplicateSkip: () -> Unit,
    onDuplicateKeepBoth: () -> Unit,
    onDuplicateReplaceUri: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingBook by remember { mutableStateOf<BookWithDetails?>(null) }
    var deletingBook by remember { mutableStateOf<BookWithDetails?>(null) }
    var relocatingBookId by remember { mutableStateOf<String?>(null) }
    var coverTargetBookId by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val relocateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = relocatingBookId
        relocatingBookId = null
        if (uri != null && id != null) {
            onRelocateFile(id, uri)
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val id = coverTargetBookId
        coverTargetBookId = null
        if (uri != null && id != null) {
            onSetBookCover(id, uri)
        }
    }

    Scaffold(
        modifier = modifier.testTag("library_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onImportClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("library_import_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add_book))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("library_sort_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.action_sort))
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortMenuOption(stringResource(R.string.library_sort_date_added), state.sortOption == LibrarySortOption.DATE_ADDED) {
                                onSelectSort(LibrarySortOption.DATE_ADDED); sortMenuExpanded = false
                            }
                            SortMenuOption(stringResource(R.string.library_sort_recently_opened), state.sortOption == LibrarySortOption.RECENTLY_OPENED) {
                                onSelectSort(LibrarySortOption.RECENTLY_OPENED); sortMenuExpanded = false
                            }
                            SortMenuOption(stringResource(R.string.library_sort_title), state.sortOption == LibrarySortOption.TITLE) {
                                onSelectSort(LibrarySortOption.TITLE); sortMenuExpanded = false
                            }
                            SortMenuOption(stringResource(R.string.library_sort_author), state.sortOption == LibrarySortOption.AUTHOR) {
                                onSelectSort(LibrarySortOption.AUTHOR); sortMenuExpanded = false
                            }
                            SortMenuOption(stringResource(R.string.library_sort_progress), state.sortOption == LibrarySortOption.PROGRESS) {
                                onSelectSort(LibrarySortOption.PROGRESS); sortMenuExpanded = false
                            }
                        }
                    }
                    IconButton(
                        onClick = onToggleGridView,
                        modifier = Modifier.testTag("library_view_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = stringResource(
                                if (state.isGridView) R.string.library_view_list else R.string.library_view_grid
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input"),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )

            // Filter chips row: All, Favorites, Categories, Collections, Tags
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filterChip == LibraryFilterChip.ALL &&
                        state.selectedCategoryId == null &&
                        state.selectedTagId == null &&
                        state.selectedCollectionId == null,
                    onClick = {
                        onSelectFilterChip(LibraryFilterChip.ALL)
                        onSelectCategory(null)
                        onSelectTag(null)
                        onSelectCollection(null)
                    },
                    label = { Text(stringResource(R.string.library_filter_all)) }
                )
                FilterChip(
                    selected = state.filterChip == LibraryFilterChip.FAVORITES,
                    onClick = {
                        onSelectFilterChip(
                            if (state.filterChip == LibraryFilterChip.FAVORITES) LibraryFilterChip.ALL else LibraryFilterChip.FAVORITES
                        )
                    },
                    label = { Text(stringResource(R.string.library_filter_favorites)) }
                )
                state.categories.forEach { cat ->
                    FilterChip(
                        selected = state.selectedCategoryId == cat.id,
                        onClick = { onSelectCategory(cat.id) },
                        label = { Text(cat.name) }
                    )
                }
                state.collections.forEach { col ->
                    FilterChip(
                        selected = state.selectedCollectionId == col.id,
                        onClick = { onSelectCollection(col.id) },
                        label = { Text("📁 ${col.name}") }
                    )
                }
                state.tags.forEach { tag ->
                    FilterChip(
                        selected = state.selectedTagId == tag.id,
                        onClick = { onSelectTag(tag.id) },
                        label = { Text("#${tag.name}") }
                    )
                }
            }

            if (state.filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.library_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.library_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onImportClick) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_add_book))
                        }
                    }
                }
            } else if (state.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredBooks, key = { it.book.id }) { item ->
                        BookGridCard(
                            item = item,
                            onOpen = { onOpenBook(item.book.id) },
                            onToggleFavorite = { onToggleFavorite(item) },
                            onEdit = { editingBook = item },
                            onChangeCover = {
                                coverTargetBookId = item.book.id
                                coverPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRelocate = {
                                relocatingBookId = item.book.id
                                relocateLauncher.launch(DocumentFormat.safPickerMimeTypes)
                            },
                            onDelete = { deletingBook = item }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.filteredBooks, key = { it.book.id }) { item ->
                        BookListCard(
                            item = item,
                            onOpen = { onOpenBook(item.book.id) },
                            onToggleFavorite = { onToggleFavorite(item) },
                            onEdit = { editingBook = item },
                            onChangeCover = {
                                coverTargetBookId = item.book.id
                                coverPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRelocate = {
                                relocatingBookId = item.book.id
                                relocateLauncher.launch(DocumentFormat.safPickerMimeTypes)
                            },
                            onDelete = { deletingBook = item }
                        )
                    }
                }
            }
        }
    }

    // Metadata Editor Dialog
    editingBook?.let { item ->
        EditBookMetadataDialog(
            item = item,
            allCollections = state.collections,
            onDismiss = { editingBook = null },
            onCreateCollection = onCreateCollection,
            onSave = { title, author, desc, pub, lang, cat, tagsCsv, colIds ->
                onUpdateMetadata(item.book.id, title, author, desc, pub, lang, cat, tagsCsv, colIds)
                editingBook = null
            }
        )
    }

    // Delete Confirmation Dialog (never deletes original external file; gives option for notes)
    deletingBook?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingBook = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_message, item.book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBook(item.book.id, false)
                        deletingBook = null
                    }
                ) {
                    Text(stringResource(R.string.library_delete_keep_notes))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deletingBook = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onDeleteBook(item.book.id, true)
                            deletingBook = null
                        }
                    ) {
                        Text(
                            stringResource(R.string.library_delete_remove_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    // Duplicate Resolution Dialog
    state.duplicateQueue.firstOrNull()?.let { dupe ->
        DuplicateResolutionDialog(
            duplicate = dupe,
            onSkip = onDuplicateSkip,
            onKeepBoth = onDuplicateKeepBoth,
            onReplaceUri = onDuplicateReplaceUri
        )
    }
}

@Composable
private fun SortMenuOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (selected) "✓ $label" else label) },
        onClick = onClick
    )
}

@Composable
private fun BookGridCard(
    item: BookWithDetails,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onChangeCover: () -> Unit,
    onRelocate: () -> Unit,
    onDelete: () -> Unit
) {
    val book = item.book
    val format = DocumentFormat.fromName(book.formatName)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("book_card_${book.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BookCoverThumbnail(
                title = book.title,
                formatLabel = format.displayLabel,
                coverPath = book.coverUri,
                modifier = Modifier.fillMaxWidth()
            )
            if (book.isFileMissing) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRelocate)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.library_missing_file_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LinearProgressIndicator(
                progress = { book.readingProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(book.readingProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (book.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.library_filter_favorites),
                            tint = if (book.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(18.dp))
                        }
                        BookActionsDropdown(
                            expanded = menuExpanded,
                            isMissing = book.isFileMissing,
                            onDismiss = { menuExpanded = false },
                            onEdit = { menuExpanded = false; onEdit() },
                            onChangeCover = { menuExpanded = false; onChangeCover() },
                            onRelocate = { menuExpanded = false; onRelocate() },
                            onDelete = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookListCard(
    item: BookWithDetails,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onChangeCover: () -> Unit,
    onRelocate: () -> Unit,
    onDelete: () -> Unit
) {
    val book = item.book
    val format = DocumentFormat.fromName(book.formatName)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("book_list_item_${book.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumbnail(
                title = book.title,
                formatLabel = format.displayLabel,
                coverPath = book.coverUri,
                modifier = Modifier.width(64.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${format.displayLabel} • ${formatFileSize(book.fileSizeBytes)} • ${(book.readingProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.isFileMissing) {
                    TextButton(
                        onClick = onRelocate,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_relocate), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.library_filter_favorites),
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_edit))
                }
                BookActionsDropdown(
                    expanded = menuExpanded,
                    isMissing = book.isFileMissing,
                    onDismiss = { menuExpanded = false },
                    onEdit = { menuExpanded = false; onEdit() },
                    onChangeCover = { menuExpanded = false; onChangeCover() },
                    onRelocate = { menuExpanded = false; onRelocate() },
                    onDelete = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun BookActionsDropdown(
    expanded: Boolean,
    isMissing: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onChangeCover: () -> Unit,
    onRelocate: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_edit_metadata)) },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            onClick = onEdit
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_change_cover)) },
            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
            onClick = onChangeCover
        )
        if (isMissing) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_relocate)) },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                onClick = onRelocate
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = onDelete
        )
    }
}

@Composable
private fun EditBookMetadataDialog(
    item: BookWithDetails,
    allCollections: List<CollectionEntity>,
    onDismiss: () -> Unit,
    onCreateCollection: (String) -> Unit,
    onSave: (String, String, String, String, String, String?, String, List<String>) -> Unit
) {
    var title by remember(item) { mutableStateOf(item.book.title) }
    var author by remember(item) { mutableStateOf(item.book.author) }
    var description by remember(item) { mutableStateOf(item.book.description) }
    var publisher by remember(item) { mutableStateOf(item.book.publisher) }
    var language by remember(item) { mutableStateOf(item.book.language) }
    var category by remember(item) { mutableStateOf(item.category?.name.orEmpty()) }
    var tagsCsv by remember(item) { mutableStateOf(item.tags.joinToString(", ") { it.name }) }
    val selectedCollections = remember(item) {
        mutableStateListOf<String>().apply { addAll(item.collections.map { it.id }) }
    }
    var newColName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_edit_metadata)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.library_field_title)) }, singleLine = true)
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text(stringResource(R.string.library_field_author)) }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(stringResource(R.string.library_field_category)) }, singleLine = true)
                OutlinedTextField(value = tagsCsv, onValueChange = { tagsCsv = it }, label = { Text(stringResource(R.string.library_field_tags)) }, singleLine = true)
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text(stringResource(R.string.library_field_publisher)) }, singleLine = true)
                OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text(stringResource(R.string.library_field_language)) }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.library_field_description)) }, minLines = 2, maxLines = 4)

                Text(
                    text = stringResource(R.string.library_manage_collections),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                allCollections.forEach { col ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (col.id in selectedCollections) selectedCollections.remove(col.id)
                                else selectedCollections.add(col.id)
                            }
                    ) {
                        Checkbox(
                            checked = col.id in selectedCollections,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (col.id !in selectedCollections) selectedCollections.add(col.id)
                                } else selectedCollections.remove(col.id)
                            }
                        )
                        Text(col.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newColName,
                        onValueChange = { newColName = it },
                        placeholder = { Text(stringResource(R.string.library_collection_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (newColName.isNotBlank()) {
                            onCreateCollection(newColName)
                            newColName = ""
                        }
                    }) {
                        Text(stringResource(R.string.library_new_collection))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(title, author, description, publisher, language, category.ifBlank { null }, tagsCsv, selectedCollections.toList())
            }) {
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

@Composable
private fun DuplicateResolutionDialog(
    duplicate: DuplicatePromptState,
    onSkip: () -> Unit,
    onKeepBoth: () -> Unit,
    onReplaceUri: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.library_duplicate_title)) },
        text = {
            Text(
                stringResource(
                    R.string.library_duplicate_message,
                    duplicate.candidateBook.originalFilename,
                    duplicate.existingBook.title
                )
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onReplaceUri, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.library_duplicate_replace_uri))
                }
                OutlinedButton(onClick = onKeepBoth, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.library_duplicate_keep_both))
                }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.library_duplicate_skip))
                }
            }
        }
    )
}
