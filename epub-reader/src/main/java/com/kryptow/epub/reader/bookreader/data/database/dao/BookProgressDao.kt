package com.kryptow.epub.reader.bookreader.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kryptow.epub.reader.bookreader.data.database.entity.BookProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookProgressDao {

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId LIMIT 1")
    fun getProgress(bookId: Long): Flow<BookProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: BookProgressEntity)

    @Query("""
        UPDATE book_progress
        SET currentPage = :currentPage,
            totalPagesRead = MAX(totalPagesRead, :currentPage),
            lastReadDate = :now,
            readingTimeMinutes = readingTimeMinutes + :addMinutes
        WHERE bookId = :bookId
    """)
    suspend fun updateProgress(
        bookId: Long,
        currentPage: Int,
        addMinutes: Int,
        now: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM book_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: Long)
}
