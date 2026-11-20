package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookWithDetails
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.TagEntity
import com.example.data.local.entity.resolvedReadingStatus
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BookContentMatch
import com.example.data.repository.ImportOutcome
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ReadingStreakSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibrarySortOption {
    RECENTLY_OPENED, DATE_ADDED, TITLE, AUTHOR, PROGRESS
}

enum class LibraryFilterChip {
    ALL,
    FAVORITES,
    UNREAD,
    CURRENTLY_READING,
    FINISHED,
    RECENTLY_OPENED,
    RECENTLY_ADDED,
    ANNOTATED
}

enum class AnnotationExportFormat {
    MARKDOWN, JSON
}

data class DuplicatePromptState(
    val existingBook: BookEntity,
    val candidateBook: BookEntity,
    val candidateCoverBytes: ByteArray?
)

data class LibraryUiState(
    val allBooks: List<BookWithDetails> = emptyList(),
    val deletedBooks: List<BookWithDetails> = emptyList(),
    val filteredBooks: List<BookWithDetails> = emptyList(),
    val continueReadingBook: BookWithDetails? = null,
    val recentlyAddedBooks: List<BookWithDetails> = emptyList(),
    val recentlyOpenedBooks: List<BookWithDetails> = emptyList(),
    val favoriteBooks: List<BookWithDetails> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val searchQuery: String = "",
    val isGridView: Boolean = true,
    val filterChip: LibraryFilterChip = LibraryFilterChip.ALL,
    val selectedStatusFilter: ReadingStatus? = null,
    val selectedCategoryId: String? = null,
    val selectedTagId: String? = null,
    val selectedCollectionId: String? = null,
    val sortOption: LibrarySortOption = LibrarySortOption.DATE_ADDED,
    val duplicateQueue: List<DuplicatePromptState> = emptyList(),
    val isImporting: Boolean = false,
    val searchInsideBooksEnabled: Boolean = false,
    val isSearchingInsideBooks: Boolean = false,
    val contentSearchMatches: List<BookContentMatch> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val streakSummary: ReadingStreakSummary = ReadingStreakSummary(0, 0, 0, 0, emptyMap()),
    val dailyReadingGoalMinutes: Int = 30,
    val readingSpeedWpm: Int = 200
)

sealed interface LibraryEvent {
    data class ShowMessage(val message: String) : LibraryEvent
    data class OpenBook(val bookId: String, val pageIndex: Int? = null, val chapterIndex: Int? = null) : LibraryEvent
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isGridView = MutableStateFlow(true)
    private val _filterChip = MutableStateFlow(LibraryFilterChip.ALL)
    private val _selectedStatusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    private val _selectedTagId = MutableStateFlow<String?>(null)
    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    private val _sortOption = MutableStateFlow(LibrarySortOption.DATE_ADDED)
    private val _duplicateQueue = MutableStateFlow<List<DuplicatePromptState>>(emptyList())
    private val _isImporting = MutableStateFlow(false)
    private val _searchInsideBooksEnabled = MutableStateFlow(false)
    private val _isSearchingInsideBooks = MutableStateFlow(false)
    private val _contentSearchMatches = MutableStateFlow<List<BookContentMatch>>(emptyList())

    private var contentSearchJob: Job? = null

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val filterFlow = combine(
        _searchQuery,
        _isGridView,
        _filterChip,
        _selectedCategoryId,
        _selectedTagId
    ) { query, grid, chip, catId, tagId ->
        FilterParams(query, grid, chip, catId, tagId)
    }

    private val extendedFilterFlow = combine(
        _selectedCollectionId,
        _sortOption,
        _duplicateQueue,
        _isImporting,
        _selectedStatusFilter
    ) { colId, sort, dupes, importing, statusFilter ->
        SortAndStatus(colId, sort, dupes, importing, statusFilter)
    }

    private val contentSearchFlow = combine(
        _searchInsideBooksEnabled,
        _isSearchingInsideBooks,
        _contentSearchMatches
    ) { enabled, running, matches ->
        Triple(enabled, running, matches)
    }

    private val taxonomyFlow = combine(
        repository.categories,
        repository.tags,
        repository.collections
    ) { cats, tags, cols ->
        Triple(cats, tags, cols)
    }

    private val annotationsFlow = combine(
        repository.allBookmarks,
        repository.allHighlights,
        repository.allNotes
    ) { bms, hls, notes ->
        Triple(bms, hls, notes)
    }

    private val booksAndDeletedFlow = combine(
        repository.allBooksWithDetails,
        repository.deletedBooksWithDetails
    ) { active, deleted ->
        active to deleted
    }

    private val productivityFlow = combine(
        repository.readingSessions,
        preferencesRepository.preferencesFlow
    ) { sessions, prefs ->
        val summary = LibraryRepository.calculateStreakSummary(sessions, prefs.dailyReadingGoalMinutes)
        Triple(summary, prefs, Unit)
    }

    private val libraryMetaFlow = combine(
        booksAndDeletedFlow,
        taxonomyFlow,
        annotationsFlow,
        productivityFlow
    ) { booksPair, taxonomy, annotations, prod ->
        LibraryDataBundle(
            activeBooks = booksPair.first,
            deletedBooks = booksPair.second,
            categories = taxonomy.first,
            tags = taxonomy.second,
            collections = taxonomy.third,
            bookmarks = annotations.first,
            highlights = annotations.second,
            notes = annotations.third,
            streakSummary = prod.first,
            dailyGoalMinutes = prod.second.dailyReadingGoalMinutes,
            readingSpeedWpm = prod.second.readingSpeedWpm,
            searchHistory = prod.second.searchHistory
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryMetaFlow,
        filterFlow,
        extendedFilterFlow,
        contentSearchFlow
    ) { bundle, filters, status, contentSearch ->
        val books = bundle.activeBooks
        val annotatedBookIds = buildSet {
            bundle.bookmarks.forEach { add(it.bookId) }
            bundle.highlights.forEach { add(it.bookId) }
            bundle.notes.forEach { it.bookId?.let(::add) }
        }
        val recentCutoffMs = System.currentTimeMillis() - 14L * 86_400_000L

        val filtered = books.filter { item ->
            val book = item.book
            val resolvedStatus = book.resolvedReadingStatus()
            val matchesSearch = filters.query.isBlank() ||
                book.title.contains(filters.query, ignoreCase = true) ||
                book.author.contains(filters.query, ignoreCase = true) ||
                book.originalFilename.contains(filters.query, ignoreCase = true)

            val matchesSmartChip = when (filters.chip) {
                LibraryFilterChip.ALL -> true
                LibraryFilterChip.FAVORITES -> book.isFavorite
                LibraryFilterChip.UNREAD -> resolvedStatus == ReadingStatus.WANT_TO_READ
                LibraryFilterChip.CURRENTLY_READING -> resolvedStatus == ReadingStatus.CURRENTLY_READING
                LibraryFilterChip.FINISHED -> resolvedStatus == ReadingStatus.FINISHED
                LibraryFilterChip.RECENTLY_OPENED -> book.lastOpenedEpochMs > 0L
                LibraryFilterChip.RECENTLY_ADDED -> book.dateAddedEpochMs >= recentCutoffMs
                LibraryFilterChip.ANNOTATED -> book.id in annotatedBookIds
            }

            val matchesExplicitStatus = status.statusFilter == null || resolvedStatus == status.statusFilter
            val matchesCat = filters.categoryId == null || book.categoryId == filters.categoryId
            val matchesTag = filters.tagId == null || item.tags.any { it.id == filters.tagId }
            val matchesCol = status.collectionId == null || item.collections.any { it.id == status.collectionId }
            matchesSearch && matchesSmartChip && matchesExplicitStatus && matchesCat && matchesTag && matchesCol
        }.let { list ->
            when (status.sortOption) {
                LibrarySortOption.TITLE -> list.sortedBy { it.book.title.lowercase() }
                LibrarySortOption.AUTHOR -> list.sortedBy { it.book.author.lowercase() }
                LibrarySortOption.DATE_ADDED -> list.sortedByDescending { it.book.dateAddedEpochMs }
                LibrarySortOption.RECENTLY_OPENED -> list.sortedByDescending { it.book.lastOpenedEpochMs }
                LibrarySortOption.PROGRESS -> list.sortedByDescending { it.book.readingProgress }
            }
        }

        val recentlyOpened = books
            .filter { it.book.lastOpenedEpochMs > 0L }
            .sortedByDescending { it.book.lastOpenedEpochMs }

        LibraryUiState(
            allBooks = books,
            deletedBooks = bundle.deletedBooks,
            filteredBooks = filtered,
            continueReadingBook = recentlyOpened.firstOrNull() ?: books.firstOrNull(),
            recentlyAddedBooks = books.sortedByDescending { it.book.dateAddedEpochMs }.take(8),
            recentlyOpenedBooks = recentlyOpened.take(8),
            favoriteBooks = books.filter { it.book.isFavorite },
            categories = bundle.categories,
            tags = bundle.tags,
            collections = bundle.collections,
            bookmarks = bundle.bookmarks,
            highlights = bundle.highlights,
            notes = bundle.notes,
            searchQuery = filters.query,
            isGridView = filters.isGrid,
            filterChip = filters.chip,
            selectedStatusFilter = status.statusFilter,
            selectedCategoryId = filters.categoryId,
            selectedTagId = filters.tagId,
            selectedCollectionId = status.collectionId,
            sortOption = status.sortOption,
            duplicateQueue = status.duplicateQueue,
            isImporting = status.isImporting,
            searchInsideBooksEnabled = contentSearch.first,
            isSearchingInsideBooks = contentSearch.second,
            contentSearchMatches = contentSearch.third,
            searchHistory = bundle.searchHistory,
            streakSummary = bundle.streakSummary,
            dailyReadingGoalMinutes = bundle.dailyGoalMinutes,
            readingSpeedWpm = bundle.readingSpeedWpm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LibraryUiState()
    )

    init {
        viewModelScope.launch {
            repository.refreshMissingFileStates()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            contentSearchJob?.cancel()
            _contentSearchMatches.value = emptyList()
            _isSearchingInsideBooks.value = false
        }
    }

    fun submitSearchQuery(query: String) {
        val clean = query.trim()
        _searchQuery.value = query
        if (clean.length < 2) {
            _contentSearchMatches.value = emptyList()
            return
        }
        viewModelScope.launch {
            preferencesRepository.addSearchHistory(clean)
        }
        if (_searchInsideBooksEnabled.value) {
            runInsideBooksSearch(clean)
        }
    }

    fun toggleSearchInsideBooks(enabled: Boolean) {
        _searchInsideBooksEnabled.value = enabled
        if (!enabled) {
            contentSearchJob?.cancel()
            _contentSearchMatches.value = emptyList()
            _isSearchingInsideBooks.value = false
        } else {
            val current = _searchQuery.value.trim()
            if (current.length >= 2) {
                runInsideBooksSearch(current)
            }
        }
    }

    private fun runInsideBooksSearch(query: String) {
        contentSearchJob?.cancel()
        contentSearchJob = viewModelScope.launch {
            _isSearchingInsideBooks.value = true
            val snapshot = uiState.value.allBooks
            val matches = repository.searchLibraryContent(query, snapshot)
            _contentSearchMatches.value = matches
            _isSearchingInsideBooks.value = false
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            preferencesRepository.clearSearchHistory()
        }
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun setFilterChip(chip: LibraryFilterChip) {
        _filterChip.value = chip
    }

    fun setSelectedStatusFilter(status: ReadingStatus?) {
        _selectedStatusFilter.value = if (_selectedStatusFilter.value == status) null else status
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = if (_selectedCategoryId.value == categoryId) null else categoryId
    }

    fun selectTag(tagId: String?) {
        _selectedTagId.value = if (_selectedTagId.value == tagId) null else tagId
    }

    fun selectCollection(collectionId: String?) {
        _selectedCollectionId.value = if (_selectedCollectionId.value == collectionId) null else collectionId
    }

    fun setSortOption(option: LibrarySortOption) {
        _sortOption.value = option
    }

    fun importDocuments(uris: List<Uri>, openFirstOnSingleImport: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            var importedCount = 0
            var lastFailure: String? = null
            val pendingDupes = mutableListOf<DuplicatePromptState>()

            for (uri in uris) {
                when (val outcome = repository.importDocument(uri)) {
                    is ImportOutcome.Imported -> {
                        importedCount++
                        if (openFirstOnSingleImport && uris.size == 1) {
                            _events.emit(LibraryEvent.OpenBook(outcome.book.id))
                        }
                    }
                    is ImportOutcome.DuplicateFound -> {
                        pendingDupes += DuplicatePromptState(
                            existingBook = outcome.existingBook,
                            candidateBook = outcome.candidateBook,
                            candidateCoverBytes = outcome.candidateCoverBytes
                        )
                    }
                    is ImportOutcome.Failed -> {
                        lastFailure = "${outcome.filename}: ${outcome.reason}"
                    }
                }
            }
            if (pendingDupes.isNotEmpty()) {
                _duplicateQueue.value = _duplicateQueue.value + pendingDupes
            }
            _isImporting.value = false
            when {
                importedCount > 0 -> _events.emit(LibraryEvent.ShowMessage("Imported $importedCount document(s)"))
                lastFailure != null -> _events.emit(LibraryEvent.ShowMessage(lastFailure))
            }
        }
    }

    fun resolveDuplicateSkip() {
        _duplicateQueue.value = _duplicateQueue.value.drop(1)
    }

    fun resolveDuplicateKeepBoth() {
        val current = _duplicateQueue.value.firstOrNull() ?: return
        _duplicateQueue.value = _duplicateQueue.value.drop(1)
        viewModelScope.launch {
            repository.confirmImportKeepBoth(current.candidateBook, current.candidateCoverBytes)
            _events.emit(LibraryEvent.ShowMessage("Saved duplicate copy: ${current.candidateBook.title}"))
        }
    }

    fun resolveDuplicateReplaceUri() {
        val current = _duplicateQueue.value.firstOrNull() ?: return
        _duplicateQueue.value = _duplicateQueue.value.drop(1)
        viewModelScope.launch {
            repository.confirmReplaceExistingUri(current.existingBook.id, current.candidateBook)
            _events.emit(LibraryEvent.ShowMessage("Updated file location for ${current.existingBook.title}"))
        }
    }

    fun relocateBookFile(bookId: String, newUri: Uri) {
        viewModelScope.launch {
            val res = repository.relocateMissingBook(bookId, newUri)
            res.onSuccess {
                _events.emit(LibraryEvent.ShowMessage("File link restored"))
            }.onFailure { err ->
                _events.emit(LibraryEvent.ShowMessage(err.localizedMessage ?: "Failed to relocate file"))
            }
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(book.id, book.isFavorite)
        }
    }

    fun setReadingStatus(bookId: String, status: ReadingStatus) {
        viewModelScope.launch {
            repository.setReadingStatus(bookId, status)
        }
    }

    fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String,
        description: String,
        publisher: String,
        language: String,
        categoryName: String?,
        tagsCsv: String,
        collectionIds: List<String>
    ) {
        viewModelScope.launch {
            val tagList = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            repository.updateBookMetadata(
                bookId = bookId,
                title = title,
                author = author,
                description = description,
                publisher = publisher,
                language = language,
                categoryName = categoryName,
                tagNames = tagList,
                collectionIds = collectionIds
            )
            _events.emit(LibraryEvent.ShowMessage("Book details saved"))
        }
    }

    fun setBookCover(bookId: String, imageUri: Uri) {
        viewModelScope.launch {
            val ok = repository.setBookCover(bookId, imageUri)
            if (!ok) {
                _events.emit(LibraryEvent.ShowMessage("Could not decode selected image"))
            }
        }
    }

    fun restoreDefaultCover(bookId: String) {
        viewModelScope.launch {
            repository.restoreDefaultCover(bookId)
        }
    }

    fun createCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createCategory(name)
        }
    }

    fun renameCategory(categoryId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameCategory(categoryId, newName)
        }
    }

    fun deleteCategory(categoryId: String, reassignToCategoryId: String? = null) {
        if (_selectedCategoryId.value == categoryId) {
            _selectedCategoryId.value = null
        }
        viewModelScope.launch {
            repository.deleteCategory(categoryId, reassignToCategoryId)
        }
    }

    fun createCollection(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createCollection(name, description)
        }
    }

    fun updateCollection(collectionId: String, newName: String, newDescription: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateCollection(collectionId, newName, newDescription)
        }
    }

    fun deleteCollection(collectionId: String) {
        if (_selectedCollectionId.value == collectionId) {
            _selectedCollectionId.value = null
        }
        viewModelScope.launch {
            repository.deleteCollection(collectionId)
        }
    }

    fun softDeleteBook(bookId: String) {
        viewModelScope.launch {
            repository.softDeleteBook(bookId)
            _events.emit(LibraryEvent.ShowMessage("Moved to Recently Deleted"))
        }
    }

    fun restoreBookFromTrash(bookId: String) {
        viewModelScope.launch {
            repository.restoreBookFromTrash(bookId)
            _events.emit(LibraryEvent.ShowMessage("Book restored to My Library"))
        }
    }

    fun removeBook(bookId: String, deleteNotes: Boolean) {
        viewModelScope.launch {
            repository.removeBookFromLibrary(bookId, deleteNotes)
        }
    }

    fun exportAnnotations(destUri: Uri, format: AnnotationExportFormat) {
        viewModelScope.launch {
            val res = when (format) {
                AnnotationExportFormat.MARKDOWN -> repository.exportAnnotationsMarkdown(destUri)
                AnnotationExportFormat.JSON -> repository.exportAnnotationsJson(destUri)
            }
            res.onSuccess { count ->
                _events.emit(LibraryEvent.ShowMessage("Exported $count annotation(s)"))
            }.onFailure { err ->
                _events.emit(LibraryEvent.ShowMessage(err.localizedMessage ?: "Export failed"))
            }
        }
    }

    fun saveStandaloneNote(
        id: String? = null,
        bookId: String?,
        bookTitle: String,
        title: String,
        content: String
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.saveNote(
                id = id,
                bookId = bookId,
                bookTitleSnapshot = bookTitle,
                title = title,
                content = content
            )
        }
    }

    fun deleteBookmark(id: String) = viewModelScope.launch { repository.deleteBookmark(id) }
    fun deleteHighlight(id: String) = viewModelScope.launch { repository.deleteHighlight(id) }
    fun deleteNote(id: String) = viewModelScope.launch { repository.deleteNote(id) }

    private data class FilterParams(
        val query: String,
        val isGrid: Boolean,
        val chip: LibraryFilterChip,
        val categoryId: String?,
        val tagId: String?
    )

    private data class SortAndStatus(
        val collectionId: String?,
        val sortOption: LibrarySortOption,
        val duplicateQueue: List<DuplicatePromptState>,
        val isImporting: Boolean,
        val statusFilter: ReadingStatus?
    )

    private data class LibraryDataBundle(
        val activeBooks: List<BookWithDetails>,
        val deletedBooks: List<BookWithDetails>,
        val categories: List<CategoryEntity>,
        val tags: List<TagEntity>,
        val collections: List<CollectionEntity>,
        val bookmarks: List<BookmarkEntity>,
        val highlights: List<HighlightEntity>,
        val notes: List<NoteEntity>,
        val streakSummary: ReadingStreakSummary,
        val dailyGoalMinutes: Int,
        val readingSpeedWpm: Int,
        val searchHistory: List<String>
    )
}
