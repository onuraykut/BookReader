package com.kryptow.epub.reader.bookreader.domain.model

/** Kütüphane sıralama seçenekleri. */
enum class LibrarySortOrder {
    /** Son okunan önce; hiç açılmamışlar eklenme tarihine göre sonda. */
    LAST_READ,
    DATE_ADDED,
    TITLE,
    AUTHOR,
    /** En çok ilerlenen önce. */
    PROGRESS;

    companion object {
        val DEFAULT = LAST_READ
    }
}

/** Okuma durumuna göre filtre. */
enum class ReadingStatusFilter { ALL, UNREAD, READING, FINISHED }

/** Kitabın okuma durumu. */
enum class ReadingStatus { UNREAD, READING, FINISHED }

val Book.readingStatus: ReadingStatus
    get() = when {
        isFinished -> ReadingStatus.FINISHED
        lastReadAt != null || readingProgressPercent > 0f -> ReadingStatus.READING
        else -> ReadingStatus.UNREAD
    }

/**
 * Kütüphane listesine filtre + sıralama uygular (saf fonksiyon — birim testlenebilir).
 */
object LibraryOrganizer {

    fun apply(
        books: List<Book>,
        sort: LibrarySortOrder,
        status: ReadingStatusFilter,
    ): List<Book> {
        val filtered = when (status) {
            ReadingStatusFilter.ALL -> books
            ReadingStatusFilter.UNREAD -> books.filter { it.readingStatus == ReadingStatus.UNREAD }
            ReadingStatusFilter.READING -> books.filter { it.readingStatus == ReadingStatus.READING }
            ReadingStatusFilter.FINISHED -> books.filter { it.readingStatus == ReadingStatus.FINISHED }
        }
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        val comparator: Comparator<Book> = when (sort) {
            LibrarySortOrder.LAST_READ ->
                compareByDescending<Book> { it.lastReadAt ?: Long.MIN_VALUE }
                    .thenByDescending { it.dateAdded }
            LibrarySortOrder.DATE_ADDED -> compareByDescending { it.dateAdded }
            LibrarySortOrder.TITLE -> Comparator { a, b -> collator.compare(a.title, b.title) }
            LibrarySortOrder.AUTHOR -> Comparator<Book> { a, b -> collator.compare(a.author, b.author) }
                .then(Comparator { a, b -> collator.compare(a.title, b.title) })
            LibrarySortOrder.PROGRESS ->
                compareByDescending<Book> { if (it.isFinished) 100f else it.readingProgressPercent }
                    .thenByDescending { it.lastReadAt ?: Long.MIN_VALUE }
        }
        return filtered.sortedWith(comparator)
    }
}
