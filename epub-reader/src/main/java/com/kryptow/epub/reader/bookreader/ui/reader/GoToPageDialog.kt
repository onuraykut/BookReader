package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Düz sayfa numarasına gitme dialogu (HORIZONTAL_PAGE modu için).
 * Pagination indeksleme tamamlandıktan sonra aktif olur.
 */
@Composable
fun GoToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onConfirm: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val page = input.toIntOrNull()
    val isValid = page != null && page in 1..totalPages

    fun confirm() {
        if (isValid) onConfirm(page!! - 1) // 0-tabanlı
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sayfaya Git") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { c -> c.isDigit() }.take(6) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Sayfa numarası (1 – $totalPages)") },
                placeholder = { Text("$currentPage") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { confirm() }),
                singleLine = true,
                isError = input.isNotBlank() && !isValid,
                supportingText = if (input.isNotBlank() && !isValid) {
                    { Text("1 ile $totalPages arasında bir sayı girin") }
                } else null,
            )
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = isValid) { Text("Git") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
