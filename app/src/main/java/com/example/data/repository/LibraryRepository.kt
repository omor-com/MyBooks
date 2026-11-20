package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.TagEntity
import com.example.data.reader.DocumentFormat
import com.example.data.reader.DocumentValidator
import com.example.data.reader.EpubParser
import com.example.data.reader.ExtendedDocumentParser
import com.example.data.reader.PdfTextExtractor
import com.example.data.reader.ReaderCategory
import com.example.data.reader.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.TimeZone
import java.util.UUID
import kotlin.math.max

sealed interface ImportOutcome {
    data class Imported(val book: BookEntity) : ImportOutcome
    data class DuplicateFound(
        val existingBook: BookEntity,
        val candidateBook: BookEntity,
        val candidateCoverBytes: ByteArray?
    ) : ImportOutcome
    data class Failed(val filename: String, val reason: String) : ImportOutcome
}

data class BookContentMatch(
    val bookId: String,
    val bookTitle: String,
    val locationLabel: String,
    val snippet: String,
    val pageIndex: Int = 0,
    val chapterIndex: Int = 0
)

data class StorageBreakdown(
    val importedBooksBytes: Long,
    val coversBytes: Long,
    val audioBytes: Long,
    val cacheBytes: Long
) {
    val totalBytes: Long get() = importedBooksBytes + coversBytes + audioBytes + cacheBytes
}

data class ReadingStreakSummary(
    val todayMinutes: Int,
    val totalMinutes: Int,
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val minutesByEpochDay: Map<Long, Int>
)

@Serializable
data class AnnotationExportPayload(
    val exportedAtEpochMs: Long,
    val bookmarks: List<BookmarkEntity>,
    val highlights: List<HighlightEntity>,
    val notes: List<NoteEntity>
)

class LibraryRepository(
    private val context: Context,
    private val libraryDao: LibraryDao,
    private val annotationDao: AnnotationDao
) {
    val allBooksWithDetails: Flow<List<BookWithDetails>> = libraryDao.observeAllBooksWithDetails()
    val deletedBooksWithDetails: Flow<List<BookWithDetails>> = libraryDao.observeDeletedBooksWithDetails()
    val categories: Flow<List<CategoryEntity>> = libraryDao.observeCategories()
    val tags: Flow<List<TagEntity>> = libraryDao.observeTags()
    val collections: Flow<List<CollectionEntity>> = libraryDao.observeCollections()
    val allBookmarks: Flow<List<BookmarkEntity>> = annotationDao.observeAllBookmarks()
    val allHighlights: Flow<List<HighlightEntity>> = annotationDao.observeAllHighlights()
    val allNotes: Flow<List<NoteEntity>> = annotationDao.observeAllNotes()
    val readingSessions: Flow<List<ReadingSessionEntity>> = libraryDao.observeAllReadingSessions()

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

    suspend fun setBookCover(bookId: String, imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val existing = libraryDao.getBookById(bookId) ?: return@withContext false
        val decoded = decodeDownsampledBitmap(imageUri, maxDimPx = 1000) ?: return@withContext false
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val destFile = File(coversDir, "$bookId.jpg")
        try {
            destFile.outputStream().use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        } finally {
            decoded.recycle()
        }
        libraryDao.updateBook(
            existing.copy(coverUri = destFile.absolutePath, updatedAtEpochMs = System.currentTimeMillis())
        )
        true
    }

    suspend fun restoreDefaultCover(bookId: String) = withContext(Dispatchers.IO) {
        val existing = libraryDao.getBookById(bookId) ?: return@withContext
        val oldPath = existing.coverUri
        if (!oldPath.isNullOrBlank()) {
            val f = File(oldPath)
            val coversDir = File(context.filesDir, "covers")
            if (f.parentFile?.absolutePath == coversDir.absolutePath && f.exists()) {
                f.delete()
            }
        }
        libraryDao.clearBookCover(bookId)
    }

    suspend fun createCategory(name: String): CategoryEntity? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext null
        val existing = libraryDao.findCategoryByName(clean)
        if (existing != null) return@withContext existing
        val created = CategoryEntity(id = UUID.randomUUID().toString(), name = clean)
        libraryDao.insertCategory(created)
        created
    }

    suspend fun renameCategory(categoryId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isEmpty()) return@withContext false
        val conflict = libraryDao.findCategoryByName(clean)
        if (conflict != null && conflict.id != categoryId) return@withContext false
        libraryDao.renameCategory(categoryId, clean)
        true
    }

    suspend fun deleteCategory(categoryId: String, reassignToCategoryId: String? = null) =
        withContext(Dispatchers.IO) {
            libraryDao.deleteCategoryWithReassignment(categoryId, reassignToCategoryId)
        }

    suspend fun importExternalWithMetadata(
        uri: Uri,
        title: String,
        author: String,
        categoryName: String?,
        currentPage: Int,
        totalPages: Int,
        currentChapter: Int,
        totalChapters: Int,
        scrollOffset: Int
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        when (val outcome = importDocument(uri, takePersistablePermission = true)) {
            is ImportOutcome.Failed -> Result.failure(IllegalStateException(outcome.reason))
            is ImportOutcome.DuplicateFound -> {
                val target = outcome.existingBook
                if (title.isNotBlank() || author.isNotBlank() || !categoryName.isNullOrBlank()) {
                    val catId = if (!categoryName.isNullOrBlank()) createCategory(categoryName)?.id else target.categoryId
                    libraryDao.updateBook(
                        target.copy(
                            title = title.trim().ifBlank { target.title },
                            author = author.trim().ifBlank { target.author },
                            categoryId = catId,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    )
                }
                val fraction = computeFraction(currentPage, totalPages, currentChapter, totalChapters)
                saveReadingProgress(
                    bookId = target.id,
                    progressFraction = fraction,
                    pageIndex = currentPage,
                    totalPages = totalPages,
                    chapterIndex = currentChapter,
                    totalChapters = totalChapters,
                    scrollOffset = scrollOffset,
                    locatorJson = """{"page":$currentPage,"chapter":$currentChapter,"scroll":$scrollOffset}"""
                )
                Result.success(libraryDao.getBookById(target.id) ?: target)
            }
            is ImportOutcome.Imported -> {
                val imported = outcome.book
                val catId = if (!categoryName.isNullOrBlank()) createCategory(categoryName)?.id else null
                val updated = imported.copy(
                    title = title.trim().ifBlank { imported.title },
                    author = author.trim().ifBlank { imported.author },
                    categoryId = catId,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                libraryDao.updateBook(updated)
                val fraction = computeFraction(currentPage, totalPages, currentChapter, totalChapters)
                saveReadingProgress(
                    bookId = updated.id,
                    progressFraction = fraction,
                    pageIndex = currentPage,
                    totalPages = totalPages,
                    chapterIndex = currentChapter,
                    totalChapters = totalChapters,
                    scrollOffset = scrollOffset,
                    locatorJson = """{"page":$currentPage,"chapter":$currentChapter,"scroll":$scrollOffset}"""
                )
                Result.success(libraryDao.getBookById(updated.id) ?: updated)
            }
        }
    }

    private fun computeFraction(page: Int, totalPages: Int, chapter: Int, totalChapters: Int): Float {
        return if (totalPages > 1) {
            (page + 1).toFloat() / totalPages.toFloat()
        } else {
            (chapter + 1).toFloat() / totalChapters.coerceAtLeast(1).toFloat()
        }.coerceIn(0f, 1f)
    }

    private fun decodeDownsampledBitmap(uri: Uri, maxDimPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType.isNullOrBlank()) return null
            var sampleSize = 1
            while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimPx * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null
            val maxSide = max(raw.width, raw.height)
            if (maxSide <= maxDimPx) {
                raw
            } else {
                val scale = maxDimPx.toFloat() / maxSide.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * scale).toInt().coerceAtLeast(1),
                    (raw.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== raw) raw.recycle()
                scaled
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun createCollection(name: String, description: String = ""): CollectionEntity =
        withContext(Dispatchers.IO) {
            val clean = name.trim()
            val existing = libraryDao.findCollectionByName(clean)
            if (existing != null) return@withContext existing
            val c = CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = clean,
                description = description.trim()
            )
            libraryDao.insertCollection(c)
            c
        }

    suspend fun updateCollection(collectionId: String, newName: String, newDescription: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = newName.trim()
            if (clean.isEmpty()) return@withContext false
            val conflict = libraryDao.findCollectionByName(clean)
            if (conflict != null && conflict.id != collectionId) return@withContext false
            libraryDao.updateCollection(collectionId, clean, newDescription.trim())
            true
        }

    suspend fun deleteCollection(collectionId: String) = withContext(Dispatchers.IO) {
        libraryDao.deleteCollectionById(collectionId)
    }

    suspend fun toggleFavorite(bookId: String, currentFavorite: Boolean) {
        libraryDao.setFavorite(bookId, !currentFavorite)
    }

    suspend fun setReadingStatus(bookId: String, status: ReadingStatus) {
        libraryDao.setReadingStatus(bookId, status.name)
    }

    suspend fun softDeleteBook(bookId: String) {
        libraryDao.softDeleteBook(bookId)
    }

    suspend fun restoreBookFromTrash(bookId: String) {
        libraryDao.restoreDeletedBook(bookId)
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
        val safeFraction = progressFraction.coerceIn(0f, 1f)
        val existing = libraryDao.getBookById(bookId)
        val autoStatusName = when {
            safeFraction >= 0.99f -> ReadingStatus.FINISHED.name
            existing?.readingStatusName == ReadingStatus.FINISHED.name -> ReadingStatus.FINISHED.name
            safeFraction > 0f || pageIndex > 0 || chapterIndex > 0 -> ReadingStatus.CURRENTLY_READING.name
            else -> existing?.readingStatusName ?: ReadingStatus.WANT_TO_READ.name
        }
        libraryDao.saveReadingProgress(
            ReadingProgressEntity(
                bookId = bookId,
                progressFraction = safeFraction,
                pageIndex = pageIndex,
                totalPages = maxOf(1, totalPages),
                chapterIndex = chapterIndex,
                totalChapters = maxOf(1, totalChapters),
                scrollOffset = scrollOffset,
                locatorJson = locatorJson,
                updatedAtEpochMs = now
            ),
            openedAt = now,
            autoStatusName = autoStatusName
        )
    }

    suspend fun recordReadingSession(
        bookId: String?,
        bookTitle: String,
        durationSeconds: Int,
        pagesRead: Int = 0,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long = System.currentTimeMillis(),
        isFocusTimer: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (durationSeconds < 10) return@withContext
        val session = ReadingSessionEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            bookTitleSnapshot = bookTitle,
            epochDay = localEpochDay(endedAtEpochMs),
            durationSeconds = durationSeconds.coerceAtLeast(1),
            pagesRead = pagesRead.coerceAtLeast(0),
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            isFocusTimer = isFocusTimer
        )
        libraryDao.insertReadingSession(session)
    }

    suspend fun searchLibraryContent(
        query: String,
        books: List<BookWithDetails>,
        maxResults: Int = 30
    ): List<BookContentMatch> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val matches = mutableListOf<BookContentMatch>()
        for (details in books) {
            if (matches.size >= maxResults) break
            val book = details.book
            if (book.isDeleted || book.isFileMissing) continue
            val uri = runCatching { Uri.parse(book.fileUri) }.getOrNull() ?: continue
            val format = DocumentFormat.fromName(book.formatName)
            when (format.readerCategory) {
                ReaderCategory.EPUB -> {
                    EpubParser.parse(context.contentResolver, uri).onSuccess { epub ->
                        for ((chIdx, ch) in epub.chapters.withIndex()) {
                            for (block in ch.paragraphs) {
                                val text = blockText(block)
                                val idx = text.indexOf(clean, ignoreCase = true)
                                if (idx >= 0) {
                                    matches += BookContentMatch(
                                        bookId = book.id,
                                        bookTitle = book.title,
                                        locationLabel = ch.title,
                                        snippet = excerptAround(text, idx, clean.length),
                                        chapterIndex = chIdx
                                    )
                                    break
                                }
                            }
                            if (matches.size >= maxResults) break
                        }
                    }
                }
                ReaderCategory.REFLOWABLE_TEXT -> {
                    ExtendedDocumentParser.parseTextDocument(
                        contentResolver = context.contentResolver,
                        uri = uri,
                        format = format,
                        fallbackTitle = book.title
                    ).onSuccess { doc ->
                        for ((secIdx, sec) in doc.sections.withIndex()) {
                            for (block in sec.blocks) {
                                val text = blockText(block)
                                val idx = text.indexOf(clean, ignoreCase = true)
                                if (idx >= 0) {
                                    matches += BookContentMatch(
                                        bookId = book.id,
                                        bookTitle = book.title,
                                        locationLabel = sec.title,
                                        snippet = excerptAround(text, idx, clean.length),
                                        chapterIndex = secIdx
                                    )
                                    break
                                }
                            }
                            if (matches.size >= maxResults) break
                        }
                    }
                }
                ReaderCategory.PDF -> {
                    val hits = PdfTextExtractor.searchPdfText(
                        contentResolver = context.contentResolver,
                        uri = uri,
                        query = clean,
                        maxHits = 3
                    )
                    hits.forEach { hit ->
                        matches += BookContentMatch(
                            bookId = book.id,
                            bookTitle = book.title,
                            locationLabel = "Page ${hit.pageIndex + 1}",
                            snippet = hit.snippet,
                            pageIndex = hit.pageIndex
                        )
                    }
                }
                ReaderCategory.IMAGE_COLLECTION -> Unit
            }
        }
        matches.take(maxResults)
    }

    suspend fun exportAnnotationsMarkdown(destUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val booksById = libraryDao.getAllBooksSnapshot().associateBy { it.id }
            val bookmarks = annotationDao.getAllBookmarksSnapshot()
            val highlights = annotationDao.getAllHighlightsSnapshot()
            val notes = annotationDao.getAllNotesSnapshot()

            val sb = StringBuilder()
            sb.appendLine("# MyBooks Annotations & Notes Export")
            sb.appendLine()
            sb.appendLine("Total: ${bookmarks.size} bookmarks, ${highlights.size} highlights, ${notes.size} notes")
            sb.appendLine()

            if (highlights.isNotEmpty()) {
                sb.appendLine("## Highlights")
                sb.appendLine()
                highlights.forEach { hl ->
                    val bookTitle = booksById[hl.bookId]?.title ?: hl.bookTitleSnapshot
                    sb.appendLine("- **${bookTitle}** (${hl.locatorDisplay.ifBlank { "Page ${hl.pageIndex + 1}" }})")
                    sb.appendLine("  > ${hl.selectedText}")
                }
                sb.appendLine()
            }

            if (notes.isNotEmpty()) {
                sb.appendLine("## Notes")
                sb.appendLine()
                notes.forEach { note ->
                    val bookTitle = note.bookId?.let { booksById[it]?.title } ?: note.bookTitleSnapshot
                    val header = note.title.ifBlank { "Note" }
                    sb.appendLine("### $header")
                    if (bookTitle.isNotBlank()) {
                        sb.appendLine("*Book: $bookTitle* ${if (note.locatorDisplay.isNotBlank()) "— ${note.locatorDisplay}" else ""}")
                    }
                    sb.appendLine()
                    sb.appendLine(note.content)
                    sb.appendLine()
                }
            }

            if (bookmarks.isNotEmpty()) {
                sb.appendLine("## Bookmarks")
                sb.appendLine()
                bookmarks.forEach { bm ->
                    val bookTitle = booksById[bm.bookId]?.title ?: ""
                    sb.appendLine("- **$bookTitle**: ${bm.title} (${bm.locatorDisplay.ifBlank { "Page ${bm.pageIndex + 1}" }})")
                }
                sb.appendLine()
            }

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Cannot write export file"))
            Result.success(bookmarks.size + highlights.size + notes.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportAnnotationsJson(destUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val bookmarks = annotationDao.getAllBookmarksSnapshot()
            val highlights = annotationDao.getAllHighlightsSnapshot()
            val notes = annotationDao.getAllNotesSnapshot()
            val payload = AnnotationExportPayload(
                exportedAtEpochMs = System.currentTimeMillis(),
                bookmarks = bookmarks,
                highlights = highlights,
                notes = notes
            )
            val json = Json { prettyPrint = true; encodeDefaults = true }
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Cannot write JSON export"))
            Result.success(bookmarks.size + highlights.size + notes.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun blockText(block: TextBlock): String = when (block) {
        is TextBlock.Heading -> block.text
        is TextBlock.Paragraph -> block.text
        is TextBlock.Quote -> block.text
        is TextBlock.Code -> block.text
        is TextBlock.BulletItem -> block.text
    }

    private fun excerptAround(text: String, idx: Int, queryLen: Int): String {
        val start = (idx - 35).coerceAtLeast(0)
        val end = (idx + queryLen + 70).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return (prefix + text.substring(start, end) + suffix).replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        fun localEpochDay(epochMs: Long = System.currentTimeMillis()): Long {
            val offset = TimeZone.getDefault().getOffset(epochMs)
            return (epochMs + offset) / 86_400_000L
        }

        fun calculateStreakSummary(
            sessions: List<ReadingSessionEntity>,
            dailyGoalMinutes: Int,
            nowEpochMs: Long = System.currentTimeMillis()
        ): ReadingStreakSummary {
            val todayDay = localEpochDay(nowEpochMs)
            val secByDay = sessions.groupBy { it.epochDay }
                .mapValues { (_, list) -> list.sumOf { it.durationSeconds } }
            val minByDay = secByDay.mapValues { (_, sec) -> sec / 60 }

            val todayMinutes = (secByDay[todayDay] ?: 0) / 60
            val totalMinutes = sessions.sumOf { it.durationSeconds } / 60

            // Current streak counts consecutive days with reading activity (at least 1 min or session) ending today or yesterday
            val qualifiedDays = secByDay.filterValues { it >= 60 }.keys.sorted()
            var currentStreak = 0
            if (qualifiedDays.isNotEmpty()) {
                var checkDay = if ((secByDay[todayDay] ?: 0) >= 60) todayDay else todayDay - 1
                while ((secByDay[checkDay] ?: 0) >= 60) {
                    currentStreak++
                    checkDay--
                }
            }

            var bestStreak = 0
            var run = 0
            var prev: Long? = null
            for (day in qualifiedDays) {
                run = if (prev != null && day == prev + 1L) run + 1 else 1
                if (run > bestStreak) bestStreak = run
                prev = day
            }

            return ReadingStreakSummary(
                todayMinutes = todayMinutes,
                totalMinutes = totalMinutes,
                currentStreakDays = currentStreak,
                bestStreakDays = maxOf(bestStreak, currentStreak),
                minutesByEpochDay = minByDay
            )
        }
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

    fun calculateStorageBreakdown(): StorageBreakdown {
        fun dirSize(f: File): Long {
            if (!f.exists()) return 0L
            if (f.isFile) return f.length()
            return f.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }
        val booksBytes = dirSize(File(context.filesDir, "imported_books")) +
            dirSize(File(context.filesDir, "restored_books"))
        val coversBytes = dirSize(File(context.filesDir, "covers"))
        val audioBytes = dirSize(File(context.filesDir, "audio"))
        val cacheBytes = dirSize(context.cacheDir)
        return StorageBreakdown(
            importedBooksBytes = booksBytes,
            coversBytes = coversBytes,
            audioBytes = audioBytes,
            cacheBytes = cacheBytes
        )
    }

    fun calculateAppCacheSizeBytes(): Long = calculateStorageBreakdown().cacheBytes

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

    fun resolveDisplayName(uri: Uri): String {
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
