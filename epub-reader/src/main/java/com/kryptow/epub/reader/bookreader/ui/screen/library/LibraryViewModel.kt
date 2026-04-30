package com.kryptow.epub.reader.bookreader.ui.screen.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.usecase.AddBookUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.DeleteBookUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetFavoriteBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetRecentBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.ToggleFavoriteUseCase
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.FolderScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { ALL, RECENT, FAVORITES }
enum class LibraryViewMode { GRID, LIST }

class LibraryViewModel(
    getBooksUseCase: GetBooksUseCase,
    getRecentBooksUseCase: GetRecentBooksUseCase,
    getFavoriteBooksUseCase: GetFavoriteBooksUseCase,
    private val addBookUseCase: AddBookUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val epubParser: EpubParser,
    private val folderScanner: FolderScanner,
) : ViewModel() {

    // ─── Arama sorgusu ────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ─── Kitap listeleri (arama filtreli) ────────────────────────────────────
    private val _allBooksRaw = getBooksUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBooks: StateFlow<List<Book>> = combine(_allBooksRaw, _searchQuery) { books, q ->
        books.filtered(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentBooks: StateFlow<List<Book>> = combine(
        getRecentBooksUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()),
        _searchQuery,
    ) { books, q -> books.filtered(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteBooks: StateFlow<List<Book>> = combine(
        getFavoriteBooksUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()),
        _searchQuery,
    ) { books, q -> books.filtered(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ─── UI durum ─────────────────────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(LibraryTab.ALL)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _viewMode = MutableStateFlow(LibraryViewMode.GRID)
    val viewMode: StateFlow<LibraryViewMode> = _viewMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    // ─── UI aksiyonlar ────────────────────────────────────────────────────────
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }

    fun selectTab(tab: LibraryTab) { _selectedTab.value = tab }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == LibraryViewMode.GRID)
            LibraryViewMode.LIST else LibraryViewMode.GRID
    }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch {
            toggleFavoriteUseCase(book.id, !book.isFavorite)
        }
    }

    // ─── İçe aktarma ─────────────────────────────────────────────────────────
    fun importEpub(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                addSingleEpub(uri)
            } catch (e: Exception) {
                _error.value = "EPUB yüklenemedi: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val foundUris = folderScanner.scanForEpubs(treeUri)
                val existingPaths = allBooks.value.map { it.filePath }.toSet()
                var added = 0
                var failed = 0
                for (uri in foundUris) {
                    if (uri.toString() in existingPaths) continue
                    try { addSingleEpub(uri); added++ } catch (_: Exception) { failed++ }
                }
                _info.value = when {
                    foundUris.isEmpty() -> "Seçilen klasörde EPUB bulunamadı"
                    added == 0 && failed == 0 -> "Tüm kitaplar zaten kütüphanede"
                    failed == 0 -> "$added kitap eklendi"
                    else -> "$added eklendi, $failed başarısız"
                }
            } catch (e: Exception) {
                _error.value = "Klasör taranamadı: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun addSingleEpub(uri: Uri) {
        val epubBook = epubParser.parse(uri)
        val coverPath = epubBook.coverImageBytes?.let { epubParser.saveCoverImage(it, uri) }
        addBookUseCase(
            Book(
                title = epubBook.title,
                author = epubBook.author,
                filePath = uri.toString(),
                coverPath = coverPath,
                totalChapters = epubBook.chapters.size,
            )
        )
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { deleteBookUseCase(book) }
    }

    fun clearError() { _error.value = null }
    fun clearInfo() { _info.value = null }
}

private fun List<Book>.filtered(query: String): List<Book> {
    if (query.isBlank()) return this
    val q = query.lowercase()
    return filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
}
