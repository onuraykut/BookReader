package com.kryptow.epub.reader.bookreader.ui.reader

import android.annotation.SuppressLint
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences

// Sistem action mode menü öğe ID'leri
private const val MENU_HIGHLIGHT = 9001
private const val MENU_NOTE = 9002
private const val MENU_DEFINITION = 9003

/**
 * Sayfa bazlı (CSS multi-column) EPUB WebView.
 *
 * - Metin seçimi Android ActionMode'u üzerinden: "Vurgula" ve "Not Ekle" öğeleri eklenir.
 * - [highlights] değişince sayfa üzerindeki vurgular otomatik güncellenir.
 * - Swipe sırasında metin seçiliyse sayfa çevirme engellenir.
 *
 * @param targetPageInChapter 0 = ilk sayfa, [Int.MAX_VALUE] = son sayfa.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaginatedEpubWebView(
    htmlContent: String,
    preferences: ReadingPreferences,
    colors: ReadingColors,
    targetPageInChapter: Int,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    searchDirection: Int = 0,
    highlights: List<Highlight> = emptyList(),
    ttsHighlightSentence: String = "",
    onPagesCalculated: (totalPages: Int) -> Unit = {},
    onPageChanged: (pageIndex: Int) -> Unit = {},
    onSwipeBeyondStart: () -> Unit = {},
    onSwipeBeyondEnd: () -> Unit = {},
    onWordTapped: (word: String) -> Unit = {},
    onTextSelected: (text: String) -> Unit = {},
    onAddNote: (text: String) -> Unit = {},
    onMiddleTap: () -> Unit = {},
    onSearchResultCount: (total: Int, current: Int) -> Unit = { _, _ -> },
    onWebViewReady: (WebView) -> Unit = {},
) {
    val pagesCalculated = rememberUpdatedState(onPagesCalculated)
    val pageChanged = rememberUpdatedState(onPageChanged)
    val swipeBeyondStart = rememberUpdatedState(onSwipeBeyondStart)
    val swipeBeyondEnd = rememberUpdatedState(onSwipeBeyondEnd)
    val wordTapped = rememberUpdatedState(onWordTapped)
    val textSelected = rememberUpdatedState(onTextSelected)
    val addNote = rememberUpdatedState(onAddNote)
    val middleTap = rememberUpdatedState(onMiddleTap)
    val searchResultCount = rememberUpdatedState(onSearchResultCount)
    val highlightsState = rememberUpdatedState(highlights)

    val bridge = remember {
        PaginationBridge(
            onPagesCalculated = { pagesCalculated.value(it) },
            onPageChanged = { pageChanged.value(it) },
            onSwipeBeyondStart = { swipeBeyondStart.value() },
            onSwipeBeyondEnd = { swipeBeyondEnd.value() },
            onMiddleTap = { middleTap.value() },
        )
    }

    val webViewRef = remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

    LaunchedEffect(searchQuery) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (searchQuery.isNotBlank()) {
            wv.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) searchResultCount.value(numberOfMatches, activeMatchOrdinal + 1)
            }
            wv.findAllAsync(searchQuery)
        } else {
            wv.clearMatches()
            searchResultCount.value(0, 0)
        }
    }

    LaunchedEffect(searchDirection) {
        if (searchDirection != 0 && searchQuery.isNotBlank()) {
            webViewRef.value?.findNext(searchDirection > 0)
        }
    }

    // Vurgular değişince sayfaya yeniden uygula
    LaunchedEffect(highlights) {
        if (highlights.isEmpty()) return@LaunchedEffect
        val wv = webViewRef.value ?: return@LaunchedEffect
        val json = buildHighlightsJson(highlights)
        wv.evaluateJavascript(
            "requestAnimationFrame(function(){" +
                "if(window.__applyHighlights__)__applyHighlights__($json);" +
                "});",
            null,
        )
    }

    // TTS: okunan cümleyi işaretle
    LaunchedEffect(ttsHighlightSentence) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        val escaped = "\"" + ttsHighlightSentence
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
        wv.evaluateJavascript(
            "if(window.__ttsMark__)__ttsMark__($escaped);",
            null,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            object : WebView(ctx) {
                override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
                    return super.startActionMode(
                        object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                                val result = callback?.onCreateActionMode(mode, menu) ?: true
                                menu?.add(Menu.NONE, MENU_DEFINITION, Menu.NONE, "Tanım Göster")
                                    ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                                menu?.add(Menu.NONE, MENU_HIGHLIGHT, Menu.NONE, "Vurgula")
                                    ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                                menu?.add(Menu.NONE, MENU_NOTE, Menu.NONE, "Not Ekle")
                                    ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                                return result
                            }

                            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean =
                                callback?.onPrepareActionMode(mode, menu) ?: false

                            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                                when (item?.itemId) {
                                    MENU_DEFINITION -> {
                                        evaluateJavascript("window.getSelection().toString()") { raw ->
                                            val text = raw?.removeSurrounding("\"")
                                                ?.replace("\\n", " ")?.trim() ?: ""
                                            if (text.isNotBlank()) wordTapped.value(text)
                                            mode?.finish()
                                        }
                                        return true
                                    }
                                    MENU_HIGHLIGHT -> {
                                        evaluateJavascript("window.getSelection().toString()") { raw ->
                                            val text = raw?.removeSurrounding("\"")
                                                ?.replace("\\n", " ")?.trim() ?: ""
                                            if (text.isNotBlank()) textSelected.value(text)
                                            mode?.finish()
                                        }
                                        return true
                                    }
                                    MENU_NOTE -> {
                                        evaluateJavascript("window.getSelection().toString()") { raw ->
                                            val text = raw?.removeSurrounding("\"")
                                                ?.replace("\\n", " ")?.trim() ?: ""
                                            if (text.isNotBlank()) addNote.value(text)
                                            mode?.finish()
                                        }
                                        return true
                                    }
                                }
                                return callback?.onActionItemClicked(mode, item) ?: false
                            }

                            override fun onDestroyActionMode(mode: ActionMode?) {
                                callback?.onDestroyActionMode(mode)
                            }
                        },
                        type,
                    )
                }

                override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
                    startActionMode(callback, ActionMode.TYPE_FLOATING)
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    defaultTextEncodingName = "UTF-8"
                    cacheMode = WebSettings.LOAD_DEFAULT
                    loadWithOverviewMode = false
                    useWideViewPort = false
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(bridge, "AndroidPagination")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "__initPagination__(${targetPageInChapterSafe(targetPageInChapter)});",
                            null,
                        )
                        // Vurguları DOM tam render olduktan sonra uygula
                        val json = buildHighlightsJson(highlightsState.value)
                        view?.evaluateJavascript(
                            "requestAnimationFrame(function(){" +
                                "requestAnimationFrame(function(){" +
                                "if(window.__applyHighlights__)__applyHighlights__($json);" +
                                "});" +
                                "});",
                            null,
                        )
                        if (searchQuery.isNotBlank()) {
                            view?.findAllAsync(searchQuery)
                        }
                    }
                }
            }.also { wv ->
                webViewRef.value = wv
                onWebViewReady(wv)
            }
        },
        update = { webView ->
            webView.setBackgroundColor(
                android.graphics.Color.parseColor(colors.toCss().background)
            )
            webView.loadDataWithBaseURL(
                null,
                buildPaginatedHtml(htmlContent, preferences, colors),
                "text/html",
                "UTF-8",
                null,
            )
        },
    )
}

private fun targetPageInChapterSafe(target: Int): String =
    if (target == Int.MAX_VALUE) "-1" else target.toString()

private class PaginationBridge(
    private val onPagesCalculated: (Int) -> Unit,
    private val onPageChanged: (Int) -> Unit,
    private val onSwipeBeyondStart: () -> Unit,
    private val onSwipeBeyondEnd: () -> Unit,
    private val onMiddleTap: () -> Unit,
) {
    @JavascriptInterface fun onPagesCalculated(total: Int) = onPagesCalculated.invoke(total)
    @JavascriptInterface fun onPageChanged(page: Int) = onPageChanged.invoke(page)
    @JavascriptInterface fun onSwipeBeyondStart() = onSwipeBeyondStart.invoke()
    @JavascriptInterface fun onSwipeBeyondEnd() = onSwipeBeyondEnd.invoke()
    @JavascriptInterface fun onMiddleTap() = onMiddleTap.invoke()
}

private fun buildPaginatedHtml(
    content: String,
    prefs: ReadingPreferences,
    colors: ReadingColors,
): String {
    val css = colors.toCss()
    val fontFamily = prefs.fontFamily.toCssFontFamily()
    val marginH = prefs.marginHorizontal
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
  html, body {
    margin: 0; padding: 0;
    background-color: ${css.background};
    color: ${css.text};
    overflow: hidden;
    width: 100vw; height: 100vh;
  }
  body {
    font-family: $fontFamily;
    font-size: ${prefs.fontSize}pt;
    line-height: ${prefs.lineHeight};
    word-wrap: break-word;
    -webkit-hyphens: auto; hyphens: auto;
    text-align: justify;
  }
  #viewport { width: 100vw; height: 100vh; overflow: hidden; position: relative; }
  #content {
    box-sizing: border-box;
    padding: 16px ${marginH}px;
    height: 100vh;
    column-width: calc(100vw - ${marginH * 2}px);
    column-gap: ${marginH * 2}px;
    column-fill: auto;
    -webkit-column-width: calc(100vw - ${marginH * 2}px);
    -webkit-column-gap: ${marginH * 2}px;
    -webkit-column-fill: auto;
    transition: transform 0.22s ease-out;
    will-change: transform;
  }
  p { margin: 0 0 0.9em 0; }
  h1, h2, h3, h4 { color: ${css.text}; line-height: 1.3; margin-top: 1.2em; break-inside: avoid; }
  a { color: ${css.accent}; text-decoration: none; }
  img { max-width: 100%; max-height: 90vh; height: auto; display: block; margin: 1em auto; break-inside: avoid; }
  blockquote {
    border-left: 3px solid ${css.accent}; margin: 1em 0;
    padding: 0.3em 1em; color: ${css.textSecondary}; break-inside: avoid;
  }
  pre, code {
    font-family: 'Courier New', monospace;
    background: ${css.textSecondary}22; padding: 2px 4px; border-radius: 3px;
  }
  hr { border: none; border-top: 1px solid ${css.textSecondary}55; margin: 2em 0; }
  ::selection { background: ${css.accent}55; }
  .epub-hl { border-radius: 2px; }
</style>
</head>
<body>
<div id="viewport">
  <div id="content">
$content
  </div>
</div>
<script>
${highlightScript()}
${ttsScript()}
(function() {
  var currentPage = 0, totalPages = 1, pageWidth = 0;
  var contentEl = document.getElementById('content');

  function recalc() {
    pageWidth = window.innerWidth;
    totalPages = Math.max(1, Math.round(contentEl.scrollWidth / pageWidth));
    if (window.AndroidPagination) AndroidPagination.onPagesCalculated(totalPages);
  }

  function applyPage(p) {
    if (p < 0) p = 0;
    if (p > totalPages - 1) p = totalPages - 1;
    currentPage = p;
    contentEl.style.transform = 'translateX(' + (-p * pageWidth) + 'px)';
    if (window.AndroidPagination) AndroidPagination.onPageChanged(currentPage);
  }

  window.__initPagination__ = function(target) {
    recalc();
    var p = (target < 0) ? (totalPages - 1) : target;
    contentEl.style.transition = 'none';
    applyPage(p);
    requestAnimationFrame(function() {
      requestAnimationFrame(function() { contentEl.style.transition = 'transform 0.22s ease-out'; });
    });
  };

  window.__goToPage__ = function(p) { applyPage(p); };
  window.__nextPage__ = function() { applyPage(currentPage + 1); };
  window.__prevPage__ = function() { applyPage(currentPage - 1); };

  var resizeTimer = null;
  window.addEventListener('resize', function() {
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function() {
      var oldPage = currentPage; recalc();
      contentEl.style.transition = 'none';
      applyPage(Math.min(oldPage, totalPages - 1));
      requestAnimationFrame(function() {
        requestAnimationFrame(function() { contentEl.style.transition = 'transform 0.22s ease-out'; });
      });
    }, 100);
  });

  var touchStartX = 0, touchStartY = 0, touchStartTime = 0;
  var SWIPE_THRESHOLD = 40, SWIPE_MAX_TIME = 600;

  document.addEventListener('touchstart', function(e) {
    var t = e.changedTouches[0];
    touchStartX = t.clientX; touchStartY = t.clientY; touchStartTime = Date.now();
  }, { passive: true });

  document.addEventListener('touchend', function(e) {
    var sel = window.getSelection();
    if (sel && sel.toString().trim().length > 2) return;
    var t = e.changedTouches[0];
    var dx = t.clientX - touchStartX, dy = t.clientY - touchStartY;
    var dt = Date.now() - touchStartTime;
    if (dt > SWIPE_MAX_TIME) return;
    if (Math.abs(dy) > Math.abs(dx)) return;
    if (Math.abs(dx) < SWIPE_THRESHOLD) {
      var x = t.clientX, w = window.innerWidth;
      if (x < w / 3) goBackward();
      else if (x > 2 * w / 3) goForward();
      else if (window.AndroidPagination) AndroidPagination.onMiddleTap();
      return;
    }
    if (dx < 0) goForward(); else goBackward();
  }, { passive: true });

  function goForward() {
    if (currentPage < totalPages - 1) applyPage(currentPage + 1);
    else if (window.AndroidPagination) AndroidPagination.onSwipeBeyondEnd();
  }
  function goBackward() {
    if (currentPage > 0) applyPage(currentPage - 1);
    else if (window.AndroidPagination) AndroidPagination.onSwipeBeyondStart();
  }
})();
</script>
</body>
</html>
    """.trimIndent()
}
