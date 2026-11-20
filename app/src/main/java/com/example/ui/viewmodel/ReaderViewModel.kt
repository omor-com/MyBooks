package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.PageTurnSoundManager
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.ComicAndImageLoader
import com.example.data.reader.DocumentFormat
import com.example.data.reader.DocumentSection
import com.example.data.reader.DocumentValidator
import com.example.data.reader.EpubBookContent
import com.example.data.reader.EpubParser
import com.example.data.reader.ExtendedDocumentParser
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ReaderContentState {
    data object Loading : ReaderContentState
    data class PdfReady(
        val book: BookEntity,
        val pageCount: Int,
        val currentPage: Int
    ) : ReaderContentState

    data class EpubReady(
        val book: BookEntity,
        val content: EpubBookContent,
        val currentChapter: Int,
        val initialScrollOffset: Int
    ) : ReaderContentState

    data class TextDocumentReady(
        val book: BookEntity,
        val sections: List<DocumentSection>,
        val currentSection: Int,
        val initialScrollOffset: Int
    ) : ReaderContentState

    data class ImageCollectionReady(
        val book: BookEntity,
        val imageEntries: List<String>, // empty for single image URI
        val currentPage: Int,
        val isSingleImage: Boolean
    ) : ReaderContentState

    data class Error(
        val book: BookEntity?,
        val isMissingFile: Boolean,
        val message: String,
        val isExternalUri: Boolean = false
    ) : ReaderContentState
}

data class ReaderSearchMatch(
    val sectionIndex: Int,
    val sectionTitle: String,
    val snippet: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val appContext: Context,
    private val bookId: String,
    private val externalUri: String? = null,
    private val initialPageOverride: Int?,
    private val initialChapterOverride: Int?,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: PageTurnSoundManager
) : ViewModel() {

    private val _activeBookId = MutableStateFlow(bookId)
    val activeBookId: StateFlow<String> = _activeBookId.asStateFlow()

    private val _isExternalUnsaved = MutableStateFlow(bookId.isBlank() && !externalUri.isNullOrBlank())
    val isExternalUnsaved: StateFlow<Boolean> = _isExternalUnsaved.asStateFlow()

    private val _contentState = MutableStateFlow<ReaderContentState>(ReaderContentState.Loading)
    val contentState: StateFlow<ReaderContentState> = _contentState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ReaderSearchMatch>>(emptyList())
    val searchResults: StateFlow<List<ReaderSearchMatch>> = _searchResults.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _readerMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val readerMessages = _readerMessages.asSharedFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val categories: StateFlow<List<CategoryEntity>> = libraryRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val annotationsState: StateFlow<Triple<List<BookmarkEntity>, List<NoteEntity>, List<HighlightEntity>>> =
        _activeBookId.flatMapLatest { id ->
            if (id.isBlank()) {
                flowOf(Triple(emptyList(), emptyList(), emptyList()))
            } else {
                combine(
                    libraryRepository.observeBookmarksForBook(id),
                    libraryRepository.observeNotesForBook(id),
                    libraryRepository.observeHighlightsForBook(id)
                ) { bms, notes, hls -> Triple(bms, notes, hls) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), Triple(emptyList(), emptyList(), emptyList()))

    private var pdfPfd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val pdfMutex = Mutex()
    private var loadedBook: BookEntity? = null
    private var activeExternalUri: Uri? = externalUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    private var latestScrollOffset: Int = 0

    init {
        soundManager.preloadIfReadyAsync()
        loadDocument()
    }

    fun loadDocument() {
        viewModelScope.launch {
            _contentState.value = ReaderContentState.Loading
            closePdfResources()

            val targetBookId = _activeBookId.value
            if (targetBookId.isNotBlank()) {
                val book = libraryRepository.getBookById(targetBookId)
                if (book == null) {
                    _contentState.value = ReaderContentState.Error(null, false, "Book record not found.")
                    return@launch
                }
                loadedBook = book
                _isExternalUnsaved.value = false

                val accessible = libraryRepository.verifyBookAccessibility(book)
                if (!accessible) {
                    _contentState.value = ReaderContentState.Error(
                        book = book,
                        isMissingFile = true,
                        message = "File is missing or storage permission was revoked."
                    )
                    return@launch
                }

                val savedProgress = libraryRepository.getProgressForBook(targetBookId)
                val format = DocumentFormat.fromName(book.formatName)
                val uri = Uri.parse(book.fileUri)
                openByFormat(
                    book = book,
                    uri = uri,
                    format = format,
                    initialPage = initialPageOverride ?: savedProgress?.pageIndex ?: 0,
                    initialChapter = initialChapterOverride ?: savedProgress?.chapterIndex ?: 0,
                    initialScroll = savedProgress?.scrollOffset ?: 0
                )
            } else {
                val extUri = activeExternalUri
                if (extUri == null) {
                    _contentState.value = ReaderContentState.Error(null, false, "Document URI missing.")
                    return@launch
                }
                openExternalUriWithoutImport(extUri)
            }
        }
    }

    fun reopenExternalUri(newUri: Uri) {
        activeExternalUri = newUri
        loadDocument()
    }

    private suspend fun openExternalUriWithoutImport(uri: Uri) = withContext(Dispatchers.IO) {
        val filename = libraryRepository.resolveDisplayName(uri)
        val mimeType = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        val info = DocumentValidator.validateAndInspect(
            contentResolver = appContext.contentResolver,
            uri = uri,
            displayName = filename,
            mimeType = mimeType
        ).getOrElse { error ->
            val expired = error is SecurityException ||
                error.message?.contains("permission", ignoreCase = true) == true
            _contentState.value = ReaderContentState.Error(
                book = null,
                isMissingFile = expired,
                message = error.localizedMessage ?: "Unable to open external document.",
                isExternalUri = true
            )
            return@withContext
        }

        val transientBook = BookEntity(
            id = "",
            title = filename.substringBeforeLast('.').ifBlank { filename },
            originalFilename = filename,
            formatName = info.format.name,
            fileUri = uri.toString(),
            fileSizeBytes = info.fileSizeBytes,
            contentHash = info.contentHash
        )
        loadedBook = transientBook
        _isExternalUnsaved.value = true
        openByFormat(
            book = transientBook,
            uri = uri,
            format = info.format,
            initialPage = initialPageOverride ?: 0,
            initialChapter = initialChapterOverride ?: 0,
            initialScroll = 0
        )
    }

    private suspend fun openByFormat(
        book: BookEntity,
        uri: Uri,
        format: DocumentFormat,
        initialPage: Int,
        initialChapter: Int,
        initialScroll: Int
    ) {
        when (format) {
            DocumentFormat.PDF -> openPdf(book, uri, initialPage)
            DocumentFormat.EPUB -> openEpub(book, uri, initialChapter, initialScroll)
            DocumentFormat.CBZ -> openCbz(book, uri, initialPage)
            DocumentFormat.IMAGE -> openSingleImage(book, uri)
            else -> openTextDocument(
                book = book,
                uri = uri,
                format = format,
                initialSection = initialChapter,
                initialScroll = initialScroll
            )
        }
    }

    private suspend fun openPdf(book: BookEntity, uri: Uri, initialPage: Int) = withContext(Dispatchers.IO) {
        try {
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Cannot open PDF descriptor")
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) {
                renderer.close()
                pfd.close()
                throw IllegalArgumentException("PDF has 0 pages")
            }
            pdfPfd = pfd
            pdfRenderer = renderer
            val clampedPage = initialPage.coerceIn(0, renderer.pageCount - 1)
            latestScrollOffset = 0
            _contentState.value = ReaderContentState.PdfReady(book, renderer.pageCount, clampedPage)
            renderPdfPage(clampedPage)
            persistProgress(
                progress = (clampedPage + 1).toFloat() / renderer.pageCount.toFloat(),
                pageIndex = clampedPage,
                totalPages = renderer.pageCount,
                chapterIndex = 0,
                totalChapters = 1,
                scrollOffset = 0
            )
        } catch (e: SecurityException) {
            _contentState.value = ReaderContentState.Error(
                book = book,
                isMissingFile = true,
                message = "PDF is password-protected or permission expired: ${e.localizedMessage}",
                isExternalUri = _isExternalUnsaved.value
            )
        } catch (e: Exception) {
            _contentState.value = ReaderContentState.Error(
                book = book,
                isMissingFile = false,
                message = e.localizedMessage ?: "Failed to render PDF",
                isExternalUri = _isExternalUnsaved.value
            )
        }
    }

    private suspend fun renderPdfPage(pageIndex: Int) = withContext(Dispatchers.IO) {
        pdfMutex.withLock {
            val renderer = pdfRenderer ?: return@withLock
            if (pageIndex !in 0 until renderer.pageCount) return@withLock
            renderer.openPage(pageIndex).use { page ->
                val targetWidth = 1440
                val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                val targetHeight = (page.height * scale).toInt().coerceIn(200, 3200)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                _currentBitmap.value = bitmap
            }
        }
    }

    fun goToPdfPage(pageIndex: Int, playSound: Boolean = true) {
        val state = _contentState.value as? ReaderContentState.PdfReady ?: return
        val clamped = pageIndex.coerceIn(0, state.pageCount - 1)
        if (clamped == state.currentPage) return

        if (playSound) {
            soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
        }
        _contentState.value = state.copy(currentPage = clamped)
        viewModelScope.launch {
            renderPdfPage(clamped)
            persistProgress(
                progress = (clamped + 1).toFloat() / state.pageCount.toFloat(),
                pageIndex = clamped,
                totalPages = state.pageCount,
                chapterIndex = 0,
                totalChapters = 1,
                scrollOffset = 0
            )
        }
    }

    fun playExplicitPageTurnSound() {
        soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
    }

    private suspend fun openEpub(book: BookEntity, uri: Uri, initialChapter: Int, initialScroll: Int) =
        withContext(Dispatchers.IO) {
            EpubParser.parse(appContext.contentResolver, uri).onSuccess { epub ->
                val clamped = initialChapter.coerceIn(0, (epub.chapters.size - 1).coerceAtLeast(0))
                latestScrollOffset = initialScroll.coerceAtLeast(0)
                _contentState.value = ReaderContentState.EpubReady(book, epub, clamped, latestScrollOffset)
                persistProgress(
                    progress = (clamped + 1).toFloat() / epub.chapters.size.coerceAtLeast(1).toFloat(),
                    pageIndex = clamped,
                    totalPages = epub.chapters.size,
                    chapterIndex = clamped,
                    totalChapters = epub.chapters.size,
                    scrollOffset = latestScrollOffset
                )
            }.onFailure { err ->
                _contentState.value = ReaderContentState.Error(
                    book = book,
                    isMissingFile = false,
                    message = err.localizedMessage ?: "Failed to parse EPUB",
                    isExternalUri = _isExternalUnsaved.value
                )
            }
        }

    fun goToEpubChapter(chapterIndex: Int, playSound: Boolean = true) {
        val state = _contentState.value as? ReaderContentState.EpubReady ?: return
        val clamped = chapterIndex.coerceIn(0, (state.content.chapters.size - 1).coerceAtLeast(0))
        if (clamped == state.currentChapter) return
        if (playSound) {
            soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
        }
        latestScrollOffset = 0
        _contentState.value = state.copy(currentChapter = clamped, initialScrollOffset = 0)
        viewModelScope.launch {
            val total = state.content.chapters.size.coerceAtLeast(1)
            persistProgress(
                progress = (clamped + 1).toFloat() / total.toFloat(),
                pageIndex = clamped,
                totalPages = total,
                chapterIndex = clamped,
                totalChapters = total,
                scrollOffset = 0
            )
        }
    }

    private suspend fun openTextDocument(
        book: BookEntity,
        uri: Uri,
        format: DocumentFormat,
        initialSection: Int,
        initialScroll: Int
    ) = withContext(Dispatchers.IO) {
        ExtendedDocumentParser.parseTextDocument(appContext.contentResolver, uri, format, book.title)
            .onSuccess { parsed ->
                val clamped = initialSection.coerceIn(0, (parsed.sections.size - 1).coerceAtLeast(0))
                latestScrollOffset = initialScroll.coerceAtLeast(0)
                _contentState.value = ReaderContentState.TextDocumentReady(book, parsed.sections, clamped, latestScrollOffset)
                val total = parsed.sections.size.coerceAtLeast(1)
                persistProgress(
                    progress = (clamped + 1).toFloat() / total.toFloat(),
                    pageIndex = clamped,
                    totalPages = total,
                    chapterIndex = clamped,
                    totalChapters = total,
                    scrollOffset = latestScrollOffset
                )
            }
            .onFailure { err ->
                _contentState.value = ReaderContentState.Error(
                    book = book,
                    isMissingFile = false,
                    message = err.localizedMessage ?: "Unable to read document",
                    isExternalUri = _isExternalUnsaved.value
                )
            }
    }

    fun goToTextSection(sectionIndex: Int, playSound: Boolean = true) {
        val state = _contentState.value as? ReaderContentState.TextDocumentReady ?: return
        val clamped = sectionIndex.coerceIn(0, (state.sections.size - 1).coerceAtLeast(0))
        if (clamped == state.currentSection) return
        if (playSound) {
            soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
        }
        latestScrollOffset = 0
        _contentState.value = state.copy(currentSection = clamped, initialScrollOffset = 0)
        viewModelScope.launch {
            val total = state.sections.size.coerceAtLeast(1)
            persistProgress(
                progress = (clamped + 1).toFloat() / total.toFloat(),
                pageIndex = clamped,
                totalPages = total,
                chapterIndex = clamped,
                totalChapters = total,
                scrollOffset = 0
            )
        }
    }

    private suspend fun openCbz(book: BookEntity, uri: Uri, initialPage: Int) = withContext(Dispatchers.IO) {
        ComicAndImageLoader.listCbzEntries(appContext.contentResolver, uri).onSuccess { entries ->
            val clamped = initialPage.coerceIn(0, entries.size - 1)
            latestScrollOffset = 0
            _contentState.value = ReaderContentState.ImageCollectionReady(book, entries, clamped, isSingleImage = false)
            _currentBitmap.value = ComicAndImageLoader.loadCbzPageBitmap(appContext.contentResolver, uri, entries[clamped])
            persistProgress(
                progress = (clamped + 1).toFloat() / entries.size.toFloat(),
                pageIndex = clamped,
                totalPages = entries.size,
                chapterIndex = 0,
                totalChapters = 1,
                scrollOffset = 0
            )
        }.onFailure { err ->
            _contentState.value = ReaderContentState.Error(
                book = book,
                isMissingFile = false,
                message = err.localizedMessage ?: "Failed to read CBZ archive",
                isExternalUri = _isExternalUnsaved.value
            )
        }
    }

    private suspend fun openSingleImage(book: BookEntity, uri: Uri) = withContext(Dispatchers.IO) {
        val bmp = ComicAndImageLoader.loadSingleImageBitmap(appContext.contentResolver, uri)
        if (bmp == null) {
            _contentState.value = ReaderContentState.Error(
                book = book,
                isMissingFile = false,
                message = "Unable to decode image file",
                isExternalUri = _isExternalUnsaved.value
            )
        } else {
            latestScrollOffset = 0
            _currentBitmap.value = bmp
            _contentState.value = ReaderContentState.ImageCollectionReady(book, emptyList(), 0, isSingleImage = true)
            persistProgress(1f, 0, 1, 0, 1, 0)
        }
    }

    fun goToImagePage(pageIndex: Int, playSound: Boolean = true) {
        val state = _contentState.value as? ReaderContentState.ImageCollectionReady ?: return
        if (state.isSingleImage || state.imageEntries.isEmpty()) return
        val clamped = pageIndex.coerceIn(0, state.imageEntries.size - 1)
        if (clamped == state.currentPage) return

        if (playSound) {
            soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
        }
        _contentState.value = state.copy(currentPage = clamped)
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(state.book.fileUri)
            _currentBitmap.value = ComicAndImageLoader.loadCbzPageBitmap(
                appContext.contentResolver,
                uri,
                state.imageEntries[clamped]
            )
            persistProgress(
                progress = (clamped + 1).toFloat() / state.imageEntries.size.toFloat(),
                pageIndex = clamped,
                totalPages = state.imageEntries.size,
                chapterIndex = 0,
                totalChapters = 1,
                scrollOffset = 0
            )
        }
    }

    fun updateReflowableScrollOffset(itemIndex: Int) {
        latestScrollOffset = itemIndex.coerceAtLeast(0)
    }

    fun flushCurrentPosition() {
        val targetId = _activeBookId.value
        if (targetId.isBlank()) return
        val progressData = when (val st = _contentState.value) {
            is ReaderContentState.PdfReady -> ReadingPositionSnapshot(
                progress = (st.currentPage + 1).toFloat() / st.pageCount.coerceAtLeast(1),
                pageIndex = st.currentPage,
                totalPages = st.pageCount,
                chapterIndex = 0,
                totalChapters = 1,
                scrollOffset = 0
            )
            is ReaderContentState.EpubReady -> ReadingPositionSnapshot(
                progress = (st.currentChapter + 1).toFloat() / st.content.chapters.size.coerceAtLeast(1),
                pageIndex = st.currentChapter,
                totalPages = st.content.chapters.size.coerceAtLeast(1),
                chapterIndex = st.currentChapter,
                totalChapters = st.content.chapters.size.coerceAtLeast(1),
                scrollOffset = latestScrollOffset
            )
            is ReaderContentState.TextDocumentReady -> ReadingPositionSnapshot(
                progress = (st.currentSection + 1).toFloat() / st.sections.size.coerceAtLeast(1),
                pageIndex = st.currentSection,
                totalPages = st.sections.size.coerceAtLeast(1),
                chapterIndex = st.currentSection,
                totalChapters = st.sections.size.coerceAtLeast(1),
                scrollOffset = latestScrollOffset
            )
            is ReaderContentState.ImageCollectionReady -> ReadingPositionSnapshot(
                progress = if (st.isSingleImage) 1f else (st.currentPage + 1).toFloat() / st.imageEntries.size.coerceAtLeast(1),
                pageIndex = st.currentPage,
                totalPages = if (st.isSingleImage) 1 else st.imageEntries.size.coerceAtLeast(1),
                chapterIndex = 0,
                totalChapters = 1,
                scrollOffset = 0
            )
            else -> null
        } ?: return

        viewModelScope.launch {
            persistProgress(
                progress = progressData.progress,
                pageIndex = progressData.pageIndex,
                totalPages = progressData.totalPages,
                chapterIndex = progressData.chapterIndex,
                totalChapters = progressData.totalChapters,
                scrollOffset = progressData.scrollOffset
            )
        }
    }

    fun saveExternalBookToLibrary(title: String, author: String, categoryName: String?) {
        val extUri = activeExternalUri ?: return
        val snapshot = when (val st = _contentState.value) {
            is ReaderContentState.PdfReady -> Triple(st.currentPage, st.pageCount, 0 to 1)
            is ReaderContentState.EpubReady -> Triple(st.currentChapter, st.content.chapters.size, st.currentChapter to st.content.chapters.size)
            is ReaderContentState.TextDocumentReady -> Triple(st.currentSection, st.sections.size, st.currentSection to st.sections.size)
            is ReaderContentState.ImageCollectionReady -> Triple(st.currentPage, if (st.isSingleImage) 1 else st.imageEntries.size, 0 to 1)
            else -> Triple(0, 1, 0 to 1)
        }
        viewModelScope.launch {
            libraryRepository.importExternalWithMetadata(
                uri = extUri,
                title = title,
                author = author,
                categoryName = categoryName,
                currentPage = snapshot.first,
                totalPages = snapshot.second,
                currentChapter = snapshot.third.first,
                totalChapters = snapshot.third.second,
                scrollOffset = latestScrollOffset
            ).onSuccess { savedBook ->
                loadedBook = savedBook
                _activeBookId.value = savedBook.id
                _isExternalUnsaved.value = false
                _contentState.value = when (val cur = _contentState.value) {
                    is ReaderContentState.PdfReady -> cur.copy(book = savedBook)
                    is ReaderContentState.EpubReady -> cur.copy(book = savedBook)
                    is ReaderContentState.TextDocumentReady -> cur.copy(book = savedBook)
                    is ReaderContentState.ImageCollectionReady -> cur.copy(book = savedBook)
                    else -> cur
                }
                _readerMessages.emit("Added \"${savedBook.title}\" to MyBooks Library")
            }.onFailure { err ->
                _readerMessages.emit(err.localizedMessage ?: "Failed to save book to library")
            }
        }
    }

    fun searchInDocument(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        val matches = mutableListOf<ReaderSearchMatch>()
        when (val st = _contentState.value) {
            is ReaderContentState.EpubReady -> {
                st.content.chapters.forEachIndexed { idx, ch ->
                    ch.paragraphs.forEach { block ->
                        val text = extractBlockText(block)
                        if (text.contains(clean, ignoreCase = true) && matches.size < 40) {
                            matches += ReaderSearchMatch(idx, ch.title, text.take(160))
                        }
                    }
                }
            }
            is ReaderContentState.TextDocumentReady -> {
                st.sections.forEachIndexed { idx, sec ->
                    sec.blocks.forEach { block ->
                        val text = extractBlockText(block)
                        if (text.contains(clean, ignoreCase = true) && matches.size < 40) {
                            matches += ReaderSearchMatch(idx, sec.title, text.take(160))
                        }
                    }
                }
            }
            else -> Unit
        }
        _searchResults.value = matches
    }

    private fun extractBlockText(block: com.example.data.reader.TextBlock): String = when (block) {
        is com.example.data.reader.TextBlock.Heading -> block.text
        is com.example.data.reader.TextBlock.Paragraph -> block.text
        is com.example.data.reader.TextBlock.Quote -> block.text
        is com.example.data.reader.TextBlock.Code -> block.text
        is com.example.data.reader.TextBlock.BulletItem -> block.text
    }

    fun addCurrentLocationBookmark(customTitle: String? = null) {
        val book = loadedBook ?: return
        if (book.id.isBlank()) return
        viewModelScope.launch {
            val (pageIdx, chapIdx, display) = currentLocationDescriptor()
            libraryRepository.addBookmark(
                bookId = book.id,
                title = customTitle?.takeIf { it.isNotBlank() } ?: "${book.title} — $display",
                locatorJson = """{"page":$pageIdx,"chapter":$chapIdx,"scroll":$latestScrollOffset}""",
                locatorDisplay = display,
                pageIndex = pageIdx,
                chapterIndex = chapIdx,
                scrollOffset = latestScrollOffset
            )
        }
    }

    fun addNoteAtCurrentLocation(title: String, content: String) {
        val book = loadedBook ?: return
        if (book.id.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val (pageIdx, chapIdx, display) = currentLocationDescriptor()
            libraryRepository.saveNote(
                bookId = book.id,
                bookTitleSnapshot = book.title,
                title = title,
                content = content,
                locatorJson = """{"page":$pageIdx,"chapter":$chapIdx,"scroll":$latestScrollOffset}""",
                locatorDisplay = display,
                pageIndex = pageIdx,
                chapterIndex = chapIdx,
                scrollOffset = latestScrollOffset
            )
        }
    }

    fun addHighlight(selectedText: String) {
        val book = loadedBook ?: return
        if (book.id.isBlank() || selectedText.isBlank()) return
        viewModelScope.launch {
            val (pageIdx, chapIdx, display) = currentLocationDescriptor()
            libraryRepository.addHighlight(
                bookId = book.id,
                bookTitle = book.title,
                selectedText = selectedText.trim(),
                locatorJson = """{"page":$pageIdx,"chapter":$chapIdx}""",
                locatorDisplay = display,
                pageIndex = pageIdx,
                chapterIndex = chapIdx
            )
        }
    }

    fun deleteBookmark(id: String) = viewModelScope.launch { libraryRepository.deleteBookmark(id) }

    fun updateReaderPrefs(block: suspend UserPreferencesRepository.() -> Unit) {
        viewModelScope.launch { preferencesRepository.block() }
    }

    fun togglePageTurnSound(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPageTurnSoundEnabled(enabled)
            if (enabled) {
                val ready = soundManager.verifyLocalSoundIntegrity()
                if (!ready) {
                    soundManager.startDownload()
                }
            }
        }
    }

    private fun currentLocationDescriptor(): Triple<Int, Int, String> = when (val st = _contentState.value) {
        is ReaderContentState.PdfReady -> Triple(st.currentPage, 0, "Page ${st.currentPage + 1}/${st.pageCount}")
        is ReaderContentState.EpubReady -> Triple(0, st.currentChapter, st.content.chapters.getOrNull(st.currentChapter)?.title ?: "Ch ${st.currentChapter + 1}")
        is ReaderContentState.TextDocumentReady -> Triple(0, st.currentSection, st.sections.getOrNull(st.currentSection)?.title ?: "Sec ${st.currentSection + 1}")
        is ReaderContentState.ImageCollectionReady -> Triple(st.currentPage, 0, "Image ${st.currentPage + 1}")
        else -> Triple(0, 0, "Start")
    }

    private suspend fun persistProgress(
        progress: Float,
        pageIndex: Int,
        totalPages: Int,
        chapterIndex: Int,
        totalChapters: Int,
        scrollOffset: Int
    ) {
        val targetBookId = _activeBookId.value
        if (targetBookId.isBlank()) return
        libraryRepository.saveReadingProgress(
            bookId = targetBookId,
            progressFraction = progress,
            pageIndex = pageIndex,
            totalPages = totalPages,
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            scrollOffset = scrollOffset,
            locatorJson = """{"page":$pageIndex,"chapter":$chapterIndex,"scroll":$scrollOffset}"""
        )
    }

    private fun closePdfResources() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {
        }
        try {
            pdfPfd?.close()
        } catch (_: Exception) {
        }
        pdfRenderer = null
        pdfPfd = null
    }

    override fun onCleared() {
        super.onCleared()
        closePdfResources()
    }

    private data class ReadingPositionSnapshot(
        val progress: Float,
        val pageIndex: Int,
        val totalPages: Int,
        val chapterIndex: Int,
        val totalChapters: Int,
        val scrollOffset: Int
    )
}
