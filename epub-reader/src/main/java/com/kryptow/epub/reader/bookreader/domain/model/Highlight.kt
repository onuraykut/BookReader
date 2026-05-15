package com.kryptow.epub.reader.bookreader.domain.model

import com.kryptow.epub.reader.R

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

enum class HighlightColor(val hex: String, val labelRes: Int) {
    YELLOW("#FFF176", R.string.color_yellow),
    GREEN("#A5D6A7", R.string.color_green),
    BLUE("#90CAF9", R.string.color_blue),
    PINK("#F48FB1", R.string.color_red),
    PURPLE("#CE93D8", R.string.color_orange),
}
