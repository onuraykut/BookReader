package com.kryptow.epub.reader.bookreader.epub

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * SAF (Storage Access Framework) ile seçilen bir klasörü ve alt klasörlerini
 * özyinelemeli olarak tarar, .epub uzantılı tüm dosyaların Uri'lerini toplar.
 */
class FolderScanner(private val context: Context) {

    /** @return Klasör ağacı içindeki tüm EPUB dosyalarının Uri listesi. */
    fun scanForEpubs(treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<Uri>()
        collect(root, results)
        return results
    }

    private fun collect(dir: DocumentFile, out: MutableList<Uri>) {
        if (!dir.isDirectory) return
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> collect(child, out)
                child.isFile && child.isEpub() -> out.add(child.uri)
            }
        }
    }

    private fun DocumentFile.isEpub(): Boolean {
        val name = name?.lowercase() ?: return false
        if (name.endsWith(".epub")) return true
        val mime = type?.lowercase()
        return mime == "application/epub+zip"
    }
}
