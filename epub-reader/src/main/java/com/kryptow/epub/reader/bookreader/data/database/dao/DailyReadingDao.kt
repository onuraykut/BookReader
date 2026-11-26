package com.kryptow.epub.reader.bookreader.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kryptow.epub.reader.bookreader.data.database.entity.DayTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReadingDao {

    @Query("INSERT OR IGNORE INTO daily_reading (day, bookId, seconds) VALUES (:day, :bookId, 0)")
    suspend fun ensureRow(day: Long, bookId: Long)

    @Query("UPDATE daily_reading SET seconds = seconds + :seconds WHERE day = :day AND bookId = :bookId")
    suspend fun increment(day: Long, bookId: Long, seconds: Long)

    /** Satır yoksa oluşturur, ardından süreyi ekler (atomik). */
    @Transaction
    suspend fun addSeconds(day: Long, bookId: Long, seconds: Long) {
        ensureRow(day, bookId)
        increment(day, bookId, seconds)
    }

    /** [fromDay] (dahil) sonrasındaki günlük toplamlar. */
    @Query(
        """SELECT day, SUM(seconds) AS seconds FROM daily_reading
           WHERE day >= :fromDay GROUP BY day ORDER BY day"""
    )
    fun observeDailyTotals(fromDay: Long): Flow<List<DayTotal>>

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM daily_reading")
    fun observeTotalSeconds(): Flow<Long>
}
