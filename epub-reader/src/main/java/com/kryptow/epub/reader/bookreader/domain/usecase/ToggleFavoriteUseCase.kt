package com.kryptow.epub.reader.bookreader.domain.usecase

import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository

class ToggleFavoriteUseCase(private val repo: BookRepository) {
    suspend operator fun invoke(bookId: Long, isFavorite: Boolean) =
        repo.toggleFavorite(bookId, isFavorite)
}
