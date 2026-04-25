package com.kryptow.epub.reader.bookreader.domain.model

data class Bookmark(
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val scrollOffset: Int,
    val note: String? = null,
    val highlight: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
