package com.kryptow.epub.reader.bookreader.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servis ile araç ekranları arasında paylaşılan TTS oynatma durumu.
 * TtsPlaybackService yazar, car Screen'ler okur.
 */
object TtsPlaybackState {

    data class Info(
        val bookId: Long = -1L,
        val bookTitle: String = "",
        val chapterTitle: String = "",
        val chapterIndex: Int = 0,
        val totalChapters: Int = 0,
        val isPlaying: Boolean = false,
    )

    private val _current = MutableStateFlow(Info())
    val current: StateFlow<Info> = _current.asStateFlow()

    internal fun update(info: Info) {
        _current.value = info
    }
}
