package com.lagersystem.interactivesystem.ui.warehouse

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.log.ActionType

@Composable
fun LendDialog(
    item: Item,
    borrowedCount: Int,
    missingCount: Int,
    onConfirm: (Int, ActionType) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember(item.inventoryNumber) { mutableStateOf("1") }
    var action by remember(item.inventoryNumber) { mutableStateOf(ActionType.LEND) }

    val amount = amountText.toIntOrNull() ?: 0
    val maxAmount = when (action) {
        ActionType.LEND -> item.stock
        ActionType.MISSING -> item.stock + borrowedCount
        ActionType.RETURN -> borrowedCount
        ActionType.FOUND -> missingCount
        else -> 0
    }
    val isValid = amount > 0 && amount <= maxAmount

    val actions = listOf(
        Triple(ActionType.LEND, "Ausleihen", item.stock),
        Triple(ActionType.RETURN, "Zurückgeben", borrowedCount),
        Triple(ActionType.MISSING, "Verschwunden", item.stock + borrowedCount),
        Triple(ActionType.FOUND, "Wiedergefunden", missingCount)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Inventarnummer ${item.inventoryNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (item.attributes.isNotBlank() || item.location.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (item.attributes.isNotBlank()) {
                                Text("Beschriftung: ${item.attributes}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (item.location.isNotBlank()) {
                                Text("Ort: ${item.location}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusCountCard("Vorhanden", item.stock, Modifier.weight(1f))
                    StatusCountCard("Verwendet", borrowedCount, Modifier.weight(1f))
                    StatusCountCard("Verschwunden", missingCount, Modifier.weight(1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktion", style = MaterialTheme.typography.titleSmall)
                    actions.chunked(2).forEach { actionRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            actionRow.forEach { (option, label, available) ->
                val selected = action == option
                val color = when (option) {
                    ActionType.LEND -> MaterialTheme.colorScheme.primary
                    ActionType.RETURN -> MaterialTheme.colorScheme.secondary
                    ActionType.MISSING -> MaterialTheme.colorScheme.error
                    ActionType.FOUND -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 68.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { action = option },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) color.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { action = option },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                "$available verfügbar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Anzahl", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                amountText = ((amount - 1).coerceAtLeast(1)).toString()
                            },
                            enabled = amount > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Anzahl verringern")
                        }
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.width(112.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        IconButton(
                            onClick = {
                if (amount < maxAmount) amountText = (amount + 1).toString()
                            },
                            enabled = amount < maxAmount
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Anzahl erhöhen")
                        }
                    }
                    Text(
                        text = if (maxAmount == 0) {
                            when (action) {
                ActionType.RETURN -> "Keine Geräte zur Rückgabe erfasst."
                ActionType.MISSING -> "Keine Geräte zum Melden vorhanden."
                ActionType.FOUND -> "Keine verschwundenen Geräte erfasst."
                else -> "Keine Geräte zum Ausleihen vorhanden."
                            }
                        } else {
                            "Maximal $maxAmount für diese Aktion"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (maxAmount == 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount, action) },
                enabled = isValid
            ) {
                Text(
                    when (action) {
                        ActionType.LEND -> "Ausleihen"
                        ActionType.RETURN -> "Zurückgeben"
                        ActionType.MISSING -> "Als verschwunden melden"
                        ActionType.FOUND -> "Als wiedergefunden melden"
                        else -> "Bestätigen"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun StatusCountCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
