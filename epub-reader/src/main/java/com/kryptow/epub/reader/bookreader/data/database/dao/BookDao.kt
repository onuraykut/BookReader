package com.kryptow.epub.reader.bookreader.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kryptow.epub.reader.bookreader.data.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** Tüm kitaplar — son okunan önce */
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, dateAdded DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    /** Son okunanlar — lastReadAt dolu olan, max 20 kayıt */
    @Query("SELECT * FROM books WHERE lastReadAt IS NOT NULL ORDER BY lastReadAt DESC LIMIT 20")
    fun getRecentBooks(): Flow<List<BookEntity>>

    /** Favori kitaplar — son eklenen önce */
    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun getBookByFilePath(filePath: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("""
        UPDATE books
        SET currentChapter = :chapter,
            currentScrollOffset = :scrollOffset,
            readingProgressPercent = :percent,
            lastReadAt = :timestamp
        WHERE id = :bookId
    """)
    suspend fun updateReadingProgress(
        bookId: Long,
        chapter: Int,
        scrollOffset: Int,
        percent: Float,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun setFavorite(bookId: Long, isFavorite: Boolean)
}
