package com.kryptow.epub.reader.bookreader.ui.screen.reader

import com.kryptow.epub.reader.bookreader.domain.model.ScrollMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveInitialPosition] için saf-fonksiyon unit testleri.
 *
 * Kapsanan davranışlar:
 *  - Saved progress varsa migration verisi yok sayılır
 *  - Saved progress yoksa legacy page number kullanılır (mode-aware)
 *  - Caller-supplied initial position, legacy page'in altında öncelikte
 *  - Hiçbir şey verilmediğinde (0, 0)
 *  - Edge cases: negatif/0 legacy, chapter clamp
 */
class InitialPositionTest {

    private val maxChapter = 20

    // ─── 1) Saved progress wins ───────────────────────────────────────────────

    @Test
    fun `saved progress used, migration data ignored when chapter saved`() {
        val pos = resolveInitialPosition(
            savedChapter = 5,
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = 124,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(5, pos.chapter)
        assertEquals(0, pos.scrollOffset)
    }

    @Test
    fun `saved progress used, migration data ignored when offset saved`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 8200,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = 50,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(0, pos.chapter)
        assertEquals(8200, pos.scrollOffset)
    }

    @Test
    fun `saved progress chapter clamped to max`() {
        val pos = resolveInitialPosition(
            savedChapter = 999,         // overshoot
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = -1,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = 9,        // last valid index
        )
        assertEquals(9, pos.chapter)
        assertEquals(0, pos.scrollOffset)
    }

    // ─── 2) Legacy page number — no saved progress ────────────────────────────

    @Test
    fun `legacy page applied in vertical mode as pixel offset`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = 124,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(0, pos.chapter)
        assertEquals(124 * ESTIMATED_PIXELS_PER_LEGACY_PAGE, pos.scrollOffset)
    }

    @Test
    fun `legacy page applied in horizontal mode as page index`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = 124,
            scrollMode = ScrollMode.HORIZONTAL_PAGE,
            maxChapterIndex = maxChapter,
        )
        assertEquals(0, pos.chapter)
        assertEquals(124, pos.scrollOffset)
    }

    @Test
    fun `legacy page zero ignored`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 3,
            initialScrollOffset = 100,
            legacyPageNumber = 0,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        // legacy=0 yok sayılır → caller-supplied geçerli
        assertEquals(3, pos.chapter)
        assertEquals(100, pos.scrollOffset)
    }

    @Test
    fun `legacy page negative ignored`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = -1,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(0, pos.chapter)
        assertEquals(0, pos.scrollOffset)
    }

    // ─── 3) Caller-supplied initial position ──────────────────────────────────

    @Test
    fun `initial chapter and offset used when no legacy page and no saved progress`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 7,
            initialScrollOffset = 4200,
            legacyPageNumber = -1,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(7, pos.chapter)
        assertEquals(4200, pos.scrollOffset)
    }

    @Test
    fun `legacy page beats caller-supplied initial position`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 7,
            initialScrollOffset = 4200,
            legacyPageNumber = 50,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        // legacy var → caller-supplied yok sayılır
        assertEquals(0, pos.chapter)
        assertEquals(50 * ESTIMATED_PIXELS_PER_LEGACY_PAGE, pos.scrollOffset)
    }

    // ─── 4) Defaults ─────────────────────────────────────────────────────────

    @Test
    fun `defaults to 0 0 when nothing supplied`() {
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = -1,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(0, pos.chapter)
        assertEquals(0, pos.scrollOffset)
    }

    // ─── 5) Edge cases ────────────────────────────────────────────────────────

    @Test
    fun `negative saved scroll offset clamped to 0`() {
        val pos = resolveInitialPosition(
            savedChapter = 3,
            savedScrollOffset = -100,    // corrupted DB? coerce up
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = -1,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(3, pos.chapter)
        assertEquals(0, pos.scrollOffset)
    }

    @Test
    fun `large legacy page in vertical mode produces large offset (clamping happens downstream)`() {
        // Bilinçli olarak büyük: WebView'de "scroll to N" güvenli — extra altı görünmez,
        // sadece scroll-restore en alta gider. Test maxChapter clamp dışı bir senaryoyu doğrular.
        val pos = resolveInitialPosition(
            savedChapter = 0,
            savedScrollOffset = 0,
            initialChapter = 0,
            initialScrollOffset = 0,
            legacyPageNumber = 1000,
            scrollMode = ScrollMode.VERTICAL,
            maxChapterIndex = maxChapter,
        )
        assertEquals(0, pos.chapter)
        assertEquals(1000 * ESTIMATED_PIXELS_PER_LEGACY_PAGE, pos.scrollOffset)
    }
}
