package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.dao.AnnotationDao
import com.example.data.local.dao.LibraryDao
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookWithDetails
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.TagEntity
import com.example.data.reader.DocumentFormat
import com.example.data.reader.DocumentValidator
import com.example.data.reader.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface ImportOutcome {
    data class Imported(val book: BookEntity) : ImportOutcome
    data class DuplicateFound(
        val existingBook: BookEntity,
        val candidateBook: BookEntity,
        val candidateCoverBytes: ByteArray?
    ) : ImportOutcome
    data class Failed(val filename: String, val reason: String) : ImportOutcome
}

class LibraryRepository(
    private val context: Context,
    private val libraryDao: LibraryDao,
    private val annotationDao: AnnotationDao
) {
    val allBooksWithDetails: Flow<List<BookWithDetails>> = libraryDao.observeAllBooksWithDetails()
    val categories: Flow<List<CategoryEntity>> = libraryDao.observeCategories()
    val tags: Flow<List<TagEntity>> = libraryDao.observeTags()
    val collections: Flow<List<CollectionEntity>> = libraryDao.observeCollections()
    val allBookmarks: Flow<List<BookmarkEntity>> = annotationDao.observeAllBookmarks()
    val allHighlights: Flow<List<HighlightEntity>> = annotationDao.observeAllHighlights()
    val allNotes: Flow<List<NoteEntity>> = annotationDao.observeAllNotes()

    fun observeBook(bookId: String): Flow<BookWithDetails?> = libraryDao.observeBookWithDetails(bookId)
    fun observeBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> = annotationDao.observeBookmarksForBook(bookId)
    fun observeHighlightsForBook(bookId: String): Flow<List<HighlightEntity>> = annotationDao.observeHighlightsForBook(bookId)
    fun observeNotesForBook(bookId: String): Flow<List<NoteEntity>> = annotationDao.observeNotesForBook(bookId)

    suspend fun getBookById(bookId: String): BookEntity? = libraryDao.getBookById(bookId)
    suspend fun getBookWithDetails(bookId: String): BookWithDetails? = libraryDao.getBookWithDetails(bookId)
    suspend fun getProgressForBook(bookId: String): ReadingProgressEntity? = libraryDao.getProgressForBook(bookId)

    suspend fun importDocument(uri: Uri, takePersistablePermission: Boolean = true): ImportOutcome =
        withContext(Dispatchers.IO) {
            if (takePersistablePermission) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers (like external VIEW intents) do not grant persistable permission
                }
            }

            val filename = resolveDisplayName(uri)
            val mimeType = context.contentResolver.getType(uri)
            val validation = DocumentValidator.validateAndInspect(
                contentResolver = context.contentResolver,
                uri = uri,
                displayName = filename,
                mimeType = mimeType
            )

            val info = validation.getOrElse { error ->
                return@withContext ImportOutcome.Failed(
                    filename = filename,
                    reason = error.localizedMessage ?: "Unsupported or inaccessible document"
                )
            }

            // If non-persistable content URI (e.g. received via Share/Open intent from external app),
            // copy to app-private imported_books directory so reading remains offline and reliable.
            val storedUriString = if (!canPersistUri(uri)) {
                copyToPrivateStorage(uri, filename) ?: uri.toString()
            } else {
                uri.toString()
            }

            var title = filename.substringBeforeLast('.').ifBlank { filename }
            var author = ""
            var publisher = ""
            var language = ""
            var description = ""
            var coverBytes: ByteArray? = null

            if (info.format == DocumentFormat.EPUB) {
                EpubParser.parse(context.contentResolver, uri).onSuccess { epub ->
                    if (epub.title.isNotBlank()) title = epub.title
                    author = epub.author
                    publisher = epub.publisher
                    language = epub.language
                    description = epub.description
                    coverBytes = epub.coverBytes
                }
            }

            val bookId = UUID.randomUUID().toString()
            val candidate = BookEntity(
                id = bookId,
                title = title,
                author = author,
                description = description,
                publisher = publisher,
                language = language,
                originalFilename = filename,
                formatName = info.format.name,
                fileUri = storedUriString,
                fileSizeBytes = info.fileSizeBytes,
                contentHash = info.contentHash
            )

            // Duplicate check by SHA-256 content hash or (filename + size)
            val existing = libraryDao.findByContentHash(info.contentHash)
                ?: libraryDao.findByFilenameAndSize(filename, info.fileSizeBytes)

            if (existing != null) {
                return@withContext ImportOutcome.DuplicateFound(
                    existingBook = existing,
                    candidateBook = candidate,
                    candidateCoverBytes = coverBytes
                )
            }

            val saved = persistBookWithOptionalCover(candidate, coverBytes)
            ImportOutcome.Imported(saved)
        }

    suspend fun confirmImportKeepBoth(candidate: BookEntity, coverBytes: ByteArray?): BookEntity =
        withContext(Dispatchers.IO) {
            persistBookWithOptionalCover(candidate, coverBytes)
        }

    suspend fun confirmReplaceExistingUri(existingBookId: String, candidate: BookEntity) =
        withContext(Dispatchers.IO) {
            libraryDao.relocateBookFile(
                bookId = existingBookId,
                newUri = candidate.fileUri,
                filename = candidate.originalFilename,
                sizeBytes = candidate.fileSizeBytes,
                hash = candidate.contentHash
            )
        }

    suspend fun relocateMissingBook(bookId: String, newUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        newUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                val filename = resolveDisplayName(newUri)
                val mime = context.contentResolver.getType(newUri)
                val info = DocumentValidator.validateAndInspect(
                    contentResolver = context.contentResolver,
                    uri = newUri,
                    displayName = filename,
                    mimeType = mime
                ).getOrThrow()

                libraryDao.relocateBookFile(
                    bookId = bookId,
                    newUri = newUri.toString(),
                    filename = filename,
                    sizeBytes = info.fileSizeBytes,
                    hash = info.contentHash
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun verifyBookAccessibility(book: BookEntity): Boolean = withContext(Dispatchers.IO) {
        val accessible = try {
            val uri = Uri.parse(book.fileUri)
            if (uri.scheme == "file") {
                File(uri.path.orEmpty()).let { it.exists() && it.canRead() }
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (_: Exception) {
            false
        }
        if (book.isFileMissing == accessible) {
            libraryDao.setFileMissing(book.id, !accessible)
        }
        accessible
    }

    suspend fun refreshMissingFileStates() = withContext(Dispatchers.IO) {
        libraryDao.getAllBooksSnapshot().forEach { verifyBookAccessibility(it) }
    }

    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String,
        description: String,
        publisher: String,
        language: String,
        categoryName: String?,
        tagNames: List<String>,
        collectionIds: List<String>
    ) = withContext(Dispatchers.IO) {
        val existing = libraryDao.getBookById(bookId) ?: return@withContext
        val categoryId = if (!categoryName.isNullOrBlank()) {
            val clean = categoryName.trim()
            val allCats = libraryDao.getAllCategoriesSnapshot()
            val match = allCats.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            if (match != null) match.id else {
                val newCat = CategoryEntity(id = UUID.randomUUID().toString(), name = clean)
                libraryDao.insertCategory(newCat)
                newCat.id
            }
        } else null

        val existingTags = libraryDao.getAllTagsSnapshot()
        val tagIds = tagNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { tagName ->
            val match = existingTags.firstOrNull { it.name.equals(tagName, ignoreCase = true) }
            if (match != null) match.id else {
                val newTag = TagEntity(id = UUID.randomUUID().toString(), name = tagName)
                libraryDao.insertTag(newTag)
                newTag.id
            }
        }

        libraryDao.updateBook(
            existing.copy(
                title = title.trim().ifBlank { existing.title },
                author = author.trim(),
                description = description.trim(),
                publisher = publisher.trim(),
                language = language.trim(),
                categoryId = categoryId,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        libraryDao.replaceBookTagsAndCollections(bookId, tagIds, collectionIds)
    }

    suspend fun setBookCover(bookId: String, imageUri: Uri) = withContext(Dispatchers.IO) {
        val existing = libraryDao.getBookById(bookId) ?: return@withContext
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val destFile = File(coversDir, "$bookId.jpg")
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        libraryDao.updateBook(
            existing.copy(coverUri = destFile.absolutePath, updatedAtEpochMs = System.currentTimeMillis())
        )
    }

    suspend fun createCollection(name: String, description: String = ""): CollectionEntity =
        withContext(Dispatchers.IO) {
            val c = CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim()
            )
            libraryDao.insertCollection(c)
            c
        }

    suspend fun toggleFavorite(bookId: String, currentFavorite: Boolean) {
        libraryDao.setFavorite(bookId, !currentFavorite)
    }

    suspend fun removeBookFromLibrary(bookId: String, deleteNotes: Boolean) {
        libraryDao.removeBook(bookId, deleteNotes)
    }

    suspend fun saveReadingProgress(
        bookId: String,
        progressFraction: Float,
        pageIndex: Int,
        totalPages: Int,
        chapterIndex: Int,
        totalChapters: Int,
        scrollOffset: Int,
        locatorJson: String
    ) {
        val now = System.currentTimeMillis()
        libraryDao.saveReadingProgress(
            ReadingProgressEntity(
                bookId = bookId,
                progressFraction = progressFraction.coerceIn(0f, 1f),
                pageIndex = pageIndex,
                totalPages = maxOf(1, totalPages),
                chapterIndex = chapterIndex,
                totalChapters = maxOf(1, totalChapters),
                scrollOffset = scrollOffset,
                locatorJson = locatorJson,
                updatedAtEpochMs = now
            ),
            openedAt = now
        )
    }

    suspend fun addBookmark(
        bookId: String,
        title: String,
        locatorJson: String,
        locatorDisplay: String,
        pageIndex: Int,
        chapterIndex: Int,
        scrollOffset: Int
    ) {
        annotationDao.upsertBookmark(
            BookmarkEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                title = title,
                locatorJson = locatorJson,
                locatorDisplay = locatorDisplay,
                pageIndex = pageIndex,
                chapterIndex = chapterIndex,
                scrollOffset = scrollOffset
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: String) = annotationDao.deleteBookmark(bookmarkId)

    suspend fun addHighlight(
        bookId: String,
        bookTitle: String,
        selectedText: String,
        locatorJson: String,
        locatorDisplay: String,
        pageIndex: Int,
        chapterIndex: Int,
        colorHex: String = "#FFB703"
    ) {
        annotationDao.upsertHighlight(
            HighlightEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                bookTitleSnapshot = bookTitle,
                selectedText = selectedText,
                locatorJson = locatorJson,
                locatorDisplay = locatorDisplay,
                pageIndex = pageIndex,
                chapterIndex = chapterIndex,
                colorHex = colorHex
            )
        )
    }

    suspend fun deleteHighlight(highlightId: String) = annotationDao.deleteHighlight(highlightId)

    suspend fun saveNote(
        id: String? = null,
        bookId: String?,
        bookTitleSnapshot: String,
        title: String,
        content: String,
        locatorJson: String = "",
        locatorDisplay: String = "",
        pageIndex: Int = 0,
        chapterIndex: Int = 0,
        scrollOffset: Int = 0
    ) {
        val now = System.currentTimeMillis()
        annotationDao.upsertNote(
            NoteEntity(
                id = id ?: UUID.randomUUID().toString(),
                bookId = bookId,
                bookTitleSnapshot = bookTitleSnapshot,
                title = title.trim(),
                content = content.trim(),
                locatorJson = locatorJson,
                locatorDisplay = locatorDisplay,
                pageIndex = pageIndex,
                chapterIndex = chapterIndex,
                scrollOffset = scrollOffset,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
    }

    suspend fun deleteNote(noteId: String) = annotationDao.deleteNote(noteId)

    fun calculateAppCacheSizeBytes(): Long {
        fun dirSize(f: File): Long {
            if (!f.exists()) return 0L
            if (f.isFile) return f.length()
            return f.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }
        return dirSize(context.cacheDir)
    }

    fun clearAppCache() {
        context.cacheDir.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }

    private suspend fun persistBookWithOptionalCover(book: BookEntity, coverBytes: ByteArray?): BookEntity {
        var finalBook = book
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val coverFile = File(coversDir, "${book.id}.jpg")
            coverFile.writeBytes(coverBytes)
            finalBook = book.copy(coverUri = coverFile.absolutePath)
        }
        libraryDao.insertBook(finalBook)
        return finalBook
    }

    private fun canPersistUri(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    private fun copyToPrivateStorage(uri: Uri, filename: String): String? {
        return try {
            val dir = File(context.filesDir, "imported_books").apply { mkdirs() }
            val safeName = "${UUID.randomUUID()}_${filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val outFile = File(dir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Uri.fromFile(outFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "document" } ?: "document"
    }
}
