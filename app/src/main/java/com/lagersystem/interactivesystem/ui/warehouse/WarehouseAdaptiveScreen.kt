package com.lagersystem.interactivesystem.ui.warehouse

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlaylistAdd
import com.lagersystem.interactivesystem.ui.packinglist.PackingListScreen
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.warehouse.Warehouse
import com.lagersystem.interactivesystem.data.user.UserRole
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.lagersystem.interactivesystem.util.ExportUtils
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import java.io.InputStream

@Composable
fun WarehouseAdaptiveScreen(
    viewModel: WarehouseViewModel,
    windowWidthSizeClass: WindowWidthSizeClass,
    highlightedInventoryNumber: String? = null,
    isAdmin: Boolean = false,
    onCameraClick: () -> Unit,
    onNfcClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onItemSaved: (Item) -> Unit = {},
    nfcDisabled: Boolean = false,
    cameraDisabled: Boolean = false,
    currentUserId: String = ""
) {
    val warehouses by viewModel.allWarehouses.collectAsStateWithLifecycle()
    val warehouseSummaries by viewModel.warehouseSummaries.collectAsStateWithLifecycle()
    val selectedWarehouse by viewModel.selectedWarehouse.collectAsStateWithLifecycle()

    var itemToDelete by remember { mutableStateOf<Item?>(null) }
    var showAddWarehouseDialog by remember { mutableStateOf(false) }
    var warehouseToDelete by remember { mutableStateOf<Warehouse?>(null) }
    val scope = rememberCoroutineScope()

    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = if (!isExpanded) selectedWarehouse else null,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "warehouseTransition"
        ) { warehouse ->
            if (warehouse != null) {
                WarehouseDetail(
                    viewModel = viewModel,
                    highlightedInventoryNumber = highlightedInventoryNumber,
                    isAdmin = isAdmin,
                    onBack = { viewModel.selectWarehouse(null) },
                    onCameraClick = onCameraClick,
                    onNfcClick = onNfcClick,
                    onItemClick = onItemClick,
                    onItemSaved = onItemSaved,
                    onLongClick = { if(isAdmin) itemToDelete = it },
                    nfcDisabled = nfcDisabled,
                    cameraDisabled = cameraDisabled,
                    currentUserId = currentUserId
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // List Pane
                    WarehouseList(
                        warehouses = warehouses,
                        warehouseSummaries = warehouseSummaries,
                        onWarehouseClick = { viewModel.selectWarehouse(it) },
                        onDeleteWarehouse = { warehouse ->
                            scope.launch {
                                val itemsCount = viewModel.getItemsInWarehouseSync(warehouse.id).size
                                if (itemsCount == 0) {
                                    viewModel.deleteWarehouse(warehouse)
                                } else {
                                    warehouseToDelete = warehouse
                                }
                            }
                        },
                        onAddWarehouse = { showAddWarehouseDialog = true },
                        isAdmin = isAdmin,
                        modifier = Modifier.weight(if (isExpanded) 0.4f else 1f)
                    )

                    // Show Detail side-by-side on large screens
                    if (isExpanded) {
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                        Box(modifier = Modifier.weight(0.6f)) {
                            if (selectedWarehouse != null) {
                                WarehouseDetail(
                                    viewModel = viewModel,
                                    highlightedInventoryNumber = highlightedInventoryNumber,
                                    isAdmin = isAdmin,
                                    onBack = { viewModel.selectWarehouse(null) },
                                    onCameraClick = onCameraClick,
                                    onNfcClick = onNfcClick,
                                    onItemClick = onItemClick,
                                    onItemSaved = onItemSaved,
                                    onLongClick = { if(isAdmin) itemToDelete = it }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Wähle ein Lager aus")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (warehouseToDelete != null) {
        var itemsCount by remember { mutableIntStateOf(-1) }
        LaunchedEffect(warehouseToDelete) {
            itemsCount = viewModel.getItemsInWarehouseSync(warehouseToDelete!!.id).size
        }

        AlertDialog(
            onDismissRequest = { warehouseToDelete = null },
            title = { Text("Lager '${warehouseToDelete!!.name}' löschen?") },
            text = {
                Column {
                    Text("Möchten Sie dieses Lager wirklich unwiderruflich löschen?")
                    if (itemsCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "WARNUNG: Dieses Lager enthält noch $itemsCount Artikel. Diese werden ebenfalls gelöscht!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (itemsCount == 0) {
                        Text("Dieses Lager ist aktuell leer.")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWarehouse(warehouseToDelete!!)
                        warehouseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Endgültig löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { warehouseToDelete = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showAddWarehouseDialog) {
        var newName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddWarehouseDialog = false },
            title = { Text("Neues Lager anlegen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name des Lagers") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        viewModel.addWarehouse(newName, 1, 1)
                        showAddWarehouseDialog = false
                    },
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Erstellen") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWarehouseDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Item löschen") },
            text = { Text("Möchten Sie '${itemToDelete!!.name}' wirklich aus dem Lager entfernen?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteItem(itemToDelete!!)
                    itemToDelete = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun WarehouseList(
    warehouses: List<Warehouse>,
    warehouseSummaries: Map<Long, String>,
    onWarehouseClick: (Warehouse) -> Unit,
    onDeleteWarehouse: (Warehouse) -> Unit,
    onAddWarehouse: () -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alle Lagerregale",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isAdmin) {
                IconButton(onClick = onAddWarehouse) {
                    Icon(Icons.Default.Add, "Lager hinzufügen")
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(warehouses) { warehouse ->
                ListItem(
                    headlineContent = { Text(warehouse.name) },
                    supportingContent = { Text(warehouseSummaries[warehouse.id] ?: "Übersicht wird geladen...") },
                    trailingContent = {
                        if (isAdmin) {
                            IconButton(onClick = { onDeleteWarehouse(warehouse) }) {
                                Icon(Icons.Default.Delete, "Lager löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    modifier = Modifier.clickable { onWarehouseClick(warehouse) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseDetail(
    viewModel: WarehouseViewModel,
    highlightedInventoryNumber: String? = null,
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onCameraClick: () -> Unit,
    onNfcClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onItemSaved: (Item) -> Unit,
    onLongClick: (Item) -> Unit,
    nfcDisabled: Boolean = false,
    cameraDisabled: Boolean = false,
    currentUserId: String = ""
) {
    val items by viewModel.itemsInWarehouse.collectAsStateWithLifecycle()
    val itemLogStatuses by viewModel.itemLogStatuses.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val selectedWarehouse by viewModel.selectedWarehouse.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showAdminForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Item?>(null) }
    var showPackingList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Suchen...") },
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        viewModel.updateSearchQuery("") 
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.Close, "Löschen")
                                    }
                                }
                                IconButton(onClick = { showExportMenu = true }) {
                                    Icon(Icons.Default.Share, "Exportieren")
                                }
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, "Sortieren")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Inventarnummer ${if (sortOption == WarehouseViewModel.SortOption.INVENTORY_NUMBER) "✓" else ""}") },
                                        onClick = {
                                            showSortMenu = false
                                            viewModel.setSortOption(WarehouseViewModel.SortOption.INVENTORY_NUMBER)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Kategorie ${if (sortOption == WarehouseViewModel.SortOption.CATEGORY) "✓" else ""}") },
                                        onClick = {
                                            showSortMenu = false
                                            viewModel.setSortOption(WarehouseViewModel.SortOption.CATEGORY)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Marke ${if (sortOption == WarehouseViewModel.SortOption.BRAND) "✓" else ""}") },
                                        onClick = {
                                            showSortMenu = false
                                            viewModel.setSortOption(WarehouseViewModel.SortOption.BRAND)
                                        }
                                    )
                                }
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Als PDF exportieren") },
                                        onClick = {
                                            showExportMenu = false
                                            scope.launch {
                                                val warehouse = selectedWarehouse
                                                val warehouseItems = items
                                                val logs = viewModel.getAllLogsSync()
                                                if (warehouse != null) {
                                                    ExportUtils.exportWarehousePdf(context, warehouse, warehouseItems, logs)
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Als CSV exportieren") },
                                        onClick = {
                                            showExportMenu = false
                                            scope.launch {
                                                val warehouse = selectedWarehouse
                                                val warehouseItems = items
                                                val logs = viewModel.getAllLogsSync()
                                                if (warehouse != null) {
                                                    ExportUtils.exportWarehouseCsv(context, warehouse, warehouseItems, logs)
                                                }
                                            }
                                        }
                                    )
                                }
                                if (!cameraDisabled) {
                                    IconButton(onClick = onCameraClick) { 
                                        Icon(Icons.Default.CameraAlt, "QR Scan") 
                                    }
                                }
                                if (!nfcDisabled) {
                                    IconButton(onClick = onNfcClick) { 
                                        Icon(Icons.Default.Nfc, "NFC Scan") 
                                    }
                                }
                                IconButton(onClick = { showPackingList = true }) {
                                    Icon(Icons.Default.PlaylistAdd, "Packliste")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin && !showAdminForm) {
                FloatingActionButton(onClick = {
                    editingItem = null
                    showAdminForm = true
                }) {
                    Icon(Icons.Default.Add, "Artikel hinzufügen")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showAdminForm && selectedWarehouse != null) {
                AdminItemDialog(
                    item = editingItem,
                    warehouseId = selectedWarehouse!!.id,
                    row = editingItem?.row ?: 0,
                    column = editingItem?.column ?: 0,
                    onConfirm = { newItem ->
                        viewModel.upsertItem(editingItem, newItem)
                        onItemSaved(newItem)
                        showAdminForm = false
                        editingItem = null
                    },
                    onDismiss = {
                        showAdminForm = false
                        editingItem = null
                    }
                )
            } else {
                WarehouseItemList(
                    items = items,
                    itemLogStatuses = itemLogStatuses,
                    isAdmin = isAdmin,
                    highlightedInventoryNumber = highlightedInventoryNumber,
                    isSortedByCategory = sortOption == WarehouseViewModel.SortOption.CATEGORY,
                    onItemClick = { item ->
                        onItemClick(item.inventoryNumber)
                    },
                    onEdit = { item ->
                        editingItem = item
                        showAdminForm = true
                    },
                    onLongClick = onLongClick
                )
            }
        }
    }

    if (showPackingList && selectedWarehouse != null) {
        PackingListScreen(
            viewModel = hiltViewModel(),
            warehouseId = selectedWarehouse!!.id,
            isAdmin = isAdmin,
            currentUserId = currentUserId,
            onDismiss = { showPackingList = false }
        )
    }
}

@Composable
fun WarehouseItemList(
    items: List<Item>,
    itemLogStatuses: Map<String, ItemLogStatus> = emptyMap(),
    isAdmin: Boolean,
    highlightedInventoryNumber: String? = null,
    isSortedByCategory: Boolean = false,
    onItemClick: (Item) -> Unit,
    onEdit: (Item) -> Unit,
    onLongClick: (Item) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Keine Artikel gefunden")
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "blink")
        val blinkColor by infiniteTransition.animateColor(
            initialValue = Color(0xFF4CAF50).copy(alpha = 0.4f), 
            targetValue = Color(0xFFC8E6C9).copy(alpha = 0.2f),
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blinkColor"
        )

        val grouped = remember(items, isSortedByCategory) {
            if (isSortedByCategory) items.groupBy { it.type.ifBlank { "Allgemein" } }
            else mapOf("" to items)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grouped.forEach { (category, catItems) ->
                if (isSortedByCategory && category.isNotEmpty()) {
                    item(key = "header_$category") {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
                items(catItems, key = { it.inventoryNumber }) { item ->
                    val isHighlighted = item.inventoryNumber == highlightedInventoryNumber
                    val context = LocalContext.current
                    val logStatus = itemLogStatuses[item.inventoryNumber] ?: ItemLogStatus()
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isHighlighted) Modifier.border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { onItemClick(item) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighlighted) blinkColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        ListItem(
                            headlineContent = { 
                                Text(
                                    text = if (item.model.isNotBlank()) "${item.name} (${item.model})" else item.name, 
                                    style = MaterialTheme.typography.titleMedium
                                ) 
                            },
                            supportingContent = {
                                Column {
                                    val detailsText = buildList {
                                        add("Inv-Nr: ${item.inventoryNumber}")
                                        if (item.location.isNotBlank()) add("Ort: ${item.location}")
                                        if (logStatus.borrowed > 0) add("In Verwendung: ${logStatus.borrowed}")
                                        if (logStatus.missing > 0) add("Verschwunden: ${logStatus.missing}")
                                    }.joinToString(" · ")
                                    Text(
                                        text = detailsText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val serials = item.serialNumbers.lines().filter { it.isNotBlank() }
                                    if (serials.isNotEmpty()) {
                                        Text(
                                            "Seriennummern hinterlegt: ${serials.size}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (item.attributes.isNotBlank()) {
                                        Text(
                                            "Beschriftung: ${item.attributes}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            leadingContent = { 
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val iconName = item.iconName
                                        val bitmap = remember(iconName) {
                                            if (iconName.isNullOrEmpty()) null
                                            else {
                                                try {
                                                    val inputStream: InputStream = context.assets.open("ICONS/$iconName.png")
                                                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                                                } catch (_: Exception) {
                                                    null
                                                }
                                            }
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.stock}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (isAdmin) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = { onEdit(item) }) {
                                            Icon(Icons.Default.Edit, "Bearbeiten", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onLongClick(item) }) {
                                            Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
