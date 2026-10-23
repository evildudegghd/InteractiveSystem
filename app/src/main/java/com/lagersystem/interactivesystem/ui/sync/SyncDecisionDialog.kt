package com.lagersystem.interactivesystem.ui.sync

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.lagersystem.interactivesystem.data.model.SyncData

@Composable
fun SyncDecisionDialog(
    onMerge: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Synchronisations-Konflikt") },
        text = { Text("Es wurden neuere Daten in der Cloud gefunden. Möchten Sie die Änderungen zusammenführen (Merge) oder die lokalen Daten durch die Cloud-Version ersetzen (Download)?") },
        confirmButton = {
            Button(onClick = onMerge) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDownload) {
                Text("Download")
            }
        }
    )
}
