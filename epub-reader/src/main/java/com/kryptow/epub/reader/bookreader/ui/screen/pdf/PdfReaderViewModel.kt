package com.kryptow.epub.reader.bookreader.ui.screen.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.pdf.PdfPageRenderer
import com.kryptow.epub.reader.bookreader.pdf.PdfSearchResult
import com.kryptow.epub.reader.bookreader.pdf.PdfTextIndexer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PdfUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val fileName: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
)

data class PdfSearchUiState(
    val query: String = "",
    val results: List<PdfSearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

class PdfReaderViewModel(
    private val context: Context,
) : ViewModel() {

    private val renderer = PdfPageRenderer(context.applicationContext)
    private val indexer = PdfTextIndexer(context.applicationContext)

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    private val _search = MutableStateFlow(PdfSearchUiState())
    val search: StateFlow<PdfSearchUiState> = _search.asStateFlow()

    private var searchJob: Job? = null

    fun openPdf(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _state.value = PdfUiState(isLoading = true, fileName = fileName)
            val success = renderer.open(uri)
            // Indexer'ı arka planda aç — arama açılana kadar bloklamasın
            launch { indexer.open(uri) }

            if (success) {
                _state.value = PdfUiState(
                    isLoading = false,
                    fileName = fileName,
                    currentPage = 0,
                    totalPages = renderer.pageCount,
                )
            } else {
                _state.value = PdfUiState(
                    isLoading = false,
                    error = context.getString(R.string.pdf_open_failed),
                    fileName = fileName,
                )
            }
        }
    }

    fun goToPage(page: Int) {
        val total = _state.value.totalPages
        if (total == 0) return
        _state.value = _state.value.copy(
            currentPage = page.coerceIn(0, total - 1)
        )
    }

    fun nextPage() = goToPage(_state.value.currentPage + 1)
    fun prevPage() = goToPage(_state.value.currentPage - 1)

    suspend fun renderPage(pageIndex: Int, width: Int, height: Int) =
        renderer.renderPage(pageIndex, width, height)

    // ─── Arama ────────────────────────────────────────────────────────────────

    /**
     * Sorguyu günceller ve debounce ile arar.
     * Boş sorguda sonuçları temizler.
     */
    fun setSearchQuery(query: String) {
        _search.value = _search.value.copy(query = query)
        searchJob?.cancel()

        if (query.trim().isEmpty()) {
            _search.value = _search.value.copy(results = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(250) // debounce — kullanıcı yazarken her tuşa arama yapma
            _search.value = _search.value.copy(isSearching = true)
            val results = indexer.search(query)
            _search.value = _search.value.copy(results = results, isSearching = false)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = PdfSearchUiState()
    }

    override fun onCleared() {
        super.onCleared()
        renderer.close()
        indexer.close()
    }
}
