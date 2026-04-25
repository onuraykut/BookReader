package com.kryptow.epub.reader.bookreader.domain.model

data class Highlight(
    val id: Long = 0,
    val bookId: Long,
    val page: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class HighlightColor(val hex: String, val labelTr: String) {
    YELLOW("#FFF176", "Sarı"),
    GREEN("#A5D6A7", "Yeşil"),
    BLUE("#90CAF9", "Mavi"),
    PINK("#F48FB1", "Pembe"),
    PURPLE("#CE93D8", "Mor"),
}
