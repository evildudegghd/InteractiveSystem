package com.lagersystem.interactivesystem.ui.warehouse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagersystem.interactivesystem.data.item.Item
import java.io.InputStream

data class CategoryDef(
    val name: String,
    val defaultIcon: String,
    val availableIcons: List<String>
)

@Composable
fun AdminItemDialog(
    item: Item?, // null if creating new
    warehouseId: Long,
    row: Int,
    column: Int,
    onConfirm: (Item) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inventoryNumber by remember { mutableStateOf(item?.inventoryNumber ?: "") }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var model by remember { mutableStateOf(item?.model ?: "") }
    var stock by remember { mutableStateOf(item?.stock?.toString() ?: "1") }
    var type by remember { mutableStateOf(item?.type ?: "Scheinwerfer") }
    var attributes by remember { mutableStateOf(item?.attributes ?: "") }
    var serialNumbers by remember { mutableStateOf(item?.serialNumbers ?: "") }
    var location by remember { mutableStateOf(item?.location ?: "") }
    var status by remember { mutableStateOf(item?.status ?: "Vorhanden") }
    var selectedIcon by remember { mutableStateOf(item?.iconName ?: "scheinwerfer") }

    val categories = listOf(
        CategoryDef("Scheinwerfer", "scheinwerfer", listOf("scheinwerfer", "lichtstativ")),
        CategoryDef("Kameratechnik", "kamera", listOf("kamera", "capture_card", "tripod")),
        CategoryDef("PC-Technik", "bildschirm", listOf("bildschirm", "capture_card")),
        CategoryDef("DMX", "artnet", listOf("artnet", "dmx", "splitter")),
        CategoryDef("Audio", "speaker", listOf("mischpult", "speaker", "subwoofer", "cable_microphone", "wireless_microphone", "kopfhoerer")),
        CategoryDef("Sonstiges", "splitter", listOf("splitter"))
    )

    val currentCategory = categories.find { it.name == type } ?: categories.first()
    val stockValue = stock.toIntOrNull()
    val serialCapacity = if (item == null) stockValue ?: 0 else maxOf(stockValue ?: 0, item.maxStock)
    val serialEntries = remember(serialNumbers) {
        serialNumbers.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
    val serialNumbersValid = serialEntries.size <= serialCapacity &&
        serialEntries.distinct().size == serialEntries.size


    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (item == null) Icons.Default.AddBox else Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = if (item == null) "Neues Item erstellen" else "Item bearbeiten",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Artikeldetails",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = inventoryNumber, 
                        onValueChange = { inventoryNumber = it }, 
                        label = { Text("Inventarnummer") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("z.B. A00001") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = { Text("Marke") }, 
                            modifier = Modifier.weight(1f), 
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model, 
                            onValueChange = { model = it }, 
                            label = { Text("Modell") }, 
                            modifier = Modifier.weight(1f), 
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = stock, 
                        onValueChange = { stock = it }, 
                        label = { Text("Bestand (Anzahl)") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Lagerort / Ort (optional)") },
                        placeholder = { Text("z.B. Regal 3, Fach B, Case 4") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = serialNumbers,
                        onValueChange = { serialNumbers = it },
                        label = { Text("Seriennummern (optional)") },
                        placeholder = { Text("Eine Seriennummer pro Zeile") },
                        supportingText = {
                            val helperText = when {
                                serialEntries.size > serialCapacity -> "Maximal $serialCapacity Seriennummern möglich"
                                serialEntries.distinct().size != serialEntries.size -> "Seriennummern müssen eindeutig sein"
                                else -> "${serialEntries.size} von $serialCapacity eingetragen"
                            }
                            Text(helperText)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        minLines = 2
                    )

                    Text(
                        text = "Gerätestatus",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val statuses = listOf("Vorhanden", "In Verwendung", "Nicht gefunden", "Verschwunden")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(statuses) { stat ->
                            FilterChip(
                                selected = status == stat,
                                onClick = { status = stat },
                                label = { Text(stat) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Text(
                        text = "Kategorie & Symbol",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kategorie auswählen:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = type == cat.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { 
                                        type = cat.name
                                        selectedIcon = cat.defaultIcon
                                    },
                                    label = { Text(cat.name) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Icon Selector
                    if (currentCategory.availableIcons.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val labelText = if (currentCategory.availableIcons.size > 1) "Symbol-Variante für ${currentCategory.name}:" else "Symbol für ${currentCategory.name}:"
                            Text(labelText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(currentCategory.availableIcons) { icon ->
                                    val bitmap = remember(icon) {
                                        try {
                                            val inputStream: InputStream = context.assets.open("ICONS/$icon.png")
                                            BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }

                                    val isSelected = selectedIcon == icon
                                    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(backgroundColor)
                                            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                            .clickable { selectedIcon = icon }
                                            .padding(10.dp)
                                            .size(54.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        bitmap?.let {
                                            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = attributes, 
                        onValueChange = { attributes = it }, 
                        label = { Text("Merkmale (Komma-getrennt)") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("z.B. 4K, Bluetooth, Akku, defekt, o. ä.") }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer Actions
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .width(130.dp)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Abbrechen")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            enabled = inventoryNumber.isNotBlank() && name.isNotBlank() &&
                                stockValue != null && stockValue >= 0 && serialNumbersValid,
                            onClick = {
                                val s = stock.toIntOrNull() ?: 1
                                val newItem = Item(
                                    inventoryNumber = inventoryNumber,
                                    name = name,
                                    model = model,
                                    stock = s,
                                    maxStock = if (item != null) maxOf(s, item.maxStock) else s,
                                    warehouseId = warehouseId,
                                    row = row,
                                    column = column,
                                    type = type,
                                    attributes = attributes,
                                    location = location,
                                    serialNumbers = serialEntries.joinToString("\n"),
                                    iconName = if (selectedIcon.isEmpty()) null else selectedIcon,
                                    status = status
                                )
                                onConfirm(newItem)
                            },
                            modifier = Modifier
                                .width(150.dp)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Speichern")
                        }
                    }
                }
            }
        }
    }
}
