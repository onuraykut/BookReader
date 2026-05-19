package com.kryptow.epub.reader

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.ui.screen.pdf.PdfReaderScreen
import com.kryptow.epub.reader.bookreader.ui.screen.reader.ReaderScreen
import com.kryptow.epub.reader.bookreader.ui.screen.settings.SettingsScreen
import com.kryptow.epub.reader.bookreader.ui.theme.BookReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * EPUB okuyucu Activity.
 *
 * İki şekilde başlatılabilir:
 *
 * **1) Veritabanı ID'siyle** (kitap daha önce eklenmiş):
 * ```kotlin
 * EpubReader.openBook(context, bookId)
 * ```
 *
 * **2) Dosya yolu / URI'siyle** (kitap otomatik kayıt edilir):
 * ```kotlin
 * EpubReader.openFile(context, "/storage/emulated/0/book.epub")
 * EpubReader.openUri(context, uri)
 * ```
 */
class EpubReaderActivity : ComponentActivity() {

    private val bookRepository: BookRepository by inject()
    private val epubParser: EpubParser by inject()

    // Basit iç navigasyon: false = Reader, true = Settings
    private var showSettings by mutableStateOf(false)

    // Yeni okuyucu / migration için Intent extras'tan okunan başlangıç durumu.
    // showReader() içine forward edilir, oradan ReaderScreen → loadBook'a iner.
    private var initialChapter: Int = 0
    private var initialScrollOffset: Int = 0
    private var legacyPageNumber: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        val fileUri = intent.getParcelableExtra<Uri>(EXTRA_FILE_URI)
            ?: intent.data  // ACTION_VIEW ile gelen URI de desteklenir

        // Opsiyonel başlangıç pozisyonu (yoksa default'ları korur)
        initialChapter = intent.getIntExtra(EXTRA_INITIAL_CHAPTER, 0)
        initialScrollOffset = intent.getIntExtra(EXTRA_INITIAL_SCROLL_OFFSET, 0)
        legacyPageNumber = intent.getIntExtra(EXTRA_LEGACY_PAGE_NUMBER, -1)

        when {
            bookId != -1L -> showReader(bookId)
            fileUri != null -> {
                if (fileUri.isPdf(this)) showPdfReader(fileUri)
                else resolveAndOpen(fileUri)
            }
            else -> {
                Toast.makeText(this, getString(R.string.error_invalid_book), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ─── ID'yi çözümle (URI → DB kaydı) ──────────────────────────────────────

    private fun resolveAndOpen(uri: Uri) {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { resolveBookId(uri) }
            if (id != null) {
                showReader(id)
            } else {
                Toast.makeText(this@EpubReaderActivity, getString(R.string.error_epub_open), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * URI'ye karşılık gelen kitabın DB ID'sini döndürür.
     * Kitap daha önce eklenmemişse EpubParser ile parse edilip DB'ye kaydedilir.
     */
    private suspend fun resolveBookId(uri: Uri): Long? {
        val uriString = uri.toString()

        // 1) Aynı dosya daha önce eklenmişse direkt döndür
        bookRepository.getBookByFilePath(uriString)?.let { return it.id }

        // 2) Yoksa parse et ve ekle
        return try {
            val epubBook = epubParser.parse(uri)
            val coverPath = epubBook.coverImageBytes?.let { epubParser.saveCoverImage(it, uri) }
            val book = Book(
                title = epubBook.title.ifBlank { uri.lastPathSegment ?: getString(R.string.error_unknown_book) },
                author = epubBook.author.ifBlank { getString(R.string.error_unknown_author) },
                filePath = uriString,
                coverPath = coverPath,
                totalChapters = epubBook.chapters.size,
            )
            bookRepository.addBook(book)
        } catch (e: Exception) {
            null
        }
    }

    // ─── PDF ekranı göster ────────────────────────────────────────────────────

    private fun showPdfReader(uri: Uri) {
        val fileName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.removeSuffix(".pdf")
            ?: getString(R.string.pdf_unknown_document)
        setContent {
            BookReaderTheme {
                PdfReaderScreen(
                    uri = uri,
                    fileName = fileName,
                    onBack = { finish() },
                )
            }
        }
    }

    // ─── EPUB ekranı göster ───────────────────────────────────────────────────

    private fun showReader(bookId: Long) {
        setContent {
            BookReaderTheme {
                if (showSettings) {
                    SettingsScreen(
                        onBack = { showSettings = false },
                    )
                } else {
                    ReaderScreen(
                        bookId = bookId,
                        onBack = { finish() },
                        onSettingsClick = { showSettings = true },
                        onNotesClick = {},
                        initialChapter = initialChapter,
                        initialScrollOffset = initialScrollOffset,
                        legacyPageNumber = legacyPageNumber,
                    )
                }
            }
        }
    }

    companion object {
        /** DB kayıtlı kitap ID'si ile açmak için. */
        const val EXTRA_BOOK_ID = "epub_reader_book_id"

        /** Dosya URI'si ile açmak için — kitap yoksa otomatik kayıt edilir. */
        const val EXTRA_FILE_URI = "epub_reader_file_uri"

        /** Opsiyonel başlangıç bölümü (>0 → kullanılır). */
        const val EXTRA_INITIAL_CHAPTER = "epub_reader_initial_chapter"

        /** Opsiyonel başlangıç scroll offset'i (dikey: piksel, sayfa modu: sayfa indeksi). */
        const val EXTRA_INITIAL_SCROLL_OFFSET = "epub_reader_initial_scroll_offset"

        /**
         * Eski okuyucudan miras kalan tek-int sayfa numarası (>0 → migration).
         * Sadece kullanıcının yeni okuyucuda kaydedilmiş pozisyonu yoksa uygulanır.
         */
        const val EXTRA_LEGACY_PAGE_NUMBER = "epub_reader_legacy_page_number"
    }
}

// ─── Yardımcı ─────────────────────────────────────────────────────────────────

private fun Uri.isPdf(context: Context): Boolean {
    // 1) MIME type (content:// için en güvenilir yol)
    val mime = context.contentResolver.getType(this)?.lowercase()
    if (mime == "application/pdf") return true

    // 2) Görünen dosya adı (OpenableColumns)
    val displayName = runCatching {
        var name: String? = null
        val cursor: Cursor? = context.contentResolver.query(
            this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
        name
    }.getOrNull()
    if (displayName?.lowercase()?.endsWith(".pdf") == true) return true

    // 3) URI string fallback (file://)
    return toString().lowercase().let { it.endsWith(".pdf") || it.contains(".pdf?") }
}
