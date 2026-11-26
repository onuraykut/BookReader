package com.kryptow.epub.reader.bookreader.ui.screen.notes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.export.ExportFormat
import com.kryptow.epub.reader.bookreader.domain.export.ExportLabels
import com.kryptow.epub.reader.bookreader.domain.export.ExportResult
import com.kryptow.epub.reader.bookreader.domain.export.HighlightExporter
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
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
    private val context: Context,
    bookRepository: BookRepository? = null,
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)

    init {
        if (bookRepository != null) {
            viewModelScope.launch { _book.value = bookRepository.getBookById(bookId) }
        }
    }

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

    /** Vurgu var mı? (Dışa aktarma butonunu etkinleştirmek için) */
    val hasHighlights: Boolean get() = _raw.value.isNotEmpty()

    /**
     * Tüm vurguları seçilen biçimde döner.
     * Renk filtresi uygulanmaz — daima tüm notlar dışa aktarılır.
     */
    fun export(format: ExportFormat): ExportResult {
        val book = _book.value
        val labels = ExportLabels(
            notesTitle = context.getString(R.string.notes_title),
            chapter = { n -> context.getString(R.string.chapter_format, n) },
            colorName = { h -> context.getString(h.color.labelRes) },
            chapterHeader = context.getString(R.string.export_chapter_label),
            textHeader = context.getString(R.string.export_text_label),
            noteHeader = context.getString(R.string.export_note_label),
            colorHeader = context.getString(R.string.export_color_label),
            dateHeader = context.getString(R.string.export_date_label),
        )
        return HighlightExporter.export(
            format = format,
            bookTitle = book?.title ?: context.getString(R.string.notes_title),
            bookAuthor = book?.author,
            highlights = _raw.value,
            labels = labels,
        )
    }

    /** Geriye dönük uyumluluk: düz metin dışa aktarma. */
    fun exportAsTxt(): String = export(ExportFormat.TEXT).content
}
