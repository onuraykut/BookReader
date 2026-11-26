package com.kryptow.epub.reader.bookreader.ui.reader

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebView tabanlı okuyucular için kararlılık yardımcıları.
 *
 * 1. **JS bridge thread'i** — `@JavascriptInterface` metotları WebView'in "JavaBridge"
 *    arka plan thread'inde çağrılır. Bunlardan Compose state'ine ya da ViewModel'e
 *    doğrudan yazmak kompozisyonla yarışır ("Unsupported concurrent change during
 *    composition" vb.). [runOnMain] ile ana thread'e aktarılır.
 * 2. **Renderer çökmesi** — Düşük bellekte sistem WebView renderer sürecini öldürebilir.
 *    `onRenderProcessGone` ele alınmazsa varsayılan davranış **uygulamanın çökmesidir**.
 *    [SafeWebViewClient] bunu yakalar ve yeniden oluşturma için haber verir.
 * 3. **Sızıntı** — Compose'dan çıkan WebView'ler [safeDestroy] ile yok edilmezse
 *    native bellek birikir ve OOM'a gider.
 */
internal object WebViewSafety {
    private const val TAG = "WebViewSafety"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post {
                try {
                    block()
                } catch (e: Exception) {
                    // Bridge callback'i hiçbir zaman uygulamayı çökertmemeli
                    Log.w(TAG, "JS bridge callback failed", e)
                }
            }
        }
    }

    internal fun logRendererGone(didCrash: Boolean) {
        Log.w(TAG, "WebView renderer gone (crash=$didCrash) — recreating view")
    }
}

/** Ana thread'e aktarılan bridge callback'i — kısa yol. */
internal fun onMain(block: () -> Unit) = WebViewSafety.runOnMain(block)

/**
 * Renderer süreci ölünce uygulamayı çökertmek yerine [onRendererGone] çağırır.
 * Çağıran taraf ölü WebView'i ağaçtan çıkarıp yenisini oluşturmalıdır (ör. `key()` artırarak).
 */
internal open class SafeWebViewClient(
    private val onRendererGone: () -> Unit,
) : WebViewClient() {

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail?.didCrash() ?: false else false
        WebViewSafety.logRendererGone(crashed)
        // Ölü WebView bir daha kullanılamaz. Compose tarafı key'i artırınca eski view
        // onRelease'te safeDestroy() ile yok edilir; burada ağaçtan zorla çıkarmıyoruz.
        runCatching { view?.stopLoading() }
        onRendererGone()
        return true
    }
}

/** Yükü durdur, ağaçtan çıkar ve native kaynakları bırak. Birden çok kez çağrılması güvenlidir. */
internal fun WebView.safeDestroy() {
    runCatching {
        stopLoading()
        webViewClient = WebViewClient()
        (parent as? ViewGroup)?.removeView(this)
        destroy()
    }
}
