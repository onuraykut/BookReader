package com.kryptow.epub.reader.bookreader.domain.model

/**
 * Kütüphane ekranında gösterilen okuma özeti.
 *
 * @property todaySeconds  Bugün okunan toplam süre
 * @property goalMinutes   Günlük hedef (0 = hedef kapalı)
 * @property streakDays    Arka arkaya okunan gün sayısı (bugün henüz okunmadıysa dünden geriye sayılır)
 * @property last7Days     Son 7 günün saniye toplamları — en eski önce, son eleman bugün
 * @property totalSeconds  Tüm zamanların toplamı
 */
data class ReadingStats(
    val todaySeconds: Long = 0,
    val goalMinutes: Int = 0,
    val streakDays: Int = 0,
    val last7Days: List<Long> = List(7) { 0L },
    val totalSeconds: Long = 0,
) {
    val todayMinutes: Int get() = (todaySeconds / 60).toInt()

    /** 0f..1f — hedef kapalıysa 0. */
    val goalProgress: Float
        get() = if (goalMinutes <= 0) 0f
        else (todaySeconds / 60f / goalMinutes).coerceIn(0f, 1f)

    val isGoalReached: Boolean get() = goalMinutes > 0 && todaySeconds >= goalMinutes * 60L
}

/**
 * Okuma istatistikleri için saf (Android'den bağımsız) hesaplamalar — birim testlenebilir.
 */
object ReadingStatsCalculator {

    /** Bir günün serime sayılması için gereken minimum okuma süresi. */
    const val MIN_SECONDS_FOR_STREAK = 60L

    /**
     * Arka arkaya okunan gün sayısı.
     * Bugün henüz [MIN_SECONDS_FOR_STREAK] dolmadıysa seri bozulmuş sayılmaz; dünden geriye sayılır.
     *
     * @param secondsByDay epochDay → saniye
     */
    fun streak(secondsByDay: Map<Long, Long>, today: Long): Int {
        fun counts(day: Long) = (secondsByDay[day] ?: 0L) >= MIN_SECONDS_FOR_STREAK
        var day = if (counts(today)) today else today - 1
        var streak = 0
        while (counts(day)) {
            streak++
            day--
        }
        return streak
    }

    /** Son [days] günün saniye listesi — en eski önce. */
    fun lastDays(secondsByDay: Map<Long, Long>, today: Long, days: Int = 7): List<Long> =
        (days - 1 downTo 0).map { secondsByDay[today - it] ?: 0L }
}

/**
 * Bölüm için kalan okuma süresi tahmini.
 */
object ReadingTimeEstimator {

    /** Ortalama sessiz okuma hızı (kelime/dakika). */
    const val DEFAULT_WORDS_PER_MINUTE = 230

    private val TAG_REGEX = Regex("<[^>]+>")
    private val STYLE_SCRIPT_REGEX = Regex(
        "<(style|script)[^>]*>.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val ENTITY_REGEX = Regex("&[a-zA-Z#0-9]+;")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /** HTML içeriğindeki yaklaşık kelime sayısı. */
    fun wordCount(html: String): Int {
        val text = html
            .replace(STYLE_SCRIPT_REGEX, " ")
            .replace(TAG_REGEX, " ")
            .replace(ENTITY_REGEX, " ")
            .trim()
        if (text.isEmpty()) return 0
        return text.split(WHITESPACE_REGEX).count { token -> token.any { it.isLetterOrDigit() } }
    }

    /**
     * @param remainingFraction 0f..1f — bölümün okunmamış kısmı
     * @return yukarı yuvarlanmış dakika; 0 kelimelik bölüm için 0
     */
    fun minutesLeft(
        wordCount: Int,
        remainingFraction: Float,
        wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE,
    ): Int {
        if (wordCount <= 0 || wordsPerMinute <= 0) return 0
        val remainingWords = wordCount * remainingFraction.coerceIn(0f, 1f)
        if (remainingWords <= 0f) return 0
        return kotlin.math.ceil(remainingWords / wordsPerMinute).toInt().coerceAtLeast(1)
    }
}
