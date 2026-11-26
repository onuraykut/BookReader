package com.kryptow.epub.reader.bookreader.data.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Gün + kitap bazında biriken okuma süresi.
 *
 * - [day]: yerel saat dilimine göre epoch günü (bkz. `localEpochDay`).
 * - Kitap silinse de istatistikler korunur — bu yüzden `books` tablosuna FK yoktur.
 */
@Entity(
    tableName = "daily_reading",
    primaryKeys = ["day", "bookId"],
    indices = [Index("bookId")],
)
data class DailyReadingEntity(
    val day: Long,
    val bookId: Long,
    val seconds: Long = 0,
)

/** `GROUP BY day` sorgu sonucu. */
data class DayTotal(
    val day: Long,
    val seconds: Long,
)
