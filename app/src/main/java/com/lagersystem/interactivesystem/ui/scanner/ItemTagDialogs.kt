package com.lagersystem.interactivesystem.ui.scanner

import android.app.Activity
import android.graphics.Bitmap
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ItemTagActionDialog(
    inventoryNumber: String,
    onShowQr: (String) -> Unit,
    onWriteNfc: (String) -> Unit,
    onDismiss: () -> Unit,
    nfcDisabled: Boolean = false
) {
    var additionalInfo by remember(inventoryNumber) { mutableStateOf("") }
    val payload = remember(inventoryNumber, additionalInfo) {
        com.lagersystem.interactivesystem.scanner.InventoryTagPayload.encode(inventoryNumber, additionalInfo)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Code für $inventoryNumber erstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    label = { Text("Zusatzinformation (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Die Zusatzinformation wird im Klartext gespeichert. Base64 kodiert die Inventarnummer, ist aber keine Verschlüsselung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = { onShowQr(payload) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("QR-Code anzeigen")
                }
                if (!nfcDisabled) {
                    OutlinedButton(
                        onClick = { onWriteNfc(payload) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("NFC-Tag beschreiben")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Später")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun ItemQrCodeDialog(payload: String, onDismiss: () -> Unit) {
    val generation = remember(payload) {
        try {
            val matrix = QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                640,
                640,
                mapOf(EncodeHintType.MARGIN to 1)
            )
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                val x = index % matrix.width
                val y = index / matrix.width
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            QrResult(Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            }, null)
        } catch (_: WriterException) {
            QrResult(null, "Der Inhalt ist zu lang, um als QR-Code dargestellt zu werden.")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR-Code für Inventarnummer") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bitmap = generation.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR-Code",
                        modifier = Modifier.size(280.dp).padding(8.dp)
                    )
                } else {
                    Text(generation.error ?: "QR-Code konnte nicht erstellt werden.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}

@Composable
fun NfcTagWriteDialog(payload: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    var status by remember { mutableStateOf("Halte einen beschreibbaren NFC-Tag an die Rückseite des Geräts.") }

    DisposableEffect(activity, adapter, payload) {
        if (activity == null) {
            status = "NFC-Beschreiben ist in diesem Fenster nicht verfügbar."
            onDispose { }
        } else if (adapter == null) {
            status = "Dieses Gerät unterstützt kein NFC."
            onDispose { }
        } else if (!adapter.isEnabled) {
            status = "NFC ist deaktiviert. Aktiviere NFC und versuche es erneut."
            onDispose { }
        } else {
            val mainHandler = Handler(Looper.getMainLooper())
            val tagHandled = AtomicBoolean(false)
            val readerCallback = NfcAdapter.ReaderCallback { tag ->
                if (tagHandled.compareAndSet(false, true)) {
                    val result = try {
                        writeTag(tag, payload)
                    } catch (exception: Exception) {
                        "NFC-Tag konnte nicht beschrieben werden: ${exception.localizedMessage ?: "Unbekannter Fehler"}"
                    }
                    mainHandler.post {
                        status = result
                        adapter.disableReaderMode(activity)
                    }
                }
            }
            adapter.enableReaderMode(
                activity,
                readerCallback,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
                null
            )
            onDispose {
                adapter.disableReaderMode(activity)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NFC-Tag beschreiben") },
        text = { Text(status) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}

private data class QrResult(val bitmap: Bitmap?, val error: String?)

private fun writeTag(tag: Tag, payload: String): String {
    val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("de", payload)))
    val ndef = Ndef.get(tag)
    if (ndef != null) {
        try {
            ndef.connect()
            if (!ndef.isWritable) return "Dieser NFC-Tag ist schreibgeschützt."
            if (message.toByteArray().size > ndef.maxSize) return "Der Inhalt ist zu groß für diesen NFC-Tag."
            ndef.writeNdefMessage(message)
        } finally {
            ndef.close()
        }
        return "NFC-Tag erfolgreich beschrieben."
    }

    val formatable = NdefFormatable.get(tag)
        ?: return "Dieser NFC-Tag unterstützt kein NDEF-Format."
    try {
        formatable.connect()
        formatable.format(message)
    } finally {
        formatable.close()
    }
    return "NFC-Tag erfolgreich formatiert und beschrieben."
}
