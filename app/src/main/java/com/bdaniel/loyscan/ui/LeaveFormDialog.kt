package com.bdaniel.loyscan.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.bdaniel.loyscan.R

const val LEAVE_FORM_DIALOG_TAG = "leaveFormDialog"

/**
 * Asks before Back leaves a form with changes (#209): **Discard** and **Save** ([saveText], the form's own Save button's
 * text), or **Discard** and **Keep editing** when the form can't be saved ([saveText] `null`). A tap outside it or Back
 * keeps editing.
 */
@Composable
fun LeaveFormDialog(
    title: String,
    saveText: String?,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = Modifier.testTag(LEAVE_FORM_DIALOG_TAG),
        title = { Text(title) },
        confirmButton = {
            if (saveText != null) {
                TextButton(onClick = onSave) { Text(saveText) }
            } else {
                TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.keep_editing)) }
            }
        },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
    )
}
