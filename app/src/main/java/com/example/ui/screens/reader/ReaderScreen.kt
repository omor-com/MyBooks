package com.example.ui.screens.reader

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.CategoryEntity
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.ReaderFontFamilyOption
import com.example.data.preferences.UserPreferences
import com.example.data.reader.DocumentFormat
import com.example.data.reader.TextBlock
import com.example.ui.theme.AmoledBackground
import com.example.ui.theme.AmoledSurface
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOnSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightOnSurface
import com.example.ui.theme.LightSurface
import com.example.ui.theme.NotoBengaliFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.ReaderThemeMode
import com.example.ui.theme.SepiaBackground
import com.example.ui.theme.SepiaSurface
import com.example.ui.theme.SepiaText
import com.example.ui.viewmodel.ReaderContentState
import com.example.ui.viewmodel.ReaderViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private data class ReaderColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val subtleText: Color
)

private fun readerColorsFor(mode: ReaderThemeMode): ReaderColors = when (mode) {
    ReaderThemeMode.LIGHT -> ReaderColors(LightBackground, LightSurface, LightOnSurface, Color(0xFF5A5366))
    ReaderThemeMode.DARK -> ReaderColors(DarkBackground, DarkSurface, DarkOnSurface, Color(0xFFB5ADC4))
    ReaderThemeMode.SEPIA -> ReaderColors(SepiaBackground, SepiaSurface, SepiaText, Color(0xFF6B5B45))
    ReaderThemeMode.AMOLED -> ReaderColors(AmoledBackground, AmoledSurface, Color(0xFFEDEDED), Color(0xFFA0A0A0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
    val annotations by viewModel.annotationsState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isExternalUnsaved by viewModel.isExternalUnsaved.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    // Start in clean distraction-free reading mode; tapping center reveals overlay chrome
    var showControls by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(contentState) {
        if (contentState is ReaderContentState.Error) {
            showControls = true
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.flushCurrentPosition()
    }

    // System bars immersive hide/show synced cleanly with showControls; always restored on dispose
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

    // Keep Screen On support scoped strictly while reading
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
            showNoteDialog -> showNoteDialog = false
            showHighlightDialog -> showHighlightDialog = false
            showSaveExternalDialog -> showSaveExternalDialog = false
            showControls -> showControls = false
            else -> {
                viewModel.flushCurrentPosition()
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
        // Core full-bleed edge-to-edge reader content (never resized when overlay controls toggle)
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
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.reader_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        fontWeight = FontWeight.Bold
                    )
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
                ZoomableBitmapViewer(
                    bitmap = currentBitmap,
                    contentDescription = stringResource(R.string.reader_page_indicator, st.currentPage + 1, st.pageCount),
                    fitMode = prefs.readerPdfFitMode,
                    zoomResetSignal = zoomResetSignal,
                    onTapLeft = navigatePrev,
                    onTapRight = navigateNext,
                    onTapCenter = { showControls = !showControls },
                    onSwipePrev = navigatePrev,
                    onSwipeNext = navigateNext,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ReaderContentState.ImageCollectionReady -> {
                ZoomableBitmapViewer(
                    bitmap = currentBitmap,
                    contentDescription = st.book.title,
                    fitMode = prefs.readerPdfFitMode,
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
                    initialScrollOffset = st.initialScrollOffset,
                    prefs = prefs,
                    colors = colors,
                    onScrollOffsetChanged = viewModel::updateReflowableScrollOffset,
                    onTapLeft = navigatePrev,
                    onTapCenter = { showControls = !showControls },
                    onTapRight = navigateNext
                )
            }
            is ReaderContentState.TextDocumentReady -> {
                val section = st.sections.getOrNull(st.currentSection)
                ReflowableBlocksReader(
                    sectionTitle = section?.title.orEmpty(),
                    blocks = section?.blocks.orEmpty(),
                    chapterIndex = st.currentSection,
                    initialScrollOffset = st.initialScrollOffset,
                    prefs = prefs,
                    colors = colors,
                    onScrollOffsetChanged = viewModel::updateReflowableScrollOffset,
                    onTapLeft = navigatePrev,
                    onTapCenter = { showControls = !showControls },
                    onTapRight = navigateNext
                )
            }
        }

        // Translucent Overlay Top App Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = colors.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                TopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
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
                                viewModel.flushCurrentPosition()
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
                        if (contentState is ReaderContentState.EpubReady || contentState is ReaderContentState.TextDocumentReady) {
                            IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.testTag("reader_search_button")) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            IconButton(onClick = { showTocSheet = true }, modifier = Modifier.testTag("reader_toc_button")) {
                                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = stringResource(R.string.reader_toc))
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
                            IconButton(onClick = { showNoteDialog = true }, modifier = Modifier.testTag("reader_add_note_button")) {
                                Icon(Icons.Rounded.NoteAdd, contentDescription = stringResource(R.string.reader_add_note))
                            }
                        }
                        IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.testTag("reader_settings_button")) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.reader_settings))
                        }
                    }
                )
            }
        }

        // Translucent Overlay Bottom Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomControls(
                state = contentState,
                colors = colors,
                showProgress = prefs.readerShowProgress,
                isExternalUnsaved = isExternalUnsaved,
                onPrev = navigatePrev,
                onNext = navigateNext,
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

        feedbackMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2000)
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
            onDismiss = { showSettingsSheet = false },
            onSelectTheme = { mode -> viewModel.updateReaderPrefs { setReaderThemeMode(mode) } },
            onFontSizeChange = { size -> viewModel.updateReaderPrefs { setReaderFontSize(size) } },
            onLineSpacingChange = { sp -> viewModel.updateReaderPrefs { setReaderLineSpacing(sp) } },
            onMarginChange = { m -> viewModel.updateReaderPrefs { setReaderMarginDp(m) } },
            onFontFamilyChange = { f -> viewModel.updateReaderPrefs { setReaderFontFamily(f) } },
            onSelectPdfFitMode = { mode ->
                zoomResetSignal++
                viewModel.updateReaderPrefs { setReaderPdfFitMode(mode) }
            },
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
                Text(
                    text = stringResource(R.string.reader_toc),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                when (val st = contentState) {
                    is ReaderContentState.EpubReady -> {
                        LazyColumn(
                            modifier = Modifier.height(340.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                        LazyColumn(
                            modifier = Modifier.height(340.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                    Text(
                        text = stringResource(R.string.reader_bookmarks),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                    if (query.isNotBlank() && searchResults.isEmpty()) {
                        Text(stringResource(R.string.reader_search_no_results), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(searchResults) { match ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (contentState) {
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
        var excerpt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showHighlightDialog = false },
            title = { Text(stringResource(R.string.reader_save_highlight)) },
            text = {
                OutlinedTextField(
                    value = excerpt,
                    onValueChange = { excerpt = it },
                    label = { Text(stringResource(R.string.bookmarks_note_content_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addHighlight(excerpt)
                        feedbackMessage = context.getString(R.string.reader_highlight_saved)
                        showHighlightDialog = false
                    },
                    enabled = excerpt.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHighlightDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ZoomableBitmapViewer(
    bitmap: Bitmap?,
    contentDescription: String,
    fitMode: PdfFitMode,
    zoomResetSignal: Int,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapCenter: () -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(bitmap, zoomResetSignal) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap, zoomResetSignal) { mutableStateOf(Offset.Zero) }
    val verticalScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF141414))
            .pointerInput(bitmap, zoomResetSignal) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                        }
                    },
                    onTap = { tap ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            tap.x < width * 0.25f -> onTapLeft()
                            tap.x > width * 0.75f -> onTapRight()
                            else -> onTapCenter()
                        }
                    }
                )
            }
            .pointerInput(bitmap, zoomResetSignal, fitMode) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var horizontalDragTotal = 0f
                    var verticalDragTotal = 0f
                    var lockedAsHorizontalSwipe = false
                    var lockedAsTransform = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressedCount > 1 || zoomChange != 1f || scale > 1.05f || lockedAsTransform) {
                            lockedAsTransform = true
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1.01f) {
                                val maxOffsetX = size.width * (newScale - 1f) * 0.6f
                                val maxOffsetY = size.height * (newScale - 1f) * 0.6f
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (fitMode == PdfFitMode.FIT_PAGE) {
                            // Unzoomed 1-finger swipe in Fit Page mode
                            horizontalDragTotal += panChange.x
                            verticalDragTotal += panChange.y
                            if (!lockedAsHorizontalSwipe &&
                                abs(horizontalDragTotal) > touchSlop &&
                                abs(horizontalDragTotal) > abs(verticalDragTotal) * 1.25f
                            ) {
                                lockedAsHorizontalSwipe = true
                            }
                            if (lockedAsHorizontalSwipe) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else {
                            // In Fit Width mode, only swipe horizontally if horizontal strongly dominates vertical
                            horizontalDragTotal += panChange.x
                            verticalDragTotal += panChange.y
                            if (!lockedAsHorizontalSwipe &&
                                abs(horizontalDragTotal) > touchSlop * 1.5f &&
                                abs(horizontalDragTotal) > abs(verticalDragTotal) * 1.6f
                            ) {
                                lockedAsHorizontalSwipe = true
                            }
                            if (lockedAsHorizontalSwipe) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    }

                    if (lockedAsHorizontalSwipe && scale <= 1.05f) {
                        when {
                            horizontalDragTotal < -90f -> onSwipeNext()
                            horizontalDragTotal > 90f -> onSwipePrev()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            val imageModifier = if (fitMode == PdfFitMode.FIT_WIDTH && scale <= 1.02f) {
                val aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
                val targetHeightDp = (maxWidth / aspectRatio).coerceAtLeast(maxHeight)
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(verticalScrollState)
                    .heightIn(min = targetHeightDp)
            } else {
                Modifier.fillMaxSize()
            }

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = if (fitMode == PdfFitMode.FIT_WIDTH) ContentScale.FillWidth else ContentScale.Fit,
                modifier = imageModifier.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
            )
            if (scale > 1.05f) {
                OutlinedButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Rounded.ZoomOutMap, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reader_zoom_reset), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReflowableBlocksReader(
    sectionTitle: String,
    blocks: List<TextBlock>,
    chapterIndex: Int,
    initialScrollOffset: Int,
    prefs: UserPreferences,
    colors: ReaderColors,
    onScrollOffsetChanged: (Int) -> Unit,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit
) {
    val listState = rememberLazyListState()
    var lastRestoredChapter by remember { mutableIntStateOf(-1) }

    LaunchedEffect(chapterIndex, initialScrollOffset) {
        if (lastRestoredChapter != chapterIndex) {
            lastRestoredChapter = chapterIndex
            listState.scrollToItem(initialScrollOffset.coerceAtLeast(0))
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> onScrollOffsetChanged(idx) }
    }

    val fontFamily = when (prefs.readerFontFamily) {
        ReaderFontFamilyOption.DEFAULT -> FontFamily.Default
        ReaderFontFamilyOption.BENGALI_SANS -> NotoBengaliFontFamily
        ReaderFontFamilyOption.SERIF -> FontFamily.Serif
        ReaderFontFamilyOption.SANS -> PlusJakartaFontFamily
    }

    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chapterIndex) {
                    detectTapGestures(
                        onTap = { tap ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                tap.x < w * 0.2f -> onTapLeft()
                                tap.x > w * 0.8f -> onTapRight()
                                else -> onTapCenter()
                            }
                        }
                    )
                },
            contentPadding = PaddingValues(
                horizontal = prefs.readerMarginDp.dp,
                vertical = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sectionTitle.isNotBlank()) {
                item {
                    Text(
                        text = sectionTitle,
                        fontFamily = fontFamily,
                        fontSize = (prefs.readerFontSizeSp + 5f).sp,
                        lineHeight = ((prefs.readerFontSizeSp + 5f) * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        color = colors.subtleText.copy(alpha = 0.25f)
                    )
                }
            }
            items(blocks) { block ->
                val baseSize = prefs.readerFontSizeSp
                val lineHeight = (baseSize * prefs.readerLineSpacing).sp
                when (block) {
                    is TextBlock.Heading -> Text(
                        text = block.text,
                        fontFamily = fontFamily,
                        fontSize = (baseSize + (4 - block.level).coerceIn(1, 4)).sp,
                        lineHeight = ((baseSize + 3f) * 1.35f).sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    is TextBlock.Paragraph -> Text(
                        text = block.text,
                        fontFamily = fontFamily,
                        fontSize = baseSize.sp,
                        lineHeight = lineHeight,
                        color = colors.text
                    )
                    is TextBlock.Quote -> Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = baseSize.sp,
                            lineHeight = lineHeight,
                            fontStyle = FontStyle.Italic,
                            color = colors.subtleText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    is TextBlock.Code -> Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (baseSize - 2f).coerceAtLeast(11f).sp,
                            color = colors.text,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    is TextBlock.BulletItem -> Text(
                        text = "•  ${block.text}",
                        fontFamily = fontFamily,
                        fontSize = baseSize.sp,
                        lineHeight = lineHeight,
                        color = colors.text,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(
    state: ReaderContentState,
    colors: ReaderColors,
    showProgress: Boolean,
    isExternalUnsaved: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onScrubPageSilent: (Int) -> Unit,
    onScrubFinishedPlaySound: () -> Unit,
    onToggleHighlight: () -> Unit,
    onAddToLibraryClick: () -> Unit
) {
    val labelAndProgress: Triple<String, Float, Pair<Boolean, Boolean>>? = when (state) {
        is ReaderContentState.PdfReady -> Triple(
            stringResource(R.string.reader_page_indicator, state.currentPage + 1, state.pageCount),
            (state.currentPage + 1).toFloat() / state.pageCount.coerceAtLeast(1),
            (state.currentPage > 0) to (state.currentPage < state.pageCount - 1)
        )
        is ReaderContentState.EpubReady -> Triple(
            stringResource(R.string.reader_chapter_indicator, state.currentChapter + 1, state.content.chapters.size),
            (state.currentChapter + 1).toFloat() / state.content.chapters.size.coerceAtLeast(1),
            (state.currentChapter > 0) to (state.currentChapter < state.content.chapters.size - 1)
        )
        is ReaderContentState.TextDocumentReady -> Triple(
            stringResource(R.string.reader_chapter_indicator, state.currentSection + 1, state.sections.size),
            (state.currentSection + 1).toFloat() / state.sections.size.coerceAtLeast(1),
            (state.currentSection > 0) to (state.currentSection < state.sections.size - 1)
        )
        is ReaderContentState.ImageCollectionReady -> Triple(
            if (state.isSingleImage) "1 / 1" else stringResource(R.string.reader_page_indicator, state.currentPage + 1, state.imageEntries.size),
            if (state.isSingleImage) 1f else (state.currentPage + 1).toFloat() / state.imageEntries.size.coerceAtLeast(1),
            (state.currentPage > 0) to (!state.isSingleImage && state.currentPage < state.imageEntries.size - 1)
        )
        else -> null
    }

    if (labelAndProgress == null) return
    val (label, progress, canNav) = labelAndProgress
    var scrubbingValue by remember { mutableStateOf<Float?>(null) }
    var dragStartPage by remember { mutableIntStateOf(-1) }

    Surface(color = colors.surface.copy(alpha = 0.92f), tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (showProgress) {
                if (state is ReaderContentState.PdfReady && state.pageCount > 1) {
                    Slider(
                        value = scrubbingValue ?: state.currentPage.toFloat(),
                        onValueChange = { nextVal ->
                            if (scrubbingValue == null) {
                                dragStartPage = state.currentPage
                            }
                            scrubbingValue = nextVal
                            onScrubPageSilent(nextVal.roundToInt())
                        },
                        onValueChangeFinished = {
                            val changed = dragStartPage != -1 && state.currentPage != dragStartPage
                            scrubbingValue = null
                            dragStartPage = -1
                            if (changed) {
                                onScrubFinishedPlaySound()
                            }
                        },
                        valueRange = 0f..(state.pageCount - 1).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrev,
                    enabled = canNav.first,
                    modifier = Modifier.testTag("reader_prev_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.NavigateBefore,
                        contentDescription = stringResource(R.string.reader_prev_page),
                        tint = if (canNav.first) colors.text else colors.subtleText.copy(alpha = 0.35f)
                    )
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExternalUnsaved) {
                        IconButton(onClick = onAddToLibraryClick) {
                            Icon(Icons.Rounded.LibraryAdd, contentDescription = stringResource(R.string.reader_add_to_library), tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = onToggleHighlight, modifier = Modifier.testTag("reader_highlight_button")) {
                            Icon(Icons.Rounded.Highlight, contentDescription = stringResource(R.string.reader_save_highlight), tint = colors.text)
                        }
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = canNav.second,
                        modifier = Modifier.testTag("reader_next_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.NavigateNext,
                            contentDescription = stringResource(R.string.reader_next_page),
                            tint = if (canNav.second) colors.text else colors.subtleText.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsBottomSheet(
    prefs: UserPreferences,
    isPaginatedFormat: Boolean,
    onDismiss: () -> Unit,
    onSelectTheme: (ReaderThemeMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onMarginChange: (Int) -> Unit,
    onFontFamilyChange: (ReaderFontFamilyOption) -> Unit,
    onSelectPdfFitMode: (PdfFitMode) -> Unit,
    onResetZoom: () -> Unit,
    onPageTurnSoundChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onShowProgressChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.action_reset))
                }
            }

            if (isPaginatedFormat) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = prefs.readerPdfFitMode == PdfFitMode.FIT_PAGE,
                        onClick = { onSelectPdfFitMode(PdfFitMode.FIT_PAGE) },
                        label = { Text(stringResource(R.string.reader_fit_page)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = prefs.readerPdfFitMode == PdfFitMode.FIT_WIDTH,
                        onClick = { onSelectPdfFitMode(PdfFitMode.FIT_WIDTH) },
                        label = { Text(stringResource(R.string.reader_fit_width)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = onResetZoom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ZoomOutMap, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reader_zoom_reset))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ReaderThemeMode.LIGHT to R.string.reader_theme_light,
                        ReaderThemeMode.SEPIA to R.string.reader_theme_sepia,
                        ReaderThemeMode.DARK to R.string.reader_theme_dark,
                        ReaderThemeMode.AMOLED to R.string.reader_theme_amoled
                    ).forEach { (mode, resId) ->
                        FilterChip(
                            selected = prefs.readerThemeMode == mode,
                            onClick = { onSelectTheme(mode) },
                            label = { Text(stringResource(resId), maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column {
                    Text("${stringResource(R.string.reader_font_size)}: ${prefs.readerFontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = prefs.readerFontSizeSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..30f
                    )
                }

                Column {
                    Text("${stringResource(R.string.reader_line_spacing)}: ${String.format("%.1f", prefs.readerLineSpacing)}x", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = prefs.readerLineSpacing,
                        onValueChange = onLineSpacingChange,
                        valueRange = 1.1f..2.1f
                    )
                }

                Column {
                    Text("${stringResource(R.string.reader_margins)}: ${prefs.readerMarginDp} dp", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = prefs.readerMarginDp.toFloat(),
                        onValueChange = { onMarginChange(it.roundToInt()) },
                        valueRange = 8f..44f
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFontFamilyOption.entries.forEach { option ->
                        FilterChip(
                            selected = prefs.readerFontFamily == option,
                            onClick = { onFontFamilyChange(option) },
                            label = { Text(option.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_page_turn_sound), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.pageTurnSoundEnabled, onCheckedChange = onPageTurnSoundChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.reader_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.readerKeepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_show_progress), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.readerShowProgress, onCheckedChange = onShowProgressChange)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SaveExternalBookDialog(
    initialTitle: String,
    originalFilename: String,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_add_to_library)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (originalFilename.isNotBlank()) {
                    Text(
                        text = originalFilename,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.library_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.library_field_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.library_field_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, author, category.ifBlank { null }) },
                enabled = title.isNotBlank()
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
