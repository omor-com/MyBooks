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
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.ComicAndImageLoader
import com.example.data.reader.DocumentFormat
import com.example.data.reader.DocumentSection
import com.example.data.reader.EpubBookContent
import com.example.data.reader.EpubParser
import com.example.data.reader.ExtendedDocumentParser
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        val message: String
    ) : ReaderContentState
}

data class ReaderSearchMatch(
    val sectionIndex: Int,
    val sectionTitle: String,
    val snippet: String
)

class ReaderViewModel(
    private val appContext: Context,
    private val bookId: String,
    private val initialPageOverride: Int?,
    private val initialChapterOverride: Int?,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: PageTurnSoundManager
) : ViewModel() {

    private val _contentState = MutableStateFlow<ReaderContentState>(ReaderContentState.Loading)
    val contentState: StateFlow<ReaderContentState> = _contentState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ReaderSearchMatch>>(emptyList())
    val searchResults: StateFlow<List<ReaderSearchMatch>> = _searchResults.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val annotationsState: StateFlow<Triple<List<BookmarkEntity>, List<NoteEntity>, List<HighlightEntity>>> = combine(
        libraryRepository.observeBookmarksForBook(bookId),
        libraryRepository.observeNotesForBook(bookId),
        libraryRepository.observeHighlightsForBook(bookId)
    ) { bms, notes, hls -> Triple(bms, notes, hls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), Triple(emptyList(), emptyList(), emptyList()))

    private var pdfPfd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val pdfMutex = Mutex()
    private var loadedBook: BookEntity? = null

    init {
        loadDocument()
    }

    fun loadDocument() {
        viewModelScope.launch {
            _contentState.value = ReaderContentState.Loading
            closePdfResources()

            val book = libraryRepository.getBookById(bookId)
            if (book == null) {
                _contentState.value = ReaderContentState.Error(null, false, "Book record not found.")
                return@launch
            }
            loadedBook = book

            val accessible = libraryRepository.verifyBookAccessibility(book)
            if (!accessible) {
                _contentState.value = ReaderContentState.Error(
                    book = book,
                    isMissingFile = true,
                    message = "File is missing or storage permission was revoked."
                )
                return@launch
            }

            val savedProgress = libraryRepository.getProgressForBook(bookId)
            val format = DocumentFormat.fromName(book.formatName)
            val uri = Uri.parse(book.fileUri)

            when (format) {
                DocumentFormat.PDF -> openPdf(book, uri, initialPageOverride ?: savedProgress?.pageIndex ?: 0)
                DocumentFormat.EPUB -> openEpub(book, uri, initialChapterOverride ?: savedProgress?.chapterIndex ?: 0, savedProgress?.scrollOffset ?: 0)
                DocumentFormat.CBZ -> openCbz(book, uri, initialPageOverride ?: savedProgress?.pageIndex ?: 0)
                DocumentFormat.IMAGE -> openSingleImage(book, uri)
                else -> openTextDocument(
                    book = book,
                    uri = uri,
                    format = format,
                    initialSection = initialChapterOverride ?: savedProgress?.chapterIndex ?: 0,
                    initialScroll = savedProgress?.scrollOffset ?: 0
                )
            }
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
            _contentState.value = ReaderContentState.Error(book, false, "PDF is password-protected or inaccessible: ${e.localizedMessage}")
        } catch (e: Exception) {
            _contentState.value = ReaderContentState.Error(book, false, e.localizedMessage ?: "Failed to render PDF")
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

    private suspend fun openEpub(book: BookEntity, uri: Uri, initialChapter: Int, initialScroll: Int) =
        withContext(Dispatchers.IO) {
            EpubParser.parse(appContext.contentResolver, uri).onSuccess { epub ->
                val clamped = initialChapter.coerceIn(0, (epub.chapters.size - 1).coerceAtLeast(0))
                _contentState.value = ReaderContentState.EpubReady(book, epub, clamped, initialScroll)
                persistProgress(
                    progress = (clamped + 1).toFloat() / epub.chapters.size.coerceAtLeast(1).toFloat(),
                    pageIndex = clamped,
                    totalPages = epub.chapters.size,
                    chapterIndex = clamped,
                    totalChapters = epub.chapters.size,
                    scrollOffset = initialScroll
                )
            }.onFailure { err ->
                _contentState.value = ReaderContentState.Error(book, false, err.localizedMessage ?: "Failed to parse EPUB")
            }
        }

    fun goToEpubChapter(chapterIndex: Int, playSound: Boolean = true) {
        val state = _contentState.value as? ReaderContentState.EpubReady ?: return
        val clamped = chapterIndex.coerceIn(0, (state.content.chapters.size - 1).coerceAtLeast(0))
        if (playSound && clamped != state.currentChapter) {
            soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
        }
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
                _contentState.value = ReaderContentState.TextDocumentReady(book, parsed.sections, clamped, initialScroll)
                val total = parsed.sections.size.coerceAtLeast(1)
                persistProgress(
                    progress = (clamped + 1).toFloat() / total.toFloat(),
                    pageIndex = clamped,
                    totalPages = total,
                    chapterIndex = clamped,
                    totalChapters = total,
                    scrollOffset = initialScroll
                )
            }
            .onFailure { err ->
                _contentState.value = ReaderContentState.Error(book, false, err.localizedMessage ?: "Unable to read document")
            }
    }

    fun goToTextSection(sectionIndex: Int, playSound: Boolean = true) {
        val state = _contentState.value as? ReaderContentState.TextDocumentReady ?: return
        val clamped = sectionIndex.coerceIn(0, (state.sections.size - 1).coerceAtLeast(0))
        if (playSound && clamped != state.currentSection) {
            soundManager.playPageTurnIfEnabled(preferences.value.pageTurnSoundEnabled)
        }
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
            _contentState.value = ReaderContentState.Error(book, false, err.localizedMessage ?: "Failed to read CBZ archive")
        }
    }

    private suspend fun openSingleImage(book: BookEntity, uri: Uri) = withContext(Dispatchers.IO) {
        val bmp = ComicAndImageLoader.loadSingleImageBitmap(appContext.contentResolver, uri)
        if (bmp == null) {
            _contentState.value = ReaderContentState.Error(book, false, "Unable to decode image file")
        } else {
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
        viewModelScope.launch {
            val (pageIdx, chapIdx, display) = currentLocationDescriptor()
            libraryRepository.addBookmark(
                bookId = book.id,
                title = customTitle?.takeIf { it.isNotBlank() } ?: "${book.title} — $display",
                locatorJson = """{"page":$pageIdx,"chapter":$chapIdx}""",
                locatorDisplay = display,
                pageIndex = pageIdx,
                chapterIndex = chapIdx,
                scrollOffset = 0
            )
        }
    }

    fun addNoteAtCurrentLocation(title: String, content: String) {
        val book = loadedBook ?: return
        if (content.isBlank()) return
        viewModelScope.launch {
            val (pageIdx, chapIdx, display) = currentLocationDescriptor()
            libraryRepository.saveNote(
                bookId = book.id,
                bookTitleSnapshot = book.title,
                title = title,
                content = content,
                locatorJson = """{"page":$pageIdx,"chapter":$chapIdx}""",
                locatorDisplay = display,
                pageIndex = pageIdx,
                chapterIndex = chapIdx
            )
        }
    }

    fun addHighlight(selectedText: String) {
        val book = loadedBook ?: return
        if (selectedText.isBlank()) return
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
        libraryRepository.saveReadingProgress(
            bookId = bookId,
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
}
