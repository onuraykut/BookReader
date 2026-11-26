package com.kryptow.epub.reader.bookreader.ui.screen.library

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.model.LibraryOrganizer
import com.kryptow.epub.reader.bookreader.domain.model.LibrarySortOrder
import com.kryptow.epub.reader.bookreader.domain.model.ReadingStats
import com.kryptow.epub.reader.bookreader.domain.model.ReadingStatusFilter
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import com.kryptow.epub.reader.bookreader.domain.repository.ReadingStatsRepository
import com.kryptow.epub.reader.bookreader.domain.usecase.AddBookUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.DeleteBookUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetFavoriteBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetRecentBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.ToggleFavoriteUseCase
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.FolderScanner
import com.kryptow.epub.reader.bookreader.pdf.PdfPageRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val bookRepository: BookRepository,
    private val preferencesRepository: PreferencesRepository,
    readingStatsRepository: ReadingStatsRepository,
    private val context: Context,
) : ViewModel() {

    // ─── Sıralama / filtre / istatistik ───────────────────────────────────────
    val sortOrder: StateFlow<LibrarySortOrder> = preferencesRepository.getLibrarySort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySortOrder.DEFAULT)

    private val _statusFilter = MutableStateFlow(ReadingStatusFilter.ALL)
    val statusFilter: StateFlow<ReadingStatusFilter> = _statusFilter.asStateFlow()

    val readingStats: StateFlow<ReadingStats> = readingStatsRepository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())

    val dailyGoalMinutes: StateFlow<Int> = preferencesRepository.getBehaviorPreferences()
        .map { it.dailyGoalMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)


    // ─── Arama sorgusu ────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ─── Kitap listeleri (arama filtreli) ────────────────────────────────────
    private val _allBooksRaw = getBooksUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBooks: StateFlow<List<Book>> = combine(
        _allBooksRaw, _searchQuery, sortOrder, _statusFilter,
    ) { books, q, sort, status ->
        LibraryOrganizer.apply(books.filtered(q), sort, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentBooks: StateFlow<List<Book>> = combine(
        getRecentBooksUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()),
        _searchQuery,
    ) { books, q -> books.filtered(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteBooks: StateFlow<List<Book>> = combine(
        getFavoriteBooksUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()),
        _searchQuery,
        sortOrder,
        _statusFilter,
    ) { books, q, sort, status -> LibraryOrganizer.apply(books.filtered(q), sort, status) }
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

    fun setSortOrder(order: LibrarySortOrder) {
        viewModelScope.launch { preferencesRepository.setLibrarySort(order) }
    }

    fun setStatusFilter(filter: ReadingStatusFilter) { _statusFilter.value = filter }

    fun setFinished(book: Book, finished: Boolean) {
        viewModelScope.launch { bookRepository.setFinished(book.id, finished) }
    }

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch {
            val current = preferencesRepository.getBehaviorPreferences().first()
            preferencesRepository.updateBehaviorPreferences(
                current.copy(dailyGoalMinutes = minutes.coerceIn(0, 600))
            )
        }
    }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch {
            toggleFavoriteUseCase(book.id, !book.isFavorite)
        }
    }

    // ─── İçe aktarma ─────────────────────────────────────────────────────────

    /** EPUB veya PDF URI'sini otomatik algılar ve ekler. */
    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (uri.isPdf(context)) addSinglePdf(uri) else addSingleEpub(uri)
            } catch (e: Exception) {
                _error.value = context.getString(R.string.import_failed, e.message ?: "")
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
                    try {
                        if (uri.isPdf(context)) addSinglePdf(uri) else addSingleEpub(uri)
                        added++
                    } catch (_: Exception) { failed++ }
                }
                _info.value = when {
                    foundUris.isEmpty() -> context.getString(R.string.import_no_files_in_folder)
                    added == 0 && failed == 0 -> context.getString(R.string.import_all_already_added)
                    failed == 0 -> context.getString(R.string.import_added_count, added)
                    else -> context.getString(R.string.import_partial_success, added, failed)
                }
            } catch (e: Exception) {
                _error.value = context.getString(R.string.import_folder_failed, e.message ?: "")
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
                title = epubBook.title.ifBlank { uri.lastPathSegment ?: context.getString(R.string.error_unknown_book) },
                author = epubBook.author.ifBlank { context.getString(R.string.error_unknown_author) },
                filePath = uri.toString(),
                coverPath = coverPath,
                totalChapters = epubBook.chapters.size,
            )
        )
    }

    private suspend fun addSinglePdf(uri: Uri) {
        // Önce OpenableColumns'dan görünen dosya adını al
        val displayName = runCatching {
            var name: String? = null
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )
            cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
            name
        }.getOrNull()

        val fileName = (displayName ?: uri.lastPathSegment ?: context.getString(R.string.pdf_document))
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")

        // İlk sayfayı kapak olarak render et
        val coverPath = PdfPageRenderer.generateCover(context, uri)

        // Toplam sayfa sayısını al
        val pageCount = runCatching {
            val renderer = PdfPageRenderer(context)
            try {
                if (renderer.open(uri)) renderer.pageCount else 1
            } finally { renderer.close() }
        }.getOrDefault(1)

        addBookUseCase(
            Book(
                title = fileName,
                author = "PDF",
                filePath = uri.toString(),
                coverPath = coverPath,
                totalChapters = pageCount,
            )
        )
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { deleteBookUseCase(book) }
    }

    fun clearError() { _error.value = null }
    fun clearInfo() { _info.value = null }
}

private fun Uri.isPdf(context: Context): Boolean {
    // 1) MIME type kontrolü (content:// URI için en güvenilir yol)
    val mime = context.contentResolver.getType(this)?.lowercase()
    if (mime == "application/pdf") return true

    // 2) Görünen dosya adı kontrolü (OpenableColumns)
    val displayName = runCatching {
        var name: String? = null
        val cursor: Cursor? = context.contentResolver.query(
            this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
        name
    }.getOrNull()
    if (displayName?.lowercase()?.endsWith(".pdf") == true) return true

    // 3) URI string kontrolü (file:// URI için)
    return toString().lowercase().let { it.endsWith(".pdf") || it.contains(".pdf?") }
}

private fun List<Book>.filtered(query: String): List<Book> {
    if (query.isBlank()) return this
    val q = query.lowercase()
    return filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
}
