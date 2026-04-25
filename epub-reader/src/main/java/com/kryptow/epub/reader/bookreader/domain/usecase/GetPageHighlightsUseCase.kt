package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.repository.HighlightRepository
import kotlinx.coroutines.flow.Flow

class GetPageHighlightsUseCase(private val repository: HighlightRepository) {
    operator fun invoke(bookId: Long, page: Int): Flow<List<Highlight>> =
        repository.getHighlightsForPage(bookId, page)
}
