package com.kryptow.epub.reader.bookreader.ui.screen.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.bookreader.domain.model.Bookmark
import com.kryptow.epub.reader.bookreader.domain.model.DictionaryEntry
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import com.kryptow.epub.reader.bookreader.domain.model.ReaderBehaviorPreferences
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.domain.model.ReadingTimeEstimator
import com.kryptow.epub.reader.bookreader.domain.model.ScrollMode
import com.kryptow.epub.reader.bookreader.domain.model.WordDefinition
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.domain.repository.DictionaryRepository
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import com.kryptow.epub.reader.bookreader.domain.repository.ReadingStatsRepository
import com.kryptow.epub.reader.bookreader.domain.usecase.AddHighlightUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetBookmarksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetHighlightsUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.UpdateReadingProgressUseCase
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.EpubTooLargeException
import com.kryptow.epub.reader.bookreader.epub.model.EpubBook
import com.kryptow.epub.reader.bookreader.epub.model.EpubChapter
import com.kryptow.epub.reader.bookreader.ui.reader.TtsManager
import com.kryptow.epub.reader.bookreader.ui.reader.TtsPlayState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.yield
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
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
    /** Yerelleştirilmiş hata (öncelikli). */
    @androidx.annotation.StringRes val errorRes: Int? = null,
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

/** TTS uyku zamanlayıcısı seçenekleri. */
enum class SleepTimerOption(val minutes: Int) {
    OFF(0), MIN_15(15), MIN_30(30), MIN_45(45), MIN_60(60),
    /** Mevcut bölüm bitince dur. */
    END_OF_CHAPTER(-1),
}

/** Donanım tuşu / dokunma bölgesi kaynaklı sayfa çevirme isteği. */
enum class PageTurn { FORWARD, BACKWARD }

/** TTS (sesle okuma) durumu */
data class TtsUiState(
    val isVisible: Boolean = false,
    val playState: TtsPlayState = TtsPlayState.IDLE,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentence: String = "",
    val speed: Float = 1.0f,
    val language: String = "en",
    val sleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    /** Dakika bazlı zamanlayıcıda kalan saniye; aksi halde null. */
    val sleepTimerRemainingSeconds: Long? = null,
)

class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val epubParser: EpubParser,
    private val updateProgressUseCase: UpdateReadingProgressUseCase,
    private val getBookmarksUseCase: GetBookmarksUseCase,
    private val addHighlightUseCase: AddHighlightUseCase,
    private val getHighlightsUseCase: GetHighlightsUseCase,
    private val dictionaryRepository: DictionaryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ttsManager: TtsManager,
    private val readingStatsRepository: ReadingStatsRepository,
    /** Test için enjekte edilebilir saat (ms). */
    private val clock: () -> Long = System::currentTimeMillis,
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

    val behavior: StateFlow<ReaderBehaviorPreferences> = preferencesRepository
        .getBehaviorPreferences()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderBehaviorPreferences())

    private val _readingSeconds = MutableStateFlow(0L)
    val readingSeconds: StateFlow<Long> = _readingSeconds.asStateFlow()

    /**
     * Mevcut bölümün okunmamış kısmı için tahmini dakika.
     * null → henüz hesaplanamadı (ör. sayfa sayısı bilinmiyor).
     */
    private val _chapterMinutesLeft = MutableStateFlow<Int?>(null)
    val chapterMinutesLeft: StateFlow<Int?> = _chapterMinutesLeft.asStateFlow()

    /** Ses tuşu gibi WebView dışı kaynaklardan gelen sayfa çevirme istekleri. */
    private val _pageTurnRequests = MutableSharedFlow<PageTurn>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pageTurnRequests: SharedFlow<PageTurn> = _pageTurnRequests.asSharedFlow()

    private var readingTimerJob: Job? = null
    private var sleepTimerJob: Job? = null
    /** Henüz veritabanına yazılmamış okuma saniyeleri. */
    private var unsavedReadingSeconds: Long = 0
    /** Son kullanıcı etkileşimi (sayfa çevirme, kaydırma, …) — boşta süreyi saymamak için. */
    private var lastInteractionAt: Long = clock()
    /** Dikey modda bölüm içi kaydırma oranı (0f..1f). */
    private var verticalFraction: Float = 0f
    /** Bu oturumda kitap "bitti" olarak işaretlendi mi? */
    private var finishedMarked = false
    /** Bölüm düz metinleri / kelime sayıları (arka planda hesaplanır). */
    @Volatile private var plainTexts: List<String>? = null
    @Volatile private var wordCounts: List<Int>? = null
    private var searchJob: Job? = null
    private var ttsContentJob: Job? = null
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
                if (_tts.value.sleepTimer == SleepTimerOption.END_OF_CHAPTER) {
                    // Uyku zamanlayıcısı: bölüm sonunda dur, sonraki bölüme geçme
                    setSleepTimer(SleepTimerOption.OFF)
                    return@launch
                }
                val s = _uiState.value
                if (s.currentChapterIndex < s.totalChapters - 1) {
                    goToChapter(s.currentChapterIndex + 1)
                    delay(400) // yeni bölüm yüklenene kadar bekle
                    val content = _uiState.value.currentChapter?.content ?: return@launch
                    ttsManager.setContentAsync(content)
                    ttsManager.play()
                } else {
                    // Kitap bitti
                    _tts.value = _tts.value.copy(isVisible = false)
                }
            }
        }
    }

    /**
     * Kitabı yükler ve başlangıç pozisyonunu belirler.
     *
     * Pozisyon önceliği [resolveInitialPosition] içinde:
     *  1) Kullanıcının yeni okuyucuda kaydettiği pozisyon (BookEntity)
     *  2) [legacyPageNumber] varsa — eski okuyucudan migration
     *  3) Caller'ın verdiği [initialChapter] / [initialScrollOffset]
     *  4) (0, 0)
     *
     * @param bookId               DB'deki kitap kimliği
     * @param initialChapter       Opsiyonel: başlangıç bölüm önerisi (>0 → kullanılır)
     * @param initialScrollOffset  Opsiyonel: başlangıç offset önerisi
     * @param legacyPageNumber     Eski okuyucudan miras kalan sayfa numarası (>0 → migration)
     */
    fun loadBook(
        bookId: Long,
        initialChapter: Int = 0,
        initialScrollOffset: Int = 0,
        legacyPageNumber: Int = -1,
    ) {
        if (this.bookId == bookId) return
        this.bookId = bookId

        viewModelScope.launch {
            _uiState.value = ReaderUiState(isLoading = true)
            try {
                val domainBook = bookRepository.getBookById(bookId)
                    ?: error("Kitap bulunamadı: $bookId")
                val epubBook = epubParser.parse(Uri.parse(domainBook.filePath))

                val position = resolveInitialPosition(
                    savedChapter = domainBook.currentChapter,
                    savedScrollOffset = domainBook.currentScrollOffset,
                    initialChapter = initialChapter,
                    initialScrollOffset = initialScrollOffset,
                    legacyPageNumber = legacyPageNumber,
                    scrollMode = preferences.value.scrollMode,
                    maxChapterIndex = epubBook.chapters.lastIndex.coerceAtLeast(0),
                )

                _uiState.value = ReaderUiState(
                    isLoading = false,
                    book = epubBook,
                    currentChapterIndex = position.chapter,
                    currentChapter = epubBook.chapters.getOrNull(position.chapter),
                    // Dikey mod: piksel scroll pozisyonu (WebView scroll-restore yolu bunu okur,
                    // tek seferlik consume eder — yani BookProgressEntity'ye eagerly yazılmaz)
                    initialScrollOffset = position.scrollOffset,
                )

                // Yatay sayfa modu: sayfa içi indeks olarak geri yükle
                if (position.scrollOffset > 0) {
                    _pagination.value = _pagination.value.copy(
                        targetPageInChapter = position.scrollOffset,
                    )
                }

                recomputeMinutesLeft()
                launch { getBookmarksUseCase(bookId).collect { _bookmarks.value = it } }
                launch { getHighlightsUseCase(bookId).collect { _highlights.value = it } }
                // Arama ve süre tahmini için düz metni bir kez, arka planda hazırla
                launch { buildTextIndex(epubBook) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: EpubTooLargeException) {
                _uiState.value = ReaderUiState(isLoading = false, error = e.message)
            } catch (e: Exception) {
                // Teknik mesaj (URI, SecurityException…) yerine anlaşılır hata göster
                _uiState.value = ReaderUiState(isLoading = false, errorRes = R.string.error_open_failed)
            } catch (e: OutOfMemoryError) {
                _uiState.value = ReaderUiState(isLoading = false, errorRes = R.string.error_book_too_large)
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
        verticalFraction = 0f
        markInteraction()
        saveProgress(0)
        recomputeMinutesLeft()
        // TTS aktifse yeni bölümün içeriğini yükle (cümle ayıklama arka planda)
        if (_tts.value.isVisible) {
            ttsContentJob?.cancel()
            ttsContentJob = viewModelScope.launch { ttsManager.setContentAsync(chapter.content) }
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
        if (chapterIndex == _uiState.value.currentChapterIndex) recomputeMinutesLeft()
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
        markInteraction()
        // Her sayfa geçişinde konumu kaydet
        // (HORIZONTAL_PAGE modunda sayfa indeksi, scrollOffset alanında saklanır)
        saveProgress(page)
        recomputeMinutesLeft()
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
            ttsContentJob?.cancel()
            ttsContentJob = viewModelScope.launch {
                ttsManager.setContentAsync(content)
                ttsManager.play()
            }
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
        setSleepTimer(SleepTimerOption.OFF)
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

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Tüm kitabı taramak büyük kitaplarda saniyeler sürer — ana thread'de ANR'a yol açar
            val results = withContext(Dispatchers.Default) { searchChapters(book, query) }
            _search.value = _search.value.copy(results = results, isSearching = false)
        }
    }

    private suspend fun searchChapters(book: EpubBook, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val queryLower = query.lowercase()
        val texts = plainTexts ?: book.chapters.map { ReadingTimeEstimator.plainText(it.content) }
        run {
            texts.forEachIndexed { index, plain ->
                yield() // iptal edilebilir olsun (yeni sorgu yazıldığında)
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
        }
        return results
    }

    /** Bölümlerin düz metni ve kelime sayıları — [loadBook] sonrası arka planda doldurulur. */
    private suspend fun buildTextIndex(book: EpubBook) {
        val texts = withContext(Dispatchers.Default) {
            book.chapters.map { ReadingTimeEstimator.plainText(it.content) }
        }
        val counts = withContext(Dispatchers.Default) { texts.map { ReadingTimeEstimator.wordCountOfText(it) } }
        plainTexts = texts
        wordCounts = counts
        recomputeMinutesLeft()
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
        markInteraction()
        saveProgress(scrollY)
    }

    /** Dikey modda bölüm içi kaydırma oranı (0f = başı, 1f = sonu). */
    fun onVerticalScrollFraction(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        if (kotlin.math.abs(f - verticalFraction) < 0.005f) return
        verticalFraction = f
        recomputeMinutesLeft()
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
        val chapterCount = book.chapters.size.coerceAtLeast(1)
        val percent = (((s.currentChapterIndex + chapterReadFraction()) / chapterCount) * 100f)
            .coerceIn(0f, 100f)
        // Son bölümün sonuna gelindi → kitabı "bitti" olarak işaretle (oturumda bir kez)
        val markFinished = !finishedMarked &&
            s.currentChapterIndex == book.chapters.lastIndex && isAtChapterEnd()
        if (markFinished) finishedMarked = true
        viewModelScope.launch {
            updateProgressUseCase(bookId, s.currentChapterIndex, scrollOffset, percent)
            if (markFinished) bookRepository.setFinished(bookId, true)
        }
    }

    /** Mevcut bölümün okunmuş oranı (0f..1f). */
    private fun chapterReadFraction(): Float {
        return if (preferences.value.scrollMode == ScrollMode.HORIZONTAL_PAGE) {
            val p = _pagination.value
            val total = p.chapterPageCounts[_uiState.value.currentChapterIndex] ?: return 0f
            if (total <= 1) 0f else (p.currentPageInChapter.toFloat() / (total - 1)).coerceIn(0f, 1f)
        } else {
            verticalFraction
        }
    }

    private fun isAtChapterEnd(): Boolean {
        return if (preferences.value.scrollMode == ScrollMode.HORIZONTAL_PAGE) {
            val p = _pagination.value
            val total = p.chapterPageCounts[_uiState.value.currentChapterIndex] ?: return false
            p.currentPageInChapter >= total - 1
        } else {
            verticalFraction >= 0.98f
        }
    }

    private fun recomputeMinutesLeft() {
        val s = _uiState.value
        val chapter = s.currentChapter ?: run { _chapterMinutesLeft.value = null; return }
        // Kelime sayıları arka planda hesaplanır; hazır değilse tahmini sonra göster
        val words = wordCounts?.getOrNull(s.currentChapterIndex)
            ?: run { _chapterMinutesLeft.value = null; return }
        val remaining = if (preferences.value.scrollMode == ScrollMode.HORIZONTAL_PAGE) {
            val p = _pagination.value
            val total = p.chapterPageCounts[s.currentChapterIndex]
                ?: run { _chapterMinutesLeft.value = null; return }
            // Mevcut sayfa okunuyor sayılır → kalan = (total - page) / total
            (total - p.currentPageInChapter).toFloat() / total.coerceAtLeast(1)
        } else {
            1f - verticalFraction
        }
        _chapterMinutesLeft.value = ReadingTimeEstimator.minutesLeft(words, remaining)
    }

    /**
     * ON_PAUSE veya geri tuşunda çağrılır.
     * - HORIZONTAL_PAGE: currentPageInChapter (sayfa indeksi)
     * - VERTICAL: _lastScrollY (son scroll piksel Y)
     * Her iki değer de aynı `currentScrollOffset` alanında saklanır.
     */
    fun flushProgress() {
        val isPaged = preferences.value.scrollMode == ScrollMode.HORIZONTAL_PAGE
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

    /** Kullanıcı bir şey yaptı → okuma süresi saymaya devam et. */
    fun markInteraction() {
        lastInteractionAt = clock()
    }

    /**
     * Saniyede bir tik atar. Yalnızca kullanıcı son [IDLE_TIMEOUT_MS] içinde etkileşimde
     * bulunduysa veya TTS çalıyorsa süre sayılır — ekran açık bırakılınca istatistik şişmez.
     * Biriken süre [FLUSH_EVERY_SECONDS] saniyede bir ve [stopReadingTimer]'da kaydedilir.
     */
    fun startReadingTimer() {
        if (readingTimerJob?.isActive == true) return
        markInteraction()
        readingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val ttsPlaying = _tts.value.playState == TtsPlayState.PLAYING
                if (!ttsPlaying && clock() - lastInteractionAt > IDLE_TIMEOUT_MS) continue
                _readingSeconds.value = _readingSeconds.value + 1
                unsavedReadingSeconds++
                if (unsavedReadingSeconds >= FLUSH_EVERY_SECONDS) flushReadingTime()
            }
        }
    }

    fun stopReadingTimer() {
        readingTimerJob?.cancel()
        readingTimerJob = null
        viewModelScope.launch { flushReadingTime() }
    }

    private suspend fun flushReadingTime() {
        val secs = unsavedReadingSeconds
        if (secs <= 0 || bookId < 0) return
        unsavedReadingSeconds = 0
        // İptal edilse bile yazma tamamlansın (ekrandan çıkış sırasında)
        withContext(NonCancellable) { readingStatsRepository.recordReading(bookId, secs) }
    }

    // ─── Davranış tercihleri ─────────────────────────────────────────────────

    /** Ses tuşu yakalandı; tuş tercihleri kapalıysa false döner (sistem sesi değiştirsin). */
    fun onVolumeKey(isVolumeUp: Boolean): Boolean {
        val b = behavior.value
        if (!b.volumeKeysEnabled) return false
        val forward = if (b.volumeKeysInverted) isVolumeUp else !isVolumeUp
        markInteraction()
        _pageTurnRequests.tryEmit(if (forward) PageTurn.FORWARD else PageTurn.BACKWARD)
        return true
    }

    fun setBrightness(value: Float) {
        val v = if (value < 0f) ReaderBehaviorPreferences.BRIGHTNESS_SYSTEM
        else value.coerceIn(ReaderBehaviorPreferences.MIN_BRIGHTNESS, 1f)
        updateBehavior { it.copy(brightness = v) }
    }

    private fun updateBehavior(transform: (ReaderBehaviorPreferences) -> ReaderBehaviorPreferences) {
        val updated = transform(behavior.value)
        viewModelScope.launch { preferencesRepository.updateBehaviorPreferences(updated) }
    }

    // ─── TTS uyku zamanlayıcısı ──────────────────────────────────────────────

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _tts.value = _tts.value.copy(
            sleepTimer = option,
            sleepTimerRemainingSeconds = if (option.minutes > 0) option.minutes * 60L else null,
        )
        if (option.minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            var remaining = option.minutes * 60L
            while (remaining > 0) {
                delay(1_000)
                // Duraklatılmışken geri sayım da durur
                if (_tts.value.playState != TtsPlayState.PLAYING) continue
                remaining--
                _tts.value = _tts.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            ttsManager.pause()
            _tts.value = _tts.value.copy(
                sleepTimer = SleepTimerOption.OFF,
                sleepTimerRemainingSeconds = null,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        readingTimerJob?.cancel()
        sleepTimerJob?.cancel()
        ttsManager.destroy()
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        const val FLUSH_EVERY_SECONDS = 30L
    }
}
