package com.example.ui.screens.reader

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.preferences.PdfScrollMode
import com.example.data.reader.DocumentFormat
import com.example.ui.viewmodel.ReaderContentState
import com.example.ui.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
    val pdfPageBitmaps by viewModel.pdfPageBitmaps.collectAsStateWithLifecycle()
    val annotations by viewModel.annotationsState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearchingDocument by viewModel.isSearchingDocument.collectAsStateWithLifecycle()
    val isExternalUnsaved by viewModel.isExternalUnsaved.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val ocrTextByPage by viewModel.ocrTextByPage.collectAsStateWithLifecycle()
    val isRunningOcr by viewModel.isRunningOcr.collectAsStateWithLifecycle()
    val ocrProgressPage by viewModel.ocrProgressPage.collectAsStateWithLifecycle()

    var showControls by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showHighlightDialog by remember { mutableStateOf(false) }
    var showSaveExternalDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var zoomResetSignal by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val view = LocalView.current

    val repickExternalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.reopenExternalUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.readerMessages.collect { msg ->
            feedbackMessage = msg
        }
    }

    LaunchedEffect(ttsState.statusError) {
        ttsState.statusError?.let { feedbackMessage = it }
    }

    LaunchedEffect(contentState) {
        if (contentState is ReaderContentState.Error) {
            showControls = true
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.flushCurrentPosition(recordSession = true)
    }

    DisposableEffect(showControls, view) {
        val window = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.window
        val controller = if (window != null) {
            runCatching { WindowCompat.getInsetsController(window, view) }.getOrNull()
        } else null

        if (controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!showControls) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    DisposableEffect(prefs.readerKeepScreenOn) {
        val window = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.window
        if (prefs.readerKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler {
        when {
            showSettingsSheet -> showSettingsSheet = false
            showTocSheet -> showTocSheet = false
            showSearchDialog -> showSearchDialog = false
            showJumpDialog -> showJumpDialog = false
            showOcrSheet -> showOcrSheet = false
            showNoteDialog -> showNoteDialog = false
            showHighlightDialog -> showHighlightDialog = false
            showSaveExternalDialog -> showSaveExternalDialog = false
            showControls -> showControls = false
            else -> {
                viewModel.flushCurrentPosition(recordSession = true)
                onBack()
            }
        }
    }

    val colors = readerColorsFor(prefs.readerThemeMode)
    val bookTitle = when (val st = contentState) {
        is ReaderContentState.PdfReady -> st.book.title
        is ReaderContentState.EpubReady -> st.book.title
        is ReaderContentState.TextDocumentReady -> st.book.title
        is ReaderContentState.ImageCollectionReady -> st.book.title
        is ReaderContentState.Error -> st.book?.title ?: stringResource(R.string.reader_error_title)
        ReaderContentState.Loading -> stringResource(R.string.reader_loading)
    }

    val isPaginatedFormat = contentState is ReaderContentState.PdfReady ||
        contentState is ReaderContentState.ImageCollectionReady
    val isPdfFormat = contentState is ReaderContentState.PdfReady

    val currentChapterOrPage = when (val st = contentState) {
        is ReaderContentState.PdfReady -> st.currentPage to 0
        is ReaderContentState.EpubReady -> 0 to st.currentChapter
        is ReaderContentState.TextDocumentReady -> 0 to st.currentSection
        is ReaderContentState.ImageCollectionReady -> st.currentPage to 0
        else -> 0 to 0
    }

    val matchingBookmark = annotations.first.firstOrNull {
        it.pageIndex == currentChapterOrPage.first && it.chapterIndex == currentChapterOrPage.second
    }

    val navigatePrev: () -> Unit = {
        when (val st = contentState) {
            is ReaderContentState.PdfReady -> viewModel.goToPdfPage(st.currentPage - 1)
            is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(st.currentChapter - 1)
            is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(st.currentSection - 1)
            is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(st.currentPage - 1)
            else -> Unit
        }
    }

    val navigateNext: () -> Unit = {
        when (val st = contentState) {
            is ReaderContentState.PdfReady -> viewModel.goToPdfPage(st.currentPage + 1)
            is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(st.currentChapter + 1)
            is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(st.currentSection + 1)
            is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(st.currentPage + 1)
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isPaginatedFormat) Color(0xFF141414) else colors.bg)
            .testTag("reader_screen")
    ) {
        when (val st = contentState) {
            ReaderContentState.Loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reader_loading), color = colors.text)
                }
            }
            is ReaderContentState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.reader_error_title), style = MaterialTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (st.isMissingFile && !st.isExternalUri) stringResource(R.string.reader_error_missing) else st.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.subtleText
                    )
                    if (st.isExternalUri && st.isMissingFile) {
                        Button(onClick = { repickExternalLauncher.launch(DocumentFormat.safPickerMimeTypes) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.reader_select_file_again))
                        }
                    }
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                }
            }
            is ReaderContentState.PdfReady -> {
                if (prefs.readerPdfScrollMode == PdfScrollMode.CONTINUOUS_VERTICAL) {
                    PdfContinuousScrollViewer(
                        pageCount = st.pageCount,
                        currentPage = st.currentPage,
                        pageBitmaps = pdfPageBitmaps,
                        tapZoneMode = prefs.readerTapZoneMode,
                        onRequestPage = viewModel::requestPdfPageBitmap,
                        onVisiblePageChanged = { idx -> viewModel.goToPdfPage(idx, playSound = false) },
                        onTapCenter = { showControls = !showControls },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ZoomableBitmapViewer(
                        bitmap = currentBitmap,
                        contentDescription = stringResource(R.string.reader_page_indicator, st.currentPage + 1, st.pageCount),
                        fitMode = prefs.readerPdfFitMode,
                        tapZoneMode = prefs.readerTapZoneMode,
                        zoomResetSignal = zoomResetSignal,
                        onTapLeft = navigatePrev,
                        onTapRight = navigateNext,
                        onTapCenter = { showControls = !showControls },
                        onSwipePrev = navigatePrev,
                        onSwipeNext = navigateNext,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is ReaderContentState.ImageCollectionReady -> {
                ZoomableBitmapViewer(
                    bitmap = currentBitmap,
                    contentDescription = st.book.title,
                    fitMode = prefs.readerPdfFitMode,
                    tapZoneMode = prefs.readerTapZoneMode,
                    zoomResetSignal = zoomResetSignal,
                    onTapLeft = { if (!st.isSingleImage) navigatePrev() else showControls = !showControls },
                    onTapRight = { if (!st.isSingleImage) navigateNext() else showControls = !showControls },
                    onTapCenter = { showControls = !showControls },
                    onSwipePrev = { if (!st.isSingleImage) navigatePrev() },
                    onSwipeNext = { if (!st.isSingleImage) navigateNext() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ReaderContentState.EpubReady -> {
                val chapter = st.content.chapters.getOrNull(st.currentChapter)
                ReflowableBlocksReader(
                    sectionTitle = chapter?.title.orEmpty(),
                    blocks = chapter?.paragraphs.orEmpty(),
                    chapterIndex = st.currentChapter,
                    totalChapters = st.content.chapters.size,
                    initialScrollOffset = st.initialScrollOffset,
                    prefs = prefs,
                    colors = colors,
                    onScrollOffsetChanged = viewModel::updateReflowableScrollOffset,
                    onPageTurnAction = {
                        viewModel.recordExplicitPageTurnInReflowable()
                        viewModel.playExplicitPageTurnSound()
                    },
                    onPrevChapter = navigatePrev,
                    onNextChapter = navigateNext,
                    onTapCenter = { showControls = !showControls }
                )
            }
            is ReaderContentState.TextDocumentReady -> {
                val section = st.sections.getOrNull(st.currentSection)
                ReflowableBlocksReader(
                    sectionTitle = section?.title.orEmpty(),
                    blocks = section?.blocks.orEmpty(),
                    chapterIndex = st.currentSection,
                    totalChapters = st.sections.size,
                    initialScrollOffset = st.initialScrollOffset,
                    prefs = prefs,
                    colors = colors,
                    onScrollOffsetChanged = viewModel::updateReflowableScrollOffset,
                    onPageTurnAction = {
                        viewModel.recordExplicitPageTurnInReflowable()
                        viewModel.playExplicitPageTurnSound()
                    },
                    onPrevChapter = navigatePrev,
                    onNextChapter = navigateNext,
                    onTapCenter = { showControls = !showControls }
                )
            }
        }

        // Translucent Overlay Top App Bar + Quick Tool Pills
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(color = colors.surface.copy(alpha = 0.94f), tonalElevation = 4.dp) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = colors.text,
                            navigationIconContentColor = colors.text,
                            actionIconContentColor = colors.text
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.flushCurrentPosition(recordSession = true)
                                    onBack()
                                },
                                modifier = Modifier.testTag("reader_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                        title = {
                            Text(
                                text = bookTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        actions = {
                            if (isExternalUnsaved) {
                                FilledTonalButton(
                                    onClick = { showSaveExternalDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .testTag("reader_add_to_library_button")
                                ) {
                                    Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.reader_add_to_library_short), maxLines = 1)
                                }
                            }
                            if (contentState is ReaderContentState.PdfReady ||
                                contentState is ReaderContentState.EpubReady ||
                                contentState is ReaderContentState.TextDocumentReady
                            ) {
                                IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.testTag("reader_search_button")) {
                                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                                }
                            }
                            if (!isExternalUnsaved) {
                                IconButton(
                                    onClick = {
                                        if (matchingBookmark != null) {
                                            viewModel.deleteBookmark(matchingBookmark.id)
                                            feedbackMessage = context.getString(R.string.reader_bookmark_removed)
                                        } else {
                                            viewModel.addCurrentLocationBookmark()
                                            feedbackMessage = context.getString(R.string.reader_bookmark_saved)
                                        }
                                    },
                                    modifier = Modifier.testTag("reader_bookmark_button")
                                ) {
                                    Icon(
                                        imageVector = if (matchingBookmark != null) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                        contentDescription = stringResource(
                                            if (matchingBookmark != null) R.string.reader_remove_bookmark else R.string.reader_add_bookmark
                                        ),
                                        tint = if (matchingBookmark != null) MaterialTheme.colorScheme.secondary else colors.text
                                    )
                                }
                            }
                            IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.testTag("reader_settings_button")) {
                                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.reader_settings))
                            }
                        }
                    )

                    // Quick secondary action row so TOC, Note, TTS Read Aloud, and Offline OCR never overcrowd TopAppBar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (contentState is ReaderContentState.EpubReady || contentState is ReaderContentState.TextDocumentReady) {
                            FilledTonalButton(
                                onClick = { showTocSheet = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reader_toc_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reader_toc), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (!isExternalUnsaved) {
                            FilledTonalButton(
                                onClick = { showNoteDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reader_add_note_button")
                            ) {
                                Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reader_add_note), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (contentState !is ReaderContentState.Loading && contentState !is ReaderContentState.Error) {
                            val missingPdfMsg = stringResource(R.string.reader_tts_no_pdf_text)
                            FilledTonalButton(
                                onClick = { viewModel.toggleTtsPlayPause(missingPdfMsg) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reader_tts_button")
                            ) {
                                Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reader_read_aloud), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (isPaginatedFormat) {
                            FilledTonalButton(
                                onClick = { showOcrSheet = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reader_ocr_button")
                            ) {
                                Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reader_ocr_action), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Translucent Overlay Bottom Controls + Optional Mini TTS Bar
        AnimatedVisibility(
            visible = showControls || ttsState.isPlaying || ttsState.isPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (ttsState.isPlaying || ttsState.isPaused) {
                    val missingPdfMsg = stringResource(R.string.reader_tts_no_pdf_text)
                    TtsMiniControlBar(
                        ttsState = ttsState,
                        colors = colors,
                        onTogglePlayPause = { viewModel.toggleTtsPlayPause(missingPdfMsg) },
                        onPrevious = viewModel::ttsPreviousChunk,
                        onNext = viewModel::ttsNextChunk,
                        onCycleRate = {
                            val rates = listOf(0.85f, 1.0f, 1.25f, 1.5f)
                            val next = rates.firstOrNull { it > ttsState.speechRate + 0.05f } ?: rates.first()
                            viewModel.setTtsRate(next)
                        },
                        onStop = viewModel::stopReadAloud
                    )
                }
                if (showControls) {
                    ReaderBottomControls(
                        state = contentState,
                        colors = colors,
                        showProgress = prefs.readerShowProgress,
                        isExternalUnsaved = isExternalUnsaved,
                        onPrev = navigatePrev,
                        onNext = navigateNext,
                        onOpenJumpDialog = { showJumpDialog = true },
                        onScrubPageSilent = { target ->
                            when (contentState) {
                                is ReaderContentState.PdfReady -> viewModel.goToPdfPage(target, playSound = false)
                                is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(target, playSound = false)
                                else -> Unit
                            }
                        },
                        onScrubFinishedPlaySound = { viewModel.playExplicitPageTurnSound() },
                        onToggleHighlight = { showHighlightDialog = true },
                        onAddToLibraryClick = { showSaveExternalDialog = true }
                    )
                }
            }
        }

        feedbackMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2200)
                feedbackMessage = null
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp)
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showSettingsSheet) {
        ReaderSettingsBottomSheet(
            prefs = prefs,
            isPaginatedFormat = isPaginatedFormat,
            isPdfFormat = isPdfFormat,
            onDismiss = { showSettingsSheet = false },
            onApplyProfile = { preset -> viewModel.updateReaderPrefs { applyReadingProfile(preset) } },
            onSelectTheme = { mode -> viewModel.updateReaderPrefs { setReaderThemeMode(mode) } },
            onFontSizeChange = { size -> viewModel.updateReaderPrefs { setReaderFontSize(size) } },
            onLineSpacingChange = { sp -> viewModel.updateReaderPrefs { setReaderLineSpacing(sp) } },
            onMarginChange = { m -> viewModel.updateReaderPrefs { setReaderMarginDp(m) } },
            onFontFamilyChange = { f -> viewModel.updateReaderPrefs { setReaderFontFamily(f) } },
            onSelectPdfFitMode = { mode ->
                zoomResetSignal++
                viewModel.updateReaderPrefs { setReaderPdfFitMode(mode) }
            },
            onSelectPdfScrollMode = { mode -> viewModel.updateReaderPrefs { setReaderPdfScrollMode(mode) } },
            onSelectReflowMode = { mode -> viewModel.updateReaderPrefs { setReaderReflowableMode(mode) } },
            onSelectTapZoneMode = { mode -> viewModel.updateReaderPrefs { setTapZoneMode(mode) } },
            onReducedAnimationChange = { r -> viewModel.updateReaderPrefs { setReducedAnimation(r) } },
            onResetZoom = {
                zoomResetSignal++
                showSettingsSheet = false
            },
            onPageTurnSoundChange = { enabled -> viewModel.togglePageTurnSound(enabled) },
            onKeepScreenOnChange = { on -> viewModel.updateReaderPrefs { setKeepScreenOn(on) } },
            onShowProgressChange = { show -> viewModel.updateReaderPrefs { setShowProgress(show) } },
            onReset = { viewModel.updateReaderPrefs { resetReadingPreferences() } }
        )
    }

    if (showJumpDialog) {
        val (maxCount, currentOne) = when (val st = contentState) {
            is ReaderContentState.PdfReady -> st.pageCount to (st.currentPage + 1)
            is ReaderContentState.EpubReady -> st.content.chapters.size to (st.currentChapter + 1)
            is ReaderContentState.TextDocumentReady -> st.sections.size to (st.currentSection + 1)
            is ReaderContentState.ImageCollectionReady -> st.imageEntries.size.coerceAtLeast(1) to (st.currentPage + 1)
            else -> 1 to 1
        }
        JumpToPageDialog(
            maxCount = maxCount,
            currentOneBased = currentOne,
            onDismiss = { showJumpDialog = false },
            onConfirmZeroBased = { targetIdx ->
                when (contentState) {
                    is ReaderContentState.PdfReady -> viewModel.goToPdfPage(targetIdx)
                    is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(targetIdx)
                    is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(targetIdx)
                    is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(targetIdx)
                    else -> Unit
                }
            }
        )
    }

    if (showOcrSheet) {
        val currentIdx = when (val st = contentState) {
            is ReaderContentState.PdfReady -> st.currentPage
            is ReaderContentState.ImageCollectionReady -> st.currentPage
            else -> 0
        }
        val missingPdfMsg = stringResource(R.string.reader_tts_no_pdf_text)
        OcrBottomSheet(
            currentPageOneBased = currentIdx + 1,
            isRunningOcr = isRunningOcr,
            ocrProgressPage = ocrProgressPage,
            recognizedText = ocrTextByPage[currentIdx].orEmpty(),
            isPdf = isPdfFormat,
            onDismiss = { showOcrSheet = false },
            onScanCurrentPage = { viewModel.runOcr(wholeBookLimited = false) },
            onScanWholeBookLimited = { viewModel.runOcr(wholeBookLimited = true) },
            onCancelScan = viewModel::cancelOcr,
            onReadAloudText = {
                showOcrSheet = false
                viewModel.startReadAloudFromCurrentLocation(missingPdfMsg)
            },
            onCopiedMessage = { feedbackMessage = "Copied recognized text" }
        )
    }

    if (showSaveExternalDialog) {
        val currentBook = when (val st = contentState) {
            is ReaderContentState.PdfReady -> st.book
            is ReaderContentState.EpubReady -> st.book
            is ReaderContentState.TextDocumentReady -> st.book
            is ReaderContentState.ImageCollectionReady -> st.book
            else -> null
        }
        SaveExternalBookDialog(
            initialTitle = currentBook?.title.orEmpty(),
            originalFilename = currentBook?.originalFilename.orEmpty(),
            categories = categories,
            onDismiss = { showSaveExternalDialog = false },
            onSave = { title, author, category ->
                viewModel.saveExternalBookToLibrary(title, author, category)
                showSaveExternalDialog = false
            }
        )
    }

    if (showTocSheet) {
        ModalBottomSheet(onDismissRequest = { showTocSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.reader_toc), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                when (val st = contentState) {
                    is ReaderContentState.EpubReady -> {
                        LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(st.content.toc) { _, item ->
                                val selected = item.chapterIndex == st.currentChapter
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.goToEpubChapter(item.chapterIndex)
                                            showTocSheet = false
                                        }
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    is ReaderContentState.TextDocumentReady -> {
                        LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(st.sections) { idx, sec ->
                                val selected = idx == st.currentSection
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.goToTextSection(idx)
                                            showTocSheet = false
                                        }
                                ) {
                                    Text(
                                        text = sec.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
                if (annotations.first.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(stringResource(R.string.reader_bookmarks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    annotations.first.take(6).forEach { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (contentState) {
                                        is ReaderContentState.PdfReady -> viewModel.goToPdfPage(bm.pageIndex)
                                        is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(bm.chapterIndex)
                                        is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(bm.chapterIndex)
                                        else -> Unit
                                    }
                                    showTocSheet = false
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bm.locatorDisplay, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deleteBookmark(bm.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text(stringResource(R.string.action_search)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.searchInDocument(it)
                        },
                        placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSearchingDocument) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (query.isNotBlank() && !isSearchingDocument && searchResults.isEmpty()) {
                        Text(stringResource(R.string.reader_search_no_results), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(searchResults) { match ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (contentState) {
                                                is ReaderContentState.PdfReady -> viewModel.goToPdfPage(match.sectionIndex)
                                                is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(match.sectionIndex)
                                                is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(match.sectionIndex)
                                                else -> Unit
                                            }
                                            showSearchDialog = false
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(match.sectionTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(match.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showNoteDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(stringResource(R.string.reader_add_note)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.bookmarks_note_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.bookmarks_note_content_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addNoteAtCurrentLocation(title, content)
                        feedbackMessage = context.getString(R.string.reader_note_saved)
                        showNoteDialog = false
                    },
                    enabled = content.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showHighlightDialog) {
        HighlightCreationDialog(
            onDismiss = { showHighlightDialog = false },
            onSave = { excerpt, colorHex ->
                viewModel.addHighlight(excerpt, colorHex)
                feedbackMessage = context.getString(R.string.reader_highlight_saved)
                showHighlightDialog = false
            }
        )
    }
}
