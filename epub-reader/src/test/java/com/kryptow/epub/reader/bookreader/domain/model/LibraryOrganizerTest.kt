package com.kryptow.epub.reader.bookreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizerTest {

    private fun book(
        id: Long,
        title: String,
        author: String = "A",
        added: Long = id,
        lastRead: Long? = null,
        percent: Float = 0f,
        finished: Boolean = false,
    ) = Book(
        id = id, title = title, author = author, filePath = "/$id",
        dateAdded = added, lastReadAt = lastRead,
        readingProgressPercent = percent, isFinished = finished,
    )

    private val unread = book(1, "Çalıkuşu", author = "Reşat Nuri", added = 100)
    private val reading = book(2, "Anna Karenina", author = "Tolstoy", added = 300, lastRead = 500, percent = 40f)
    private val finished = book(3, "beyaz Diş", author = "London", added = 200, lastRead = 400, percent = 90f, finished = true)
    private val all = listOf(unread, reading, finished)

    private fun ids(list: List<Book>) = list.map { it.id }

    @Test
    fun `reading status derivation`() {
        assertEquals(ReadingStatus.UNREAD, unread.readingStatus)
        assertEquals(ReadingStatus.READING, reading.readingStatus)
        assertEquals(ReadingStatus.FINISHED, finished.readingStatus)
        // Açılmış ama ilerlemesi 0 olan kitap da "okunuyor"
        assertEquals(ReadingStatus.READING, book(9, "x", lastRead = 1).readingStatus)
    }

    @Test
    fun `status filters`() {
        assertEquals(listOf(1L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.TITLE, ReadingStatusFilter.UNREAD)))
        assertEquals(listOf(2L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.TITLE, ReadingStatusFilter.READING)))
        assertEquals(listOf(3L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.TITLE, ReadingStatusFilter.FINISHED)))
        assertEquals(3, LibraryOrganizer.apply(all, LibrarySortOrder.TITLE, ReadingStatusFilter.ALL).size)
    }

    @Test
    fun `last read puts never-opened books last by date added`() {
        val another = book(4, "Z", added = 900)
        val result = LibraryOrganizer.apply(all + another, LibrarySortOrder.LAST_READ, ReadingStatusFilter.ALL)
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(result))
    }

    @Test
    fun `date added newest first`() {
        assertEquals(listOf(2L, 3L, 1L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.DATE_ADDED, ReadingStatusFilter.ALL)))
    }

    @Test
    fun `title sort is case and accent insensitive`() {
        // Anna, beyaz, Çalıkuşu — büyük/küçük harf ve aksan sıralamayı bozmamalı
        assertEquals(listOf(2L, 3L, 1L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.TITLE, ReadingStatusFilter.ALL)))
    }

    @Test
    fun `author sort`() {
        assertEquals(listOf(3L, 1L, 2L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.AUTHOR, ReadingStatusFilter.ALL)))
    }

    @Test
    fun `progress sort treats finished as 100`() {
        assertEquals(listOf(3L, 2L, 1L), ids(LibraryOrganizer.apply(all, LibrarySortOrder.PROGRESS, ReadingStatusFilter.ALL)))
    }
}
