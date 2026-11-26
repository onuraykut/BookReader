package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.ui.screen.reader.SleepTimerOption
import com.kryptow.epub.reader.bookreader.ui.screen.reader.TtsUiState

private val TTS_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val TTS_LANGS = listOf(
    "tr" to "TR", "en" to "EN", "de" to "DE",
    "fr" to "FR", "es" to "ES", "it" to "IT",
    "ru" to "RU", "ar" to "AR", "ja" to "JA", "zh" to "ZH",
)

/**
 * Ekranın alt kısmında görünen TTS (sesle okuma) kontrol çubuğu.
 *
 * - Mevcut cümleyi ve ilerlemeyi gösterir.
 * - Oynat / Duraklat / Önceki / Sonraki / Durdur butonları içerir.
 * - Hız ve dil seçicileri DropdownMenu ile sunulur.
 */
@Composable
fun TtsControlBar(
    state: TtsUiState,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLangChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSleepTimerChange: (SleepTimerOption) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ─── Mevcut cümle ──────────────────────────────────────────────
            if (state.currentSentence.isNotBlank()) {
                Text(
                    text = "\"${state.currentSentence}\"",
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }

            // ─── İlerleme ─────────────────────────────────────────────────
            if (state.totalSentences > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = stringResource(R.string.tts_sentence_format, state.currentSentenceIndex + 1, state.totalSentences),
                        color = textColor.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        if (state.totalSentences > 0)
                            (state.currentSentenceIndex + 1f) / state.totalSentences
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.18f),
                )
                Spacer(Modifier.height(6.dp))
            }

            // ─── Kontrol satırı ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Hız + uyku zamanlayıcısı
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TtsSpeedPicker(
                        current = state.speed,
                        accentColor = accentColor,
                        onSelect = onSpeedChange,
                    )
                    TtsSleepTimerPicker(
                        state = state,
                        textColor = textColor,
                        accentColor = accentColor,
                        onSelect = onSleepTimerChange,
                    )
                }

                // Oynatma butonları
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.tts_previous),
                            tint = textColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(
                        onClick = if (state.playState == TtsPlayState.PLAYING) onPause else onPlay,
                        modifier = Modifier.size(50.dp),
                    ) {
                        Icon(
                            imageVector = if (state.playState == TtsPlayState.PLAYING)
                                Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (state.playState == TtsPlayState.PLAYING) R.string.tts_pause
                                else R.string.tts_play
                            ),
                            tint = accentColor,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.tts_next),
                            tint = textColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                // Dil + Durdur
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TtsLangPicker(
                        current = state.language,
                        accentColor = accentColor,
                        onSelect = onLangChange,
                    )
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.tts_stop),
                            tint = textColor.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── Yardımcı composable'lar ──────────────────────────────────────────────────

@Composable
private fun TtsSpeedPicker(current: Float, accentColor: Color, onSelect: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(text = "${current.toSpeedLabel()}x", color = accentColor, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TTS_SPEEDS.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.toSpeedLabel()}x") },
                    onClick = { onSelect(s); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TtsSleepTimerPicker(
    state: TtsUiState,
    textColor: Color,
    accentColor: Color,
    onSelect: (SleepTimerOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = state.sleepTimer != SleepTimerOption.OFF
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Default.Bedtime,
                contentDescription = stringResource(R.string.tts_sleep_timer),
                tint = if (active) accentColor else textColor.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
            val remaining = state.sleepTimerRemainingSeconds
            val label = when {
                remaining != null -> "%d:%02d".format(remaining / 60, remaining % 60)
                state.sleepTimer == SleepTimerOption.END_OF_CHAPTER -> "§"
                else -> null
            }
            if (label != null) {
                Spacer(Modifier.size(4.dp))
                Text(text = label, color = accentColor, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SleepTimerOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                SleepTimerOption.OFF -> stringResource(R.string.off)
                                SleepTimerOption.END_OF_CHAPTER ->
                                    stringResource(R.string.tts_sleep_end_of_chapter)
                                else -> stringResource(R.string.minutes_short, option.minutes)
                            }
                        )
                    },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TtsLangPicker(current: String, accentColor: Color, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(text = current.uppercase(), color = accentColor, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TTS_LANGS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(code); expanded = false },
                )
            }
        }
    }
}

private fun Float.toSpeedLabel(): String = when (this) {
    0.5f  -> "0.5"
    0.75f -> "0.75"
    1.25f -> "1.25"
    1.5f  -> "1.5"
    2.0f  -> "2.0"
    else  -> "1.0"
}
