package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.repository.HighlightRepository

class DeleteHighlightUseCase(private val repository: HighlightRepository) {
    suspend operator fun invoke(highlight: Highlight) = repository.deleteHighlight(highlight)
}
