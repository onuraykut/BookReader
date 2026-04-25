package com.kryptow.epub.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kryptow.epub.reader.bookreader.ui.screen.reader.ReaderScreen
import com.kryptow.epub.reader.bookreader.ui.theme.BookReaderTheme

/**
 * EPUB okuyucu Activity.
 *
 * Başlatmak için [EpubReader.openBook] kullanın:
 * ```kotlin
 * EpubReader.openBook(context, bookId)
 * ```
 *
 * Ya da doğrudan Intent ile:
 * ```kotlin
 * startActivity(
 *     Intent(context, EpubReaderActivity::class.java)
 *         .putExtra(EpubReaderActivity.EXTRA_BOOK_ID, bookId)
 * )
 * ```
 */
class EpubReaderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId == -1L) {
            finish()
            return
        }

        setContent {
            BookReaderTheme {
                ReaderScreen(
                    bookId = bookId,
                    onBack = { finish() },
                    onSettingsClick = { /* Ayarlar isteğe bağlı */ },
                    onNotesClick = { /* Notlar isteğe bağlı */ },
                )
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "epub_reader_book_id"
    }
}
