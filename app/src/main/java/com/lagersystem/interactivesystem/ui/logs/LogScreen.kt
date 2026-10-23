package com.lagersystem.interactivesystem.ui.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagersystem.interactivesystem.data.log.ActionType
import com.lagersystem.interactivesystem.ui.warehouse.WarehouseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogScreen(
    viewModel: LogViewModel,
    warehouseViewModel: WarehouseViewModel,
    currentUserId: String,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val logModels by viewModel.logUiModels.collectAsStateWithLifecycle()
    val selectedWarehouse by warehouseViewModel.selectedWarehouse.collectAsStateWithLifecycle()
    val allWarehouses by warehouseViewModel.allWarehouses.collectAsStateWithLifecycle()
    val recentIds by warehouseViewModel.recentWarehouseIds.collectAsStateWithLifecycle()

    val recentWarehouses = remember(allWarehouses, recentIds) {
        recentIds.mapNotNull { id -> allWarehouses.find { it.id == id } }
    }

    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Ausleihe, 1: Rückgabe, 2: Status
    var ausleiheSubFilter by remember { mutableIntStateOf(0) } // 0: Alle Ausleihen, 1: Meine Ausleihen
    var rueckgabeSubFilter by remember { mutableIntStateOf(0) } // 0: Alle Rückgaben, 1: Meine Rückgaben
    var showOlderReturns by remember { mutableStateOf(false) }
    var selectedLogModel by remember { mutableStateOf<LogUiModel?>(null) }

    val threeDaysMillis = 3 * 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()

    LaunchedEffect(selectedWarehouse, selectedTab) {
        viewModel.setSelectedWarehouse(selectedWarehouse)
        selectedLogModel = null
    }

    val lendLogs = remember(logModels) { logModels.filter { it.logEntry.action == ActionType.LEND } }
    val myLendLogs = remember(lendLogs, currentUserId) { lendLogs.filter { it.logEntry.userId == currentUserId } }

    val returnLogs = remember(logModels) { logModels.filter { it.logEntry.action == ActionType.RETURN } }
    val recentReturnLogs = remember(returnLogs, now) { returnLogs.filter { now - it.logEntry.timestamp <= threeDaysMillis } }
    val myReturnLogs = remember(returnLogs, currentUserId) { returnLogs.filter { it.logEntry.userId == currentUserId } }
    val myRecentReturnLogs = remember(myReturnLogs, now) { myReturnLogs.filter { now - it.logEntry.timestamp <= threeDaysMillis } }

    val activeReturnLogs = if (showOlderReturns) returnLogs else recentReturnLogs
    val activeMyReturnLogs = if (showOlderReturns) myReturnLogs else myRecentReturnLogs

    val statusLogs = remember(logModels) {
        logModels.filter {
            it.logEntry.action == ActionType.MISSING ||
                it.logEntry.action == ActionType.LOST_WHILE_LENT ||
                it.logEntry.action == ActionType.FOUND
        }
    }

    val visibleLogs = when (selectedTab) {
        0 -> if (ausleiheSubFilter == 1) myLendLogs else lendLogs
        1 -> if (rueckgabeSubFilter == 1) activeMyReturnLogs else activeReturnLogs
        2 -> statusLogs
        else -> emptyList()
    }

    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedWarehouse == null) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Kein Lager ausgewählt",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Bitte wähle im Tab 'Lager' ein spezifisches Lager aus, um Ausleihen, Rückgaben und Statusmeldungen anzuzeigen.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (recentWarehouses.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Zuletzt verwendete Lager:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        recentWarehouses.forEach { warehouse ->
                            OutlinedButton(
                                onClick = { warehouseViewModel.selectWarehouse(warehouse) },
                                modifier = Modifier.fillMaxWidth(0.8f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Home, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(warehouse.name)
                            }
                        }
                    }
                }
            }
        } else {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ausleihe (${lendLogs.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Rückgabe (${activeReturnLogs.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Status (${statusLogs.size})") }
                )
            }

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter:", style = MaterialTheme.typography.labelMedium)
                    FilterChip(
                        selected = ausleiheSubFilter == 0,
                        onClick = { ausleiheSubFilter = 0 },
                        label = { Text("Alle Ausleihen") }
                    )
                    FilterChip(
                        selected = ausleiheSubFilter == 1,
                        onClick = { ausleiheSubFilter = 1 },
                        label = { Text("Meine Ausleihen") }
                    )
                }
            } else if (selectedTab == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = rueckgabeSubFilter == 0,
                        onClick = { rueckgabeSubFilter = 0 },
                        label = { Text("Alle Rückgaben") }
                    )
                    FilterChip(
                        selected = rueckgabeSubFilter == 1,
                        onClick = { rueckgabeSubFilter = 1 },
                        label = { Text("Meine Rückgaben") }
                    )
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = showOlderReturns,
                        onClick = { showOlderReturns = !showOlderReturns },
                        label = { Text(if (showOlderReturns) "Ältere ausblenden" else "Ältere anzeigen (>3 Tage)") }
                    )
                }
            }

            if (selectedTab == 2) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Verschwundene und wiedergefundene Geräte werden im Tab „Status“ protokolliert. Melden kannst du sie im Lager, indem du den Artikel öffnest und die passende Statusaktion auswählst.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            if (visibleLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Keine Einträge für ${selectedWarehouse?.name ?: "dieses Lager"} in dieser Kategorie vorhanden.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                if (isExpanded) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                            LogListContent(
                                visibleLogs = visibleLogs,
                                selectedLogModel = selectedLogModel,
                                dateFormat = dateFormat,
                                onLogClick = { selectedLogModel = it }
                            )
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                        Box(modifier = Modifier.weight(0.55f).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (selectedLogModel != null) {
                                LogDetailCard(model = selectedLogModel!!, dateFormat = dateFormat)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Wähle einen Log-Eintrag aus, um Details anzuzeigen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                } else {
                    LogListContent(
                        visibleLogs = visibleLogs,
                        selectedLogModel = selectedLogModel,
                        dateFormat = dateFormat,
                        onLogClick = { selectedLogModel = it }
                    )
                }
            }
        }
    }
}

@Composable
fun LogListContent(
    visibleLogs: List<LogUiModel>,
    selectedLogModel: LogUiModel?,
    dateFormat: SimpleDateFormat,
    onLogClick: (LogUiModel) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visibleLogs) { model ->
            val log = model.logEntry
            val actionText = when (log.action) {
                ActionType.LEND -> "Ausleihe"
                ActionType.RETURN -> "Rückgabe"
                ActionType.MISSING -> "Als verschwunden gemeldet"
                ActionType.LOST_WHILE_LENT -> "Während Ausleihe verschwunden"
                ActionType.FOUND -> "Wiedergefunden"
                ActionType.ADD_STOCK -> "Bestand hinzugefügt"
                ActionType.REMOVE_STOCK -> "Bestand entfernt"
            }
            val actionColor = when (log.action) {
                ActionType.LEND -> MaterialTheme.colorScheme.primary
                ActionType.RETURN -> MaterialTheme.colorScheme.secondary
                ActionType.MISSING -> MaterialTheme.colorScheme.error
                ActionType.LOST_WHILE_LENT -> MaterialTheme.colorScheme.error
                ActionType.FOUND -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }

            val isSelected = selectedLogModel == model

            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth().clickable { onLogClick(model) }
            ) {
                ListItem(
                    headlineContent = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = actionColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = actionText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = actionColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${model.itemName} (${log.itemId})", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    supportingContent = { 
                        Column {
                            Text("Lager: ${model.warehouseName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text("User: ${log.userId} | Menge: ${log.amount} | ${dateFormat.format(Date(log.timestamp))}")
                        }
                    }
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun LogDetailCard(model: LogUiModel, dateFormat: SimpleDateFormat) {
    val log = model.logEntry
    val actionText = when (log.action) {
        ActionType.LEND -> "Ausleihe"
        ActionType.RETURN -> "Rückgabe"
        ActionType.MISSING -> "Als verschwunden gemeldet"
        ActionType.LOST_WHILE_LENT -> "Während Ausleihe verschwunden"
        ActionType.FOUND -> "Wiedergefunden"
        ActionType.ADD_STOCK -> "Bestand hinzugefügt"
        ActionType.REMOVE_STOCK -> "Bestand entfernt"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Protokolldetails", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("Aktion: $actionText", style = MaterialTheme.typography.bodyLarge)
            Text("Artikel: ${model.itemName}", style = MaterialTheme.typography.bodyMedium)
            Text("Inventarnummer: ${log.itemId}", style = MaterialTheme.typography.bodyMedium)
            Text("Lager: ${model.warehouseName}", style = MaterialTheme.typography.bodyMedium)
            Text("Benutzer: ${log.userId}", style = MaterialTheme.typography.bodyMedium)
            Text("Anzahl / Menge: ${log.amount}", style = MaterialTheme.typography.bodyMedium)
            Text("Zeitpunkt: ${dateFormat.format(Date(log.timestamp))}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
