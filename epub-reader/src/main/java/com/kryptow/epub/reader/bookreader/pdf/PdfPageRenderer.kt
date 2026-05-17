package com.kryptow.epub.reader.bookreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream

/**
 * Android PdfRenderer üzerinde çalışan sayfa render yardımcısı.
 *
 * Kullanım:
 * ```kotlin
 * val renderer = PdfPageRenderer(context)
 * renderer.open(uri)
 * val bitmap = renderer.renderPage(0, screenWidth, screenHeight)
 * renderer.close()
 * ```
 */
class PdfPageRenderer(private val context: Context) {

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFd: ParcelFileDescriptor? = null

    val pageCount: Int get() = pdfRenderer?.pageCount ?: 0

    /**
     * URI'den PDF açar. content:// ve file:// desteklenir.
     * Arka planda çalıştırılmalıdır.
     */
    suspend fun open(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        close()
        try {
            val fd = when (uri.scheme) {
                "file" -> ParcelFileDescriptor.open(
                    File(uri.path!!),
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                else -> {
                    // content:// → geçici dosyaya kopyala (PdfRenderer doğrudan stream almaz)
                    val tmp = File(context.cacheDir, "pdf_${uri.hashCode()}.pdf")
                    if (!tmp.exists()) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tmp).use { output -> input.copyTo(output) }
                        }
                    }
                    ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
            parcelFd = fd
            pdfRenderer = PdfRenderer(fd)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Belirtilen sayfayı belirtilen boyutta bitmap olarak render eder.
     * @param pageIndex 0-tabanlı sayfa indeksi
     * @param width     Hedef piksel genişliği
     * @param height    Hedef piksel yüksekliği (0 ise en-boy oranı korunur)
     */
    suspend fun renderPage(pageIndex: Int, width: Int, height: Int = 0): Bitmap =
        withContext(Dispatchers.IO) {
            val renderer = pdfRenderer ?: error("PDF açık değil")
            renderer.openPage(pageIndex).use { page ->
                val ratio = page.width.toFloat() / page.height.toFloat()
                val targetW = width
                val targetH = if (height > 0) height else (width / ratio).toInt()

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }

    fun close() {
        pdfRenderer?.close()
        parcelFd?.close()
        pdfRenderer = null
        parcelFd = null
    }

    companion object {

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

                val bitmap = renderer.renderPage(0, width, 0)

                val coversDir = File(context.filesDir, "covers")
                coversDir.mkdirs()
                val file = File(coversDir, "pdf_${uri.toString().hashCode()}.jpg")
                BufferedOutputStream(FileOutputStream(file)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                file.absolutePath
            } catch (e: Exception) {
                null
            } finally {
                renderer.close()
            }
        }
    }
}
