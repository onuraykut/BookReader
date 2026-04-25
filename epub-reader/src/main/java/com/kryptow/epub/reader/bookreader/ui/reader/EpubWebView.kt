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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences

// Sistem action mode menü öğe ID'leri
private const val MENU_HIGHLIGHT = 9001
private const val MENU_NOTE = 9002
private const val MENU_DEFINITION = 9003

/**
 * EPUB bölüm içeriğini dikey kaydırma modunda WebView'de gösterir.
 *
 * - Metin seçimi Android ActionMode'u üzerinden: "Vurgula" ve "Not Ekle" öğeleri eklenir.
 * - [highlights] listesi değişince (yeni vurgu eklendikten sonra) sayfa üzerindeki
 *   vurgular otomatik olarak yeniden uygulanır.
 *
 * @param initialScrollOffset Sayfa yüklendikten sonra gidilecek piksel Y konumu.
 * @param onScrollOffsetConsumed Scroll restore edildikten sonra çağrılır.
 * @param onTextSelected "Vurgula" seçilince tetiklenir — renk seçici gösterilir.
 * @param onAddNote "Not Ekle" seçilince tetiklenir — not dialogu gösterilir.
 * @param highlights Gösterilecek vurgular; bölüm değişince dışarıdan filtrelenir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubContentWebView(
    htmlContent: String,
    preferences: ReadingPreferences,
    colors: ReadingColors,
    modifier: Modifier = Modifier,
    initialScrollOffset: Int = 0,
    searchQuery: String = "",
    searchDirection: Int = 0,
    highlights: List<Highlight> = emptyList(),
    ttsHighlightSentence: String = "",
    onScroll: (scrollY: Int) -> Unit = {},
    onWordTapped: (word: String) -> Unit = {},
    onTextSelected: (text: String) -> Unit = {},
    onAddNote: (text: String) -> Unit = {},
    onMiddleTap: () -> Unit = {},
    onScrollOffsetConsumed: () -> Unit = {},
    onSearchResultCount: (total: Int, current: Int) -> Unit = { _, _ -> },
) {
    val scrollCb = rememberUpdatedState(onScroll)
    val wordTappedCb = rememberUpdatedState(onWordTapped)
    val textSelectedCb = rememberUpdatedState(onTextSelected)
    val addNoteCb = rememberUpdatedState(onAddNote)
    val middleTapCb = rememberUpdatedState(onMiddleTap)
    val searchResultCb = rememberUpdatedState(onSearchResultCount)
    val consumedCb = rememberUpdatedState(onScrollOffsetConsumed)
    val initialOffsetState = rememberUpdatedState(initialScrollOffset)
    val highlightsState = rememberUpdatedState(highlights)
    var scrollRestored by remember(htmlContent) { mutableStateOf(false) }

    // JS bridge: yalnızca orta-tıklama (kelime tanımı artık ActionMode'dan geliyor)
    val bridge = remember {
        object {
            @JavascriptInterface
            fun onMiddleTap() = middleTapCb.value()
        }
    }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Arama sorgusu değişince güncelle
    LaunchedEffect(searchQuery) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (searchQuery.isNotBlank()) {
            wv.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) searchResultCb.value(numberOfMatches, activeMatchOrdinal + 1)
            }
            wv.findAllAsync(searchQuery)
        } else {
            wv.clearMatches()
            searchResultCb.value(0, 0)
        }
    }

    LaunchedEffect(searchDirection) {
        if (searchDirection != 0 && searchQuery.isNotBlank()) {
            webViewRef.value?.findNext(searchDirection > 0)
        }
    }

    // Vurgular değişince (yeni eklendi / silindi) sayfaya yeniden uygula
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

    // TTS: okunan cümleyi işaretle ve görünüme kaydır
    LaunchedEffect(ttsHighlightSentence) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        val escaped = ttsHighlightSentence.toEpubJsString()
        wv.evaluateJavascript(
            "if(window.__ttsMark__)__ttsMark__($escaped);",
            null,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // ── ActionMode override: sistem menüsüne "Vurgula" + "Not Ekle" ekler ──
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
                                            if (text.isNotBlank()) wordTappedCb.value(text)
                                            mode?.finish()
                                        }
                                        return true
                                    }
                                    MENU_HIGHLIGHT -> {
                                        evaluateJavascript("window.getSelection().toString()") { raw ->
                                            val text = raw?.removeSurrounding("\"")
                                                ?.replace("\\n", " ")?.trim() ?: ""
                                            if (text.isNotBlank()) textSelectedCb.value(text)
                                            mode?.finish()
                                        }
                                        return true
                                    }
                                    MENU_NOTE -> {
                                        evaluateJavascript("window.getSelection().toString()") { raw ->
                                            val text = raw?.removeSurrounding("\"")
                                                ?.replace("\\n", " ")?.trim() ?: ""
                                            if (text.isNotBlank()) addNoteCb.value(text)
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
                    loadWithOverviewMode = true
                    useWideViewPort = false
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(bridge, "AndroidReader")
                setOnScrollChangeListener { _, _, scrollY, _, _ -> scrollCb.value(scrollY) }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Scroll restore
                        val offset = initialOffsetState.value
                        if (offset > 0 && !scrollRestored) {
                            view?.evaluateJavascript(
                                """
                                requestAnimationFrame(function(){
                                  requestAnimationFrame(function(){
                                    window.scrollTo(0, $offset);
                                  });
                                });
                                """.trimIndent(),
                                null,
                            )
                            scrollRestored = true
                            consumedCb.value()
                        }
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
                        // Aktif arama varsa yeniden uygula
                        if (searchQuery.isNotBlank()) {
                            view?.findAllAsync(searchQuery)
                        }
                    }
                }
            }.also { webViewRef.value = it }
        },
        update = { webView ->
            webView.setBackgroundColor(
                android.graphics.Color.parseColor(colors.toCss().background)
            )
            webView.loadDataWithBaseURL(
                null,
                buildStyledHtml(htmlContent, preferences, colors),
                "text/html",
                "UTF-8",
                null,
            )
        },
    )
}

// ─── JSON yardımcısı ─────────────────────────────────────────────────────────

internal fun buildHighlightsJson(highlights: List<Highlight>): String {
    if (highlights.isEmpty()) return "[]"
    return highlights.joinToString(",", "[", "]") { h ->
        val text = h.selectedText.toEpubJsString()
        """{"text":$text,"color":"${h.color.hex}"}"""
    }
}

/** Seçili metni JS string literaline güvenli şekilde dönüştürür. */
private fun String.toEpubJsString(): String = buildString {
    append('"')
    for (c in this@toEpubJsString) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

// ─── HTML üreticiler ─────────────────────────────────────────────────────────

private fun buildStyledHtml(
    content: String,
    prefs: ReadingPreferences,
    colors: ReadingColors,
): String {
    val css = colors.toCss()
    val fontFamily = prefs.fontFamily.toCssFontFamily()
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
  html, body {
    margin: 0;
    padding: 0;
    background-color: ${css.background};
    color: ${css.text};
  }
  body {
    font-family: $fontFamily;
    font-size: ${prefs.fontSize}pt;
    line-height: ${prefs.lineHeight};
    padding: 16px ${prefs.marginHorizontal}px 32px ${prefs.marginHorizontal}px;
    word-wrap: break-word;
    -webkit-hyphens: auto;
    hyphens: auto;
    text-align: justify;
  }
  p { margin: 0 0 0.9em 0; }
  h1, h2, h3, h4 { color: ${css.text}; line-height: 1.3; margin-top: 1.2em; }
  a { color: ${css.accent}; text-decoration: none; }
  img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
  blockquote {
    border-left: 3px solid ${css.accent};
    margin: 1em 0;
    padding: 0.3em 1em;
    color: ${css.textSecondary};
  }
  pre, code {
    font-family: 'Courier New', monospace;
    background: ${css.textSecondary}22;
    padding: 2px 4px;
    border-radius: 3px;
  }
  hr { border: none; border-top: 1px solid ${css.textSecondary}55; margin: 2em 0; }
  ::selection { background: ${css.accent}55; }
  .epub-hl { border-radius: 2px; }
</style>
</head>
<body>
$content
<script>
${highlightScript()}
${ttsScript()}
(function() {
  var touchStartX = 0, touchStartY = 0, touchStartTime = 0;
  document.addEventListener('touchstart', function(e) {
    var t = e.changedTouches[0];
    touchStartX = t.clientX; touchStartY = t.clientY; touchStartTime = Date.now();
  }, { passive: true });
  document.addEventListener('touchend', function(e) {
    var t = e.changedTouches[0];
    var dx = Math.abs(t.clientX - touchStartX);
    var dy = Math.abs(t.clientY - touchStartY);
    var dt = Date.now() - touchStartTime;
    if (dx < 15 && dy < 30 && dt < 400) {
      var x = t.clientX, w = window.innerWidth;
      if (x >= w / 3 && x <= 2 * w / 3 && window.AndroidReader) {
        AndroidReader.onMiddleTap();
      }
    }
  }, { passive: true });
})();
</script>
</body>
</html>
    """.trimIndent()
}

// ─── Vurgulama JS ─────────────────────────────────────────────────────────────

/**
 * TTS okuma sırasında okunan cümleyi işaretleyen ve görünüme kaydıran JS snippet'i.
 * __ttsMark__("cümle metni") ile çağrılır; boş string geçilince işaret kaldırılır.
 */
internal fun ttsScript(): String = """
window.__ttsMark__ = function(text) {
  var old = document.querySelector('.epub-tts');
  if (old) {
    var op = old.parentNode; if (op) {
      while (old.firstChild) op.insertBefore(old.firstChild, old);
      op.removeChild(old);
      op.normalize();
    }
  }
  if (!text) return;
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
  var nodes = [], n;
  while ((n = walker.nextNode())) nodes.push(n);
  var lower = text.toLowerCase();
  for (var i = 0; i < nodes.length; i++) {
    var tn = nodes[i];
    if (!tn.parentNode) continue;
    var val = tn.nodeValue;
    var idx = val.toLowerCase().indexOf(lower);
    if (idx < 0) continue;
    var parent = tn.parentNode;
    var span = document.createElement('span');
    span.className = 'epub-tts';
    span.style.backgroundColor = 'rgba(255,220,0,0.38)';
    span.style.borderRadius = '3px';
    span.textContent = val.substring(idx, idx + text.length);
    var after = document.createTextNode(val.substring(idx + text.length));
    parent.replaceChild(after, tn);
    parent.insertBefore(span, after);
    if (idx > 0) parent.insertBefore(document.createTextNode(val.substring(0, idx)), span);
    span.scrollIntoView({ behavior: 'smooth', block: 'center' });
    break;
  }
};
""".trimIndent()

/**
 * Sayfaya enjekte edilen vurgulama JavaScript snippet'i.
 * __applyHighlights__([{text:"...", color:"#RRGGBB"}, ...]) ile çağrılır.
 */
internal fun highlightScript(): String = """
function _epubHexRgba(hex, a) {
  var r = parseInt(hex.slice(1,3),16);
  var g = parseInt(hex.slice(3,5),16);
  var b = parseInt(hex.slice(5,7),16);
  return 'rgba('+r+','+g+','+b+','+a+')';
}
function _epubMarkText(searchText, hexColor) {
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
  var nodes = [], n;
  while ((n = walker.nextNode())) nodes.push(n);
  var lower = searchText.toLowerCase();
  for (var i = 0; i < nodes.length; i++) {
    var tn = nodes[i];
    if (!tn.parentNode) continue;
    var val = tn.nodeValue;
    var idx = val.toLowerCase().indexOf(lower);
    if (idx < 0) continue;
    var parent = tn.parentNode;
    var span = document.createElement('span');
    span.className = 'epub-hl';
    span.style.backgroundColor = _epubHexRgba(hexColor, 0.45);
    span.style.borderRadius = '2px';
    span.textContent = val.substring(idx, idx + searchText.length);
    var after = document.createTextNode(val.substring(idx + searchText.length));
    // Doğru sıra: 1) tn → after, 2) span'i after'dan önce ekle, 3) before-text'i span'den önce ekle
    parent.replaceChild(after, tn);
    parent.insertBefore(span, after);
    if (idx > 0) parent.insertBefore(document.createTextNode(val.substring(0, idx)), span);
  }
}
window.__applyHighlights__ = function(hl) {
  // Önceki vurgu span'lerini kaldır, metin düğümlerini birleştir
  var existing = [].slice.call(document.querySelectorAll('.epub-hl'));
  existing.forEach(function(el) {
    var p = el.parentNode; if (!p) return;
    while (el.firstChild) p.insertBefore(el.firstChild, el);
    p.removeChild(el);
  });
  document.body.normalize();
  if (!hl || hl.length === 0) return;
  hl.forEach(function(h) { try { _epubMarkText(h.text, h.color); } catch(e) {} });
};
""".trimIndent()
