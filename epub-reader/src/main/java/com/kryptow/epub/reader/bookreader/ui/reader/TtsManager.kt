package com.kryptow.epub.reader.bookreader.ui.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsPlayState { IDLE, PLAYING, PAUSED }

/**
 * Android TextToSpeech sarmalayıcısı.
 *
 * - Bölüm HTML içeriğini cümlelere böler.
 * - [play] / [pause] / [stop] / [next] / [prev] ile gezinilir.
 * - StateFlow'lar Compose'a reaktif olarak aktarılır.
 * - Bölüm bitince [onChapterComplete] çağrılır (ViewModel otomatik ilerleme yapabilir).
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingPlay = false

    private val _playState = MutableStateFlow(TtsPlayState.IDLE)
    val playState: StateFlow<TtsPlayState> = _playState.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _totalSentences = MutableStateFlow(0)
    val totalSentences: StateFlow<Int> = _totalSentences.asStateFlow()

    private var sentences: List<String> = emptyList()
    private var speed: Float = 1.0f
    private var locale: Locale = Locale.ENGLISH

    /** Bölüm sona erince çağrılır (ana thread'de dispatch edilmeli). */
    var onChapterComplete: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setSpeechRate(speed)
                applyLocale(locale)
                if (pendingPlay) {
                    pendingPlay = false
                    speakCurrent()
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (_playState.value != TtsPlayState.PLAYING) return
                val next = _currentIndex.value + 1
                if (next < sentences.size) {
                    _currentIndex.value = next
                    speakCurrent()
                } else {
                    _playState.value = TtsPlayState.IDLE
                    onChapterComplete?.invoke()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _playState.value = TtsPlayState.IDLE
            }
        })
    }

    // ─── İçerik ──────────────────────────────────────────────────────────────

    fun setContent(html: String) {
        tts?.stop()
        _playState.value = TtsPlayState.IDLE
        sentences = extractSentences(html)
        _totalSentences.value = sentences.size
        _currentIndex.value = 0
    }

    fun getSentence(index: Int): String = sentences.getOrElse(index) { "" }

    // ─── Oynatma kontrolleri ──────────────────────────────────────────────────

    fun play() {
        if (sentences.isEmpty()) return
        if (!isReady) { pendingPlay = true; return }
        speakCurrent()
    }

    fun pause() {
        tts?.stop()
        _playState.value = TtsPlayState.PAUSED
    }

    fun resume() {
        if (_playState.value == TtsPlayState.PAUSED) speakCurrent()
    }

    fun stop() {
        tts?.stop()
        _playState.value = TtsPlayState.IDLE
        _currentIndex.value = 0
    }

    fun next() {
        val wasPlaying = _playState.value == TtsPlayState.PLAYING
        tts?.stop()
        _currentIndex.value = (_currentIndex.value + 1).coerceAtMost(
            (sentences.size - 1).coerceAtLeast(0)
        )
        if (wasPlaying) speakCurrent()
    }

    fun prev() {
        val wasPlaying = _playState.value == TtsPlayState.PLAYING
        tts?.stop()
        _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
        if (wasPlaying) speakCurrent()
    }

    // ─── Ayarlar ─────────────────────────────────────────────────────────────

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
        tts?.setSpeechRate(newSpeed)
    }

    fun setLocale(newLocale: Locale) {
        locale = newLocale
        applyLocale(newLocale)
    }

    private fun applyLocale(l: Locale) {
        val result = tts?.setLanguage(l) ?: return
        if (result == TextToSpeech.LANG_NOT_SUPPORTED ||
            result == TextToSpeech.LANG_MISSING_DATA
        ) {
            tts?.setLanguage(Locale.getDefault())
        }
    }

    // ─── İç ──────────────────────────────────────────────────────────────────

    private fun speakCurrent() {
        val sentence = sentences.getOrNull(_currentIndex.value) ?: return
        _playState.value = TtsPlayState.PLAYING
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "utt_${_currentIndex.value}")
    }

    fun destroy() {
        tts?.shutdown()
        tts = null
    }

    // ─── HTML → Cümle listesi ─────────────────────────────────────────────────

    companion object {
        fun extractSentences(html: String): List<String> {
            val text = html
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("</(p|div|li|h[1-6]|blockquote|tr)>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace(Regex("\\s+"), " ")
                .trim()

            // Nokta/soru/ünlem sonrası boşlukla biten cümleleri ayır
            return text
                .split(Regex("(?<=[.!?…])\\s+"))
                .map { it.trim() }
                .filter { it.length > 8 }
        }
    }
}
