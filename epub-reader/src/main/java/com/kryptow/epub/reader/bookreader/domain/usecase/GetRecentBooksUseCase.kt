package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class GetRecentBooksUseCase(private val repo: BookRepository) {
    operator fun invoke(): Flow<List<Book>> = repo.getRecentBooks()
}
