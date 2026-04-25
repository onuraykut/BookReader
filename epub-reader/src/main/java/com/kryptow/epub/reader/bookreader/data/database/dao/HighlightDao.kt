package com.kryptow.epub.reader.bookreader.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kryptow.epub.reader.bookreader.data.database.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getHighlightsForBook(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND page = :page ORDER BY startOffset ASC")
    fun getHighlightsForPage(bookId: Long, page: Int): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE bookId = :bookId")
    suspend fun deleteAllForBook(bookId: Long)
}
