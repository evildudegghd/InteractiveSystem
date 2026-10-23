package com.lagersystem.interactivesystem.ui.packinglist

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.packinglist.PackingList
import com.lagersystem.interactivesystem.data.packinglist.PackingListItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PackingListScreen(
    viewModel: PackingListViewModel,
    warehouseId: Long,
    isAdmin: Boolean,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    LaunchedEffect(warehouseId) {
        viewModel.setWarehouseId(warehouseId)
    }

    val packingLists by viewModel.packingLists.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedListForDetail by remember { mutableStateOf<PackingList?>(null) }
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Packlisten für dieses Lager")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ListAlt, contentDescription = "Schließen")
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                if (packingLists.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ListAlt, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Keine Packlisten für dieses Lager vorhanden.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(packingLists) { list ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                onClick = { selectedListForDetail = list }
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = list.title, style = MaterialTheme.typography.titleMedium)
                                            Text(text = "Von: ${list.createdBy} am ${dateFormat.format(Date(list.timestamp))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (isAdmin) {
                                            IconButton(onClick = { viewModel.deletePackingList(warehouseId, list.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    Text(text = "${list.items.sumOf { it.quantity }} Artikel (${list.items.size} Positionen)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Neue Packliste erstellen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )

    if (showCreateDialog) {
        CreatePackingListDialog(
            allItems = allItems.filter { it.warehouseId == warehouseId },
            currentUserId = currentUserId,
            onDismiss = { showCreateDialog = false },
            onSave = { title, items ->
                viewModel.createPackingList(warehouseId, title, currentUserId, items)
                showCreateDialog = false
            }
        )
    }

    if (selectedListForDetail != null) {
        PackingListDetailDialog(
            list = selectedListForDetail!!,
            dateFormat = dateFormat,
            onDismiss = { selectedListForDetail = null },
            onShare = { text ->
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, "Packliste: ${selectedListForDetail!!.title}")
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(intent, "Packliste teilen"))
            }
        )
    }
}

@Composable
fun CreatePackingListDialog(
    allItems: List<Item>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSave: (String, List<PackingListItem>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val selectedQuantities = remember { mutableStateMapOf<String, Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Packliste erstellen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel der Packliste") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Artikel auswählen & Mengen festlegen:", style = MaterialTheme.typography.labelLarge)
                
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allItems) { item ->
                        val qty = selectedQuantities[item.inventoryNumber] ?: 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = if (item.model.isNotBlank()) "${item.name} (${item.model})" else item.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "Inv-Nr: ${item.inventoryNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            if (qty > 0) selectedQuantities[item.inventoryNumber] = qty - 1
                                            if (qty - 1 <= 0) selectedQuantities.remove(item.inventoryNumber)
                                        },
                                        modifier = Modifier.size(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("-")
                                    }
                                    Text(text = qty.toString(), style = MaterialTheme.typography.bodyLarge)
                                    OutlinedButton(
                                        onClick = {
                                            selectedQuantities[item.inventoryNumber] = qty + 1
                                        },
                                        modifier = Modifier.size(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && selectedQuantities.isNotEmpty(),
                onClick = {
                    val listItems = selectedQuantities.mapNotNull { (invNum, q) ->
                        val item = allItems.find { it.inventoryNumber == invNum }
                        if (item != null && q > 0) {
                            PackingListItem(
                                inventoryNumber = item.inventoryNumber,
                                name = item.name,
                                model = item.model,
                                quantity = q
                            )
                        } else null
                    }
                    onSave(title, listItems)
                }
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun PackingListDetailDialog(
    list: PackingList,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit
) {
    val itemsList = list.items

    val shareText = remember(list, itemsList) {
        buildString {
            append("PACKLISTE: ${list.title}\n")
            append("Erstellt von: ${list.createdBy} am ${dateFormat.format(Date(list.timestamp))}\n")
            append("----------------------------------------\n")
            itemsList.forEach { item ->
                append("• [${item.quantity}x] ${item.name} ${item.model} (Inv-Nr: ${item.inventoryNumber})\n")
            }
            append("----------------------------------------\n")
            append("Gesamtpositionen: ${itemsList.size}, Gesamtmenge: ${itemsList.sumOf { it.quantity }}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(list.title)
                IconButton(onClick = { onShare(shareText) }) {
                    Icon(Icons.Default.Share, contentDescription = "Teilen / Exportieren")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Erstellt von: ${list.createdBy} am ${dateFormat.format(Date(list.timestamp))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(itemsList) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = if (item.model.isNotBlank()) "${item.name} (${item.model})" else item.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "Inv-Nr: ${item.inventoryNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${item.quantity}x",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onShare(shareText) }) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Exportieren / Teilen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
