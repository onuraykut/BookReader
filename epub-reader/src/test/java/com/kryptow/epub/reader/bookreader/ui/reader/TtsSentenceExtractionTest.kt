package com.kryptow.epub.reader.bookreader.ui.reader

import com.kryptow.epub.reader.bookreader.domain.model.ReadingTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TtsSentenceExtractionTest {

    private val bigImage = "data:image/png;base64," + "A".repeat(200_000)

    @Test
    fun `inline base64 images are not read aloud`() {
        val html = """<p>First sentence here.</p><img src="$bigImage"/><p>Second sentence here!</p>"""
        val sentences = TtsManager.extractSentences(html)
        assertEquals(listOf("First sentence here.", "Second sentence here!"), sentences)
        assertFalse(sentences.any { it.contains("base64") })
    }

    @Test
    fun `plain text strips data uris tags and entities`() {
        val html = """<p>Bir&nbsp;iki</p><img src="$bigImage" alt="x"/><p>üç</p>"""
        assertEquals("Bir iki üç", ReadingTimeEstimator.plainText(html))
        assertEquals(3, ReadingTimeEstimator.wordCount(html))
    }
}
