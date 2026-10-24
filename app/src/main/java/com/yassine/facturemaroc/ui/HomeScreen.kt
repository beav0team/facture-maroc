package com.yassine.facturemaroc.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.XmlExportOutcome
import com.yassine.facturemaroc.data.billedInMonth
import com.yassine.facturemaroc.invoice.InvoiceXml
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab(val label: String) {
    INVOICES("Factures"),
    CLIENTS("Clients"),
    ARTICLES("Articles"),
}

/**
 * The screen the app opens on: what has been invoiced, who it was for, and
 * what the shop sells by heart.
 *
 * The three tabs are the whole app in one place. Before this, clients and
 * saved items were only reachable from inside a half-written invoice, which
 * meant they could not be prepared ahead of time and could not be corrected
 * afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: InvoiceViewModel,
    onNewInvoice: (InvoiceClient?) -> Unit,
    onOpenRecord: (InvoiceRecord) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val records by viewModel.invoices.invoices.collectAsState()
    val clients by viewModel.clients.clients.collectAsState()
    val items by viewModel.savedItems.items.collectAsState()
    val folders by viewModel.savedItems.folders.collectAsState()
    val shop by viewModel.shop.collectAsState()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val tab = HomeTab.entries[tabIndex]

    var clientEditorOpen by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<InvoiceClient?>(null) }
    var itemEditorOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SavedItem?>(null) }
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var folderEditorOpen by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    var deleteFolder by remember { mutableStateOf<String?>(null) }
    var manageFoldersOpen by remember { mutableStateOf(false) }
    var detailRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var xmlRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var deleteInvoice by remember { mutableStateOf<InvoiceRecord?>(null) }
    var deleteClient by remember { mutableStateOf<InvoiceClient?>(null) }
    var deleteItem by remember { mutableStateOf<SavedItem?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val xmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri: Uri? ->
        val record = xmlRecord
        xmlRecord = null
        if (uri != null && record != null) {
            scope.launch {
                val message = when (viewModel.exportRecordXml(record, uri)) {
                    XmlExportOutcome.Saved ->
                        "Fichier XML enregistré : ${record.data.number}"
                    XmlExportOutcome.Failed ->
                        "Le fichier XML n'a pas pu être enregistré."
                    XmlExportOutcome.NothingToExport ->
                        "Ce document n'a pas de lignes à exporter."
                }
                snackbar.showSnackbar(message)
            }
        }
    }

    fun exportXml(record: InvoiceRecord) {
        xmlRecord = record
        xmlLauncher.launch(InvoiceXml.fileName(record.data))
    }

    val fabLabel = when (tab) {
        HomeTab.INVOICES -> "Nouvelle facture"
        HomeTab.CLIENTS -> "Nouveau client"
        HomeTab.ARTICLES -> "Nouvel article"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Facture Maroc",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            shop.name.ifBlank { "Gestion de facturation" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                HomeTab.entries.forEach { candidate ->
                    NavigationBarItem(
                        selected = candidate == tab,
                        onClick = {
                            tabIndex = candidate.ordinal
                            query = ""
                        },
                        icon = {
                            Icon(
                                when (candidate) {
                                    HomeTab.INVOICES -> Icons.Filled.ReceiptLong
                                    HomeTab.CLIENTS -> Icons.Filled.People
                                    HomeTab.ARTICLES -> Icons.Filled.Inventory2
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(candidate.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (tab) {
                        HomeTab.INVOICES -> onNewInvoice(null)
                        HomeTab.CLIENTS -> {
                            editingClient = null
                            clientEditorOpen = true
                        }
                        HomeTab.ARTICLES -> {
                            editingItem = null
                            itemEditorOpen = true
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Add, contentDescription = fabLabel)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = when (tab) {
                    HomeTab.INVOICES -> "Client, numéro ou date"
                    HomeTab.CLIENTS -> "Nom, téléphone ou ICE"
                    HomeTab.ARTICLES -> "Rechercher un article"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            when (tab) {
                HomeTab.INVOICES -> InvoicesTab(
                    records = records,
                    query = query,
                    onOpenRecord = onOpenRecord,
                    onShowDetail = { detailRecord = it },
                    onShare = { record ->
                        scope.launch {
                            val outcome = viewModel.shareRecord(record)
                            snackbar.showSnackbar(outcome.shareMessage())
                        }
                    },
                    onExportXml = ::exportXml,
                    onDuplicate = onOpenRecord,
                    onDelete = { deleteInvoice = it },
                )

                HomeTab.CLIENTS -> ClientsTab(
                    clients = clients,
                    records = records,
                    query = query,
                    onEdit = {
                        editingClient = it
                        clientEditorOpen = true
                    },
                    onInvoice = { onNewInvoice(it) },
                    onDelete = { deleteClient = it },
                )

                HomeTab.ARTICLES -> ItemsTab(
                    items = items,
                    folders = folders,
                    query = query,
                    selectedFolder = folderFilter,
                    onSelectFolder = { folderFilter = it },
                    onCreateFolder = {
                        renamingFolder = null
                        folderEditorOpen = true
                    },
                    onManageFolders = { manageFoldersOpen = true },
                    onEdit = {
                        editingItem = it
                        itemEditorOpen = true
                    },
                    onDelete = { deleteItem = it },
                )
            }
        }
    }

    if (clientEditorOpen) {
        ClientEditorDialog(
            initial = editingClient,
            onDismiss = { clientEditorOpen = false },
            onSave = { updated ->
                val previous = editingClient
                if (previous != null && !previous.name.equals(updated.name, ignoreCase = true)) {
                    viewModel.clients.delete(previous)
                }
                viewModel.clients.save(updated)
                clientEditorOpen = false
            },
        )
    }

    if (itemEditorOpen) {
        ItemEditorDialog(
            initial = editingItem,
            folders = folders,
            defaultFolder = folderFilter ?: "",
            onDismiss = { itemEditorOpen = false },
            onSave = { updated ->
                val previous = editingItem
                if (previous != null && !previous.label.equals(updated.label, ignoreCase = true)) {
                    viewModel.savedItems.delete(previous)
                }
                viewModel.savedItems.save(updated)
                itemEditorOpen = false
            },
        )
    }

    detailRecord?.let { record ->
        InvoiceDetailSheet(
            record = record,
            onDismiss = { detailRecord = null },
            onShare = {
                detailRecord = null
                scope.launch {
                    val outcome = viewModel.shareRecord(record)
                    snackbar.showSnackbar(outcome.shareMessage())
                }
            },
            onExportXml = {
                detailRecord = null
                exportXml(record)
            },
            onDuplicate = {
                detailRecord = null
                onOpenRecord(record)
            },
            onDelete = {
                detailRecord = null
                deleteInvoice = record
            },
        )
    }

    deleteInvoice?.let { record ->
        ConfirmDeleteDialog(
            title = "Supprimer cette facture ?",
            message = "${record.data.number} · ${record.title} — ${record.data.total.asDirhams()}. " +
                "Cette action ne touche pas au PDF déjà envoyé, mais la ligne disparaît de l'historique.",
            onDismiss = { deleteInvoice = null },
            onConfirm = {
                viewModel.deleteRecord(record.id)
                deleteInvoice = null
                scope.launch { snackbar.showSnackbar("Facture supprimée de l'historique.") }
            },
        )
    }

    deleteClient?.let { client ->
        DeleteSimpleDialog(
            title = "Supprimer ce client ?",
            message = "${client.name} disparaîtra de la liste. Les factures déjà émises gardent ses coordonnées.",
            onDismiss = { deleteClient = null },
            onConfirm = {
                viewModel.clients.delete(client)
                deleteClient = null
            },
        )
    }

    deleteItem?.let { item ->
        DeleteSimpleDialog(
            title = "Supprimer cet article ?",
            message = "${item.label} ne sera plus proposé sur les prochaines factures. Les lignes déjà écrites ne changent pas.",
            onDismiss = { deleteItem = null },
            onConfirm = {
                viewModel.savedItems.delete(item)
                deleteItem = null
            },
        )
    }

    if (manageFoldersOpen) {
        ManageFoldersDialog(
            folders = folders,
            counts = items.groupingBy { it.folder }.eachCount(),
            onCreate = {
                manageFoldersOpen = false
                renamingFolder = null
                folderEditorOpen = true
            },
            onRename = { folder ->
                manageFoldersOpen = false
                renamingFolder = folder
                folderEditorOpen = true
            },
            onDelete = { folder ->
                manageFoldersOpen = false
                deleteFolder = folder
            },
            onDismiss = { manageFoldersOpen = false },
        )
    }

    if (folderEditorOpen) {
        val previous = renamingFolder
        FolderEditorDialog(
            initial = previous,
            onSave = { name ->
                val saved = if (previous == null) {
                    viewModel.savedItems.createFolder(name)
                } else {
                    viewModel.savedItems.renameFolder(previous, name)
                }
                if (saved) {
                    if (previous != null && folderFilter == previous) folderFilter = name
                    folderEditorOpen = false
                } else {
                    scope.launch {
                        snackbar.showSnackbar(
                            if (previous == null) {
                                "Ce dossier existe déjà."
                            } else {
                                "Ce nom est déjà utilisé par un autre dossier."
                            },
                        )
                    }
                }
            },
            onDismiss = { folderEditorOpen = false },
        )
    }

    deleteFolder?.let { folder ->
        val count = items.count { it.folder.equals(folder, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("Supprimer le dossier ?") },
            text = {
                Text(
                    "« $folder » sera supprimé. " + when (count) {
                        0 -> "Aucun article n'y est rangé."
                        1 -> "1 article sera conservé dans « Sans dossier »."
                        else -> "$count articles seront conservés dans « Sans dossier »."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.savedItems.deleteFolder(folder)
                        if (folderFilter == folder) folderFilter = null
                        deleteFolder = null
                    },
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun DeleteSimpleDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun InvoicesTab(
    records: List<InvoiceRecord>,
    query: String,
    onOpenRecord: (InvoiceRecord) -> Unit,
    onShowDetail: (InvoiceRecord) -> Unit,
    onShare: (InvoiceRecord) -> Unit,
    onExportXml: (InvoiceRecord) -> Unit,
    onDuplicate: (InvoiceRecord) -> Unit,
    onDelete: (InvoiceRecord) -> Unit,
) {
    val filtered = remember(records, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) records else records.filter { record ->
            listOf(
                record.title,
                record.data.number,
                record.data.date,
                record.data.total.toString(),
            ).any { it.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.ReceiptLong,
            title = if (records.isEmpty()) "Aucune facture pour l'instant" else "Aucun résultat",
            message = if (records.isEmpty()) {
                "Appuyez sur + pour créer votre premier document. " +
                    "Il sera gardé ici, brouillon puis facture émise."
            } else {
                "Essayez un nom de client, un numéro ou une date."
            },
        )
        return
    }

    val draftCount = filtered.count { it.isDraft }
    val issuedCount = filtered.size - draftCount

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (query.isBlank()) {
            item(key = "summary") {
                MonthSummary(records = records)
            }
        }

        if (draftCount > 0) {
            item(key = "drafts-heading") {
                SectionHeading("Brouillons", trailing = "$draftCount en attente")
            }
            items(filtered.filter { it.isDraft }, key = { "draft-${it.id}" }) { record ->
                InvoiceCard(
                    record = record,
                    onClick = { onOpenRecord(record) },
                    onShare = { onShare(record) },
                    onExportXml = { onExportXml(record) },
                    onDuplicate = { onDuplicate(record) },
                    onDelete = { onDelete(record) },
                )
            }
        }

        if (issuedCount > 0) {
            item(key = "issued-heading") {
                SectionHeading("Documents émis", trailing = "$issuedCount")
            }
            items(filtered.filterNot { it.isDraft }, key = { "issued-${it.id}" }) { record ->
                InvoiceCard(
                    record = record,
                    onClick = { onShowDetail(record) },
                    onShare = { onShare(record) },
                    onExportXml = { onExportXml(record) },
                    onDuplicate = { onDuplicate(record) },
                    onDelete = { onDelete(record) },
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(records: List<InvoiceRecord>) {
    val monthStamp = remember {
        SimpleDateFormat("MM/yyyy", Locale.FRANCE).format(Date())
    }
    val issuedThisMonth = remember(records, monthStamp) {
        records.billedInMonth(monthStamp)
    }
    val total = issuedThisMonth.sumOf { it.data.total }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Facturé ce mois-ci",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                total.asDirhams(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when (issuedThisMonth.size) {
                        0 -> "Aucune facture émise ce mois-ci"
                        1 -> "1 facture émise ce mois-ci"
                        else -> "${issuedThisMonth.size} factures émises ce mois-ci"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    record: InvoiceRecord,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onExportXml: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val draft = record.isDraft

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (draft) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (draft) Icons.Filled.Edit else Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = if (draft) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    record.data.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.data.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    record.data.total.asDirhams(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    text = record.status.label,
                    container = if (draft) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    content = if (draft) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Plus d'actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (draft) "Reprendre le brouillon" else "Ouvrir") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    if (!draft) {
                        DropdownMenuItem(
                            text = { Text("Partager le PDF") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Exporter en XML") },
                            leadingIcon = {
                                Icon(Icons.Filled.Description, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onExportXml()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Dupliquer") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientsTab(
    clients: List<InvoiceClient>,
    records: List<InvoiceRecord>,
    query: String,
    onEdit: (InvoiceClient) -> Unit,
    onInvoice: (InvoiceClient) -> Unit,
    onDelete: (InvoiceClient) -> Unit,
) {
    val filtered = remember(clients, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) clients else clients.filter {
            listOf(it.name, it.phone, it.email, it.ice).any { field -> field.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.People,
            title = if (clients.isEmpty()) "Aucun client enregistré" else "Aucun résultat",
            message = if (clients.isEmpty()) {
                "Enregistrez vos clients une fois. Sur chaque facture, leur nom, ICE et adresse " +
                    "se remplissent sans les retaper."
            } else {
                "Essayez un autre nom, un téléphone ou un ICE."
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "count") {
            SectionHeading("Clients enregistrés", trailing = "${filtered.size}")
        }
        items(filtered, key = { "client-${it.name.lowercase()}" }) { client ->
            ClientCard(
                client = client,
                documentCount = records.count {
                    !it.isDraft && it.data.client.name.equals(client.name, ignoreCase = true)
                },
                onClick = { onEdit(client) },
                onInvoice = { onInvoice(client) },
                onDelete = { onDelete(client) },
            )
        }
    }
}

@Composable
private fun ClientCard(
    client: InvoiceClient,
    documentCount: Int,
    onClick: () -> Unit,
    onInvoice: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val initials = remember(client.name) {
        client.name.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    client.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    client.phone.ifBlank { null },
                    client.ice.ifBlank { null }?.let { "ICE $it" },
                ).joinToString("  ·  ")
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (documentCount > 0) {
                Text(
                    if (documentCount == 1) "1 document" else "$documentCount documents",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Plus d'actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Facturer ce client") },
                        leadingIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onInvoice()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemsTab(
    items: List<SavedItem>,
    folders: List<String>,
    query: String,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    onEdit: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onCreateFolder: () -> Unit,
    onManageFolders: () -> Unit,
) {
    val itemsWithoutFolder = items.filter { it.folder.isBlank() }

    // Determine which items to show based on selected folder
    val scoped = remember(items, selectedFolder) {
        if (selectedFolder == null) {
            itemsWithoutFolder
        } else {
            items.filter { it.folder.equals(selectedFolder, ignoreCase = true) }
        }
    }
    val filtered = remember(scoped, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) scoped else scoped.filter {
            "${it.label} ${it.unit} ${it.folder}".lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Folder cards section — shown at root level only
        if (selectedFolder == null) {
            item {
                FolderCard(
                    title = "Tous",
                    subtitle = "${items.size} article${if (items.size > 1) "s" else ""}",
                    onClick = { onSelectFolder(null) },
                    isSelected = false,
                )
            }
            item {
                FolderCard(
                    title = "Sans dossier",
                    subtitle = "${itemsWithoutFolder.size} article${if (itemsWithoutFolder.size > 1) "s" else ""}",
                    onClick = { onSelectFolder("") },
                    isSelected = false,
                )
            }
            if (folders.isNotEmpty()) {
                item {
                    Text(
                        text = "Mes Dossiers",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(folders, key = { "folder-$it" }) { folder ->
                    val count = items.count { it.folder.equals(folder, ignoreCase = true) }
                    FolderCard(
                        title = folder,
                        subtitle = "$count article${if (count > 1) "s" else ""}",
                        onClick = { onSelectFolder(folder) },
                        isSelected = false,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
            }
        }

        // Items heading
        item {
            val headerTitle = when {
                selectedFolder == null -> "Articles et services"
                selectedFolder.isEmpty() -> "Sans dossier"
                else -> selectedFolder
            }
            ItemsHeading(
                title = headerTitle,
                count = filtered.size,
                onCreateFolder = onCreateFolder,
                onManageFolders = onManageFolders,
            )
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    EmptyState(
                        icon = Icons.Filled.Inventory2,
                        title = when {
                            items.isEmpty() -> "Aucun article enregistré"
                            selectedFolder != null && query.isBlank() -> "Dossier vide"
                            else -> "Aucun résultat"
                        },
                        message = when {
                            items.isEmpty() -> {
                                "Ajoutez ce que vous vendez souvent — une prestation, un produit, " +
                                    "une pièce — et retrouvez-le en un geste sur chaque facture."
                            }
                            selectedFolder != null && query.isBlank() -> {
                                if (selectedFolder.isEmpty()) {
                                    "Aucun article n'est encore rangé dans « Sans dossier »."
                                } else {
                                    "Ajoutez un article à « $selectedFolder » ou choisissez un autre dossier."
                                }
                            }
                            else -> "Essayez un autre mot."
                        },
                    )
                }
            }
        } else {
            items(filtered, key = { "item-${it.label.lowercase()}" }) { item ->
                ItemCard(item = item, onClick = { onEdit(item) }, onDelete = { onDelete(item) })
            }
        }
    }
}

/**
 * A tappable card representing a folder, showing its name and item count.
 */
@Composable
private fun FolderCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The articles heading, with the two folder actions beside it.
 *
 * The actions used to sit at the end of the filter row, where they took enough
 * width that a folder name was cut in half. They belong to the list, not to the
 * filters: creating a folder and managing them are things done to the whole
 * catalogue, while tapping a chip only changes which items are shown.
 */
@Composable
private fun ItemsHeading(
    title: String,
    count: Int,
    onCreateFolder: () -> Unit,
    onManageFolders: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onCreateFolder) {
            Icon(
                Icons.Filled.CreateNewFolder,
                contentDescription = "Nouveau dossier",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onManageFolders) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Gérer les dossiers",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = listOf(item.folder, item.unit)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                item.price.asDirhams(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Plus d'actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceDetailSheet(
    record: InvoiceRecord,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onExportXml: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = record.status.label,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    record.data.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(record.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${record.data.header} du ${record.data.date}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                record.data.total.asDirhams(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Total TTC",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            DetailRow("Articles", "${record.data.lines.size}")
            DetailRow("Total HT", record.data.totalExcludingVat.asDirhams())
            DetailRow(
                if (record.data.effectiveVatPercent == 0.0) "TVA (exonérée)" else "TVA ${record.data.effectiveVatPercent.trimZeros()} %",
                record.data.vatAmount.asDirhams(),
            )
            if (record.data.discountPercent > 0) {
                DetailRow("Remise", "−${record.data.discountPercent} %")
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Partager le PDF")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExportXml,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Exporter en XML")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDuplicate,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dupliquer en nouvelle facture")
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Supprimer de l'historique", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
