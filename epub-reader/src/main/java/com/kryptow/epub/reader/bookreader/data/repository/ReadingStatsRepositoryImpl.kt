package com.kryptow.epub.reader.bookreader.data.repository

import com.kryptow.epub.reader.bookreader.data.database.dao.DailyReadingDao
import com.kryptow.epub.reader.bookreader.domain.model.ReadingStats
import com.kryptow.epub.reader.bookreader.domain.model.ReadingStatsCalculator
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import com.kryptow.epub.reader.bookreader.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone

class ReadingStatsRepositoryImpl(
    private val dao: DailyReadingDao,
    private val preferencesRepository: PreferencesRepository,
    private val today: () -> Long = ::localEpochDay,
) : ReadingStatsRepository {

    override suspend fun recordReading(bookId: Long, seconds: Long) {
        if (seconds <= 0) return
        dao.addSeconds(today(), bookId, seconds)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStats(): Flow<ReadingStats> {
        // Seri hesabı için geniş bir pencere yeterli; 1 yıldan uzun seri nadirdir ve
        // 366'da kesilmesi kabul edilebilir.
        val todayFlow = flow { emit(today()) }
        return todayFlow.flatMapLatest { day ->
            combine(
                dao.observeDailyTotals(fromDay = day - STREAK_WINDOW_DAYS),
                dao.observeTotalSeconds(),
                preferencesRepository.getBehaviorPreferences()
                    .map { it.dailyGoalMinutes }
                    .distinctUntilChanged(),
            ) { totals, total, goal ->
                val byDay = totals.associate { it.day to it.seconds }
                ReadingStats(
                    todaySeconds = byDay[day] ?: 0L,
                    goalMinutes = goal,
                    streakDays = ReadingStatsCalculator.streak(byDay, day),
                    last7Days = ReadingStatsCalculator.lastDays(byDay, day, 7),
                    totalSeconds = total,
                )
            }
        }
    }

    private companion object {
        const val STREAK_WINDOW_DAYS = 366L
    }
}

/**
 * Cihazın yerel saat dilimine göre bugünün epoch günü.
 * `java.time.LocalDate` API 26 gerektirdiği için (minSdk 24) elle hesaplanır.
 */
internal fun localEpochDay(
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): Long = Math.floorDiv(nowMillis + timeZone.getOffset(nowMillis), 86_400_000L)
