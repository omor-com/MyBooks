package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.MyBooksDatabase
import com.example.data.local.entity.BookCollectionCrossRef
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.TagEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.sanitizeZipEntryPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class BackupMode {
    METADATA_ONLY, SELECTED_BOOKS, FULL_LIBRARY
}

enum class DuplicatePolicy {
    SKIP_DUPLICATES, KEEP_BOTH, OVERWRITE_MATCHES
}

@Serializable
data class BackupManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appId: String = "com.mybooks.omor.dev",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val mode: String = BackupMode.METADATA_ONLY.name,
    val bookCount: Int = 0,
    val bookmarkCount: Int = 0,
    val noteCount: Int = 0,
    val highlightCount: Int = 0,
    val sha256Checksums: Map<String, String> = emptyMap(),
    val excludedFiles: List<String> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupLibraryPayload(
    val schemaVersion: Int = BackupManifest.CURRENT_SCHEMA_VERSION,
    val books: List<BookEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val bookTagCrossRefs: List<BookTagCrossRef> = emptyList(),
    val bookCollectionCrossRefs: List<BookCollectionCrossRef> = emptyList(),
    val readingProgress: List<ReadingProgressEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val includedBookFiles: Map<String, String> = emptyMap(), // bookId -> zip entry path
    val includedCoverFiles: Map<String, String> = emptyMap(), // bookId -> zip entry path
    val includesPageTurnSound: Boolean = false
)

data class BackupCreateResult(
    val bookCount: Int,
    val excludedFiles: List<String>
)

data class InspectedBackup(
    val manifest: BackupManifest,
    val payload: BackupLibraryPayload,
    val stagedFilesDir: File
)

data class RestoreSummary(
    val booksProcessed: Int,
    val bookmarksProcessed: Int,
    val notesProcessed: Int,
    val skippedDuplicates: Int
)

class BackupManager(
    private val context: Context,
    private val database: MyBooksDatabase,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundFileProvider: () -> File
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val libraryDao = database.libraryDao()
    private val annotationDao = database.annotationDao()

    suspend fun createBackup(
        destinationUri: Uri,
        mode: BackupMode,
        selectedBookIds: Set<String>,
        preferences: UserPreferences
    ): Result<BackupCreateResult> = withContext(Dispatchers.IO) {
        try {
            val allBooks = libraryDao.getAllBooksSnapshot()
            val targetBooks = when (mode) {
                BackupMode.METADATA_ONLY -> allBooks
                BackupMode.SELECTED_BOOKS -> allBooks.filter { it.id in selectedBookIds }
                BackupMode.FULL_LIBRARY -> allBooks
            }
            val targetBookIds = targetBooks.map { it.id }.toSet()

            val categories = libraryDao.getAllCategoriesSnapshot()
            val tags = libraryDao.getAllTagsSnapshot()
            val collections = libraryDao.getAllCollectionsSnapshot()
            val tagRefs = libraryDao.getAllBookTagCrossRefs().filter { it.bookId in targetBookIds }
            val collectionRefs = libraryDao.getAllBookCollectionCrossRefs().filter { it.bookId in targetBookIds }
            val progressList = libraryDao.getAllProgressSnapshot().filter { it.bookId in targetBookIds }
            val bookmarks = annotationDao.getAllBookmarksSnapshot().filter { it.bookId in targetBookIds }
            val highlights = annotationDao.getAllHighlightsSnapshot().filter { it.bookId in targetBookIds }
            val notes = annotationDao.getAllNotesSnapshot().filter { it.bookId == null || it.bookId in targetBookIds }

            val checksums = mutableMapOf<String, String>()
            val excludedFiles = mutableListOf<String>()
            val includedBookFiles = mutableMapOf<String, String>()
            val includedCoverFiles = mutableMapOf<String, String>()

            val tmpZip = File(context.cacheDir, "backup_staging_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(tmpZip)).use { zos ->
                // 1. Include cover images from app-private files if available
                targetBooks.forEach { book ->
                    val coverPath = book.coverUri
                    if (!coverPath.isNullOrBlank()) {
                        val coverFile = File(coverPath)
                        if (coverFile.exists() && coverFile.isFile) {
                            val entryName = "covers/${book.id}.jpg"
                            val bytes = coverFile.readBytes()
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(bytes)
                            zos.closeEntry()
                            checksums[entryName] = sha256(bytes)
                            includedCoverFiles[book.id] = entryName
                        }
                    }
                }

                // 2. Include page-turn sound if downloaded
                val soundFile = soundFileProvider()
                var includesSound = false
                if (soundFile.exists() && soundFile.isFile && soundFile.length() > 1024L) {
                    val entryName = "audio/bookreader-page-flip.mp3"
                    val bytes = soundFile.readBytes()
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(bytes)
                    zos.closeEntry()
                    checksums[entryName] = sha256(bytes)
                    includesSound = true
                }

                // 3. Include book document files if mode includes files
                if (mode == BackupMode.SELECTED_BOOKS || mode == BackupMode.FULL_LIBRARY) {
                    targetBooks.forEach { book ->
                        try {
                            val uri = Uri.parse(book.fileUri)
                            val safeExt = book.originalFilename.substringAfterLast('.', "bin")
                                .replace(Regex("[^a-zA-Z0-9]"), "")
                                .ifBlank { "bin" }
                            val entryName = "books/${book.id}.$safeExt"
                            val digest = MessageDigest.getInstance("SHA-256")
                            val stream = context.contentResolver.openInputStream(uri)
                            if (stream == null) {
                                excludedFiles += book.originalFilename
                            } else {
                                zos.putNextEntry(ZipEntry(entryName))
                                stream.use { input ->
                                    val buf = ByteArray(16 * 1024)
                                    while (true) {
                                        val read = input.read(buf)
                                        if (read <= 0) break
                                        zos.write(buf, 0, read)
                                        digest.update(buf, 0, read)
                                    }
                                }
                                zos.closeEntry()
                                checksums[entryName] = digest.digest().joinToString("") { "%02x".format(it) }
                                includedBookFiles[book.id] = entryName
                            }
                        } catch (_: Exception) {
                            excludedFiles += book.originalFilename
                        }
                    }
                }

                // 4. Write library_data.json
                val payload = BackupLibraryPayload(
                    schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
                    books = targetBooks,
                    categories = categories,
                    tags = tags,
                    collections = collections,
                    bookTagCrossRefs = tagRefs,
                    bookCollectionCrossRefs = collectionRefs,
                    readingProgress = progressList,
                    bookmarks = bookmarks,
                    highlights = highlights,
                    notes = notes,
                    preferences = preferences,
                    includedBookFiles = includedBookFiles,
                    includedCoverFiles = includedCoverFiles,
                    includesPageTurnSound = includesSound
                )
                val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("library_data.json"))
                zos.write(payloadBytes)
                zos.closeEntry()
                checksums["library_data.json"] = sha256(payloadBytes)

                // 5. Write manifest.json
                val manifest = BackupManifest(
                    schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
                    mode = mode.name,
                    bookCount = targetBooks.size,
                    bookmarkCount = bookmarks.size,
                    noteCount = notes.size,
                    highlightCount = highlights.size,
                    sha256Checksums = checksums,
                    excludedFiles = excludedFiles
                )
                val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestBytes)
                zos.closeEntry()
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                FileInputStream(tmpZip).use { it.copyTo(out) }
            } ?: run {
                tmpZip.delete()
                return@withContext Result.failure(IllegalStateException("Cannot write to destination URI"))
            }
            tmpZip.delete()
            Result.success(BackupCreateResult(targetBooks.size, excludedFiles))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectAndValidateBackup(sourceUri: Uri): Result<InspectedBackup> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
        try {
            stagingDir.mkdirs()
            val maxEntryBytes = 64L * 1024L * 1024L
            val maxTotalBytes = 256L * 1024L * 1024L
            var totalBytes = 0L
            val actualChecksums = mutableMapOf<String, String>()

            context.contentResolver.openInputStream(sourceUri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val safePath = sanitizeZipEntryPath(entry.name)
                                ?: throw SecurityException("Unsafe archive entry path: ${entry.name}")
                            val outFile = File(stagingDir, safePath)
                            val canonicalStaging = stagingDir.canonicalPath + File.separator
                            if (!outFile.canonicalPath.startsWith(canonicalStaging)) {
                                throw SecurityException("Path traversal detected: ${entry.name}")
                            }
                            outFile.parentFile?.mkdirs()

                            val digest = MessageDigest.getInstance("SHA-256")
                            var entryBytes = 0L
                            FileOutputStream(outFile).use { fos ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val read = zis.read(buf)
                                    if (read <= 0) break
                                    entryBytes += read
                                    totalBytes += read
                                    if (entryBytes > maxEntryBytes || totalBytes > maxTotalBytes) {
                                        throw SecurityException("Backup archive exceeds safe extraction size")
                                    }
                                    fos.write(buf, 0, read)
                                    digest.update(buf, 0, read)
                                }
                            }
                            actualChecksums[safePath] = digest.digest().joinToString("") { "%02x".format(it) }
                        }
                        zis.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("Cannot open selected backup archive")

            val manifestFile = File(stagingDir, "manifest.json")
            val dataFile = File(stagingDir, "library_data.json")
            if (!manifestFile.exists() || !dataFile.exists()) {
                throw IllegalArgumentException("Missing manifest.json or library_data.json in archive")
            }

            val manifest = json.decodeFromString<BackupManifest>(manifestFile.readText(Charsets.UTF_8))
            if (manifest.schemaVersion < 1 || manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION) {
                throw IllegalArgumentException("Unsupported backup schema version: ${manifest.schemaVersion}")
            }

            // Verify SHA-256 checksums for every recorded entry
            manifest.sha256Checksums.forEach { (entryPath, expectedHash) ->
                val actualHash = actualChecksums[entryPath]
                if (actualHash == null || actualHash != expectedHash) {
                    throw SecurityException("Checksum verification failed for $entryPath")
                }
            }

            val payload = migratePayloadIfNeeded(
                json.decodeFromString<BackupLibraryPayload>(dataFile.readText(Charsets.UTF_8))
            )

            Result.success(InspectedBackup(manifest, payload, stagingDir))
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            Result.failure(e)
        }
    }

    suspend fun applyRestore(
        inspected: InspectedBackup,
        duplicatePolicy: DuplicatePolicy
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val payload = inspected.payload
            val existingBooks = libraryDao.getAllBooksSnapshot()
            val existingByHash = existingBooks.filter { it.contentHash.isNotBlank() }.associateBy { it.contentHash }
            val existingById = existingBooks.associateBy { it.id }

            var skippedDuplicates = 0
            val bookIdRemap = mutableMapOf<String, String>()
            val booksToSave = mutableListOf<BookEntity>()

            val restoredBooksDir = File(context.filesDir, "restored_books").apply { mkdirs() }
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

            payload.books.forEach { incomingBook ->
                val duplicate = existingById[incomingBook.id]
                    ?: existingByHash[incomingBook.contentHash]?.takeIf { incomingBook.contentHash.isNotBlank() }

                val targetBookId = when {
                    duplicate == null -> incomingBook.id
                    duplicatePolicy == DuplicatePolicy.SKIP_DUPLICATES -> {
                        skippedDuplicates++
                        return@forEach
                    }
                    duplicatePolicy == DuplicatePolicy.KEEP_BOTH -> UUID.randomUUID().toString()
                    else -> duplicate.id
                }
                bookIdRemap[incomingBook.id] = targetBookId

                // Copy restored book file to app-private restored_books if included in archive
                var finalUri = incomingBook.fileUri
                var isMissing = incomingBook.isFileMissing
                val archivedBookPath = payload.includedBookFiles[incomingBook.id]
                if (archivedBookPath != null) {
                    val stagedBook = File(inspected.stagedFilesDir, archivedBookPath)
                    if (stagedBook.exists()) {
                        val destFile = File(restoredBooksDir, "${targetBookId}_${incomingBook.originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")
                        stagedBook.copyTo(destFile, overwrite = true)
                        finalUri = Uri.fromFile(destFile).toString()
                        isMissing = false
                    }
                }

                // Restore cover image if included
                var finalCover = incomingBook.coverUri
                val archivedCoverPath = payload.includedCoverFiles[incomingBook.id]
                if (archivedCoverPath != null) {
                    val stagedCover = File(inspected.stagedFilesDir, archivedCoverPath)
                    if (stagedCover.exists()) {
                        val destCover = File(coversDir, "$targetBookId.jpg")
                        stagedCover.copyTo(destCover, overwrite = true)
                        finalCover = destCover.absolutePath
                    }
                }

                booksToSave += incomingBook.copy(
                    id = targetBookId,
                    fileUri = finalUri,
                    coverUri = finalCover,
                    isFileMissing = isMissing
                )
            }

            // Restore page-turn sound if included and valid
            var restoredSoundReady = false
            if (payload.includesPageTurnSound) {
                val stagedSound = File(inspected.stagedFilesDir, "audio/bookreader-page-flip.mp3")
                if (stagedSound.exists() && stagedSound.length() > 1024L) {
                    val destSound = soundFileProvider()
                    destSound.parentFile?.mkdirs()
                    stagedSound.copyTo(destSound, overwrite = true)
                    restoredSoundReady = true
                }
            }

            var bookmarksProcessed = 0
            var notesProcessed = 0

            database.withTransaction {
                libraryDao.upsertCategories(payload.categories)
                libraryDao.upsertTags(payload.tags)
                libraryDao.upsertCollections(payload.collections)

                booksToSave.forEach { libraryDao.insertBook(it) }

                payload.bookTagCrossRefs.forEach { ref ->
                    val mapped = bookIdRemap[ref.bookId]
                    if (mapped != null) libraryDao.insertBookTagCrossRef(ref.copy(bookId = mapped))
                }
                payload.bookCollectionCrossRefs.forEach { ref ->
                    val mapped = bookIdRemap[ref.bookId]
                    if (mapped != null) libraryDao.insertBookCollectionCrossRef(ref.copy(bookId = mapped))
                }
                payload.readingProgress.forEach { prog ->
                    val mapped = bookIdRemap[prog.bookId]
                    if (mapped != null) libraryDao.upsertReadingProgress(prog.copy(bookId = mapped))
                }

                val bookmarksToSave = payload.bookmarks.mapNotNull { bm ->
                    val mapped = bookIdRemap[bm.bookId] ?: return@mapNotNull null
                    val newId = if (duplicatePolicy == DuplicatePolicy.KEEP_BOTH) UUID.randomUUID().toString() else bm.id
                    bm.copy(id = newId, bookId = mapped)
                }
                annotationDao.upsertBookmarks(bookmarksToSave)
                bookmarksProcessed = bookmarksToSave.size

                val highlightsToSave = payload.highlights.mapNotNull { hl ->
                    val mapped = bookIdRemap[hl.bookId] ?: return@mapNotNull null
                    val newId = if (duplicatePolicy == DuplicatePolicy.KEEP_BOTH) UUID.randomUUID().toString() else hl.id
                    hl.copy(id = newId, bookId = mapped)
                }
                annotationDao.upsertHighlights(highlightsToSave)

                val notesToSave = payload.notes.mapNotNull { note ->
                    val mappedBook = note.bookId?.let { bookIdRemap[it] ?: return@mapNotNull null }
                    val newId = if (duplicatePolicy == DuplicatePolicy.KEEP_BOTH) UUID.randomUUID().toString() else note.id
                    note.copy(id = newId, bookId = mappedBook)
                }
                annotationDao.upsertNotes(notesToSave)
                notesProcessed = notesToSave.size
            }

            preferencesRepository.restoreFromBackup(
                prefs = payload.preferences,
                soundFileExists = restoredSoundReady || soundFileProvider().exists()
            )

            Result.success(
                RestoreSummary(
                    booksProcessed = booksToSave.size,
                    bookmarksProcessed = bookmarksProcessed,
                    notesProcessed = notesProcessed,
                    skippedDuplicates = skippedDuplicates
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inspected.stagedFilesDir.deleteRecursively()
        }
    }

    private fun migratePayloadIfNeeded(payload: BackupLibraryPayload): BackupLibraryPayload {
        // Explicit migration hook for future backup schema versions
        return payload
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
