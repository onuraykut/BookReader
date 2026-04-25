package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Kullanıcının bölüm numarası girerek belirli bir bölüme atlamasını sağlayan dialog.
 *
 * @param currentChapter Geçerli bölüm (1-tabanlı, text field'da gösterilir)
 * @param totalChapters Toplam bölüm sayısı (aralık doğrulama için)
 * @param onConfirm Geçerli numara onaylandığında çağrılır (0-tabanlı indeks)
 * @param onDismiss Dialog kapatıldığında
 */
@Composable
fun GoToChapterDialog(
    currentChapter: Int,
    totalChapters: Int,
    onConfirm: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var textValue by remember {
        val s = currentChapter.toString()
        mutableStateOf(TextFieldValue(text = s, selection = TextRange(0, s.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val parsed = textValue.text.toIntOrNull()
    val isValid = parsed != null && parsed in 1..totalChapters
    val errorMessage = when {
        textValue.text.isBlank() -> null
        parsed == null -> "Geçerli bir sayı girin"
        parsed < 1 -> "En az 1 olmalı"
        parsed > totalChapters -> "En fazla $totalChapters olabilir"
        else -> null
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bölüme Git") },
        text = {
            Column {
                Text(
                    text = "1 ile $totalChapters arasında bir bölüm numarası girin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Bölüm numarası") },
                    placeholder = { Text("örn. 5") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { if (isValid) onConfirm(parsed!! - 1) }
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(parsed!! - 1) },
                enabled = isValid,
            ) {
                Text("Git")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}
