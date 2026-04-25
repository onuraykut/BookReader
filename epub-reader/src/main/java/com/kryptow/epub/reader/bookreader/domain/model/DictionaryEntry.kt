package com.kryptow.epub.reader.bookreader.domain.model

data class DictionaryEntry(
    val word: String,
    val phonetic: String? = null,
    val definitions: List<WordDefinition> = emptyList(),
)

data class WordDefinition(
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null,
)
