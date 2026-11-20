package com.example.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.preferences.ReaderFontFamilyOption
import com.example.data.preferences.UserPreferences
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

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showHighlightDialog by remember { mutableStateOf(false) }
    var showBookmarkFeedback by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Keep Screen On support scoped strictly while reading
    DisposableEffect(prefs.readerKeepScreenOn) {
        val window = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
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
            prefs.readerFullscreen -> viewModel.updateReaderPrefs { setFullscreen(false) }
            else -> onBack()
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_screen"),
        containerColor = colors.bg,
        contentWindowInsets = if (prefs.readerFullscreen) WindowInsets(0) else WindowInsets.safeDrawing,
        topBar = {
            if (!prefs.readerFullscreen) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surface,
                        titleContentColor = colors.text,
                        navigationIconContentColor = colors.text,
                        actionIconContentColor = colors.text
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
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
                        if (contentState is ReaderContentState.EpubReady || contentState is ReaderContentState.TextDocumentReady) {
                            IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.testTag("reader_search_button")) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            IconButton(onClick = { showTocSheet = true }, modifier = Modifier.testTag("reader_toc_button")) {
                                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = stringResource(R.string.reader_toc))
                            }
                        }
                        IconButton(
                            onClick = {
                                if (matchingBookmark != null) {
                                    viewModel.deleteBookmark(matchingBookmark.id)
                                    showBookmarkFeedback = context.getString(R.string.reader_bookmark_removed)
                                } else {
                                    viewModel.addCurrentLocationBookmark()
                                    showBookmarkFeedback = context.getString(R.string.reader_bookmark_saved)
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
                        IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.testTag("reader_settings_button")) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.reader_settings))
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!prefs.readerFullscreen) {
                ReaderBottomControls(
                    state = contentState,
                    colors = colors,
                    showProgress = prefs.readerShowProgress,
                    onPrev = {
                        when (val st = contentState) {
                            is ReaderContentState.PdfReady -> viewModel.goToPdfPage(st.currentPage - 1)
                            is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(st.currentChapter - 1)
                            is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(st.currentSection - 1)
                            is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(st.currentPage - 1)
                            else -> Unit
                        }
                    },
                    onNext = {
                        when (val st = contentState) {
                            is ReaderContentState.PdfReady -> viewModel.goToPdfPage(st.currentPage + 1)
                            is ReaderContentState.EpubReady -> viewModel.goToEpubChapter(st.currentChapter + 1)
                            is ReaderContentState.TextDocumentReady -> viewModel.goToTextSection(st.currentSection + 1)
                            is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(st.currentPage + 1)
                            else -> Unit
                        }
                    },
                    onJumpPage = { target ->
                        when (contentState) {
                            is ReaderContentState.PdfReady -> viewModel.goToPdfPage(target)
                            is ReaderContentState.ImageCollectionReady -> viewModel.goToImagePage(target)
                            else -> Unit
                        }
                    },
                    onToggleHighlight = { showHighlightDialog = true },
                    onToggleFullscreen = { viewModel.updateReaderPrefs { setFullscreen(true) } }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                            text = if (st.isMissingFile) stringResource(R.string.reader_error_missing) else st.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.subtleText
                        )
                        Button(onClick = onBack) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
                is ReaderContentState.PdfReady -> {
                    ZoomableBitmapViewer(
                        bitmap = currentBitmap,
                        contentDescription = stringResource(R.string.reader_page_indicator, st.currentPage + 1, st.pageCount),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is ReaderContentState.ImageCollectionReady -> {
                    ZoomableBitmapViewer(
                        bitmap = currentBitmap,
                        contentDescription = st.book.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is ReaderContentState.EpubReady -> {
                    val chapter = st.content.chapters.getOrNull(st.currentChapter)
                    ReflowableBlocksReader(
                        sectionTitle = chapter?.title.orEmpty(),
                        blocks = chapter?.paragraphs.orEmpty(),
                        chapterIndex = st.currentChapter,
                        prefs = prefs,
                        colors = colors
                    )
                }
                is ReaderContentState.TextDocumentReady -> {
                    val section = st.sections.getOrNull(st.currentSection)
                    ReflowableBlocksReader(
                        sectionTitle = section?.title.orEmpty(),
                        blocks = section?.blocks.orEmpty(),
                        chapterIndex = st.currentSection,
                        prefs = prefs,
                        colors = colors
                    )
                }
            }

            if (prefs.readerFullscreen) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface.copy(alpha = 0.86f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                ) {
                    IconButton(onClick = { viewModel.updateReaderPrefs { setFullscreen(false) } }) {
                        Icon(
                            imageVector = Icons.Rounded.FullscreenExit,
                            contentDescription = stringResource(R.string.reader_fullscreen),
                            tint = colors.text
                        )
                    }
                }
            }

            showBookmarkFeedback?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1800)
                    showBookmarkFeedback = null
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
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
    }

    if (showSettingsSheet) {
        ReaderSettingsBottomSheet(
            prefs = prefs,
            onDismiss = { showSettingsSheet = false },
            onSelectTheme = { mode -> viewModel.updateReaderPrefs { setReaderThemeMode(mode) } },
            onFontSizeChange = { size -> viewModel.updateReaderPrefs { setReaderFontSize(size) } },
            onLineSpacingChange = { sp -> viewModel.updateReaderPrefs { setReaderLineSpacing(sp) } },
            onMarginChange = { m -> viewModel.updateReaderPrefs { setReaderMarginDp(m) } },
            onFontFamilyChange = { f -> viewModel.updateReaderPrefs { setReaderFontFamily(f) } },
            onKeepScreenOnChange = { on -> viewModel.updateReaderPrefs { setKeepScreenOn(on) } },
            onShowProgressChange = { show -> viewModel.updateReaderPrefs { setShowProgress(show) } },
            onReset = { viewModel.updateReaderPrefs { resetReadingPreferences() } }
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
                        showBookmarkFeedback = context.getString(R.string.reader_note_saved)
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
                        showBookmarkFeedback = context.getString(R.string.reader_highlight_saved)
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
    modifier: Modifier = Modifier
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A))
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = newScale
                    if (newScale > 1f) {
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-800f, 800f),
                            y = (offset.y + pan.y).coerceIn(-1200f, 1200f)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
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
                        .align(Alignment.TopStart)
                        .padding(12.dp)
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
    prefs: UserPreferences,
    colors: ReaderColors
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chapterIndex) {
        listState.scrollToItem(0)
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = prefs.readerMarginDp.dp,
                vertical = 20.dp
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
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpPage: (Int) -> Unit,
    onToggleHighlight: () -> Unit,
    onToggleFullscreen: () -> Unit
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

    Surface(color = colors.surface, tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (showProgress) {
                if (state is ReaderContentState.PdfReady && state.pageCount > 1) {
                    Slider(
                        value = state.currentPage.toFloat(),
                        onValueChange = { onJumpPage(it.roundToInt()) },
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
                    IconButton(onClick = onToggleHighlight, modifier = Modifier.testTag("reader_highlight_button")) {
                        Icon(Icons.Rounded.Highlight, contentDescription = stringResource(R.string.reader_save_highlight), tint = colors.text)
                    }
                    IconButton(onClick = onToggleFullscreen, modifier = Modifier.testTag("reader_fullscreen_button")) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.reader_fullscreen), tint = colors.text)
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
    onDismiss: () -> Unit,
    onSelectTheme: (ReaderThemeMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onMarginChange: (Int) -> Unit,
    onFontFamilyChange: (ReaderFontFamilyOption) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onShowProgressChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
