package dev.junjo.anchor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * One sheet for changing a goal or a note: the text, Delete, the one or two other things you might
 * want to do with it, and Done. Closing it any way keeps the edit, so there is no Save to forget.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditSheet(
    initial: String,
    multiLine: Boolean,
    otherLabel: String,
    onOther: (text: String) -> Unit,
    onDelete: () -> Unit,
    onClose: (text: String) -> Unit,
    extraLabel: String? = null,
    onExtra: ((text: String) -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = { onClose(text) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = !multiLine,
                minLines = if (multiLine) 3 else 1,
                maxLines = if (multiLine) 14 else 1,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (multiLine) ImeAction.Default else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onClose(text) }),
            )
            if (extraLabel != null && onExtra != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    FilledTonalButton(onClick = { onExtra(text) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                        Text(extraLabel)
                    }
                    FilledTonalButton(onClick = { onOther(text) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                        Text(otherLabel)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onClose(text) }, shapes = ButtonDefaults.shapes()) { Text("Done") }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { onOther(text) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                        Text(otherLabel)
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { onClose(text) }, shapes = ButtonDefaults.shapes()) { Text("Done") }
                }
            }
        }
    }
}
