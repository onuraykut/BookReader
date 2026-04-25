package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Bookmark
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class GetBookmarksUseCase(private val repository: BookRepository) {
    operator fun invoke(bookId: Long): Flow<List<Bookmark>> =
        repository.getBookmarksForBook(bookId)
}
