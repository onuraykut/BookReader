package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository

class AddBookUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(book: Book): Long = repository.addBook(book)
}
