package com.kryptow.epub.reader.bookreader.data.repository

import android.content.Context
import com.kryptow.epub.reader.bookreader.domain.model.DictionaryEntry
import com.kryptow.epub.reader.bookreader.domain.model.WordDefinition
import com.kryptow.epub.reader.bookreader.domain.repository.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sözlük + çeviri implementasyonu.
 *
 * - **Tanım**: Free Dictionary API (api.dictionaryapi.dev) — önce dosya önbelleği kontrol edilir.
 * - **Çeviri**: Google Translate ücretsiz endpoint — önce dosya önbelleği kontrol edilir.
 *
 * Önbellek konumu: [context.cacheDir]/dictionary/
 * Dosya adları:  def_{word}.json  |  tr_{word}_{lang}.txt
 */
class DictionaryRepositoryImpl(private val context: Context) : DictionaryRepository {

    private val cacheDir: File
        get() = File(context.cacheDir, "dictionary").also { it.mkdirs() }

    // ─── Tanım ────────────────────────────────────────────────────────────────

    override suspend fun getDefinition(word: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        val key = word.lowercase().filter { it.isLetterOrDigit() || it == '-' }
        if (key.isBlank()) return@withContext null

        // Önbellek kontrolü
        val cacheFile = File(cacheDir, "def_$key.json")
        if (cacheFile.exists()) {
            return@withContext parseCachedDefinition(word, cacheFile.readText())
        }

        // API çağrısı
        try {
            val encoded = URLEncoder.encode(key, "UTF-8")
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                cacheFile.writeText(json)
                parseDictionaryApiResponse(word, json)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDictionaryApiResponse(word: String, json: String): DictionaryEntry? = runCatching {
        val arr = JSONArray(json)
        val obj = arr.getJSONObject(0)
        val phonetic = obj.optString("phonetic").takeIf { it.isNotBlank() }
            ?: obj.optJSONArray("phonetics")?.let { phonetics ->
                (0 until phonetics.length())
                    .mapNotNull { phonetics.getJSONObject(it).optString("text").takeIf { t -> t.isNotBlank() } }
                    .firstOrNull()
            }
        val meanings = obj.optJSONArray("meanings") ?: return null
        val definitions = mutableListOf<WordDefinition>()
        for (i in 0 until meanings.length()) {
            val meaning = meanings.getJSONObject(i)
            val pos = meaning.optString("partOfSpeech", "")
            val defs = meaning.optJSONArray("definitions") ?: continue
            // Her kelime türünden en fazla 2 tanım al
            for (j in 0 until minOf(defs.length(), 2)) {
                val d = defs.getJSONObject(j)
                definitions.add(
                    WordDefinition(
                        partOfSpeech = pos,
                        definition = d.optString("definition"),
                        example = d.optString("example").takeIf { it.isNotBlank() },
                    )
                )
            }
            if (definitions.size >= 6) break
        }
        DictionaryEntry(word = word, phonetic = phonetic, definitions = definitions)
    }.getOrNull()

    private fun parseCachedDefinition(word: String, json: String): DictionaryEntry? =
        parseDictionaryApiResponse(word, json)

    // ─── Çeviri ───────────────────────────────────────────────────────────────

    override suspend fun translate(word: String, targetLang: String): String? = withContext(Dispatchers.IO) {
        val key = word.lowercase().trim()
        if (key.isBlank()) return@withContext null

        val safeKey = key.filter { it.isLetterOrDigit() || it == '-' || it == ' ' }
            .replace(' ', '_').take(80)
        val cacheFile = File(cacheDir, "tr_${safeKey}_$targetLang.txt")
        if (cacheFile.exists()) return@withContext cacheFile.readText()

        try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = URL(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val translation = parseTranslateResponse(json)
                if (translation != null) {
                    cacheFile.writeText(translation)
                }
                translation
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Google Translate yanıtını ayrıştırır.
     * Format: [[["çeviri","orijinal"],...],null,"kaynak_dil",...]
     */
    private fun parseTranslateResponse(json: String): String? = runCatching {
        val arr = JSONArray(json)
        val sentences = arr.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until sentences.length()) {
            val part = sentences.optJSONArray(i)
            if (part != null && part.length() > 0) {
                val text = part.optString(0)
                if (text.isNotBlank()) sb.append(text)
            }
        }
        sb.toString().trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}
