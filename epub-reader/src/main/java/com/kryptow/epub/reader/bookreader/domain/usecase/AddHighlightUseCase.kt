package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.repository.HighlightRepository

class AddHighlightUseCase(private val repo: HighlightRepository) {
    suspend operator fun invoke(highlight: Highlight): Long = repo.addHighlight(highlight)
}