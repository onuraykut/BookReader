package com.kryptow.epub.reader.bookreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Android PdfRenderer üzerinde çalışan sayfa render yardımcısı.
 *
 * Kararlılık notları:
 * - `PdfRenderer` thread-safe değildir ve aynı anda yalnızca **bir** sayfa açık olabilir.
 *   Pager kaydırılırken komşu sayfalar paralel render edilmeye çalışılır; bu da
 *   "Current page not closed" hatasına yol açar. Tüm erişim [mutex] ile sıralanır.
 * - Kapatıldıktan sonra gelen render istekleri (ekrandan çıkış sırasında) [renderPage]
 *   içinde null döner, exception fırlatmaz.
 * - Bitmap boyutu [PdfRenderSize] ile sınırlanır — aşırı uzun sayfalar OOM'a ya da
 *   "Canvas: trying to draw too large bitmap" çökmesine yol açmaz.
 */
class PdfPageRenderer(private val context: Context) {

    private val mutex = Mutex()
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFd: ParcelFileDescriptor? = null

    @Volatile
    var pageCount: Int = 0
        private set

    /**
     * URI'den PDF açar. content:// ve file:// desteklenir.
     * Parolalı / bozuk PDF'lerde false döner.
     */
    suspend fun open(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            try {
                val fd = when (uri.scheme) {
                    "file" -> ParcelFileDescriptor.open(
                        File(requireNotNull(uri.path) { "file:// URI without path" }),
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                    else -> ParcelFileDescriptor.open(
                        cachedCopy(context, uri),
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                }
                val renderer = try {
                    PdfRenderer(fd)
                } catch (t: Throwable) {
                    fd.close()
                    throw t
                }
                parcelFd = fd
                pdfRenderer = renderer
                pageCount = renderer.pageCount
                true
            } catch (_: Exception) {
                // IOException (bozuk), SecurityException (parolalı / izin kaybı) …
                false
            }
        }
    }

    /**
     * Belirtilen sayfayı bitmap olarak render eder.
     * @param pageIndex 0-tabanlı sayfa indeksi
     * @param width     İstenen piksel genişliği
     * @param height    İstenen piksel yüksekliği (0 ise en-boy oranı korunur)
     * @return Bitmap; PDF kapalıysa, sayfa yoksa ya da bellek yetmezse null
     */
    suspend fun renderPage(pageIndex: Int, width: Int, height: Int = 0): Bitmap? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val renderer = pdfRenderer ?: return@withLock null
                if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                try {
                    renderer.openPage(pageIndex).use { page ->
                        val size = PdfRenderSize.compute(page.width, page.height, width, height)
                        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: IllegalStateException) {
                    null
                }
            }
        }

    /**
     * Ana thread'i bloklamadan kapatır: kilit boştaysa hemen, render sürüyorsa
     * render bittikten sonra arka planda kapanır.
     */
    fun close() {
        if (mutex.tryLock()) {
            try { closeLocked() } finally { mutex.unlock() }
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                mutex.withLock { closeLocked() }
            }
        }
    }

    private fun closeLocked() {
        runCatching { pdfRenderer?.close() }
        runCatching { parcelFd?.close() }
        pdfRenderer = null
        parcelFd = null
        pageCount = 0
    }

    companion object {

        /**
         * content:// URI'yi (PdfRenderer seekable FD istediği için) önbelleğe kopyalar.
         * Önce `.part` dosyasına yazılır, sonra yeniden adlandırılır: kopyalama yarıda kesilirse
         * bir sonraki açılışta bozuk dosya kullanılmaz.
         */
        internal fun cachedCopy(context: Context, uri: Uri): File {
            val target = File(context.cacheDir, "pdf_${uri.toString().hashCode()}.pdf")
            if (target.exists() && target.length() > 0) return target
            val part = File(target.path + ".part")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw java.io.FileNotFoundException("Cannot open $uri")
            input.use { inp -> FileOutputStream(part).use { out -> inp.copyTo(out) } }
            if (!part.renameTo(target)) {
                part.delete()
                throw java.io.IOException("Cannot move PDF cache file")
            }
            return target
        }

        /**
         * Verilen PDF URI'sinin ilk sayfasını kapak olarak diske kaydeder ve dosya yolunu döndürür.
         * Kitaplık ekranında thumbnail olarak kullanılır.
         */
        suspend fun generateCover(
            context: Context,
            uri: Uri,
            width: Int = 600,
        ): String? = withContext(Dispatchers.IO) {
            val renderer = PdfPageRenderer(context)
            try {
                if (!renderer.open(uri)) return@withContext null
                if (renderer.pageCount == 0) return@withContext null

                val bitmap = renderer.renderPage(0, width, 0) ?: return@withContext null

                val coversDir = File(context.filesDir, "covers")
                coversDir.mkdirs()
                val file = File(coversDir, "pdf_${uri.toString().hashCode()}.jpg")
                BufferedOutputStream(FileOutputStream(file)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                file.absolutePath
            } catch (_: Exception) {
                null
            } finally {
                renderer.close()
            }
        }
    }
}

/**
 * PDF sayfası için güvenli bitmap boyutu hesabı (saf fonksiyon — birim testlenebilir).
 */
object PdfRenderSize {
    /** Çoğu GPU'nun texture sınırı; aşılırsa bitmap çizilemez. */
    const val MAX_DIMENSION = 4096
    /** ~ 48 MB ARGB_8888 */
    const val MAX_PIXELS = 12_000_000L

    data class Size(val width: Int, val height: Int)

    fun compute(pageWidth: Int, pageHeight: Int, requestedWidth: Int, requestedHeight: Int): Size {
        val pw = pageWidth.coerceAtLeast(1)
        val ph = pageHeight.coerceAtLeast(1)
        var w = requestedWidth.coerceAtLeast(1).toDouble()
        var h = if (requestedHeight > 0) requestedHeight.toDouble() else w * ph / pw

        // Boyut sınırı — en-boy oranını koruyarak küçült
        val dimScale = minOf(1.0, MAX_DIMENSION / w, MAX_DIMENSION / h)
        w *= dimScale
        h *= dimScale
        // Toplam piksel sınırı
        val pixels = w * h
        if (pixels > MAX_PIXELS) {
            val s = kotlin.math.sqrt(MAX_PIXELS / pixels)
            w *= s
            h *= s
        }
        return Size(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    }
}
