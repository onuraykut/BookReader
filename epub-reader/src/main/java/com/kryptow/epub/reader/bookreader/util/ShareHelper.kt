package com.kryptow.epub.reader.bookreader.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kryptow.epub.reader.bookreader.domain.export.ExportFormat
import com.kryptow.epub.reader.bookreader.domain.export.ExportResult
import java.io.File

/**
 * Kütüphaneye özel FileProvider — tüketen uygulamanın kendi `androidx.core.content.FileProvider`
 * tanımıyla manifest birleştirmede çakışmasın diye alt sınıf olarak tanımlanır.
 */
class LumenFileProvider : FileProvider()

object ShareHelper {

    private fun authority(context: Context) = "${context.packageName}.lumen.fileprovider"

    /**
     * Dışa aktarma sonucunu paylaşım menüsüyle gönderir.
     * - Düz metin: `EXTRA_TEXT` (not uygulamalarına doğrudan yapıştırılır).
     * - Markdown / CSV: önbellekte geçici dosya + `EXTRA_STREAM`; Markdown ayrıca metin olarak da eklenir.
     */
    fun share(context: Context, result: ExportResult, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, result.fileName.substringBeforeLast('.'))
        }
        if (result.format == ExportFormat.TEXT) {
            intent.type = result.format.mimeType
            intent.putExtra(Intent.EXTRA_TEXT, result.content)
        } else {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, result.fileName)
            file.writeText(result.content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, authority(context), file)
            intent.type = result.format.mimeType
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newRawUri(result.fileName, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (result.format == ExportFormat.MARKDOWN) {
                intent.putExtra(Intent.EXTRA_TEXT, result.content)
            }
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
