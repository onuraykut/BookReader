package com.kryptow.epub.reader.bookreader.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kitap bazlı okuma istatistikleri.
 * Her kitap için tek kayıt tutulur (REPLACE stratejisi).
 */
@Entity(
    tableName = "book_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId", unique = true)],
)
data class BookProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** Geçerli bölüm indeksi (0-tabanlı) */
    val currentPage: Int = 0,
    /** Toplam okunmuş bölüm sayısı */
    val totalPagesRead: Int = 0,
    val lastReadDate: Long = System.currentTimeMillis(),
    /** Bu oturumda birikmiş okuma süresi (dakika) */
    val readingTimeMinutes: Int = 0,
)
