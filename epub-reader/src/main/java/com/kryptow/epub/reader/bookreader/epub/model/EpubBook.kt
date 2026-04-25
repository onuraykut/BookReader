package com.kryptow.epub.reader.bookreader.epub.model

data class EpubBook(
    val title: String,
    val author: String,
    val coverImageBytes: ByteArray? = null,
    val chapters: List<EpubChapter>,
    val filePath: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpubBook) return false
        return filePath == other.filePath
    }

    override fun hashCode(): Int = filePath.hashCode()
}
