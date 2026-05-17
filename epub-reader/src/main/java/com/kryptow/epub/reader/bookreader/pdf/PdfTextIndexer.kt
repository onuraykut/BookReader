package com.kryptow.epub.reader.bookreader.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF sayfa metinlerini çıkarır ve arama yapar.
 *
 * Android'in dahili `PdfRenderer`'ı metin sunmadığı için bu sınıf paralel olarak
 * PdfBox-Android kullanır. Sayfa metinleri **lazy** olarak çıkarılır ve cache'lenir;
 * yani arama yapılana kadar fiziksel okuma maliyeti yoktur.
 */
class PdfTextIndexer(private val context: Context) {

    private var document: PDDocument? = null
    private val pageTextCache = mutableMapOf<Int, String>()

    val pageCount: Int get() = document?.numberOfPages ?: 0

    /**
     * URI'den PDF açar. content:// için geçici dosyaya kopyalar (PdfPageRenderer ile aynı dosyayı kullanır).
     * IO thread'de çalıştırılmalıdır.
     */
    suspend fun open(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        close()
        runCatching {
            val file = when (uri.scheme) {
                "file" -> File(uri.path!!)
                else -> {
                    // content:// → cache dosyası (PdfPageRenderer ile aynı kalıp)
                    val tmp = File(context.cacheDir, "pdf_${uri.hashCode()}.pdf")
                    if (!tmp.exists()) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tmp).use { output -> input.copyTo(output) }
                        }
                    }
                    tmp
                }
            }
            document = PDDocument.load(file)
            true
        }.getOrElse { false }
    }

    /**
     * Tek bir sayfanın metnini döner; cache'li.
     */
    suspend fun textForPage(pageIndex: Int): String = withContext(Dispatchers.IO) {
        pageTextCache[pageIndex]?.let { return@withContext it }
        val doc = document ?: return@withContext ""
        if (pageIndex < 0 || pageIndex >= doc.numberOfPages) return@withContext ""

        val text = runCatching {
            PDFTextStripper().apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }.getText(doc).trim()
        }.getOrDefault("")

        pageTextCache[pageIndex] = text
        text
    }

    /**
     * Tüm sayfalarda case-insensitive arama yapar.
     * Her sayfa için ilk eşleşme etrafında ~120 karakterlik bir snippet üretir.
     */
    suspend fun search(query: String): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val doc = document ?: return@withContext emptyList()

        val qLower = q.lowercase()
        val results = mutableListOf<PdfSearchResult>()

        for (page in 0 until doc.numberOfPages) {
            val text = textForPage(page)
            if (text.isEmpty()) continue
            val textLower = text.lowercase()
            val first = textLower.indexOf(qLower)
            if (first < 0) continue

            // Snippet: eşleşme civarında ~60 karakter öncesi/sonrası
            val start = (first - 60).coerceAtLeast(0)
            val end = (first + q.length + 60).coerceAtMost(text.length)
            val rawSnippet = text.substring(start, end)
                .replace(Regex("\\s+"), " ")
                .trim()
            val snippet = buildString {
                if (start > 0) append("…")
                append(rawSnippet)
                if (end < text.length) append("…")
            }

            // Sayfadaki toplam eşleşme sayısı
            val matchCount = countMatches(textLower, qLower)
            results.add(
                PdfSearchResult(
                    pageIndex = page,
                    snippet = snippet,
                    matchCount = matchCount,
                )
            )
        }
        results
    }

    private fun countMatches(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = haystack.indexOf(needle, idx)
            if (found < 0) break
            count++
            idx = found + needle.length
        }
        return count
    }

    fun close() {
        runCatching { document?.close() }
        document = null
        pageTextCache.clear()
    }
}

/** Tek bir PDF sayfasındaki arama sonucu. */
data class PdfSearchResult(
    val pageIndex: Int,
    val snippet: String,
    val matchCount: Int,
)
