package com.kryptow.epub.reader.bookreader.ui.screen.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.domain.model.DayTheme
import com.kryptow.epub.reader.bookreader.domain.model.NightTheme
import com.kryptow.epub.reader.bookreader.domain.model.ReadingFontFamily
import com.kryptow.epub.reader.bookreader.domain.model.ScrollMode
import com.kryptow.epub.reader.bookreader.domain.model.ThemeMode
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Section(stringResource(R.string.settings_theme_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.updatePreferences(prefs.copy(themeMode = mode)) },
                            label = {
                                Text(stringResource(when (mode) {
                                    ThemeMode.DAY -> R.string.settings_theme_day
                                    ThemeMode.NIGHT -> R.string.settings_theme_night
                                    ThemeMode.AUTO -> R.string.settings_theme_auto
                                }))
                            }
                        )
                    }
                }
            }

            if (prefs.themeMode == ThemeMode.AUTO) {
                Spacer(Modifier.height(16.dp))
                Section(stringResource(
                    R.string.settings_night_start,
                    "%02d:00".format(prefs.autoNightStartHour),
                )) {
                    Slider(
                        value = prefs.autoNightStartHour.toFloat(),
                        onValueChange = { viewModel.updatePreferences(prefs.copy(autoNightStartHour = it.toInt())) },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Section(stringResource(
                    R.string.settings_night_end,
                    "%02d:00".format(prefs.autoNightEndHour),
                )) {
                    Slider(
                        value = prefs.autoNightEndHour.toFloat(),
                        onValueChange = { viewModel.updatePreferences(prefs.copy(autoNightEndHour = it.toInt())) },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Section(stringResource(R.string.settings_day_theme)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayTheme.entries.forEach { t ->
                        FilterChip(
                            selected = prefs.dayTheme == t,
                            onClick = { viewModel.updatePreferences(prefs.copy(dayTheme = t)) },
                            label = {
                                Text(stringResource(when (t) {
                                    DayTheme.WHITE -> R.string.theme_day_white
                                    DayTheme.CREAM -> R.string.theme_day_cream
                                    DayTheme.LIGHT_BLUE -> R.string.theme_day_light_blue
                                }))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Section(stringResource(R.string.settings_night_theme)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NightTheme.entries.forEach { t ->
                        FilterChip(
                            selected = prefs.nightTheme == t,
                            onClick = { viewModel.updatePreferences(prefs.copy(nightTheme = t)) },
                            label = {
                                Text(stringResource(when (t) {
                                    NightTheme.TRUE_BLACK -> R.string.theme_night_black
                                    NightTheme.DARK_GRAY -> R.string.theme_night_dark_gray
                                    NightTheme.SEPIA -> R.string.theme_night_sepia
                                }))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Section(stringResource(R.string.settings_font_family)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReadingFontFamily.entries.forEach { f ->
                        FilterChip(
                            selected = prefs.fontFamily == f,
                            onClick = { viewModel.updatePreferences(prefs.copy(fontFamily = f)) },
                            label = { Text(stringResource(f.labelRes())) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Section(stringResource(R.string.settings_font_size, prefs.fontSize.toInt())) {
                Slider(
                    value = prefs.fontSize,
                    onValueChange = { viewModel.updatePreferences(prefs.copy(fontSize = it)) },
                    valueRange = 12f..32f,
                    steps = 19,
                )
            }

            Spacer(Modifier.height(12.dp))

            Section(stringResource(R.string.settings_line_height, "%.1fx".format(prefs.lineHeight))) {
                Slider(
                    value = prefs.lineHeight,
                    onValueChange = { viewModel.updatePreferences(prefs.copy(lineHeight = it)) },
                    valueRange = 1.0f..2.0f,
                    steps = 9,
                )
            }

            Spacer(Modifier.height(12.dp))

            Section(stringResource(R.string.settings_margin, prefs.marginHorizontal)) {
                Slider(
                    value = prefs.marginHorizontal.toFloat(),
                    onValueChange = { viewModel.updatePreferences(prefs.copy(marginHorizontal = it.toInt())) },
                    valueRange = 8f..64f,
                    steps = 13,
                )
            }

            Spacer(Modifier.height(20.dp))

            Section(stringResource(R.string.settings_scroll_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScrollMode.entries.forEach { m ->
                        FilterChip(
                            selected = prefs.scrollMode == m,
                            onClick = { viewModel.updatePreferences(prefs.copy(scrollMode = m)) },
                            label = {
                                Text(stringResource(when (m) {
                                    ScrollMode.VERTICAL -> R.string.scroll_mode_vertical
                                    ScrollMode.HORIZONTAL_PAGE -> R.string.scroll_mode_paginated
                                }))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_keep_screen_on),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = prefs.keepScreenOn,
                    onCheckedChange = { viewModel.updatePreferences(prefs.copy(keepScreenOn = it)) },
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    content()
}

private fun ReadingFontFamily.labelRes(): Int = when (this) {
    ReadingFontFamily.SERIF -> R.string.font_serif
    ReadingFontFamily.SANS_SERIF -> R.string.font_sans_serif
    ReadingFontFamily.MONOSPACE -> R.string.font_monospace
    ReadingFontFamily.CURSIVE -> R.string.font_cursive
    ReadingFontFamily.SYSTEM -> R.string.font_system
}
