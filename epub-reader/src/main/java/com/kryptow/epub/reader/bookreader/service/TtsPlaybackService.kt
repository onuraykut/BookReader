package com.kryptow.epub.reader.bookreader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.notification.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.kryptow.epub.reader.bookreader.car.TtsPlaybackState
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.model.EpubBook
import com.kryptow.epub.reader.bookreader.ui.reader.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class TtsPlaybackService : MediaBrowserServiceCompat() {

    companion object {
        const val MEDIA_ROOT_ID = "root"
        const val BOOK_ID_PREFIX = "book_"
        const val CHAPTER_ID_SEPARATOR = "/chapter_"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tts_playback_ch"

        const val ACTION_PLAY_BOOK_CHAPTER = "com.kryptow.epub.reader.ACTION_PLAY_BOOK_CHAPTER"
        const val ACTION_PLAY = "com.kryptow.epub.reader.ACTION_PLAY"
        const val ACTION_PAUSE = "com.kryptow.epub.reader.ACTION_PAUSE"
        const val ACTION_SKIP_NEXT = "com.kryptow.epub.reader.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.kryptow.epub.reader.ACTION_SKIP_PREV"

        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_CHAPTER_INDEX = "extra_chapter_index"
    }

    private val bookRepository: BookRepository by inject()
    private val epubParser: EpubParser by inject()

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var ttsManager: TtsManager

    private var currentBookId: Long = -1L
    private var currentChapterIndex: Int = 0
    private var currentEpub: EpubBook? = null

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // ── Yaşam döngüsü ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(applicationContext)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "BookReaderTts").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                        MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
            )
            setCallback(SessionCallback())
            setPlaybackState(buildIdleState())
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        ttsManager.onChapterComplete = { advanceChapter(+1) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            ACTION_PLAY_BOOK_CHAPTER -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
                val chapter = intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0)
                if (bookId != -1L) scope.launch { loadAndPlay(bookId, chapter) }
            }
            ACTION_PLAY -> mediaSession.controller.transportControls.play()
            ACTION_PAUSE -> mediaSession.controller.transportControls.pause()
            ACTION_SKIP_NEXT -> mediaSession.controller.transportControls.skipToNext()
            ACTION_SKIP_PREV -> mediaSession.controller.transportControls.skipToPrevious()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job.cancel()
        ttsManager.destroy()
        mediaSession.release()
        super.onDestroy()
    }

    // ── MediaBrowserServiceCompat ──────────────────────────────────────────────

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(MEDIA_ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.detachResults()
        scope.launch {
            val items: MutableList<MediaBrowserCompat.MediaItem> = when {
                parentId == MEDIA_ROOT_ID -> loadBookItems()
                parentId.startsWith(BOOK_ID_PREFIX) -> {
                    val bookId = parentId.removePrefix(BOOK_ID_PREFIX).toLongOrNull()
                    if (bookId != null) loadChapterItems(bookId) else mutableListOf()
                }
                else -> mutableListOf()
            }
            result.sendResult(items)
        }
    }

    // ── Medya ağacı ───────────────────────────────────────────────────────────

    private suspend fun loadBookItems(): MutableList<MediaBrowserCompat.MediaItem> =
        bookRepository.getAllBooks().first().map { book ->
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("$BOOK_ID_PREFIX${book.id}")
                    .setTitle(book.title)
                    .setSubtitle(book.author)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        }.toMutableList()

    private suspend fun loadChapterItems(bookId: Long): MutableList<MediaBrowserCompat.MediaItem> {
        val book = bookRepository.getBookById(bookId) ?: return mutableListOf()
        return try {
            epubParser.parse(Uri.parse(book.filePath)).chapters.mapIndexed { index, chapter ->
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("$BOOK_ID_PREFIX${bookId}${CHAPTER_ID_SEPARATOR}${index}")
                        .setTitle(chapter.title.ifBlank { "Bölüm ${index + 1}" })
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    // ── Oynatma ───────────────────────────────────────────────────────────────

    private suspend fun loadAndPlay(bookId: Long, chapterIndex: Int) {
        val book = bookRepository.getBookById(bookId) ?: return
        currentBookId = bookId
        currentChapterIndex = chapterIndex

        val epub = try {
            epubParser.parse(Uri.parse(book.filePath))
        } catch (e: Exception) {
            return
        }
        currentEpub = epub

        val chapter = epub.chapters.getOrNull(chapterIndex) ?: return
        ttsManager.setContent(chapter.content)

        val chapterTitle = chapter.title.ifBlank { "Bölüm ${chapterIndex + 1}" }
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, book.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book.author)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, chapterTitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (chapterIndex + 1).toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, epub.chapters.size.toLong())
                .build()
        )

        TtsPlaybackState.update(
            TtsPlaybackState.Info(
                bookId = bookId,
                bookTitle = book.title,
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                totalChapters = epub.chapters.size,
                isPlaying = true,
            )
        )

        startForeground(NOTIFICATION_ID, buildNotification())
        ttsManager.play()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    private fun advanceChapter(delta: Int) {
        val epub = currentEpub ?: return
        val next = currentChapterIndex + delta
        if (next < 0 || next >= epub.chapters.size) {
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
            TtsPlaybackState.update(TtsPlaybackState.current.value.copy(isPlaying = false))
            return
        }
        scope.launch { loadAndPlay(currentBookId, next) }
    }

    private fun setPlaybackState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(supportedActions())
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0f)
                .build()
        )
    }

    private fun buildIdleState() = PlaybackStateCompat.Builder()
        .setActions(supportedActions())
        .setState(PlaybackStateCompat.STATE_STOPPED,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
        .build()

    private fun supportedActions() =
        PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

    // ── Bildirim ──────────────────────────────────────────────────────────────

    private fun buildNotification(): android.app.Notification {
        val isPlaying = ttsManager.playState.value ==
                com.kryptow.epub.reader.bookreader.ui.reader.TtsPlayState.PLAYING
        val state = TtsPlaybackState.current.value

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.bookTitle.ifEmpty { "BookReader" })
            .setContentText(state.chapterTitle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous, "Önceki",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (isPlaying) "Durdur" else "Oynat",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this, PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
            .addAction(
                android.R.drawable.ic_media_next, "Sonraki",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "TTS Oynatma", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Sesli kitap okuma bildirimi" }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    // ── MediaSession Callback ──────────────────────────────────────────────────

    private inner class SessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            ttsManager.resume()
            TtsPlaybackState.update(TtsPlaybackState.current.value.copy(isPlaying = true))
            setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        override fun onPause() {
            ttsManager.pause()
            TtsPlaybackState.update(TtsPlaybackState.current.value.copy(isPlaying = false))
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        }

        override fun onStop() {
            ttsManager.stop()
            TtsPlaybackState.update(TtsPlaybackState.current.value.copy(isPlaying = false))
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        override fun onSkipToNext() = advanceChapter(+1)
        override fun onSkipToPrevious() = advanceChapter(-1)

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (mediaId == null) return
            val (bookId, chapterIndex) = parseMediaId(mediaId) ?: return
            scope.launch { loadAndPlay(bookId, chapterIndex) }
        }
    }

    private fun parseMediaId(mediaId: String): Pair<Long, Int>? {
        if (!mediaId.startsWith(BOOK_ID_PREFIX)) return null
        val withoutPrefix = mediaId.removePrefix(BOOK_ID_PREFIX)
        return if ("/chapter_" in withoutPrefix) {
            val parts = withoutPrefix.split("/chapter_")
            val bookId = parts[0].toLongOrNull() ?: return null
            val chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
            Pair(bookId, chapterIndex)
        } else {
            val bookId = withoutPrefix.toLongOrNull() ?: return null
            Pair(bookId, 0)
        }
    }
}
