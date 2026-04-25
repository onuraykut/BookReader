package com.kryptow.epub.reader.bookreader.domain.model

data class BookProgress(
    val id: Long = 0,
    val bookId: Long,
    val currentPage: Int = 0,
    val totalPagesRead: Int = 0,
    val lastReadDate: Long = System.currentTimeMillis(),
    val readingTimeMinutes: Int = 0,
)
