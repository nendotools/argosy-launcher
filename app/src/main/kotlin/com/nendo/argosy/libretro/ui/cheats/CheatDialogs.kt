package com.nendo.argosy.libretro.ui.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun SearchDialog(
    currentQuery: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    var textValue by remember {
        mutableStateOf(TextFieldValue(currentQuery, TextRange(0, currentQuery.length)))
    }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Search Cheats",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = { Text("Enter cheat name...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(textValue.text) }
                )
            )
        },
        confirmButton = {
            Button(onClick = { onSearch(textValue.text) }) {
                Text("Search")
            }
        },
        dismissButton = {
            Row {
                if (currentQuery.isNotEmpty()) {
                    TextButton(onClick = { onSearch("") }) {
                        Text("Clear")
                    }
                    Spacer(Modifier.width(Dimens.spacingXs))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        shape = RoundedCornerShape(Dimens.radiusXl)
    )
}

@Composable
fun CheatCreateDialog(
    address: Int,
    currentValue: Int,
    onDismiss: () -> Unit,
    onCreate: (name: String, value: Int) -> Unit
) {
    val defaultName = "Custom Cheat @ 0x${address.toString(16).uppercase().padStart(6, '0')}"
    var nameValue by remember {
        mutableStateOf(TextFieldValue(defaultName, TextRange(0, defaultName.length)))
    }
    var valueText by remember {
        mutableStateOf(currentValue.toString())
    }

    val parsedValue = valueText.toIntOrNull()
    val isValueValid = parsedValue != null && parsedValue in 0..255
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Cheat",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Address: 0x${address.toString(16).uppercase().padStart(6, '0')}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(Dimens.spacingMd))

                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it },
                    label = { Text("Cheat name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(Modifier.height(Dimens.spacingSm))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value (0-255)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (nameValue.text.isNotBlank() && isValueValid) {
                                onCreate(nameValue.text.trim(), parsedValue!!)
                            }
                        }
                    ),
                    isError = valueText.isNotEmpty() && !isValueValid,
                    supportingText = if (valueText.isNotEmpty() && !isValueValid) {
                        { Text("Enter a value between 0 and 255") }
                    } else null
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(nameValue.text.trim(), parsedValue!!) },
                enabled = nameValue.text.isNotBlank() && isValueValid,
                modifier = Modifier.focusRequester(buttonFocusRequester)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(Dimens.radiusXl)
    )
}

@Composable
fun CheatEditDialog(
    cheatId: Long,
    currentName: String,
    currentCode: String,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String) -> Unit,
    onDelete: () -> Unit
) {
    var nameValue by remember {
        mutableStateOf(TextFieldValue(currentName, TextRange(currentName.length)))
    }

    val addressPart = currentCode.substringBefore(':')
    val valuePart = currentCode.substringAfter(':', "")
    val currentValueInt = valuePart.toIntOrNull(16) ?: 0

    var valueText by remember { mutableStateOf(currentValueInt.toString()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val parsedValue = valueText.toIntOrNull()
    val isValueValid = parsedValue != null && parsedValue in 0..255
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete Cheat",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"$currentName\"?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(Dimens.radiusXl)
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Cheat",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Address: 0x$addressPart",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(Dimens.spacingMd))

                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it },
                    label = { Text("Cheat name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(Modifier.height(Dimens.spacingSm))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value (0-255)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (nameValue.text.isNotBlank() && isValueValid) {
                                val newCode = "$addressPart:${parsedValue!!.toString(16).uppercase().padStart(2, '0')}"
                                onSave(nameValue.text.trim(), newCode)
                            }
                        }
                    ),
                    isError = valueText.isNotEmpty() && !isValueValid,
                    supportingText = if (valueText.isNotEmpty() && !isValueValid) {
                        { Text("Enter a value between 0 and 255") }
                    } else null
                )

                Spacer(Modifier.height(Dimens.spacingMd))

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Cheat")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newCode = "$addressPart:${parsedValue!!.toString(16).uppercase().padStart(2, '0')}"
                    onSave(nameValue.text.trim(), newCode)
                },
                enabled = nameValue.text.isNotBlank() && isValueValid,
                modifier = Modifier.focusRequester(buttonFocusRequester)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(Dimens.radiusXl)
    )
}
