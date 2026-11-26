package com.kryptow.epub.reader.bookreader.domain.repository

import com.kryptow.epub.reader.bookreader.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

interface ReadingStatsRepository {
    /** Bugünün kaydına [seconds] ekler. */
    suspend fun recordReading(bookId: Long, seconds: Long)

    /** Okuma özeti — günlük hedef tercihlerden birleştirilir. */
    fun observeStats(): Flow<ReadingStats>
}
