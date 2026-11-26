package com.kryptow.epub.reader.bookreader.epub

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.kryptow.epub.reader.R
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

    /**
     * Kitabı tamamen ayrıştırır (okuyucu için). Bellek yetmezse [EpubTooLargeException]
     * fırlatır — `OutOfMemoryError` uygulamayı çökertmek yerine normal hata yoluna girer.
     */
    suspend fun parse(uri: Uri): EpubBook = withContext(Dispatchers.IO) {
        try {
            parseInternal(uri)
        } catch (oom: OutOfMemoryError) {
            throw EpubTooLargeException(context.getString(R.string.error_book_too_large), oom)
        }
    }

    /**
     * Kütüphaneye ekleme için hafif ayrıştırma: yalnızca başlık, yazar, bölüm sayısı ve kapak.
     * Bölüm HTML'leri ve görseller belleğe alınmaz — klasör içe aktarma onlarca kat hızlanır.
     */
    suspend fun parseMetadata(uri: Uri): EpubMetadata = withContext(Dispatchers.IO) {
        try {
            // 1. geçiş: yalnızca container.xml + OPF
            val meta = readEntries(uri) { name -> name == CONTAINER_PATH || name.endsWith(".opf", true) }
            val opfPath = findOpfPath(meta)
            val opfDir = opfPath.substringBeforeLast("/", "")
            val root = parseXml((meta[opfPath] ?: error("OPF file not found at $opfPath")).inputStream())
                .documentElement
            val title = root.getElementsByTagName("dc:title").item(0)?.textContent?.trim()
            val author = root.getElementsByTagName("dc:creator").item(0)?.textContent?.trim()
            val manifest = buildManifest(root.getElementsByTagName("item"), opfDir)
            val chapterCount = buildSpineOrder(root.getElementsByTagName("itemref")).size
            // 2. geçiş: yalnızca kapak görseli
            val coverHref = findCoverHref(manifest, root)
            val cover = coverHref?.let { href ->
                readEntries(uri) { name -> name.endsWith(href) }.values.firstOrNull()
            }
            EpubMetadata(
                title = title.orEmpty(),
                author = author.orEmpty(),
                chapterCount = chapterCount,
                coverImageBytes = cover,
            )
        } catch (oom: OutOfMemoryError) {
            throw EpubTooLargeException(context.getString(R.string.error_book_too_large), oom)
        }
    }

    /**
     * Zip girdilerini okur; [accept] false dönen girdiler belleğe alınmaz.
     * Zip bombası / aşırı büyük kitaplara karşı toplam boyut sınırı uygulanır.
     */
    private fun readEntries(uri: Uri, accept: (String) -> Boolean): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>()
        var total = 0L
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Cannot open URI: $uri")
        stream.use {
            ZipInputStream(it.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && accept(name)) {
                        val bytes = zip.readBytes()
                        if (bytes.size <= MAX_ENTRY_BYTES) {
                            total += bytes.size
                            if (total > MAX_TOTAL_BYTES) {
                                throw EpubTooLargeException(context.getString(R.string.error_book_too_large))
                            }
                            entries[name] = bytes
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    private fun parseInternal(uri: Uri): EpubBook {
        // Ses / video / font / script gibi okuyucunun kullanmadığı dosyalar belleğe alınmaz
        val entries = readEntries(uri) { name -> isUsefulEntry(name) }

        val opfPath = findOpfPath(entries)
        val opfDir = opfPath.substringBeforeLast("/", "")
        val opfBytes = entries[opfPath] ?: error("OPF file not found at $opfPath")

        val opfDoc = parseXml(opfBytes.inputStream())
        val root = opfDoc.documentElement

        val title = root.getElementsByTagName("dc:title")
            .item(0)?.textContent?.trim() ?: context.getString(R.string.error_unknown_title)
        val author = root.getElementsByTagName("dc:creator")
            .item(0)?.textContent?.trim() ?: context.getString(R.string.error_unknown_author)

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
                title = tocTitles[href] ?: tocTitles[idRef] ?: context.getString(R.string.chapter_format, index + 1),
                content = inlineImages(stripToBodyContent(rawHtml), chapterDir, entries),
                href = href,
            )
        }

        return EpubBook(
            title = title,
            author = author,
            coverImageBytes = coverImageBytes,
            chapters = chapters,
            filePath = uri.toString(),
        )
    }

    private fun isUsefulEntry(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return name == CONTAINER_PATH || ext in USEFUL_EXTENSIONS
    }

    private fun findCoverHref(manifest: Map<String, String>, opfRoot: Element): String? {
        val metaTags = opfRoot.getElementsByTagName("meta")
        for (i in 0 until metaTags.length) {
            val el = metaTags.item(i) as? Element ?: continue
            if (el.getAttribute("name") == "cover") {
                manifest[el.getAttribute("content")]?.let { return it }
            }
        }
        val items = opfRoot.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            if (el.getAttribute("properties").contains("cover-image")) return el.getAttribute("href")
        }
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            if (el.getAttribute("media-type").startsWith("image/")) return el.getAttribute("href")
        }
        return null
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String {
        val containerBytes = entries[CONTAINER_PATH]
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

        fun toDataUriUncached(src: String): String? {
            if (src.startsWith("data:") || src.startsWith("http:") || src.startsWith("https:")) return null
            val decoded = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }
            val resolved = normalizePath(if (chapterDir.isEmpty()) decoded else "$chapterDir/$decoded")
            val bytes = entries[resolved]
                ?: entries.entries.firstOrNull { it.key.endsWith("/${resolved.substringAfterLast('/')}") }?.value
                ?: return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return "data:${imageMimeType(resolved)};base64,$b64"
        }

        // Aynı görsel (ör. süsleme) birçok kez geçebilir — base64'ü bir kez üret
        val cache = HashMap<String, String?>()
        fun toDataUri(src: String): String? = cache.getOrPut(src) { toDataUriUncached(src) }

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
                // NOT: removeLast() Kotlin extension'ı Android 14+ Java işleviyle çakışıyor,
                // eski Android sürümlerinde crash. removeAt(lastIndex) güvenli alternatif.
                part == ".." && result.isNotEmpty() -> result.removeAt(result.lastIndex)
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
            isExpandEntityReferences = false
            // Harici DTD'leri ağdan indirmeye çalışma (takılma / XXE riski); destek yoksa yoksay
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newDocumentBuilder().parse(stream)

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        /** Tek girdi sınırı — bundan büyük dosya (ör. dev bir görsel) atlanır. */
        const val MAX_ENTRY_BYTES = 25 * 1024 * 1024
        /** Belleğe alınan toplam sınır — base64 gömme ile ~1.4x büyür. */
        const val MAX_TOTAL_BYTES = 150L * 1024 * 1024
        val USEFUL_EXTENSIONS = setOf(
            "opf", "ncx", "xml", "xhtml", "html", "htm",
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp",
        )
    }
}

/** Kütüphane listesi için hafif kitap bilgisi. */
data class EpubMetadata(
    val title: String,
    val author: String,
    val chapterCount: Int,
    val coverImageBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is EpubMetadata && title == other.title && author == other.author &&
            chapterCount == other.chapterCount
    override fun hashCode(): Int = (title.hashCode() * 31 + author.hashCode()) * 31 + chapterCount
}

/** Kitap cihaz belleğine sığmayacak kadar büyük. Mesaj kullanıcıya gösterilebilir. */
class EpubTooLargeException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
