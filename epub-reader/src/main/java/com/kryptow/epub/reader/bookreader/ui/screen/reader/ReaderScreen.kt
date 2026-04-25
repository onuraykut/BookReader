package com.kryptow.epub.reader.bookreader.ui.screen.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kryptow.epub.reader.bookreader.domain.model.ScrollMode
import com.kryptow.epub.reader.bookreader.ui.reader.BookPaginationIndexer
import com.kryptow.epub.reader.bookreader.ui.reader.DictionarySheet
import com.kryptow.epub.reader.bookreader.ui.reader.EpubContentWebView
import com.kryptow.epub.reader.bookreader.ui.reader.GoToChapterDialog
import com.kryptow.epub.reader.bookreader.ui.reader.GoToPageDialog
import com.kryptow.epub.reader.bookreader.ui.reader.HighlightColorPicker
import com.kryptow.epub.reader.bookreader.ui.reader.HighlightNoteDialog
import com.kryptow.epub.reader.bookreader.ui.reader.PaginatedEpubWebView
import com.kryptow.epub.reader.bookreader.ui.reader.SearchSheet
import com.kryptow.epub.reader.bookreader.ui.reader.TocSheet
import com.kryptow.epub.reader.bookreader.ui.reader.TtsControlBar
import com.kryptow.epub.reader.bookreader.ui.reader.resolveColors
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotesClick: () -> Unit = {},
    viewModel: ReaderViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val dictionaryState by viewModel.dictionary.collectAsState()
    val ttsState by viewModel.tts.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val readingSeconds by viewModel.readingSeconds.collectAsState()
    val pagination by viewModel.pagination.collectAsState()
    val search by viewModel.search.collectAsState()

    val colors = remember(preferences) { preferences.resolveColors() }
    val scope = rememberCoroutineScope()

    // Sistem barlarını gizle (tam ekran)
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.let { window ->
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { window ->
                window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            }
        }
    }

    // ─── Overlay görünürlüğü (orta tıkla aç/kapat) ───────────────────────────
    var showBars by remember { mutableStateOf(true) }

    // ─── Dialog / sheet durumları ─────────────────────────────────────────────
    val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val dictionarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showToc by remember { mutableStateOf(false) }
    var showGoToDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    // Seçili metin (renk seçici için)
    var selectedText by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }
    // "Not Ekle" action için bekleme durumu
    var pendingNoteText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // Ekran açık tut
    DisposableEffect(preferences.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (preferences.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Okuma süresi — lifecycle bazlı
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startReadingTimer()
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopReadingTimer()
                    // Arka plana gidince mevcut konumu kaydet
                    viewModel.flushProgress()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Font / boyut / satır aralığı / margin değişince pagination'ı sıfırla
    LaunchedEffect(
        preferences.fontFamily,
        preferences.fontSize,
        preferences.lineHeight,
        preferences.marginHorizontal,
        preferences.themeMode,
        preferences.scrollMode,
    ) {
        viewModel.invalidatePagination()
    }

    val currentChapterIndex = uiState.currentChapterIndex
    val isBookmarked = bookmarks.any { it.chapterIndex == currentChapterIndex }
    // Mevcut bölüme ait vurgular
    val chapterHighlights = highlights.filter { it.page == currentChapterIndex }
    val isPaged = preferences.scrollMode == ScrollMode.HORIZONTAL_PAGE

    // ─── Sayfa sayacı hesaplama ───────────────────────────────────────────────
    val book = uiState.book
    val known = pagination.chapterPageCounts
    val totalPages: Int? = if (isPaged && book != null && pagination.isIndexComplete) {
        known.values.sum()
    } else null
    val currentPageAbs: Int? = if (isPaged && book != null) {
        val avg = if (known.isNotEmpty())
            (known.values.sum().toDouble() / known.size).toInt().coerceAtLeast(1) else 1
        val before = (0 until currentChapterIndex).sumOf { known[it] ?: avg }
        before + pagination.currentPageInChapter + 1
    } else null

    val pageLabel: String? = when {
        !isPaged -> null
        pagination.isIndexing && !pagination.isIndexComplete -> {
            val pct = (pagination.indexProgress * 100f).toInt()
            "İndeksleniyor… %$pct"
        }
        currentPageAbs != null && totalPages != null -> "Sayfa $currentPageAbs / $totalPages"
        else -> null
    }

    val chapterLabel = when {
        pageLabel != null -> pageLabel
        uiState.totalChapters > 0 -> "Bölüm ${currentChapterIndex + 1} / ${uiState.totalChapters}"
        else -> ""
    }

    val onMiddleTap = { showBars = !showBars }

    // ─── Tam ekran içerik + overlay çerçevesi ────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ── İçerik (tam ekran) ───────────────────────────────────────────────
        when {
            uiState.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = colors.accent,
            )

            uiState.error != null -> Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )

            uiState.book != null -> {
                when (preferences.scrollMode) {
                    ScrollMode.VERTICAL -> {
                        uiState.currentChapter?.let { chapter ->
                            EpubContentWebView(
                                htmlContent = chapter.content,
                                preferences = preferences,
                                colors = colors,
                                modifier = Modifier.fillMaxSize(),
                                initialScrollOffset = uiState.initialScrollOffset,
                                searchQuery = if (showSearch) search.query else "",
                                searchDirection = search.searchDirection,
                                highlights = chapterHighlights,
                                ttsHighlightSentence = if (ttsState.isVisible) ttsState.currentSentence else "",
                                onScroll = { scrollY -> viewModel.onVerticalScroll(scrollY) },
                                onWordTapped = { word -> viewModel.onWordTapped(word) },
                                onTextSelected = { text ->
                                    selectedText = text
                                    showColorPicker = true
                                },
                                onAddNote = { text ->
                                    pendingNoteText = text
                                },
                                onMiddleTap = onMiddleTap,
                                onScrollOffsetConsumed = { viewModel.consumeInitialScrollOffset() },
                                onSearchResultCount = { total, current ->
                                    viewModel.onSearchMatchResult(total, current)
                                },
                            )
                        }
                    }

                    ScrollMode.HORIZONTAL_PAGE -> {
                        val bookVal = uiState.book!!
                        uiState.currentChapter?.let { chapter ->
                            androidx.compose.runtime.key(
                                currentChapterIndex,
                                pagination.paginationEpoch,
                            ) {
                                PaginatedEpubWebView(
                                    htmlContent = chapter.content,
                                    preferences = preferences,
                                    colors = colors,
                                    targetPageInChapter = pagination.targetPageInChapter,
                                    modifier = Modifier.fillMaxSize(),
                                    searchQuery = if (showSearch) search.query else "",
                                    searchDirection = search.searchDirection,
                                    highlights = chapterHighlights,
                                    ttsHighlightSentence = if (ttsState.isVisible) ttsState.currentSentence else "",
                                    onWordTapped = { word -> viewModel.onWordTapped(word) },
                                    onPagesCalculated = { total ->
                                        viewModel.onChapterPagesCalculated(currentChapterIndex, total)
                                    },
                                    onPageChanged = { page ->
                                        viewModel.onPageInChapterChanged(page)
                                    },
                                    onSwipeBeyondStart = { viewModel.onSwipeBeyondStart() },
                                    onSwipeBeyondEnd = { viewModel.onSwipeBeyondEnd() },
                                    onTextSelected = { text ->
                                        selectedText = text
                                        showColorPicker = true
                                    },
                                    onAddNote = { text ->
                                        pendingNoteText = text
                                    },
                                    onMiddleTap = onMiddleTap,
                                    onSearchResultCount = { total, current ->
                                        viewModel.onSearchMatchResult(total, current)
                                    },
                                )
                            }
                        }

                        // ─── Arka plan pagination indexer ──────────────────
                        if (!pagination.isIndexComplete) {
                            androidx.compose.runtime.key(pagination.paginationEpoch) {
                                LaunchedEffect(pagination.paginationEpoch) {
                                    viewModel.onIndexingStarted(bookVal.chapters.size)
                                }
                                BookPaginationIndexer(
                                    chapters = bookVal.chapters,
                                    preferences = preferences,
                                    colors = colors,
                                    paginationEpoch = pagination.paginationEpoch,
                                    alreadyKnown = pagination.chapterPageCounts.keys,
                                    onChapterIndexed = { idx, count ->
                                        viewModel.onChapterPagesCalculated(idx, count)
                                    },
                                    onCompleted = { viewModel.onIndexingCompleted() },
                                )
                            }
                        }
                    }
                }

                // ─── Metin seçimi renk seçici ─────────────────────────────
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    HighlightColorPicker(
                        visible = showColorPicker,
                        selectedText = selectedText,
                        onColorSelected = { color ->
                            viewModel.addHighlight(selectedText, color)
                            showColorPicker = false
                            selectedText = ""
                        },
                        onDismiss = {
                            showColorPicker = false
                            selectedText = ""
                        },
                    )
                }
            }
        }

        // ── Üst bar overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = uiState.currentChapter?.title ?: uiState.book?.title ?: "",
                backgroundColor = colors.background.copy(alpha = 0.95f),
                textColor = colors.text,
                accentColor = colors.accent,
                isBookmarked = isBookmarked,
                isTtsActive = ttsState.isVisible,
                onBack = {
                    viewModel.flushProgress()
                    onBack()
                },
                onSearch = { showSearch = true },
                onToc = { showToc = true },
                onNotes = onNotesClick,
                onBookmark = {
                    if (isBookmarked) {
                        bookmarks.firstOrNull { it.chapterIndex == currentChapterIndex }
                            ?.let { viewModel.deleteBookmark(it) }
                    } else {
                        viewModel.addBookmark(0)
                    }
                },
                onTts = { viewModel.toggleTts() },
                onSettings = onSettingsClick,
            )
        }

        // ── TTS kontrol çubuğu (alt barın hemen üstünde) ─────────────────────
        AnimatedVisibility(
            visible = ttsState.isVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TtsControlBar(
                state = ttsState,
                backgroundColor = colors.background.copy(alpha = 0.97f),
                textColor = colors.text,
                accentColor = colors.accent,
                onPlay = { viewModel.toggleTts() },
                onPause = { viewModel.pauseTts() },
                onStop = { viewModel.stopTts() },
                onNext = { viewModel.nextTtsSentence() },
                onPrev = { viewModel.prevTtsSentence() },
                onSpeedChange = { speed -> viewModel.setTtsSpeed(speed) },
                onLangChange = { lang -> viewModel.setTtsLanguage(lang) },
            )
        }

        // ── Alt bar overlay — TTS aktifken gizlenir, TTS kapalıyken gösterilir ─
        AnimatedVisibility(
            visible = showBars && !ttsState.isVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                chapterLabel = chapterLabel,
                chapterTitle = uiState.currentChapter?.title ?: "",
                progressFraction = uiState.progressFraction,
                readingSeconds = readingSeconds,
                backgroundColor = colors.background.copy(alpha = 0.95f),
                textColor = colors.text,
                accentColor = colors.accent,
                canGoPrev = currentChapterIndex > 0,
                canGoNext = currentChapterIndex < uiState.totalChapters - 1,
                onPrev = { viewModel.previousChapter() },
                onNext = { viewModel.nextChapter() },
                onCenterClick = { showGoToDialog = true },
            )
        }

    }

    // ─── TOC bottom sheet ─────────────────────────────────────────────────────
    if (showToc) {
        val chapters = uiState.book?.chapters.orEmpty()
        TocSheet(
            chapters = chapters,
            currentIndex = currentChapterIndex,
            colors = colors,
            sheetState = tocSheetState,
            onChapterSelected = { index ->
                scope.launch { tocSheetState.hide() }.invokeOnCompletion { showToc = false }
                viewModel.goToChapter(index)
            },
            onDismiss = {
                scope.launch { tocSheetState.hide() }.invokeOnCompletion { showToc = false }
            },
        )
    }

    // ─── Arama bottom sheet ───────────────────────────────────────────────────
    if (showSearch) {
        SearchSheet(
            query = search.query,
            results = search.results,
            currentChapterMatchCount = search.currentChapterMatchCount,
            currentChapterMatchIndex = search.currentChapterMatchIndex,
            sheetState = searchSheetState,
            onQueryChange = { viewModel.setSearchQuery(it) },
            onSearch = { viewModel.searchInBook() },
            onNextMatch = { viewModel.searchNext() },
            onPrevMatch = { viewModel.searchPrev() },
            onResultClick = { result ->
                scope.launch { searchSheetState.hide() }.invokeOnCompletion { showSearch = false }
                viewModel.goToChapter(result.chapterIndex)
            },
            onDismiss = {
                scope.launch { searchSheetState.hide() }.invokeOnCompletion {
                    showSearch = false
                    viewModel.clearSearch()
                }
            },
        )
    }

    // ─── Sözlük / çeviri sheet ────────────────────────────────────────────────
    if (dictionaryState.isVisible) {
        DictionarySheet(
            state = dictionaryState,
            sheetState = dictionarySheetState,
            onLangSelected = { lang -> viewModel.setDictionaryTargetLang(lang) },
            onDismiss = { viewModel.dismissDictionary() },
        )
    }

    // ─── Not + renk seçimi dialogu (action mode "Not Ekle") ──────────────────
    pendingNoteText?.let { noteText ->
        HighlightNoteDialog(
            selectedText = noteText,
            onConfirm = { color, note ->
                viewModel.addHighlight(noteText, color, note)
                pendingNoteText = null
            },
            onDismiss = { pendingNoteText = null },
        )
    }

    // ─── Sayfaya / Bölüme git dialogu ────────────────────────────────────────
    if (showGoToDialog && uiState.totalChapters > 0) {
        if (isPaged && totalPages != null && totalPages > 0) {
            GoToPageDialog(
                currentPage = currentPageAbs ?: 1,
                totalPages = totalPages,
                onConfirm = { absPage ->
                    showGoToDialog = false
                    viewModel.goToAbsolutePage(absPage)
                },
                onDismiss = { showGoToDialog = false },
            )
        } else {
            GoToChapterDialog(
                currentChapter = currentChapterIndex + 1,
                totalChapters = uiState.totalChapters,
                onConfirm = { index ->
                    showGoToDialog = false
                    viewModel.goToChapter(index)
                },
                onDismiss = { showGoToDialog = false },
            )
        }
    }
}

// ─── Üst bar ──────────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(
    title: String,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    isBookmarked: Boolean,
    isTtsActive: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onToc: () -> Unit,
    onNotes: () -> Unit,
    onBookmark: () -> Unit,
    onSettings: () -> Unit,
    onTts: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = textColor)
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, "Ara", tint = textColor)
            }
            IconButton(onClick = onToc) {
                Icon(Icons.Default.List, "İçindekiler", tint = textColor)
            }
            IconButton(onClick = onNotes) {
                Icon(Icons.Default.Notes, "Notlar", tint = textColor)
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    "Yer imi",
                    tint = textColor,
                )
            }
            IconButton(onClick = onTts) {
                Icon(
                    Icons.Default.VolumeUp,
                    "Sesle Oku",
                    tint = if (isTtsActive) accentColor else textColor,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Ayarlar", tint = textColor)
            }
        }
    }
}

// ─── Alt bar — Lithium stili ───────────────────────────────────────────────────

@Composable
private fun ReaderBottomBar(
    chapterLabel: String,      // "Sayfa 42 / 328" veya "Bölüm 7 / 14"
    chapterTitle: String,      // "V. Advice from a Caterpillar"
    progressFraction: Float,
    readingSeconds: Long,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCenterClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ← Önceki
                IconButton(
                    onClick = onPrev,
                    enabled = canGoPrev,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Önceki",
                        modifier = Modifier.size(28.dp),
                        tint = if (canGoPrev) textColor else textColor.copy(alpha = 0.25f),
                    )
                }

                // Orta — sayfa/bölüm bilgisi
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = true,
                            role = Role.Button,
                            onClick = onCenterClick,
                        )
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    // Sayfa / bölüm sayacı
                    Text(
                        text = chapterLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = textColor.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Bölüm adı
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Okuma süresi
                    Text(
                        text = formatReadingTime(readingSeconds),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.45f),
                        maxLines = 1,
                    )
                }

                // → Sonraki
                IconButton(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Sonraki",
                        modifier = Modifier.size(28.dp),
                        tint = if (canGoNext) textColor else textColor.copy(alpha = 0.25f),
                    )
                }
            }

            // İnce progress çizgisi — en altta
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.15f),
            )
        }
    }
}

private fun formatReadingTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
