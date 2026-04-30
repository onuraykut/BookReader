package com.kryptow.epub.reader

import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        val fileUri = intent.getParcelableExtra<Uri>(EXTRA_FILE_URI)
            ?: intent.data  // ACTION_VIEW ile gelen URI de desteklenir

        when {
            bookId != -1L -> showReader(bookId)
            fileUri != null -> resolveAndOpen(fileUri)
            else -> {
                Toast.makeText(this, "Geçersiz kitap", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@EpubReaderActivity, "EPUB açılamadı", Toast.LENGTH_SHORT).show()
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
                title = epubBook.title.ifBlank { uri.lastPathSegment ?: "Bilinmeyen Kitap" },
                author = epubBook.author.ifBlank { "Bilinmeyen Yazar" },
                filePath = uriString,
                coverPath = coverPath,
                totalChapters = epubBook.chapters.size,
            )
            bookRepository.addBook(book)
        } catch (e: Exception) {
            null
        }
    }

    // ─── Compose ekranı göster ────────────────────────────────────────────────

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
    }
}
