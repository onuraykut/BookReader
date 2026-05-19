package com.kryptow.epub.reader.bookreader.ui.screen.reader

import com.kryptow.epub.reader.bookreader.domain.model.ScrollMode

/**
 * Eski (legacy) okuyucudan miras kalan tek-int `pageNumber` değerini dikey moddaki
 * piksel scroll offset'ine dönüştürmek için kaba bir tahmin.
 *
 * 1080px genişliğinde ortalama bir cihazda body metninin tipik bir ekranı yaklaşık
 * 1500px civarı tutuyor. Bu değer **ampirik** — kullanıcı geri bildirimine göre
 * burada tek bir yerden ayarlanabilir. Bölüm bazında hassas çevirim imkansız
 * (legacy reader bölüm bilgisi tutmuyordu); en kötü ihtimal yakın bir noktaya
 * inilir, kullanıcı küçük bir scroll ile gerçek konumunu bulur.
 */
internal const val ESTIMATED_PIXELS_PER_LEGACY_PAGE = 1500

/**
 * Reader'ın bir kitabı ilk açtığında oturacağı pozisyon.
 *
 * @property chapter      0-tabanlı bölüm indeksi
 * @property scrollOffset Dikey modda piksel Y; sayfa modunda bölüm içi sayfa indeksi
 */
internal data class InitialPosition(
    val chapter: Int,
    val scrollOffset: Int,
)

/**
 * Bir kitabı açarken hangi pozisyonda başlayacağına karar verir.
 *
 * Öncelik sırası (yüksekten düşüğe):
 *  1. **Saved progress (BookEntity)** — kullanıcı bu kitabı yeni okuyucuda açmışsa
 *     onun pozisyonu kazanır, migration verisi yok sayılır.
 *  2. **Legacy page number** — eski okuyucudan miras kalan tek-int pozisyon.
 *     Dikey modda piksel offset'e çevrilir, sayfa modunda sayfa indeksi olarak
 *     doğrudan kullanılır.
 *  3. **Caller-supplied initialChapter / initialScrollOffset** — yeni okuyucu için
 *     opsiyonel programatik başlangıç noktası.
 *  4. **Defaults (0, 0)** — yukarıdakilerin hiçbiri yoksa.
 *
 * Saf fonksiyon: Android dependency yok, unit test'ten doğrudan çağrılabilir.
 *
 * @param savedChapter        DB'deki son kaydedilen bölüm
 * @param savedScrollOffset   DB'deki son kaydedilen offset (dikey: piksel, sayfa modu: sayfa)
 * @param initialChapter      Caller tarafından opsiyonel önerilen başlangıç bölümü
 * @param initialScrollOffset Caller tarafından opsiyonel önerilen başlangıç offset'i
 * @param legacyPageNumber    Eski okuyucudan miras kalan tek-int sayfa numarası (>0 → uygula)
 * @param scrollMode          [ScrollMode.VERTICAL] veya [ScrollMode.HORIZONTAL_PAGE]
 * @param maxChapterIndex     Kitaptaki son bölüm indeksi (clamp için)
 */
internal fun resolveInitialPosition(
    savedChapter: Int,
    savedScrollOffset: Int,
    initialChapter: Int,
    initialScrollOffset: Int,
    legacyPageNumber: Int,
    scrollMode: ScrollMode,
    maxChapterIndex: Int,
): InitialPosition {
    val clampChapter: (Int) -> Int = { it.coerceIn(0, maxChapterIndex.coerceAtLeast(0)) }

    // 1) Saved progress wins — kullanıcı yeni okuyucuda bu kitaba dokunmuş
    if (savedChapter > 0 || savedScrollOffset > 0) {
        return InitialPosition(
            chapter = clampChapter(savedChapter),
            scrollOffset = savedScrollOffset.coerceAtLeast(0),
        )
    }

    // 2) Legacy migration data
    if (legacyPageNumber > 0) {
        val offset = when (scrollMode) {
            ScrollMode.HORIZONTAL_PAGE -> legacyPageNumber  // sayfa indeksi olarak doğrudan
            ScrollMode.VERTICAL -> legacyPageNumber * ESTIMATED_PIXELS_PER_LEGACY_PAGE
        }
        return InitialPosition(chapter = 0, scrollOffset = offset)
    }

    // 3) Caller-supplied initial position
    if (initialChapter > 0 || initialScrollOffset > 0) {
        return InitialPosition(
            chapter = clampChapter(initialChapter),
            scrollOffset = initialScrollOffset.coerceAtLeast(0),
        )
    }

    // 4) Defaults
    return InitialPosition(0, 0)
}
