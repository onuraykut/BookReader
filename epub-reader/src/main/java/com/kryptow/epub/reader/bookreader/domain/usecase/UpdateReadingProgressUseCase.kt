package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository

class UpdateReadingProgressUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(bookId: Long, chapter: Int, scrollOffset: Int, percent: Float) {
        repository.updateReadingProgress(bookId, chapter, scrollOffset, percent)
    }
}
