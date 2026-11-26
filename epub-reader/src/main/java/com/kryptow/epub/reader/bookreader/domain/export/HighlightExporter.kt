package com.kryptow.epub.reader.bookreader.domain.export

import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Vurgu dışa aktarma biçimleri. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
    TEXT("txt", "text/plain"),
}

/** Dışa aktarma çıktısı: paylaşılacak içerik + önerilen dosya adı. */
data class ExportResult(
    val content: String,
    val fileName: String,
    val format: ExportFormat,
)

/**
 * Yerelleştirilmiş etiketler — Android kaynaklarından doldurulur, böylece formatter
 * saf Kotlin kalır ve birim testlenebilir.
 */
data class ExportLabels(
    val notesTitle: String,
    val chapter: (Int) -> String,
    val colorName: (Highlight) -> String,
    val chapterHeader: String = "Chapter",
    val textHeader: String = "Text",
    val noteHeader: String = "Note",
    val colorHeader: String = "Color",
    val dateHeader: String = "Date",
)

/**
 * Vurguları Markdown / CSV / düz metin olarak biçimlendirir.
 *
 * - Markdown: Obsidian / Notion / Logseq'e doğrudan yapıştırılabilir; her vurgu alıntı bloğu.
 * - CSV: RFC 4180 tırnaklama, UTF-8 BOM (Excel'de Türkçe karakterler doğru görünsün diye),
 *   ISO-8601 tarih.
 */
object HighlightExporter {

    /** Excel'in UTF-8 algılaması için dosya başı işareti. */
    private const val UTF8_BOM = '\uFEFF'

    fun export(
        format: ExportFormat,
        bookTitle: String,
        bookAuthor: String?,
        highlights: List<Highlight>,
        labels: ExportLabels,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): ExportResult {
        val sorted = highlights.sortedWith(compareBy({ it.page }, { it.timestamp }))
        val content = when (format) {
            ExportFormat.MARKDOWN -> markdown(bookTitle, bookAuthor, sorted, labels, locale, timeZone)
            ExportFormat.CSV -> csv(sorted, labels, timeZone)
            ExportFormat.TEXT -> text(bookTitle, sorted, labels, locale, timeZone)
        }
        return ExportResult(
            content = content,
            fileName = "${safeFileName(bookTitle)}.${format.extension}",
            format = format,
        )
    }

    private fun markdown(
        title: String,
        author: String?,
        list: List<Highlight>,
        labels: ExportLabels,
        locale: Locale,
        tz: TimeZone,
    ): String = buildString {
        appendLine("# ${title.ifBlank { labels.notesTitle }}")
        if (!author.isNullOrBlank()) appendLine("*$author*")
        appendLine()
        var lastPage = -1
        list.forEach { h ->
            if (h.page != lastPage) {
                appendLine("## ${labels.chapter(h.page + 1)}")
                appendLine()
                lastPage = h.page
            }
            h.selectedText.trim().lines().forEach { line -> appendLine("> ${line.trim()}") }
            appendLine()
            h.note?.takeIf { it.isNotBlank() }?.let { note ->
                appendLine("**${labels.noteHeader}:** ${note.trim()}")
                appendLine()
            }
            appendLine("<small>${labels.colorName(h)} · ${displayDate(h.timestamp, locale, tz)}</small>")
            appendLine()
        }
    }.trimEnd() + "\n"

    private fun csv(list: List<Highlight>, labels: ExportLabels, tz: TimeZone): String = buildString {
        append(UTF8_BOM)
        appendCsvRow(labels.chapterHeader, labels.textHeader, labels.noteHeader, labels.colorHeader, labels.dateHeader)
        list.forEach { h ->
            appendCsvRow(
                (h.page + 1).toString(),
                h.selectedText.trim(),
                h.note?.trim().orEmpty(),
                labels.colorName(h),
                isoDate(h.timestamp, tz),
            )
        }
    }

    private fun text(
        title: String,
        list: List<Highlight>,
        labels: ExportLabels,
        locale: Locale,
        tz: TimeZone,
    ): String = buildString {
        appendLine("=== ${title.ifBlank { labels.notesTitle }} ===")
        appendLine()
        var lastPage = -1
        list.forEach { h ->
            if (h.page != lastPage) {
                appendLine("── ${labels.chapter(h.page + 1)} ──")
                lastPage = h.page
            }
            appendLine("\"${h.selectedText.trim()}\"")
            h.note?.takeIf { it.isNotBlank() }?.let { appendLine(it.trim()) }
            appendLine("(${labels.colorName(h)}, ${displayDate(h.timestamp, locale, tz)})")
            appendLine()
        }
    }

    private fun StringBuilder.appendCsvRow(vararg cells: String) {
        cells.joinTo(this, ",") { csvCell(it) }
        append("\r\n")
    }

    internal fun csvCell(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    internal fun safeFileName(title: String): String {
        val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return cleaned.ifBlank { "highlights" }
    }

    private fun displayDate(ts: Long, locale: Locale, tz: TimeZone): String =
        SimpleDateFormat("d MMM yyyy HH:mm", locale).apply { timeZone = tz }.format(Date(ts))

    private fun isoDate(ts: Long, tz: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).apply { timeZone = tz }.format(Date(ts))
}
