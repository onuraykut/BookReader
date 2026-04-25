package com.kryptow.epub.reader.bookreader.epub.model

data class EpubChapter(
    val index: Int,
    val title: String,
    val content: String,
    val href: String,
)
