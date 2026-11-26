package com.kryptow.epub.reader.bookreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRenderSizeTest {

    @Test
    fun `normal page keeps requested width and aspect ratio`() {
        // A4 ≈ 595 x 842 pt, ekran 1080 * 1.5
        val s = PdfRenderSize.compute(595, 842, 1620, 0)
        assertEquals(1620, s.width)
        assertEquals((1620.0 * 842 / 595).toInt(), s.height)
    }

    @Test
    fun `very tall page is capped to max dimension keeping ratio`() {
        // 1:20 makbuz sayfası → yükseklik 32400 olurdu
        val s = PdfRenderSize.compute(100, 2000, 1620, 0)
        assertTrue(s.height <= PdfRenderSize.MAX_DIMENSION)
        assertEquals(20.0, s.height.toDouble() / s.width, 0.5)
    }

    @Test
    fun `total pixels are capped`() {
        val s = PdfRenderSize.compute(1000, 1000, 4000, 4000)
        assertTrue(s.width.toLong() * s.height <= PdfRenderSize.MAX_PIXELS)
        assertEquals(s.width, s.height)
    }

    @Test
    fun `degenerate inputs never produce zero sized bitmaps`() {
        val s = PdfRenderSize.compute(0, 0, 0, 0)
        assertTrue(s.width >= 1 && s.height >= 1)
    }
}
