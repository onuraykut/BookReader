package com.kryptow.epub.reader.bookreader.ui.screen.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import com.kryptow.epub.reader.bookreader.domain.usecase.DeleteHighlightUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetHighlightsUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.UpdateHighlightUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    bookId: Long,
    getHighlightsUseCase: GetHighlightsUseCase,
    private val updateHighlightUseCase: UpdateHighlightUseCase,
    private val deleteHighlightUseCase: DeleteHighlightUseCase,
) : ViewModel() {

    private val _raw = getHighlightsUseCase(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Seçili renk filtresi (null = tümü)
    private val _colorFilter = MutableStateFlow<HighlightColor?>(null)
    val colorFilter: StateFlow<HighlightColor?> = _colorFilter.asStateFlow()

    val highlights: StateFlow<List<Highlight>> = combine(_raw, _colorFilter) { list, color ->
        if (color == null) list else list.filter { it.color == color }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Düzenleme için seçili highlight
    private val _editTarget = MutableStateFlow<Highlight?>(null)
    val editTarget: StateFlow<Highlight?> = _editTarget.asStateFlow()

    fun setColorFilter(color: HighlightColor?) { _colorFilter.value = color }

    fun startEdit(highlight: Highlight) { _editTarget.value = highlight }
    fun cancelEdit() { _editTarget.value = null }

    fun saveNote(note: String) {
        val target = _editTarget.value ?: return
        viewModelScope.launch {
            updateHighlightUseCase(target.copy(note = note.ifBlank { null }))
            _editTarget.value = null
        }
    }

    fun delete(highlight: Highlight) {
        viewModelScope.launch { deleteHighlightUseCase(highlight) }
    }

    /**
     * Tüm vurguları TXT formatında döner (dışa aktarma için).
     * Renk filtresi uygulanmaz — daima tüm notlar dışa aktarılır.
     */
    fun exportAsTxt(): String {
        val list = _raw.value.sortedWith(compareBy({ it.page }, { it.timestamp }))
        return buildString {
            appendLine("=== Notlar ve Vurgular ===")
            appendLine()
            var lastPage = -1
            list.forEach { h ->
                if (h.page != lastPage) {
                    appendLine("── Bölüm ${h.page + 1} ──────────────────────────")
                    lastPage = h.page
                }
                appendLine("\"${h.selectedText}\"")
                if (h.note != null) appendLine("Not: ${h.note}")
                appendLine("(${h.color.labelTr}, ${formatDateExport(h.timestamp)})")
                appendLine()
            }
        }
    }

    private fun formatDateExport(ts: Long): String =
        java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale("tr"))
            .format(java.util.Date(ts))
}
