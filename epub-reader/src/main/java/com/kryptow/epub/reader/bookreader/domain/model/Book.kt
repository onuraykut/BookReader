package com.kryptow.epub.reader.bookreader.domain.model

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String? = null,
    val totalChapters: Int = 0,
    val currentChapter: Int = 0,
    val currentScrollOffset: Int = 0,
    val readingProgressPercent: Float = 0f,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val isFinished: Boolean = false,
    val isFavorite: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
) {
    /** Spec uyumluluğu için alias */
    val isRead: Boolean get() = isFinished
    val totalPages: Int get() = totalChapters
}
