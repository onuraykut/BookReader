package com.kryptow.epub.reader.bookreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatsCalculatorTest {

    private val today = 20_000L

    @Test
    fun `streak is zero with no reading`() {
        assertEquals(0, ReadingStatsCalculator.streak(emptyMap(), today))
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val data = mapOf(today to 600L, today - 1 to 120L, today - 2 to 60L, today - 4 to 900L)
        assertEquals(3, ReadingStatsCalculator.streak(data, today))
    }

    @Test
    fun `streak is not broken when today has not been read yet`() {
        val data = mapOf(today - 1 to 300L, today - 2 to 300L)
        assertEquals(2, ReadingStatsCalculator.streak(data, today))
    }

    @Test
    fun `days under one minute do not count`() {
        val data = mapOf(today to 59L, today - 1 to 300L)
        // Bugün 59 sn → sayılmaz ama seri dünden devam eder
        assertEquals(1, ReadingStatsCalculator.streak(data, today))
        assertEquals(0, ReadingStatsCalculator.streak(mapOf(today - 1 to 30L), today))
    }

    @Test
    fun `lastDays is oldest first and fills gaps with zero`() {
        val data = mapOf(today to 10L, today - 6 to 70L, today - 3 to 5L)
        assertEquals(
            listOf(70L, 0L, 0L, 5L, 0L, 0L, 10L),
            ReadingStatsCalculator.lastDays(data, today, 7),
        )
    }

    @Test
    fun `goal progress and reached`() {
        val stats = ReadingStats(todaySeconds = 10 * 60, goalMinutes = 20)
        assertEquals(0.5f, stats.goalProgress, 0.001f)
        assertFalse(stats.isGoalReached)
        assertTrue(stats.copy(todaySeconds = 25 * 60).isGoalReached)
        assertEquals(1f, stats.copy(todaySeconds = 25 * 60).goalProgress, 0.001f)
        // Hedef kapalı
        assertEquals(0f, stats.copy(goalMinutes = 0).goalProgress, 0.001f)
        assertFalse(stats.copy(goalMinutes = 0, todaySeconds = 9999).isGoalReached)
    }
}

class ReadingTimeEstimatorTest {

    @Test
    fun `word count strips tags entities scripts and punctuation`() {
        val html = """
            <style>p { color: red; }</style>
            <h1>Başlık</h1>
            <p>Bir iki&nbsp;üç — dört.</p>
            <script>var x = "ignored words here";</script>
            <p>beş <b>altı</b></p>
        """.trimIndent()
        // "—" kelime sayılmaz
        assertEquals(7, ReadingTimeEstimator.wordCount(html))
    }

    @Test
    fun `word count of empty content is zero`() {
        assertEquals(0, ReadingTimeEstimator.wordCount(""))
        assertEquals(0, ReadingTimeEstimator.wordCount("<p> </p>"))
    }

    @Test
    fun `minutes left rounds up and respects remaining fraction`() {
        assertEquals(10, ReadingTimeEstimator.minutesLeft(2300, 1f, 230))
        assertEquals(5, ReadingTimeEstimator.minutesLeft(2300, 0.5f, 230))
        // 1 kelime bile kalsa en az 1 dk
        assertEquals(1, ReadingTimeEstimator.minutesLeft(2300, 0.0001f, 230))
        assertEquals(0, ReadingTimeEstimator.minutesLeft(2300, 0f, 230))
        assertEquals(0, ReadingTimeEstimator.minutesLeft(0, 1f, 230))
        // Aralık dışı oranlar kırpılır
        assertEquals(10, ReadingTimeEstimator.minutesLeft(2300, 3f, 230))
    }
}
