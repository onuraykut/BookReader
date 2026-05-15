package com.kryptow.epub.reader.bookreader.car.screens

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.car.TtsPlaybackState
import com.kryptow.epub.reader.bookreader.service.TtsPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlayerCarScreen(carContext: CarContext) : Screen(carContext) {

    private var state = TtsPlaybackState.current.value

    private val screenJob = SupervisorJob()
    private val screenScope = CoroutineScope(Dispatchers.Main + screenJob)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
        screenScope.launch {
            TtsPlaybackState.current.collect { newState ->
                state = newState
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val infoRow = Row.Builder()
            .setTitle(state.bookTitle.ifEmpty { "Lumen" })
            .addText(state.chapterTitle.ifEmpty { "—" })
            .apply {
                if (state.totalChapters > 0) {
                    addText(carContext.getString(R.string.reader_chapter_format, state.chapterIndex + 1, state.totalChapters))
                }
            }
            .build()

        // Araç kısıtlamaları nedeniyle Pane'e en fazla 2 Action eklenebilir.
        // Oynatma durumuna göre hangi iki düğmenin gösterileceği belirlenir.
        val primaryAction = Action.Builder()
            .setTitle(carContext.getString(if (state.isPlaying) R.string.tts_stop else R.string.tts_play))
            .setOnClickListener {
                sendToService(
                    if (state.isPlaying) TtsPlaybackService.ACTION_PAUSE
                    else TtsPlaybackService.ACTION_PLAY
                )
            }
            .build()

        val skipAction = Action.Builder()
            .setTitle(carContext.getString(if (state.isPlaying) R.string.reader_previous_chapter else R.string.reader_next_chapter))
            .setOnClickListener {
                sendToService(
                    if (state.isPlaying) TtsPlaybackService.ACTION_SKIP_PREV
                    else TtsPlaybackService.ACTION_SKIP_NEXT
                )
            }
            .build()

        val pane = Pane.Builder()
            .addRow(infoRow)
            .addAction(primaryAction)
            .addAction(skipAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.car_now_playing))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun sendToService(action: String) {
        val intent = Intent(carContext, TtsPlaybackService::class.java).apply {
            this.action = action
        }
        carContext.startService(intent)
    }
}
