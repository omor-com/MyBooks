package com.example.ui.screens.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.CategoryEntity
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.PdfScrollMode
import com.example.data.preferences.ReaderFontFamilyOption
import com.example.data.preferences.ReadingProfilePreset
import com.example.data.preferences.ReflowablePaginationMode
import com.example.data.preferences.TapZoneMode
import com.example.data.preferences.UserPreferences
import com.example.data.reader.TextBlock
import com.example.data.reader.TtsUiState
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class ReaderColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val subtleText: Color
)

internal fun readerColorsFor(mode: ReaderThemeMode): ReaderColors = when (mode) {
    ReaderThemeMode.LIGHT -> ReaderColors(LightBackground, LightSurface, LightOnSurface, Color(0xFF5A5366))
    ReaderThemeMode.DARK -> ReaderColors(DarkBackground, DarkSurface, DarkOnSurface, Color(0xFFB5ADC4))
    ReaderThemeMode.SEPIA -> ReaderColors(SepiaBackground, SepiaSurface, SepiaText, Color(0xFF6B5B45))
    ReaderThemeMode.AMOLED -> ReaderColors(AmoledBackground, AmoledSurface, Color(0xFFEDEDED), Color(0xFFA0A0A0))
}

internal enum class TapAction {
    PREV, NEXT, TOGGLE_CHROME, NONE
}

internal fun resolveTapAction(tapX: Float, widthPx: Float, mode: TapZoneMode): TapAction {
    val w = widthPx.coerceAtLeast(1f)
    return when (mode) {
        TapZoneMode.STANDARD -> when {
            tapX < w * 0.25f -> TapAction.PREV
            tapX > w * 0.75f -> TapAction.NEXT
            else -> TapAction.TOGGLE_CHROME
        }
        TapZoneMode.LEFT_HANDED -> when {
            tapX < w * 0.25f -> TapAction.NEXT
            tapX > w * 0.75f -> TapAction.PREV
            else -> TapAction.TOGGLE_CHROME
        }
        TapZoneMode.SIMPLE_CENTER -> TapAction.TOGGLE_CHROME
        TapZoneMode.OFF -> if (tapX in (w * 0.25f)..(w * 0.75f)) TapAction.TOGGLE_CHROME else TapAction.NONE
    }
}

@Composable
internal fun ZoomableBitmapViewer(
    bitmap: Bitmap?,
    contentDescription: String,
    fitMode: PdfFitMode,
    tapZoneMode: TapZoneMode,
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
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    val verticalScrollState = rememberScrollState()

    fun handleResolvedTap(tapX: Float, width: Float) {
        when (resolveTapAction(tapX, width, tapZoneMode)) {
            TapAction.PREV -> onTapLeft()
            TapAction.NEXT -> onTapRight()
            TapAction.TOGGLE_CHROME -> onTapCenter()
            TapAction.NONE -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF141414))
            .pointerInput(bitmap, zoomResetSignal, fitMode, tapZoneMode) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val downTime = down.uptimeMillis
                    var horizontalDragTotal = 0f
                    var verticalDragTotal = 0f
                    var movedBeyondSlop = false
                    var wasMultiTouch = false
                    var lockedAsTransform = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) break
                        if (pressedCount > 1) wasMultiTouch = true

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressedCount > 1 || abs(zoomChange - 1f) > 0.01f || scale > 1.05f || lockedAsTransform) {
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
                        } else {
                            horizontalDragTotal += panChange.x
                            verticalDragTotal += panChange.y
                            if (hypot(horizontalDragTotal.toDouble(), verticalDragTotal.toDouble()) > touchSlop) {
                                movedBeyondSlop = true
                            }
                            if (fitMode == PdfFitMode.FIT_PAGE &&
                                movedBeyondSlop &&
                                abs(horizontalDragTotal) > abs(verticalDragTotal) * 1.25f
                            ) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    }

                    val elapsed = (eventTimeFallback() - downTime).coerceAtLeast(0L)
                    if (!lockedAsTransform && !wasMultiTouch && movedBeyondSlop && scale <= 1.05f) {
                        if (abs(horizontalDragTotal) > 90f && abs(horizontalDragTotal) > abs(verticalDragTotal) * 1.3f) {
                            when {
                                horizontalDragTotal < -90f -> onSwipeNext()
                                horizontalDragTotal > 90f -> onSwipePrev()
                            }
                        }
                    } else if (!movedBeyondSlop && !wasMultiTouch && elapsed < 320L) {
                        val now = System.currentTimeMillis()
                        val distFromLast = hypot(
                            (downPos.x - lastTapPosition.x).toDouble(),
                            (downPos.y - lastTapPosition.y).toDouble()
                        )
                        if (now - lastTapTimeMs < 260L && distFromLast < 96.0) {
                            lastTapTimeMs = 0L
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.2f
                            }
                        } else {
                            lastTapTimeMs = now
                            lastTapPosition = downPos
                            handleResolvedTap(downPos.x, size.width.toFloat())
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

private fun eventTimeFallback(): Long = android.os.SystemClock.uptimeMillis()

@Composable
internal fun PdfContinuousScrollViewer(
    pageCount: Int,
    currentPage: Int,
    pageBitmaps: Map<Int, Bitmap>,
    tapZoneMode: TapZoneMode,
    onRequestPage: (Int) -> Unit,
    onVisiblePageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                onVisiblePageChanged(idx)
                onRequestPage(idx)
                if (idx + 1 < pageCount) onRequestPage(idx + 1)
                if (idx - 1 >= 0) onRequestPage(idx - 1)
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(Color(0xFF141414))
            .pointerInput(tapZoneMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val downTime = down.uptimeMillis
                    var moved = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                        val cur = ev.changes.firstOrNull()?.position ?: downPos
                        if (hypot((cur.x - downPos.x).toDouble(), (cur.y - downPos.y).toDouble()) > slop) {
                            moved = true
                        }
                    }
                    val elapsed = eventTimeFallback() - downTime
                    if (!moved && elapsed < 260L) {
                        if (resolveTapAction(downPos.x, size.width.toFloat(), tapZoneMode) != TapAction.NONE) {
                            onTapCenter()
                        }
                    }
                }
            },
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pageCount) { pageIdx ->
            LaunchedEffect(pageIdx) {
                onRequestPage(pageIdx)
            }
            val bmp = pageBitmaps[pageIdx]?.takeIf { !it.isRecycled }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF222222),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (bmp != null) {
                            Modifier.aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                        } else {
                            Modifier.height(420.dp)
                        }
                    )
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.reader_page_indicator, pageIdx + 1, pageCount),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                            Text("${pageIdx + 1} / $pageCount", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReflowableBlocksReader(
    sectionTitle: String,
    blocks: List<TextBlock>,
    chapterIndex: Int,
    totalChapters: Int,
    initialScrollOffset: Int,
    prefs: UserPreferences,
    colors: ReaderColors,
    onScrollOffsetChanged: (Int) -> Unit,
    onPageTurnAction: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onTapCenter: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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

    fun performSmoothScrollStep(forward: Boolean) {
        val stepPx = (listState.layoutInfo.viewportSize.height * 0.85f).coerceAtLeast(240f)
        if (forward && !listState.canScrollForward) {
            onNextChapter()
            return
        }
        if (!forward && !listState.canScrollBackward) {
            onPrevChapter()
            return
        }
        onPageTurnAction()
        coroutineScope.launch {
            val delta = if (forward) stepPx else -stepPx
            if (prefs.readerReducedAnimation) {
                listState.scrollBy(delta)
            } else {
                listState.animateScrollBy(delta)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(chapterIndex, prefs.readerTapZoneMode, prefs.readerReflowableMode, prefs.readerReducedAnimation) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downPos = down.position
                    val downTime = down.uptimeMillis
                    var moved = false
                    var multiTouch = false
                    var consumedBySelection = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.size > 1) multiTouch = true
                        if (event.changes.any { it.isConsumed }) consumedBySelection = true
                        if (event.changes.none { it.pressed }) break
                        val cur = event.changes.firstOrNull()?.position ?: downPos
                        if (hypot((cur.x - downPos.x).toDouble(), (cur.y - downPos.y).toDouble()) > touchSlop) {
                            moved = true
                        }
                    }
                    val elapsed = eventTimeFallback() - downTime
                    if (!moved && !multiTouch && !consumedBySelection && elapsed < 260L) {
                        val action = resolveTapAction(downPos.x, size.width.toFloat(), prefs.readerTapZoneMode)
                        when {
                            action == TapAction.TOGGLE_CHROME -> onTapCenter()
                            prefs.readerReflowableMode == ReflowablePaginationMode.CONTINUOUS_SCROLL && action != TapAction.NONE -> {
                                onTapCenter()
                            }
                            action == TapAction.PREV -> performSmoothScrollStep(forward = false)
                            action == TapAction.NEXT -> performSmoothScrollStep(forward = true)
                        }
                    }
                }
            }
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
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
                if (totalChapters > 1) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPrevChapter,
                                enabled = chapterIndex > 0,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.reader_prev_chapter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = onNextChapter,
                                enabled = chapterIndex < totalChapters - 1,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.reader_next_chapter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun TtsMiniControlBar(
    ttsState: TtsUiState,
    colors: ReaderColors,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRate: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("reader_tts_mini_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.reader_read_aloud), style = MaterialTheme.typography.labelLarge, color = colors.text)
                    if (ttsState.totalChunks > 0) {
                        Text(
                            stringResource(R.string.reader_tts_chunk_progress, ttsState.currentChunkIndex + 1, ttsState.totalChunks),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.subtleText
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCycleRate) {
                    Text("${String.format("%.2g", ttsState.speechRate)}x")
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = stringResource(R.string.reader_prev_page), tint = colors.text)
                }
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.testTag("reader_tts_play_pause_button")) {
                    Icon(
                        imageVector = if (ttsState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (ttsState.isPlaying) R.string.reader_tts_pause else R.string.reader_tts_resume),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = stringResource(R.string.reader_next_page), tint = colors.text)
                }
                IconButton(onClick = onStop, modifier = Modifier.testTag("reader_tts_stop_button")) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.reader_tts_stop), tint = colors.subtleText)
                }
            }
        }
    }
}

@Composable
internal fun ReaderBottomControls(
    state: ReaderContentState,
    colors: ReaderColors,
    showProgress: Boolean,
    isExternalUnsaved: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenJumpDialog: () -> Unit,
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
                            if (scrubbingValue == null) dragStartPage = state.currentPage
                            scrubbingValue = nextVal
                            onScrubPageSilent(nextVal.roundToInt())
                        },
                        onValueChangeFinished = {
                            val changed = dragStartPage != -1 && state.currentPage != dragStartPage
                            scrubbingValue = null
                            dragStartPage = -1
                            if (changed) onScrubFinishedPlaySound()
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

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bg.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clickable(onClick = onOpenJumpDialog)
                        .testTag("reader_jump_page_button")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

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
internal fun ReaderSettingsBottomSheet(
    prefs: UserPreferences,
    isPaginatedFormat: Boolean,
    isPdfFormat: Boolean,
    onDismiss: () -> Unit,
    onApplyProfile: (ReadingProfilePreset) -> Unit,
    onSelectTheme: (ReaderThemeMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onMarginChange: (Int) -> Unit,
    onFontFamilyChange: (ReaderFontFamilyOption) -> Unit,
    onSelectPdfFitMode: (PdfFitMode) -> Unit,
    onSelectPdfScrollMode: (PdfScrollMode) -> Unit,
    onSelectReflowMode: (ReflowablePaginationMode) -> Unit,
    onSelectTapZoneMode: (TapZoneMode) -> Unit,
    onReducedAnimationChange: (Boolean) -> Unit,
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

            if (isPdfFormat) {
                Text(stringResource(R.string.reader_pdf_scroll_mode), style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.readerPdfScrollMode == PdfScrollMode.SINGLE_PAGE,
                        onClick = { onSelectPdfScrollMode(PdfScrollMode.SINGLE_PAGE) },
                        label = { Text(stringResource(R.string.reader_pdf_single_page)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = prefs.readerPdfScrollMode == PdfScrollMode.CONTINUOUS_VERTICAL,
                        onClick = { onSelectPdfScrollMode(PdfScrollMode.CONTINUOUS_VERTICAL) },
                        label = { Text(stringResource(R.string.reader_pdf_continuous)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isPaginatedFormat) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedButton(onClick = onResetZoom, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ZoomOutMap, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reader_zoom_reset))
                }
            } else {
                Text(stringResource(R.string.reader_profiles_title), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ReadingProfilePreset.COMFORTABLE to R.string.reader_profile_comfortable,
                        ReadingProfilePreset.NIGHT to R.string.reader_profile_night,
                        ReadingProfilePreset.STUDY to R.string.reader_profile_study
                    ).forEach { (preset, labelRes) ->
                        OutlinedButton(
                            onClick = { onApplyProfile(preset) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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

                Text(stringResource(R.string.reader_reflow_mode), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = prefs.readerReflowableMode == ReflowablePaginationMode.PAGINATED,
                        onClick = { onSelectReflowMode(ReflowablePaginationMode.PAGINATED) },
                        label = { Text(stringResource(R.string.reader_reflow_paginated)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = prefs.readerReflowableMode == ReflowablePaginationMode.CONTINUOUS_SCROLL,
                        onClick = { onSelectReflowMode(ReflowablePaginationMode.CONTINUOUS_SCROLL) },
                        label = { Text(stringResource(R.string.reader_reflow_continuous)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Column {
                    Text("${stringResource(R.string.reader_font_size)}: ${prefs.readerFontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.labelLarge)
                    Slider(value = prefs.readerFontSizeSp, onValueChange = onFontSizeChange, valueRange = 12f..30f)
                }

                Column {
                    Text("${stringResource(R.string.reader_line_spacing)}: ${String.format("%.1f", prefs.readerLineSpacing)}x", style = MaterialTheme.typography.labelLarge)
                    Slider(value = prefs.readerLineSpacing, onValueChange = onLineSpacingChange, valueRange = 1.1f..2.1f)
                }

                Column {
                    Text("${stringResource(R.string.reader_margins)}: ${prefs.readerMarginDp} dp", style = MaterialTheme.typography.labelLarge)
                    Slider(value = prefs.readerMarginDp.toFloat(), onValueChange = { onMarginChange(it.roundToInt()) }, valueRange = 8f..44f)
                }

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFontFamilyOption.entries.forEach { option ->
                        FilterChip(
                            selected = prefs.readerFontFamily == option,
                            onClick = { onFontFamilyChange(option) },
                            label = { Text(option.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1) }
                        )
                    }
                }
            }

            Text(stringResource(R.string.reader_tap_zones), style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TapZoneMode.STANDARD to R.string.reader_tap_standard,
                    TapZoneMode.LEFT_HANDED to R.string.reader_tap_left_handed,
                    TapZoneMode.SIMPLE_CENTER to R.string.reader_tap_center_only,
                    TapZoneMode.OFF to R.string.reader_tap_off
                ).forEach { (mode, resId) ->
                    FilterChip(
                        selected = prefs.readerTapZoneMode == mode,
                        onClick = { onSelectTapZoneMode(mode) },
                        label = { Text(stringResource(resId)) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_reduced_animation), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.readerReducedAnimation, onCheckedChange = onReducedAnimationChange)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_page_turn_sound), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.pageTurnSoundEnabled, onCheckedChange = onPageTurnSoundChange)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.readerKeepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_progress), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.readerShowProgress, onCheckedChange = onShowProgressChange)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun JumpToPageDialog(
    maxCount: Int,
    currentOneBased: Int,
    onDismiss: () -> Unit,
    onConfirmZeroBased: (Int) -> Unit
) {
    var raw by remember { mutableStateOf(currentOneBased.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_goto_page_title)) },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { next -> raw = next.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.reader_goto_page_hint, maxCount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            val target = raw.toIntOrNull()
            Button(
                onClick = {
                    if (target != null) {
                        onConfirmZeroBased((target - 1).coerceIn(0, (maxCount - 1).coerceAtLeast(0)))
                        onDismiss()
                    }
                },
                enabled = target != null && target in 1..maxCount
            ) {
                Text(stringResource(R.string.reader_goto_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OcrBottomSheet(
    currentPageOneBased: Int,
    isRunningOcr: Boolean,
    ocrProgressPage: Int,
    recognizedText: String,
    isPdf: Boolean,
    onDismiss: () -> Unit,
    onScanCurrentPage: () -> Unit,
    onScanWholeBookLimited: () -> Unit,
    onCancelScan: () -> Unit,
    onReadAloudText: () -> Unit,
    onCopiedMessage: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reader_ocr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.reader_ocr_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (isRunningOcr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.reader_ocr_scanning, ocrProgressPage.coerceAtLeast(currentPageOneBased)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelScan) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onScanCurrentPage,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reader_ocr_scan_page_button")
                    ) {
                        Text(stringResource(R.string.reader_ocr_scan_page), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isPdf) {
                        OutlinedButton(
                            onClick = onScanWholeBookLimited,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.reader_ocr_scan_book), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (recognizedText.isBlank()) {
                Text(
                    text = stringResource(R.string.reader_ocr_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(recognizedText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("OCR Text", recognizedText))
                            onCopiedMessage()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = onReadAloudText,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.reader_read_aloud))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun HighlightCreationDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val palette = listOf("#FFB703", "#80ED99", "#90E0EF", "#FFADAD", "#CDB4DB")
    var excerpt by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_save_highlight)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = excerpt,
                    onValueChange = { excerpt = it },
                    label = { Text(stringResource(R.string.bookmarks_note_content_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.reader_highlight_color), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    palette.forEach { hex ->
                        val parsedColor = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = selectedHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(excerpt, selectedHex) },
                enabled = excerpt.isNotBlank()
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

@Composable
internal fun SaveExternalBookDialog(
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
