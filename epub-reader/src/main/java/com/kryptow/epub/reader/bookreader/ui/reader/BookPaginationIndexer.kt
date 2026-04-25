package com.kryptow.epub.reader.bookreader.ui.reader

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.epub.model.EpubChapter

/**
 * Kitap açılır açılmaz tüm bölümlerin sayfa sayısını arka planda ölçer
 * (Lithium/ReadEra stili). Kullanıcı o anda bir bölümü okurken, bu görünmez
 * WebView diğer bölümleri sırayla paginate edip [onChapterIndexed] callback'iyle
 * ViewModel'e bildirir.
 *
 * Görünmezdir (`alpha = 0f`) ama layout'a dahildir — böylece CSS columns
 * gerçek ekran boyutuyla doğru ölçüm yapabilir.
 *
 * @param paginationEpoch Font/margin/orientation değişince artar ve indexer
 *   sıfırdan yeniden başlar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookPaginationIndexer(
    chapters: List<EpubChapter>,
    preferences: ReadingPreferences,
    colors: ReadingColors,
    paginationEpoch: Int,
    alreadyKnown: Set<Int>,
    onChapterIndexed: (chapterIndex: Int, pageCount: Int) -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chaptersState = rememberUpdatedState(chapters)
    val onIndexed = rememberUpdatedState(onChapterIndexed)
    val onDone = rememberUpdatedState(onCompleted)
    val knownState = rememberUpdatedState(alreadyKnown)

    // Şu anda indekslenmekte olan bölümün indeksi
    var cursor by rememberSaveable(paginationEpoch) { mutableIntStateOf(-1) }

    val bridge = remember(paginationEpoch) {
        object {
            @JavascriptInterface
            fun onMeasured(pageCount: Int) {
                val idx = cursor
                if (idx in chaptersState.value.indices) {
                    onIndexed.value(idx, pageCount)
                }
                // Sonraki bilinmeyen bölüme geç
                advance()
            }

            fun advance() {
                val total = chaptersState.value.size
                var next = cursor + 1
                while (next < total && knownState.value.contains(next)) next++
                cursor = next
                if (next >= total) onDone.value()
            }
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .alpha(0f), // tamamen şeffaf, tıklanamaz ama ölçülebilir
        factory = { ctx ->
            WebView(ctx).apply {
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
                // Etkileşimi engelle — indexer kullanıcıya görünmemeli
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                setOnTouchListener { _, _ -> true }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(bridge, "AndroidIndexer")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("__measure__();", null)
                    }
                }

                // İlk başlatma: ilk bilinmeyen bölüme konumlan
                post { bridge.advance() }
            }
        },
        update = { webView ->
            val idx = cursor
            val list = chaptersState.value
            if (idx in list.indices) {
                val html = buildIndexerHtml(list[idx].content, preferences, colors)
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
    )
}

private fun buildIndexerHtml(
    content: String,
    prefs: ReadingPreferences,
    colors: ReadingColors,
): String {
    val css = colors.toCss()
    val fontFamily = prefs.fontFamily.toCssFontFamily()
    val marginH = prefs.marginHorizontal
    // NOT: Aynı CSS'i PaginatedEpubWebView ile birebir tutmak şart —
    // ölçüm farkı olmasın.
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
  }
  p { margin: 0 0 0.9em 0; }
  h1, h2, h3, h4 { line-height: 1.3; margin-top: 1.2em; break-inside: avoid; }
  img { max-width: 100%; max-height: 90vh; height: auto; display: block; margin: 1em auto; break-inside: avoid; }
  blockquote { border-left: 3px solid ${css.accent}; margin: 1em 0; padding: 0.3em 1em; break-inside: avoid; }
  pre, code { font-family: 'Courier New', monospace; padding: 2px 4px; border-radius: 3px; }
  hr { border: none; border-top: 1px solid ${css.textSecondary}55; margin: 2em 0; }
</style>
</head>
<body>
<div id="content">
$content
</div>
<script>
(function() {
  window.__measure__ = function() {
    // Layout'un oturması için bir frame bekle
    requestAnimationFrame(function() {
      requestAnimationFrame(function() {
        var el = document.getElementById('content');
        var pw = window.innerWidth;
        var sw = el.scrollWidth;
        var pages = Math.max(1, Math.round(sw / pw));
        if (window.AndroidIndexer && AndroidIndexer.onMeasured) {
          AndroidIndexer.onMeasured(pages);
        }
      });
    });
  };
})();
</script>
</body>
</html>
    """.trimIndent()
}
