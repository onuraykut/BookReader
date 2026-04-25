package com.kryptow.epub.reader.bookreader.domain.repository

import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getBookById(id: Long): Book?
    suspend fun getBookByFilePath(filePath: String): Book?
    suspend fun addBook(book: Book): Long
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun updateReadingProgress(bookId: Long, chapter: Int, scrollOffset: Int, percent: Float)

    fun getRecentBooks(): Flow<List<Book>>
    fun getFavoriteBooks(): Flow<List<Book>>
    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean)

    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun deleteBookmark(bookmark: Bookmark)
}
