package com.kryptow.epub.reader.bookreader.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** Bölüm indeksi (0-tabanlı) — spec'teki "page" karşılığı */
    val page: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    /** HighlightColor enum adı (YELLOW, GREEN, BLUE, PINK, PURPLE) */
    val color: String = "YELLOW",
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
