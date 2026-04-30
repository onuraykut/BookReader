package com.kryptow.epub.reader.bookreader.epub

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.kryptow.epub.reader.bookreader.epub.model.EpubBook
import com.kryptow.epub.reader.bookreader.epub.model.EpubChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser(private val context: Context) {

    suspend fun parse(uri: Uri): EpubBook = withContext(Dispatchers.IO) {
        val entries = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Cannot open URI: $uri")

        val opfPath = findOpfPath(entries)
        val opfDir = opfPath.substringBeforeLast("/", "")
        val opfBytes = entries[opfPath] ?: error("OPF file not found at $opfPath")

        val opfDoc = parseXml(opfBytes.inputStream())
        val root = opfDoc.documentElement

        val title = root.getElementsByTagName("dc:title")
            .item(0)?.textContent?.trim() ?: "Bilinmeyen Başlık"
        val author = root.getElementsByTagName("dc:creator")
            .item(0)?.textContent?.trim() ?: "Bilinmeyen Yazar"

        val manifest = buildManifest(root.getElementsByTagName("item"), opfDir)
        val spineIds = buildSpineOrder(root.getElementsByTagName("itemref"))

        val tocTitles = extractTocTitles(entries, manifest, opfDir)
        val coverImageBytes = findCoverImage(entries, manifest, root)

        val chapters = spineIds.mapIndexedNotNull { index, idRef ->
            val href = manifest[idRef] ?: return@mapIndexedNotNull null
            val fullPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
            val rawHtml = entries[fullPath]?.toString(Charsets.UTF_8) ?: return@mapIndexedNotNull null
            val chapterDir = fullPath.substringBeforeLast("/", "")
            EpubChapter(
                index = index,
                title = tocTitles[href] ?: tocTitles[idRef] ?: "Bölüm ${index + 1}",
                content = inlineImages(stripToBodyContent(rawHtml), chapterDir, entries),
                href = href,
            )
        }

        EpubBook(
            title = title,
            author = author,
            coverImageBytes = coverImageBytes,
            chapters = chapters,
            filePath = uri.toString(),
        )
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String {
        val containerBytes = entries["META-INF/container.xml"]
            ?: error("META-INF/container.xml not found — not a valid EPUB")
        val doc = parseXml(containerBytes.inputStream())
        val rootfiles = doc.getElementsByTagName("rootfile")
        for (i in 0 until rootfiles.length) {
            val el = rootfiles.item(i) as? Element ?: continue
            if (el.getAttribute("media-type") == "application/oebps-package+xml") {
                return el.getAttribute("full-path")
            }
        }
        error("OPF rootfile not found in container.xml")
    }

    private fun buildManifest(items: NodeList, opfDir: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            if (id.isNotEmpty() && href.isNotEmpty()) map[id] = href
        }
        return map
    }

    private fun buildSpineOrder(itemrefs: NodeList): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until itemrefs.length) {
            val el = itemrefs.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            if (idref.isNotEmpty()) list.add(idref)
        }
        return list
    }

    private fun extractTocTitles(
        entries: Map<String, ByteArray>,
        manifest: Map<String, String>,
        opfDir: String,
    ): Map<String, String> {
        val tocTitles = mutableMapOf<String, String>()
        val navHref = manifest.entries
            .firstOrNull {
                entries.containsKey(resolveEntry(opfDir, it.value)) &&
                        (it.value.endsWith("nav.xhtml") || it.value.endsWith("nav.html"))
            }?.value
        if (navHref != null) {
            entries[resolveEntry(opfDir, navHref)]?.let {
                parseTocFromNav(it.inputStream(), tocTitles)
                return tocTitles
            }
        }
        val ncxHref = manifest.entries.firstOrNull { it.value.endsWith(".ncx") }?.value
        if (ncxHref != null) {
            entries[resolveEntry(opfDir, ncxHref)]?.let {
                parseTocFromNcx(it.inputStream(), tocTitles)
            }
        }
        return tocTitles
    }

    private fun parseTocFromNav(stream: InputStream, into: MutableMap<String, String>) {
        try {
            val doc = parseXml(stream)
            val anchors = doc.getElementsByTagName("a")
            for (i in 0 until anchors.length) {
                val el = anchors.item(i) as? Element ?: continue
                val href = el.getAttribute("href").substringBefore("#")
                val text = el.textContent.trim()
                if (href.isNotEmpty() && text.isNotEmpty()) into[href] = text
            }
        } catch (_: Exception) {}
    }

    private fun parseTocFromNcx(stream: InputStream, into: MutableMap<String, String>) {
        try {
            val doc = parseXml(stream)
            val navPoints = doc.getElementsByTagName("navPoint")
            for (i in 0 until navPoints.length) {
                val el = navPoints.item(i) as? Element ?: continue
                val label = el.getElementsByTagName("text").item(0)?.textContent?.trim() ?: continue
                val src = (el.getElementsByTagName("content").item(0) as? Element)
                    ?.getAttribute("src")?.substringBefore("#") ?: continue
                into[src] = label
            }
        } catch (_: Exception) {}
    }

    private fun findCoverImage(
        entries: Map<String, ByteArray>,
        manifest: Map<String, String>,
        opfRoot: Element,
    ): ByteArray? {
        val metaTags = opfRoot.getElementsByTagName("meta")
        for (i in 0 until metaTags.length) {
            val el = metaTags.item(i) as? Element ?: continue
            if (el.getAttribute("name") == "cover") {
                val href = manifest[el.getAttribute("content")]
                if (href != null) return entries.entries.firstOrNull { it.key.endsWith(href) }?.value
            }
        }
        val items = opfRoot.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            if (el.getAttribute("media-type").startsWith("image/")) {
                val href = el.getAttribute("href")
                return entries.entries.firstOrNull { it.key.endsWith(href) }?.value
            }
        }
        return null
    }

    /**
     * Converts relative image src/href attributes in HTML to base64 data URIs
     * so they render correctly in a WebView loaded without a base URL.
     */
    private fun inlineImages(
        bodyHtml: String,
        chapterDir: String,
        entries: Map<String, ByteArray>,
    ): String {
        val imgRegex = Regex(
            """(<img\b[^>]*?\bsrc=)(["'])([^"']+)\2""",
            RegexOption.IGNORE_CASE,
        )
        val svgRegex = Regex(
            """(<image\b[^>]*?\b(?:xlink:href|href)=)(["'])([^"']+)\2""",
            RegexOption.IGNORE_CASE,
        )

        fun toDataUri(src: String): String? {
            if (src.startsWith("data:") || src.startsWith("http:") || src.startsWith("https:")) return null
            val decoded = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }
            val resolved = normalizePath(if (chapterDir.isEmpty()) decoded else "$chapterDir/$decoded")
            val bytes = entries[resolved]
                ?: entries.entries.firstOrNull { it.key.endsWith("/${resolved.substringAfterLast('/')}") }?.value
                ?: return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return "data:${imageMimeType(resolved)};base64,$b64"
        }

        var result = imgRegex.replace(bodyHtml) { m ->
            val dataUri = toDataUri(m.groupValues[3]) ?: return@replace m.value
            "${m.groupValues[1]}\"$dataUri\""
        }
        result = svgRegex.replace(result) { m ->
            val dataUri = toDataUri(m.groupValues[3]) ?: return@replace m.value
            "${m.groupValues[1]}\"$dataUri\""
        }
        return result
    }

    private fun normalizePath(path: String): String {
        val result = mutableListOf<String>()
        for (part in path.split("/")) {
            when {
                part == ".." && result.isNotEmpty() -> result.removeLast()
                part != "." && part.isNotEmpty() -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun imageMimeType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "image/jpeg"
    }

    fun saveCoverImage(coverBytes: ByteArray, uri: Uri): String? = try {
        val coversDir = File(context.filesDir, "covers")
        coversDir.mkdirs()
        val file = File(coversDir, "${uri.toString().hashCode()}.jpg")
        file.writeBytes(coverBytes)
        file.absolutePath
    } catch (_: Exception) { null }

    private fun stripToBodyContent(html: String): String {
        val bodyRegex = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL)
        return bodyRegex.find(html)?.groupValues?.get(1)?.trim() ?: html
    }

    private fun resolveEntry(opfDir: String, href: String): String =
        if (opfDir.isEmpty()) href else "$opfDir/$href"

    private fun parseXml(stream: InputStream) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }.newDocumentBuilder().parse(stream)
}
