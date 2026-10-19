package com.bdaniel.loyscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shows what the scanner read, until the add-card flow (#6) turns a scan into a card. */
@Composable
fun ScanResultDialog(result: ScanResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Scanned") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (result.gs1) "${result.format.displayName} · GS1" else result.format.displayName, style = MaterialTheme.typography.labelLarge)
                Text(result.content, style = MaterialTheme.typography.bodyLarge)
            }
        },
    )
}
