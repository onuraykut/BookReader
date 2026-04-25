package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor

/**
 * Seçili metin için renk seçimi + not girişini tek dialogda sunar.
 * "Not Ekle" action mode öğesinden tetiklenir.
 */
@Composable
fun HighlightNoteDialog(
    selectedText: String,
    onConfirm: (color: HighlightColor, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedColor by remember { mutableStateOf(HighlightColor.YELLOW) }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Seçili metin önizleme
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "\"$selectedText\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                // Renk seçici
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Vurgu rengi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HighlightColor.entries.forEach { color ->
                            val hexColor = Color(android.graphics.Color.parseColor(color.hex))
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(hexColor)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(
                                                2.5.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape,
                                            )
                                        else Modifier,
                                    )
                                    .clickable { selectedColor = color },
                            )
                        }
                    }
                }

                // Not alanı
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Not (isteğe bağlı)") },
                    placeholder = { Text("Notunuzu yazın...") },
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedColor, noteText.trim().takeIf { it.isNotBlank() })
            }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}
