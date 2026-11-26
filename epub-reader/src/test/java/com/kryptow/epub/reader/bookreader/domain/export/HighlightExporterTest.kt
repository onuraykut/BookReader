package com.kryptow.epub.reader.bookreader.domain.export

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class HighlightExporterTest {

    private val utc = TimeZone.getTimeZone("UTC")
    // 2026-11-26T10:30Z
    private val ts = 1_795_689_000_000L

    private val labels = ExportLabels(
        notesTitle = "Notes",
        chapter = { "Chapter $it" },
        colorName = { it.color.name.lowercase() },
    )

    private val highlights = listOf(
        Highlight(id = 2, bookId = 1, page = 2, startOffset = 0, endOffset = 0,
            selectedText = "Second, with \"quotes\"", color = HighlightColor.BLUE, note = "my note", timestamp = ts + 1),
        Highlight(id = 1, bookId = 1, page = 0, startOffset = 0, endOffset = 0,
            selectedText = "First line\nsecond line", color = HighlightColor.YELLOW, timestamp = ts),
    )

    private fun export(format: ExportFormat) = HighlightExporter.export(
        format, "My: Book?", "Author", highlights, labels, Locale.ENGLISH, utc,
    )

    @Test
    fun `markdown groups by chapter in order with blockquotes and notes`() {
        val md = export(ExportFormat.MARKDOWN).content
        assertTrue(md.startsWith("# My: Book?\n*Author*\n"))
        val ch1 = md.indexOf("## Chapter 1")
        val ch3 = md.indexOf("## Chapter 3")
        assertTrue(ch1 in 0 until ch3)
        // Çok satırlı vurgunun her satırı alıntı
        assertTrue(md.contains("> First line\n> second line\n"))
        assertTrue(md.contains("**Note:** my note"))
        assertTrue(md.contains("<small>blue · 26 Nov 2026 10:30</small>"))
    }

    @Test
    fun `csv has bom header crlf and rfc4180 quoting`() {
        val csv = export(ExportFormat.CSV).content
        assertTrue(csv.startsWith("﻿Chapter,Text,Note,Color,Date\r\n"))
        val rows = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, rows.size)
        assertEquals("1,\"First line\nsecond line\",,yellow,2026-11-26T10:30", rows[1])
        assertEquals("3,\"Second, with \"\"quotes\"\"\",my note,blue,2026-11-26T10:30", rows[2])
    }

    @Test
    fun `file name is sanitized and extension matches format`() {
        assertEquals("My Book.md", export(ExportFormat.MARKDOWN).fileName)
        assertEquals("My Book.csv", export(ExportFormat.CSV).fileName)
        assertEquals("highlights", HighlightExporter.safeFileName("  /:*  "))
    }

    @Test
    fun `plain text keeps legacy layout`() {
        val txt = export(ExportFormat.TEXT).content
        assertTrue(txt.startsWith("=== My: Book? ===\n"))
        assertTrue(txt.contains("── Chapter 1 ──\n\"First line\nsecond line\"\n(yellow, 26 Nov 2026 10:30)"))
        assertFalse(txt.contains("null"))
    }

    @Test
    fun `csv cell quoting`() {
        assertEquals("plain", HighlightExporter.csvCell("plain"))
        assertEquals("\"a,b\"", HighlightExporter.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", HighlightExporter.csvCell("say \"hi\""))
    }
}
