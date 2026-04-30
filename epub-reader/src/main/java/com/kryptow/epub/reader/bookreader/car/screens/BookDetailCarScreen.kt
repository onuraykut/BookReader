package com.kryptow.epub.reader.bookreader.car.screens

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.model.EpubChapter
import com.kryptow.epub.reader.bookreader.service.TtsPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BookDetailCarScreen(
    carContext: CarContext,
    private val book: Book,
) : Screen(carContext), KoinComponent {

    private val epubParser: EpubParser by inject()
    private var chapters: List<EpubChapter> = emptyList()
    private var loading = true

    private val screenJob = SupervisorJob()
    private val screenScope = CoroutineScope(Dispatchers.IO + screenJob)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
        screenScope.launch {
            chapters = try {
                epubParser.parse(Uri.parse(book.filePath)).chapters
            } catch (e: Exception) {
                emptyList()
            }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (!loading) {
            if (chapters.isEmpty()) {
                listBuilder.setNoItemsMessage("Bölümler yüklenemedi.")
            } else {
                chapters.forEachIndexed { index, chapter ->
                    val title = chapter.title.ifBlank { "Bölüm ${index + 1}" }
                    val isCurrent = index == book.currentChapter
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(if (isCurrent) "▶ $title" else title)
                            .addText("Bölüm ${index + 1} / ${chapters.size}")
                            .setOnClickListener {
                                startPlayback(index)
                            }
                            .build()
                    )
                }
            }
        }

        return ListTemplate.Builder()
            .setTitle(book.title)
            .setHeaderAction(Action.BACK)
            .apply {
                if (loading) setLoading(true)
                else setSingleList(listBuilder.build())
            }
            .build()
    }

    private fun startPlayback(chapterIndex: Int) {
        val intent = Intent(carContext, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_PLAY_BOOK_CHAPTER
            putExtra(TtsPlaybackService.EXTRA_BOOK_ID, book.id)
            putExtra(TtsPlaybackService.EXTRA_CHAPTER_INDEX, chapterIndex)
        }
        carContext.startForegroundService(intent)
        screenManager.push(PlayerCarScreen(carContext))
    }
}
