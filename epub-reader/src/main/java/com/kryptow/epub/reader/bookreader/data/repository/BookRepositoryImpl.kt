package com.kryptow.epub.reader.bookreader.data.repository

import com.kryptow.epub.reader.bookreader.data.database.dao.BookDao
import com.kryptow.epub.reader.bookreader.data.database.dao.BookmarkDao
import com.kryptow.epub.reader.bookreader.data.database.entity.BookEntity
import com.kryptow.epub.reader.bookreader.data.database.entity.BookmarkEntity
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.model.Bookmark
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { list -> list.map { it.toDomain() } }

    override fun getRecentBooks(): Flow<List<Book>> =
        bookDao.getRecentBooks().map { list -> list.map { it.toDomain() } }

    override fun getFavoriteBooks(): Flow<List<Book>> =
        bookDao.getFavoriteBooks().map { list -> list.map { it.toDomain() } }

    override suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) =
        bookDao.setFavorite(bookId, isFavorite)

    override suspend fun getBookById(id: Long): Book? =
        bookDao.getBookById(id)?.toDomain()

    override suspend fun getBookByFilePath(filePath: String): Book? =
        bookDao.getBookByFilePath(filePath)?.toDomain()

    override suspend fun addBook(book: Book): Long =
        bookDao.insertBook(book.toEntity())

    override suspend fun updateBook(book: Book) =
        bookDao.updateBook(book.toEntity())

    override suspend fun deleteBook(book: Book) =
        bookDao.deleteBook(book.toEntity())

    override suspend fun updateReadingProgress(
        bookId: Long,
        chapter: Int,
        scrollOffset: Int,
        percent: Float,
    ) = bookDao.updateReadingProgress(bookId, chapter, scrollOffset, percent)

    override fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun addBookmark(bookmark: Bookmark): Long =
        bookmarkDao.insertBookmark(bookmark.toEntity())

    override suspend fun deleteBookmark(bookmark: Bookmark) =
        bookmarkDao.deleteBookmark(bookmark.toEntity())
}

// ─── Mapper ────────────────────────────────────────────────────────────────────

fun BookEntity.toDomain() = Book(
    id = id, title = title, author = author, filePath = filePath,
    coverPath = coverPath, totalChapters = totalChapters, currentChapter = currentChapter,
    currentScrollOffset = currentScrollOffset, readingProgressPercent = readingProgressPercent,
    dateAdded = dateAdded, lastReadAt = lastReadAt, isFinished = isFinished,
    isFavorite = isFavorite, lastModified = lastModified,
)

fun Book.toEntity() = BookEntity(
    id = id, title = title, author = author, filePath = filePath,
    coverPath = coverPath, totalChapters = totalChapters, currentChapter = currentChapter,
    currentScrollOffset = currentScrollOffset, readingProgressPercent = readingProgressPercent,
    dateAdded = dateAdded, lastReadAt = lastReadAt, isFinished = isFinished,
    isFavorite = isFavorite, lastModified = lastModified,
)

private fun BookmarkEntity.toDomain() = Bookmark(
    id = id, bookId = bookId, chapterIndex = chapterIndex, scrollOffset = scrollOffset,
    note = note, highlight = highlight, createdAt = createdAt,
)

private fun Bookmark.toEntity() = BookmarkEntity(
    id = id, bookId = bookId, chapterIndex = chapterIndex, scrollOffset = scrollOffset,
    note = note, highlight = highlight, createdAt = createdAt,
)
