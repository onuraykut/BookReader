package com.kryptow.epub.reader.bookreader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class LocalEpochDayTest {

    @Test
    fun `utc midnight boundaries`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(0L, localEpochDay(0L, utc))
        assertEquals(0L, localEpochDay(86_399_999L, utc))
        assertEquals(1L, localEpochDay(86_400_000L, utc))
    }

    @Test
    fun `local offset shifts the day`() {
        // 2026-11-26T22:30Z → İstanbul (UTC+3) için 27 Kasım
        val ts = 1_795_689_000_000L + 12 * 3_600_000L
        val utcDay = localEpochDay(ts, TimeZone.getTimeZone("UTC"))
        assertEquals(utcDay + 1, localEpochDay(ts, TimeZone.getTimeZone("Europe/Istanbul")))
        // Negatif ofset: Los Angeles hâlâ 26 Kasım
        assertEquals(utcDay, localEpochDay(ts, TimeZone.getTimeZone("America/Los_Angeles")))
    }
}
