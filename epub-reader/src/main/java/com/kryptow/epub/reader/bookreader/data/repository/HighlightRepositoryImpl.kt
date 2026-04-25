package com.kryptow.epub.reader.bookreader.data.repository

import com.kryptow.epub.reader.bookreader.data.database.dao.HighlightDao
import com.kryptow.epub.reader.bookreader.data.database.entity.HighlightEntity
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import com.kryptow.epub.reader.bookreader.domain.repository.HighlightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HighlightRepositoryImpl(
    private val dao: HighlightDao,
) : HighlightRepository {

    override fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>> =
        dao.getHighlightsForBook(bookId).map { it.map { e -> e.toDomain() } }

    override fun getHighlightsForPage(bookId: Long, page: Int): Flow<List<Highlight>> =
        dao.getHighlightsForPage(bookId, page).map { it.map { e -> e.toDomain() } }

    override suspend fun addHighlight(highlight: Highlight): Long =
        dao.insertHighlight(highlight.toEntity())

    override suspend fun updateHighlight(highlight: Highlight) =
        dao.updateHighlight(highlight.toEntity())

    override suspend fun deleteHighlight(highlight: Highlight) =
        dao.deleteHighlight(highlight.toEntity())
}

private fun HighlightEntity.toDomain() = Highlight(
    id = id, bookId = bookId, page = page,
    startOffset = startOffset, endOffset = endOffset,
    selectedText = selectedText,
    color = runCatching { HighlightColor.valueOf(color) }.getOrDefault(HighlightColor.YELLOW),
    note = note, timestamp = timestamp,
)

private fun Highlight.toEntity() = HighlightEntity(
    id = id, bookId = bookId, page = page,
    startOffset = startOffset, endOffset = endOffset,
    selectedText = selectedText, color = color.name,
    note = note, timestamp = timestamp,
)
