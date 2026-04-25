package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.repository.HighlightRepository
import kotlinx.coroutines.flow.Flow

class GetHighlightsUseCase(private val repo: HighlightRepository) {
    operator fun invoke(bookId: Long): Flow<List<Highlight>> = repo.getHighlightsForBook(bookId)
}
