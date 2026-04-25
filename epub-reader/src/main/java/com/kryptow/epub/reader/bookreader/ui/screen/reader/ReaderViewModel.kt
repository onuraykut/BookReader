package com.kryptow.epub.reader.bookreader.ui.screen.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.bookreader.domain.model.Bookmark
import com.kryptow.epub.reader.bookreader.domain.model.DictionaryEntry
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.domain.model.WordDefinition
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.domain.repository.DictionaryRepository
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import com.kryptow.epub.reader.bookreader.domain.usecase.AddHighlightUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetBookmarksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetHighlightsUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.UpdateReadingProgressUseCase
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.model.EpubBook
import com.kryptow.epub.reader.bookreader.epub.model.EpubChapter
import com.kryptow.epub.reader.bookreader.ui.reader.TtsManager
import com.kryptow.epub.reader.bookreader.ui.reader.TtsPlayState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: EpubBook? = null,
    val currentChapterIndex: Int = 0,
    val currentChapter: EpubChapter? = null,
    val error: String? = null,
    /**
     * Dikey modda: kitap yeniden açıldığında geri dönülecek piksel Y konumu.
     * [EpubContentWebView] bunu bir kez kullanır, sonra [consumeInitialScrollOffset]
     * çağrısıyla sıfırlanır. Bölüm değişince otomatik olarak 0'a çekilir.
     */
    val initialScrollOffset: Int = 0,
) {
    val totalChapters: Int get() = book?.chapters?.size ?: 0
    val progressFraction: Float
        get() = if (totalChapters > 0)
            (currentChapterIndex + 1).toFloat() / totalChapters
        else 0f
}

/**
 * Sayfa-bazlı okuma için dinamik olarak hesaplanan pagination durumu.
 */
data class PaginationUiState(
    val chapterPageCounts: Map<Int, Int> = emptyMap(),
    val currentPageInChapter: Int = 0,
    val targetPageInChapter: Int = 0,
    val paginationEpoch: Int = 0,
    val isIndexing: Boolean = false,
    val indexedChapters: Int = 0,
    val totalChaptersToIndex: Int = 0,
) {
    val isIndexComplete: Boolean
        get() = totalChaptersToIndex > 0 && indexedChapters >= totalChaptersToIndex

    val indexProgress: Float
        get() = if (totalChaptersToIndex > 0)
            indexedChapters.toFloat() / totalChaptersToIndex
        else 0f
}

/** Sözlük / çeviri durumu */
data class DictionaryUiState(
    val word: String = "",
    val isVisible: Boolean = false,
    val isLoadingDefinition: Boolean = false,
    val isLoadingTranslation: Boolean = false,
    val definitions: List<WordDefinition> = emptyList(),
    val phonetic: String? = null,
    val translation: String? = null,
    val targetLang: String = "tr",
)

/** Arama sonucu — bölüm indeksi + metin snippet'i */
data class SearchResult(
    val chapterIndex: Int,
    val textSnippet: String,
)

/** Arama durumu */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val currentChapterMatchCount: Int = 0,
    val currentChapterMatchIndex: Int = 0,
    /** WebView'e gönderilecek arama yönü: +1 = sonraki, -1 = önceki, 0 = yok */
    val searchDirection: Int = 0,
)

/** TTS (sesle okuma) durumu */
data class TtsUiState(
    val isVisible: Boolean = false,
    val playState: TtsPlayState = TtsPlayState.IDLE,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentence: String = "",
    val speed: Float = 1.0f,
    val language: String = "en",
)

class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val epubParser: EpubParser,
    private val updateProgressUseCase: UpdateReadingProgressUseCase,
    private val getBookmarksUseCase: GetBookmarksUseCase,
    private val addHighlightUseCase: AddHighlightUseCase,
    private val getHighlightsUseCase: GetHighlightsUseCase,
    private val dictionaryRepository: DictionaryRepository,
    preferencesRepository: PreferencesRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private val _dictionary = MutableStateFlow(DictionaryUiState())
    val dictionary: StateFlow<DictionaryUiState> = _dictionary.asStateFlow()

    private val _pagination = MutableStateFlow(PaginationUiState())
    val pagination: StateFlow<PaginationUiState> = _pagination.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _tts = MutableStateFlow(TtsUiState())
    val tts: StateFlow<TtsUiState> = _tts.asStateFlow()

    val preferences: StateFlow<ReadingPreferences> = preferencesRepository
        .getReadingPreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingPreferences())

    private val _readingSeconds = MutableStateFlow(0L)
    val readingSeconds: StateFlow<Long> = _readingSeconds.asStateFlow()

    private var readingTimerJob: Job? = null
    private var bookId: Long = -1L
    /** Dikey modda son kaydedilen scroll Y — flushProgress'de kullanılır */
    private var _lastScrollY: Int = 0

    init {
        // TtsManager akışlarını _tts StateFlow'a yansıt
        viewModelScope.launch {
            ttsManager.playState.collect { ps ->
                _tts.value = _tts.value.copy(playState = ps)
            }
        }
        viewModelScope.launch {
            ttsManager.currentIndex.collect { idx ->
                _tts.value = _tts.value.copy(
                    currentSentenceIndex = idx,
                    currentSentence = ttsManager.getSentence(idx),
                )
            }
        }
        viewModelScope.launch {
            ttsManager.totalSentences.collect { total ->
                _tts.value = _tts.value.copy(totalSentences = total)
            }
        }
        // Bölüm bitince otomatik ilerle
        ttsManager.onChapterComplete = {
            viewModelScope.launch {
                val s = _uiState.value
                if (s.currentChapterIndex < s.totalChapters - 1) {
                    goToChapter(s.currentChapterIndex + 1)
                    delay(400) // yeni bölüm yüklenene kadar bekle
                    val content = _uiState.value.currentChapter?.content ?: return@launch
                    ttsManager.setContent(content)
                    ttsManager.play()
                } else {
                    // Kitap bitti
                    _tts.value = _tts.value.copy(isVisible = false)
                }
            }
        }
    }

    fun loadBook(bookId: Long) {
        if (this.bookId == bookId) return
        this.bookId = bookId

        viewModelScope.launch {
            _uiState.value = ReaderUiState(isLoading = true)
            try {
                val domainBook = bookRepository.getBookById(bookId)
                    ?: error("Kitap bulunamadı: $bookId")
                val epubBook = epubParser.parse(Uri.parse(domainBook.filePath))
                val startChapter = domainBook.currentChapter
                    .coerceIn(0, epubBook.chapters.lastIndex.coerceAtLeast(0))

                val savedOffset = domainBook.currentScrollOffset.coerceAtLeast(0)

                _uiState.value = ReaderUiState(
                    isLoading = false,
                    book = epubBook,
                    currentChapterIndex = startChapter,
                    currentChapter = epubBook.chapters.getOrNull(startChapter),
                    // Dikey mod: piksel scroll pozisyonu
                    initialScrollOffset = savedOffset,
                )

                // Yatay sayfa modu: sayfa içi indeks olarak geri yükle
                if (savedOffset > 0) {
                    _pagination.value = _pagination.value.copy(
                        targetPageInChapter = savedOffset,
                    )
                }

                launch { getBookmarksUseCase(bookId).collect { _bookmarks.value = it } }
                launch { getHighlightsUseCase(bookId).collect { _highlights.value = it } }
            } catch (e: Exception) {
                _uiState.value = ReaderUiState(isLoading = false, error = e.message)
            }
        }
    }

    // ─── Navigasyon ──────────────────────────────────────────────────────────

    /** @param targetPageInChapter 0 = ilk sayfa, [Int.MAX_VALUE] = son sayfa. */
    fun goToChapter(index: Int, targetPageInChapter: Int = 0) {
        val book = _uiState.value.book ?: return
        val chapter = book.chapters.getOrNull(index) ?: return
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = index,
            currentChapter = chapter,
            initialScrollOffset = 0,  // yeni bölüm → en başa
        )
        _pagination.value = _pagination.value.copy(
            currentPageInChapter = 0,
            targetPageInChapter = targetPageInChapter,
        )
        _lastScrollY = 0
        saveProgress(0)
        // TTS aktifse yeni bölümün içeriğini yükle
        if (_tts.value.isVisible) {
            ttsManager.setContent(chapter.content)
        }
    }

    fun nextChapter() {
        val s = _uiState.value
        if (s.currentChapterIndex < s.totalChapters - 1) goToChapter(s.currentChapterIndex + 1, 0)
    }

    fun previousChapter() {
        val s = _uiState.value
        if (s.currentChapterIndex > 0) goToChapter(s.currentChapterIndex - 1, 0)
    }

    /**
     * Düz sayfa numarasına git (0-tabanlı).
     * Hangi bölüme + hangi sayfaya denk geldiğini `chapterPageCounts`'tan hesaplar.
     */
    fun goToAbsolutePage(absolutePage: Int) {
        val counts = _pagination.value.chapterPageCounts
        val book = _uiState.value.book ?: return
        var remaining = absolutePage
        for (idx in 0 until book.chapters.size) {
            val pages = counts[idx] ?: 1
            if (remaining < pages) {
                goToChapter(idx, remaining)
                return
            }
            remaining -= pages
        }
        // Sayfa sınırı aştıysa son bölümün son sayfasına git
        goToChapter(book.chapters.lastIndex, Int.MAX_VALUE)
    }

    // ─── Pagination olayları ─────────────────────────────────────────────────

    fun onChapterPagesCalculated(chapterIndex: Int, totalPages: Int) {
        val p = _pagination.value
        if (p.chapterPageCounts[chapterIndex] == totalPages) return
        val newCounts = p.chapterPageCounts + (chapterIndex to totalPages)
        _pagination.value = p.copy(
            chapterPageCounts = newCounts,
            indexedChapters = newCounts.size,
        )
    }

    fun onIndexingStarted(totalChapters: Int) {
        val p = _pagination.value
        _pagination.value = p.copy(
            isIndexing = true,
            totalChaptersToIndex = totalChapters,
            indexedChapters = p.chapterPageCounts.size,
        )
    }

    fun onIndexingCompleted() {
        _pagination.value = _pagination.value.copy(isIndexing = false)
    }

    fun onPageInChapterChanged(page: Int) {
        val p = _pagination.value
        if (p.currentPageInChapter == page) return
        _pagination.value = p.copy(currentPageInChapter = page)
        // Her sayfa geçişinde konumu kaydet
        // (HORIZONTAL_PAGE modunda sayfa indeksi, scrollOffset alanında saklanır)
        saveProgress(page)
    }

    fun onSwipeBeyondStart() {
        val s = _uiState.value
        if (s.currentChapterIndex > 0) goToChapter(s.currentChapterIndex - 1, Int.MAX_VALUE)
    }

    fun onSwipeBeyondEnd() {
        val s = _uiState.value
        if (s.currentChapterIndex < s.totalChapters - 1) goToChapter(s.currentChapterIndex + 1, 0)
    }

    fun invalidatePagination() {
        val p = _pagination.value
        _pagination.value = p.copy(
            chapterPageCounts = emptyMap(),
            targetPageInChapter = p.currentPageInChapter.coerceAtLeast(0),
            paginationEpoch = p.paginationEpoch + 1,
            isIndexing = false,
            indexedChapters = 0,
            totalChaptersToIndex = 0,
        )
    }

    // ─── Highlight (Metin vurgulama) ─────────────────────────────────────────

    fun addHighlight(selectedText: String, color: HighlightColor, note: String? = null) {
        viewModelScope.launch {
            addHighlightUseCase(
                Highlight(
                    bookId = bookId,
                    page = _uiState.value.currentChapterIndex,
                    startOffset = 0,
                    endOffset = 0,
                    selectedText = selectedText,
                    color = color,
                    note = note,
                )
            )
        }
    }

    // ─── Sözlük & Çeviri ─────────────────────────────────────────────────────

    /**
     * Kelimeye dokunulduğunda tetiklenir.
     * Sözlük tanımı ve çeviriyi paralel olarak yükler.
     */
    fun onWordTapped(word: String) {
        val current = _dictionary.value
        // Aynı kelime zaten açıksa tekrar yükleme
        if (current.isVisible && current.word.equals(word, ignoreCase = true)) return
        _dictionary.value = DictionaryUiState(
            word = word,
            isVisible = true,
            isLoadingDefinition = true,
            isLoadingTranslation = true,
            targetLang = current.targetLang,
        )
        viewModelScope.launch {
            launch {
                val entry = dictionaryRepository.getDefinition(word)
                _dictionary.value = _dictionary.value.copy(
                    isLoadingDefinition = false,
                    definitions = entry?.definitions ?: emptyList(),
                    phonetic = entry?.phonetic,
                )
            }
            launch {
                val translation = dictionaryRepository.translate(word, current.targetLang)
                _dictionary.value = _dictionary.value.copy(
                    isLoadingTranslation = false,
                    translation = translation,
                )
            }
        }
    }

    /** Hedef dil değiştiğinde çeviriyi yeniden yükler. */
    fun setDictionaryTargetLang(lang: String) {
        val word = _dictionary.value.word
        if (word.isBlank()) return
        _dictionary.value = _dictionary.value.copy(
            targetLang = lang,
            isLoadingTranslation = true,
            translation = null,
        )
        viewModelScope.launch {
            val translation = dictionaryRepository.translate(word, lang)
            _dictionary.value = _dictionary.value.copy(
                isLoadingTranslation = false,
                translation = translation,
            )
        }
    }

    fun dismissDictionary() {
        _dictionary.value = _dictionary.value.copy(isVisible = false)
    }

    // ─── TTS (Sesle Okuma) ───────────────────────────────────────────────────

    /**
     * Üst bardaki hoparlör ikonuna basılınca çağrılır.
     * - TTS çubuğu kapalıysa: açar, mevcut bölüm içeriğini yükler ve oynatır.
     * - TTS çubuğu açıksa: oynat/duraklat toggle yapar.
     */
    fun toggleTts() {
        if (!_tts.value.isVisible) {
            val content = _uiState.value.currentChapter?.content ?: return
            _tts.value = _tts.value.copy(isVisible = true)
            ttsManager.setContent(content)
            ttsManager.play()
        } else {
            when (_tts.value.playState) {
                TtsPlayState.IDLE -> ttsManager.play()
                TtsPlayState.PLAYING -> ttsManager.pause()
                TtsPlayState.PAUSED -> ttsManager.resume()
            }
        }
    }

    fun pauseTts() = ttsManager.pause()
    fun resumeTts() = ttsManager.resume()

    /** TTS'yi durdurur ve kontrol çubuğunu gizler. */
    fun stopTts() {
        ttsManager.stop()
        _tts.value = _tts.value.copy(isVisible = false)
    }

    fun nextTtsSentence() = ttsManager.next()
    fun prevTtsSentence() = ttsManager.prev()

    fun setTtsSpeed(speed: Float) {
        ttsManager.setSpeed(speed)
        _tts.value = _tts.value.copy(speed = speed)
    }

    fun setTtsLanguage(langCode: String) {
        val locale = when (langCode) {
            "tr" -> java.util.Locale("tr", "TR")
            "de" -> java.util.Locale.GERMAN
            "fr" -> java.util.Locale.FRENCH
            "es" -> java.util.Locale("es", "ES")
            "it" -> java.util.Locale.ITALIAN
            "ru" -> java.util.Locale("ru", "RU")
            "ar" -> java.util.Locale("ar", "SA")
            "ja" -> java.util.Locale.JAPANESE
            "zh" -> java.util.Locale.CHINESE
            else -> java.util.Locale.ENGLISH
        }
        ttsManager.setLocale(locale)
        _tts.value = _tts.value.copy(language = langCode)
    }

    // ─── Arama ──────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        _search.value = _search.value.copy(query = query)
    }

    /**
     * Tüm bölümleri tarar, sorgu içeren bölümleri [SearchResult] listesi olarak döner.
     * Her bölüm için HTML etiketleri çıkarılır, eşleşen kelime çevresindeki snippet alınır.
     */
    fun searchInBook() {
        val query = _search.value.query.trim()
        if (query.isBlank()) {
            _search.value = _search.value.copy(results = emptyList())
            return
        }
        val book = _uiState.value.book ?: return
        _search.value = _search.value.copy(isSearching = true)

        viewModelScope.launch {
            val results = mutableListOf<SearchResult>()
            val queryLower = query.lowercase()
            book.chapters.forEachIndexed { index, chapter ->
                val plain = chapter.content
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val plainLower = plain.lowercase()
                var pos = plainLower.indexOf(queryLower)
                if (pos >= 0) {
                    val snippetStart = (pos - 60).coerceAtLeast(0)
                    val snippetEnd = (pos + query.length + 60).coerceAtMost(plain.length)
                    val snippet = buildString {
                        if (snippetStart > 0) append("…")
                        append(plain.substring(snippetStart, snippetEnd))
                        if (snippetEnd < plain.length) append("…")
                    }
                    results.add(SearchResult(chapterIndex = index, textSnippet = snippet))
                }
            }
            _search.value = _search.value.copy(results = results, isSearching = false)
        }
    }

    /** WebView'deki eşleşme sayısını günceller (WebView.setFindListener'dan gelir). */
    fun onSearchMatchResult(total: Int, current: Int) {
        _search.value = _search.value.copy(
            currentChapterMatchCount = total,
            currentChapterMatchIndex = current,
        )
    }

    fun searchNext() {
        _search.value = _search.value.copy(searchDirection = 1)
        // Yönü sıfırla ki tekrar basılabilsin
        viewModelScope.launch {
            delay(100)
            _search.value = _search.value.copy(searchDirection = 0)
        }
    }

    fun searchPrev() {
        _search.value = _search.value.copy(searchDirection = -1)
        viewModelScope.launch {
            delay(100)
            _search.value = _search.value.copy(searchDirection = 0)
        }
    }

    fun clearSearch() {
        _search.value = SearchUiState()
    }

    // ─── Progress & Bookmarks ────────────────────────────────────────────────

    /** Dikey mod scroll olayı — hem anlık kaydeder hem de _lastScrollY günceller */
    fun onVerticalScroll(scrollY: Int) {
        _lastScrollY = scrollY
        saveProgress(scrollY)
    }

    /**
     * EpubContentWebView scroll restore'u tamamlayınca çağrılır.
     * initialScrollOffset sıfırlanır; tema/font değişiminde tekrar tetiklenmez.
     */
    fun consumeInitialScrollOffset() {
        _uiState.value = _uiState.value.copy(initialScrollOffset = 0)
    }

    fun saveProgress(scrollOffset: Int) {
        val s = _uiState.value
        val book = s.book ?: return
        val percent = ((s.currentChapterIndex.toFloat() / book.chapters.size.coerceAtLeast(1)) * 100f)
            .coerceIn(0f, 100f)
        viewModelScope.launch {
            updateProgressUseCase(bookId, s.currentChapterIndex, scrollOffset, percent)
        }
    }

    /**
     * ON_PAUSE veya geri tuşunda çağrılır.
     * - HORIZONTAL_PAGE: currentPageInChapter (sayfa indeksi)
     * - VERTICAL: _lastScrollY (son scroll piksel Y)
     * Her iki değer de aynı `currentScrollOffset` alanında saklanır.
     */
    fun flushProgress() {
        val isPaged = preferences.value.scrollMode ==
                com.kryptow.epub.reader.bookreader.domain.model.ScrollMode.HORIZONTAL_PAGE
        val offset = if (isPaged) _pagination.value.currentPageInChapter else _lastScrollY
        saveProgress(offset)
    }

    fun addBookmark(page: Int) {
        viewModelScope.launch {
            bookRepository.addBookmark(
                Bookmark(
                    bookId = bookId,
                    chapterIndex = _uiState.value.currentChapterIndex,
                    scrollOffset = page,
                )
            )
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookRepository.deleteBookmark(bookmark) }
    }

    // ─── Okuma süresi ────────────────────────────────────────────────────────

    fun startReadingTimer() {
        if (readingTimerJob?.isActive == true) return
        readingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _readingSeconds.value = _readingSeconds.value + 1
            }
        }
    }

    fun stopReadingTimer() {
        readingTimerJob?.cancel()
        readingTimerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopReadingTimer()
        ttsManager.destroy()
    }
}
