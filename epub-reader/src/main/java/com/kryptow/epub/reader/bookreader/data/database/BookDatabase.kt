package com.kryptow.epub.reader.bookreader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kryptow.epub.reader.bookreader.data.database.dao.BookDao
import com.kryptow.epub.reader.bookreader.data.database.dao.BookmarkDao
import com.kryptow.epub.reader.bookreader.data.database.dao.BookProgressDao
import com.kryptow.epub.reader.bookreader.data.database.dao.DailyReadingDao
import com.kryptow.epub.reader.bookreader.data.database.dao.HighlightDao
import com.kryptow.epub.reader.bookreader.data.database.entity.BookEntity
import com.kryptow.epub.reader.bookreader.data.database.entity.BookmarkEntity
import com.kryptow.epub.reader.bookreader.data.database.entity.BookProgressEntity
import com.kryptow.epub.reader.bookreader.data.database.entity.DailyReadingEntity
import com.kryptow.epub.reader.bookreader.data.database.entity.HighlightEntity

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        BookProgressEntity::class,
        HighlightEntity::class,
        DailyReadingEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookProgressDao(): BookProgressDao
    abstract fun highlightDao(): HighlightDao
    abstract fun dailyReadingDao(): DailyReadingDao

    companion object {
        const val DATABASE_NAME = "book_reader_db"

        /** v1 → v2: isFavorite kolonu eklendi */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3: lastModified kolonu + book_progress + highlights tabloları */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Books tablosuna lastModified ekle
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN lastModified INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}"
                )

                // book_progress tablosu
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS book_progress (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        currentPage INTEGER NOT NULL DEFAULT 0,
                        totalPagesRead INTEGER NOT NULL DEFAULT 0,
                        lastReadDate INTEGER NOT NULL,
                        readingTimeMinutes INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_book_progress_bookId ON book_progress(bookId)"
                )

                // highlights tablosu
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS highlights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        page INTEGER NOT NULL,
                        startOffset INTEGER NOT NULL,
                        endOffset INTEGER NOT NULL,
                        selectedText TEXT NOT NULL,
                        color TEXT NOT NULL DEFAULT 'YELLOW',
                        note TEXT,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_highlights_bookId ON highlights(bookId)"
                )
            }
        }

        /** v3 → v4: günlük okuma süresi tablosu (istatistik / hedef / seri) */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS daily_reading (
                        day INTEGER NOT NULL,
                        bookId INTEGER NOT NULL,
                        seconds INTEGER NOT NULL,
                        PRIMARY KEY(day, bookId)
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_reading_bookId ON daily_reading(bookId)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
