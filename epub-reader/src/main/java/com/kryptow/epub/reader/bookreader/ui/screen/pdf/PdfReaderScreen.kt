package com.kryptow.epub.reader.bookreader.ui.screen.pdf

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.kryptow.epub.reader.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel

private val ReaderBackground = Color(0xFF2A2A2A)
private val PageColor = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uri: Uri,
    fileName: String,
    onBack: () -> Unit,
    viewModel: PdfReaderViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val search by viewModel.search.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBars by remember { mutableStateOf(true) }
    var showSearchSheet by remember { mutableStateOf(false) }
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uri) {
        viewModel.openPdf(uri, fileName)
    }

    // Sistem barlarını gizle
    DisposableEffect(Unit) {
        (context as? Activity)?.window?.let { window ->
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
            window.insetsController?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            (context as? Activity)?.window?.let { window ->
                window.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderBackground),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            state.error != null -> {
                Text(
                    text = state.error!!,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }

            state.totalPages > 0 -> {
                val pagerState = rememberPagerState(
                    initialPage = state.currentPage,
                    pageCount = { state.totalPages },
                )

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        viewModel.goToPage(page)
                    }
                }

                LaunchedEffect(state.currentPage) {
                    if (pagerState.currentPage != state.currentPage) {
                        pagerState.animateScrollToPage(state.currentPage)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    PdfPageContent(
                        pageIndex = pageIndex,
                        viewModel = viewModel,
                        onToggleBars = { showBars = !showBars },
                    )
                }
            }
        }

        // ─── Üst Bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = state.fileName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${state.currentPage + 1} / ${state.totalPages}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(onClick = { showSearchSheet = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_in_book),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // ─── Arama bottom sheet ──────────────────────────────────────────────
        if (showSearchSheet) {
            PdfSearchSheet(
                query = search.query,
                results = search.results,
                isSearching = search.isSearching,
                sheetState = searchSheetState,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onResultClick = { result ->
                    viewModel.goToPage(result.pageIndex)
                    scope.launch {
                        searchSheetState.hide()
                        showSearchSheet = false
                    }
                },
                onDismiss = {
                    scope.launch {
                        searchSheetState.hide()
                        showSearchSheet = false
                    }
                },
            )
        }

        // ─── Alt Bar (sayfa kaydırıcı) ────────────────────────────────────────
        AnimatedVisibility(
            visible = showBars && state.totalPages > 1,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "1",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                    Slider(
                        value = state.currentPage.toFloat(),
                        onValueChange = { viewModel.goToPage(it.toInt()) },
                        valueRange = 0f..(state.totalPages - 1).toFloat(),
                        steps = (state.totalPages - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "${state.totalPages}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Tek bir PDF sayfasını gösterir.
 *
 * - Sayfa kendi en-boy oranında render edilir (zorla fit yapılmaz).
 * - Beyaz sayfa, hafif gölge — gerçek belge hissi.
 * - Tek parmak yatay drag → HorizontalPager devralır (sayfa değiştir).
 * - 2 parmak pinch → zoom (1x–5x).
 * - Zoomken tek parmak drag → pan.
 * - Çift tıkla zoom in/out.
 * - Tek tıkla bar aç/kapat (zoomlu değilken).
 */
@Composable
private fun PdfPageContent(
    pageIndex: Int,
    viewModel: PdfReaderViewModel,
    onToggleBars: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Sayfa değişince zoom sıfırla
    LaunchedEffect(pageIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderBackground)
            // Pinch-zoom + pan (sadece zoom durumlarında consume eder,
            // değilse parent HorizontalPager swipe alır)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pointerCount >= 2) {
                            // 2+ parmak: zoom (pan ile birlikte)
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f && panChange != Offset.Zero) {
                            // Tek parmak ama zoomlu: pan
                            offsetX += panChange.x
                            offsetY += panChange.y
                            event.changes.forEach { it.consume() }
                        }
                        // else: consume etme — HorizontalPager swipe'ı alsın
                    } while (event.changes.any { it.pressed })
                }
            }
            // Tap / double-tap (zoom yokken bar toggle, varsa zoom out)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (scale <= 1f) onToggleBars()
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PdfPageImage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
    }
}

/**
 * PDF sayfasını kendi en-boy oranıyla render eder.
 * Beyaz sayfa arka planı + gölge ile gerçek belge görünümü.
 */
@Composable
private fun PdfPageImage(
    pageIndex: Int,
    viewModel: PdfReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = null
        val displayMetrics = context.resources.displayMetrics
        // Yüksek çözünürlük için ekran genişliğinin 1.5 katı render et
        val renderWidth = (displayMetrics.widthPixels * 1.5f).toInt()
        value = runCatching {
            viewModel.renderPage(pageIndex, renderWidth, 0)
        }.getOrNull()
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
        }
        return
    }

    // Sayfa en-boy oranı
    val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = stringResource(R.string.pdf_page_format, pageIndex + 1),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .shadow(elevation = 12.dp, clip = false)
                .background(PageColor),
        )
    }
}
