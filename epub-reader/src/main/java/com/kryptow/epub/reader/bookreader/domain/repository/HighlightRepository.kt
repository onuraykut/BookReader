package com.kryptow.epub.reader.bookreader.domain.repository

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import kotlinx.coroutines.flow.Flow

interface HighlightRepository {
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>>
    fun getHighlightsForPage(bookId: Long, page: Int): Flow<List<Highlight>>
    suspend fun addHighlight(highlight: Highlight): Long
    suspend fun updateHighlight(highlight: Highlight)
    suspend fun deleteHighlight(highlight: Highlight)
}
