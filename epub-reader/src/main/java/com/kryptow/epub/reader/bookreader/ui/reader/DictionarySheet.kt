package com.kryptow.epub.reader.bookreader.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptow.epub.reader.bookreader.domain.model.WordDefinition
import com.kryptow.epub.reader.bookreader.ui.screen.reader.DictionaryUiState
import java.net.URLEncoder

/** Desteklenen hedef diller (kod → görünen ad) */
val SUPPORTED_LANGS: List<Pair<String, String>> = listOf(
    "tr" to "Türkçe",
    "en" to "English",
    "de" to "Deutsch",
    "fr" to "Français",
    "es" to "Español",
    "it" to "Italiano",
    "ru" to "Русский",
    "ar" to "العربية",
    "ja" to "日本語",
    "zh" to "中文",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    state: DictionaryUiState,
    sheetState: SheetState,
    onLangSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ─── Kelime + fonetik ──────────────────────────────────────────────
            Column {
                Text(
                    text = state.word,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (state.phonetic != null) {
                    Text(
                        text = state.phonetic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            HorizontalDivider()

            // ─── Çeviri bölümü ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Çeviri",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Dil seçici
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SUPPORTED_LANGS.forEach { (code, name) ->
                        val isSelected = state.targetLang == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    RoundedCornerShape(20.dp),
                                )
                                .clickable { onLangSelected(code) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Çeviri sonucu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                ) {
                    when {
                        state.isLoadingTranslation -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).align(Alignment.Center),
                            strokeWidth = 2.dp,
                        )
                        state.translation != null -> Text(
                            text = state.translation,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        else -> Text(
                            text = "Çeviri bulunamadı",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                // Google Translate'te aç butonu
                OutlinedButton(
                    onClick = {
                        val encoded = try {
                            URLEncoder.encode(state.word, "UTF-8")
                        } catch (e: Exception) {
                            state.word
                        }
                        val uri = Uri.parse(
                            "https://translate.google.com/?sl=auto&tl=${state.targetLang}&text=$encoded&op=translate"
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Google Translate'te Aç", fontSize = 13.sp)
                }
            }

            HorizontalDivider()

            // ─── Sözlük tanımları ─────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Sözlük (İngilizce)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                when {
                    state.isLoadingDefinition -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Yükleniyor…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.definitions.isEmpty() -> Text(
                        "Tanım bulunamadı",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                    else -> {
                        // Kelime türüne göre grupla
                        state.definitions
                            .groupBy { it.partOfSpeech }
                            .forEach { (pos, defs) ->
                                DefinitionGroup(partOfSpeech = pos, definitions = defs)
                            }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DefinitionGroup(
    partOfSpeech: String,
    definitions: List<WordDefinition>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = partOfSpeech,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
        )
        definitions.forEachIndexed { index, def ->
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = def.definition,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (def.example != null) {
                    Text(
                        text = "\"${def.example}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                }
            }
        }
    }
}
