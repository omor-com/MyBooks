package com.example.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.BookWithDetails
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.resolvedReadingStatus
import com.example.ui.viewmodel.DuplicatePromptState

@Composable
internal fun EditBookMetadataDialog(
    item: BookWithDetails,
    allCategories: List<CategoryEntity>,
    allCollections: List<CollectionEntity>,
    onDismiss: () -> Unit,
    onCreateCollection: (String) -> Unit,
    onSetReadingStatus: (ReadingStatus) -> Unit,
    onChangeCover: () -> Unit,
    onRestoreDefaultCover: () -> Unit,
    onSave: (String, String, String, String, String, String?, String, List<String>) -> Unit
) {
    var title by remember(item) { mutableStateOf(item.book.title) }
    var author by remember(item) { mutableStateOf(item.book.author) }
    var description by remember(item) { mutableStateOf(item.book.description) }
    var publisher by remember(item) { mutableStateOf(item.book.publisher) }
    var language by remember(item) { mutableStateOf(item.book.language) }
    var category by remember(item) { mutableStateOf(item.category?.name.orEmpty()) }
    var tagsCsv by remember(item) { mutableStateOf(item.tags.joinToString(", ") { it.name }) }
    var readingStatus by remember(item) { mutableStateOf(item.book.resolvedReadingStatus()) }
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
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.library_original_filename),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.book.originalFilename,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.library_set_reading_status),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ReadingStatus.WANT_TO_READ to R.string.library_status_want_to_read,
                        ReadingStatus.CURRENTLY_READING to R.string.library_status_currently_reading,
                        ReadingStatus.FINISHED to R.string.library_status_finished
                    ).forEach { (st, labelRes) ->
                        FilterChip(
                            selected = readingStatus == st,
                            onClick = {
                                readingStatus = st
                                onSetReadingStatus(st)
                            },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onChangeCover, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.library_change_cover), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!item.book.coverUri.isNullOrBlank()) {
                        OutlinedButton(onClick = onRestoreDefaultCover, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.library_restore_cover), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.library_field_title)) }, singleLine = true)
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text(stringResource(R.string.library_field_author)) }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(stringResource(R.string.library_field_category)) }, singleLine = true)
                if (allCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allCategories.forEach { cat ->
                            FilterChip(
                                selected = category.equals(cat.name, ignoreCase = true),
                                onClick = {
                                    category = if (category.equals(cat.name, ignoreCase = true)) "" else cat.name
                                },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
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
internal fun ManageCategoriesDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (categoryId: String, reassignToCategoryId: String?) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var renamingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var reassignTargetId by remember { mutableStateOf<String?>(null) }
    var reassignDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_manage_categories)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.library_manage_categories_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = { Text(stringResource(R.string.library_category_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onCreateCategory(newCategoryName)
                            newCategoryName = ""
                        },
                        enabled = newCategoryName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.library_new_category))
                    }
                }
                if (categories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_no_categories),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    categories.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    renamingCategory = cat
                                    renameInput = cat.name
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                                }
                                IconButton(onClick = {
                                    deletingCategory = cat
                                    reassignTargetId = null
                                }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )

    renamingCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingCategory = null },
            title = { Text(stringResource(R.string.library_rename_category)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(R.string.library_field_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameCategory(target.id, renameInput)
                        renamingCategory = null
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingCategory = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    deletingCategory?.let { target ->
        val otherCategories = categories.filter { it.id != target.id }
        val selectedLabel = otherCategories.firstOrNull { it.id == reassignTargetId }?.name
            ?: stringResource(R.string.library_reassign_uncategorized)
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("${stringResource(R.string.action_delete)}: ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.library_reassign_category_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Box {
                        OutlinedButton(
                            onClick = { reassignDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedLabel)
                        }
                        DropdownMenu(
                            expanded = reassignDropdownExpanded,
                            onDismissRequest = { reassignDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_reassign_uncategorized)) },
                                onClick = { reassignTargetId = null; reassignDropdownExpanded = false }
                            )
                            otherCategories.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    onClick = { reassignTargetId = option.id; reassignDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteCategory(target.id, reassignTargetId)
                    deletingCategory = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
internal fun ManageCollectionsDialog(
    collections: List<CollectionEntity>,
    onDismiss: () -> Unit,
    onCreateCollection: (String, String) -> Unit,
    onUpdateCollection: (String, String, String) -> Unit,
    onDeleteCollection: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CollectionEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_manage_collections_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(stringResource(R.string.library_collection_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onCreateCollection(newName, "")
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.library_new_collection))
                    }
                }
                if (collections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_no_collections),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    collections.forEach { col ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(col.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    if (col.description.isNotBlank()) {
                                        Text(col.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = {
                                    editing = col
                                    editName = col.name
                                    editDesc = col.description
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                                }
                                IconButton(onClick = { onDeleteCollection(col.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )

    editing?.let { target ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.library_edit_collection)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.library_collection_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text(stringResource(R.string.library_collection_desc_hint)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCollection(target.id, editName, editDesc)
                        editing = null
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
internal fun TrashBinDialog(
    deletedBooks: List<BookWithDetails>,
    onDismiss: () -> Unit,
    onRestoreBook: (String) -> Unit,
    onDeletePermanently: (BookWithDetails) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_trash_title)) },
        text = {
            if (deletedBooks.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_trash_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    deletedBooks.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.book.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.book.originalFilename,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onRestoreBook(item.book.id) }) {
                                    Icon(
                                        Icons.Rounded.RestoreFromTrash,
                                        contentDescription = stringResource(R.string.library_trash_restore),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeletePermanently(item) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.library_trash_delete_forever),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
internal fun DuplicateResolutionDialog(
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
