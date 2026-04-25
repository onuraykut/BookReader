package com.kryptow.epub.reader.bookreader.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
)
