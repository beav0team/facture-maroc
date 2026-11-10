package com.yassine.facturemaroc.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yassine.facturemaroc.data.ClientStatements
import com.yassine.facturemaroc.data.DocumentStatus
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.canBeCredited
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.PaymentDue
import com.yassine.facturemaroc.data.PaymentInfo
import com.yassine.facturemaroc.data.canBePaid
import com.yassine.facturemaroc.data.PaymentState
import com.yassine.facturemaroc.data.PaymentReminder
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.ItemField
import com.yassine.facturemaroc.data.MarginReport
import com.yassine.facturemaroc.data.PartyRole
import com.yassine.facturemaroc.data.Purchase
import com.yassine.facturemaroc.data.PurchaseOutcome
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.SavedItemStore
import com.yassine.facturemaroc.data.StockMovement
import com.yassine.facturemaroc.data.XmlExportOutcome
import com.yassine.facturemaroc.data.billedInMonth
import com.yassine.facturemaroc.data.billedTotalInMonth
import com.yassine.facturemaroc.data.creditedInMonth
import com.yassine.facturemaroc.data.vatCollectedInMonth
import com.yassine.facturemaroc.data.vatByRateInMonth
import com.yassine.facturemaroc.data.filterArticles
import com.yassine.facturemaroc.data.filterToReorder
import com.yassine.facturemaroc.data.labelTaken
import com.yassine.facturemaroc.data.nameTaken
import com.yassine.facturemaroc.data.outstanding
import com.yassine.facturemaroc.data.summarizeArticles
import com.yassine.facturemaroc.data.visibleItemFields
import com.yassine.facturemaroc.invoice.InvoiceXml
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
    onNewOrder: (InvoiceClient) -> Unit,
    onOpenRecord: (InvoiceRecord) -> Unit,
    onCreditNote: (InvoiceRecord) -> Unit,
    onConvertQuote: (InvoiceRecord) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val records by viewModel.invoices.invoices.collectAsStateWithLifecycle()
    val clients by viewModel.clients.clients.collectAsStateWithLifecycle()
    val items by viewModel.savedItems.items.collectAsStateWithLifecycle()
    val folders by viewModel.savedItems.folders.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.purchases.collectAsStateWithLifecycle()
    val shop by viewModel.shop.collectAsStateWithLifecycle()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val tab = HomeTab.entries[tabIndex]

    /*
     * Which half of the directory is on screen. The tab stays "Clients" because
     * that is the word a shop uses for the whole address book, and the two
     * chips split it: a supplier is the same record shape, and a separate tab
     * would make two kinds of party look like two kinds of screen.
     */
    var partyRoleIndex by rememberSaveable { mutableIntStateOf(0) }
    val partyRole = PartyRole.entries[partyRoleIndex]

    var clientEditorOpen by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<InvoiceClient?>(null) }
    var itemEditorOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SavedItem?>(null) }
    /* The article whose stock history is open, if any. */
    var historyItem by remember { mutableStateOf<SavedItem?>(null) }
    /* The physical count sheet, opened from the Articles tab. */
    var stocktakeOpen by remember { mutableStateOf(false) }
    /* The goods receipts list, and the receipt being written. */
    var purchasesOpen by remember { mutableStateOf(false) }
    var purchaseEditorOpen by remember { mutableStateOf(false) }
    /*
     * Where the Articles tab is filtering.
     *
     * `null` is "Tous": the tab opens on the whole catalogue, because a shop
     * looks for "the chair" far more often than it wants to browse shelves.
     * `""` is the articles in no folder, and any other value is a folder path
     * whose sub-folders are included — an article filed under Mobilier/Chaises is
     * still in Mobilier.
     */
    var articleFilter by rememberSaveable { mutableStateOf<String?>(null) }
    /*
     * Whether the list is limited to the articles at or below their reorder
     * threshold. It composes with the folder chips rather than replacing them:
     * "what is low in Mobilier?" is the question a shop actually asks.
     */
    var reorderOnly by rememberSaveable { mutableStateOf(false) }
    /*
     * Whether the folder screen is open.
     *
     * Folders are still a real tree — create, rename, delete, nest — but that
     * is rare work, so it lives behind the "Dossiers" action instead of being
     * the screen the tab lands on. [inFolder] and [showAllArticles] only
     * describe where the shop is standing inside it.
     */
    var foldersOpen by rememberSaveable { mutableStateOf(false) }
    var inFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllArticles by rememberSaveable { mutableStateOf(false) }
    var folderEditorOpen by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    /*
     * The folder a *new* folder is created inside, chosen when the dialog
     * opens. It cannot be read off the screen at save time: from the flat list
     * the parent is the active filter, and from the folder screen it is the
     * open folder, but "Tous les articles" means the root even though a folder
     * was open before it.
     */
    var newFolderParent by remember { mutableStateOf("") }
    /* The "+" offers article or folder on the Articles tab, so it needs a menu. */
    var addMenuOpen by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<String?>(null) }
    var detailRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var xmlRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var deleteInvoice by remember { mutableStateOf<InvoiceRecord?>(null) }
    var paymentRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var deleteClient by remember { mutableStateOf<InvoiceClient?>(null) }
    var deleteItem by remember { mutableStateOf<SavedItem?>(null) }
    var movingItem by remember { mutableStateOf<SavedItem?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // A write that failed leaves the screen and the file disagreeing, so it is
    // said out loud rather than left to be discovered after a relaunch.
    val writeError by viewModel.writeError.collectAsStateWithLifecycle()
    LaunchedEffect(writeError) {
        writeError?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearWriteError()
        }
    }

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
        HomeTab.CLIENTS -> "Nouveau ${partyRole.label.lowercase()}"
        HomeTab.ARTICLES -> "Ajouter"
    }

    /*
     * The folder screen is a stack — the grid, then one folder at each depth —
     * and both the top bar and the tab content read from these, so the title
     * can never disagree with the list about which folder is open.
     *
     * `null` is the grid, `""` is "Sans dossier", and anything else is a folder
     * path such as "Mobilier/Chaises".
     */
    val articleFolder = inFolder
    val managingFolders = tab == HomeTab.ARTICLES && foldersOpen
    val itemFields = visibleItemFields(shop)
    /*
     * What one unit of each article is worth, read once per catalogue change.
     *
     * The card shows the margin under the price only when a cost is really
     * known. It comes from the ledger under the shop's chosen method, with the
     * article's purchase price as the fallback for a shelf whose history has
     * never carried a price — which is exactly what a shop that has not entered
     * a receipt yet has.
     */
    val unitCosts = remember(items, purchases, shop.costMethod) {
        // One pass for the whole list: the ledger is read once, not once per
        // card, and the purchase price fills in where no history carries a cost.
        val known = viewModel.unitCosts()
        items.associate { item ->
            item.id to (known[item.id] ?: item.purchasePrice.takeIf { it > 0.0 })
        }
    }
    val marginOf: (SavedItem) -> MarginHint? = remember(unitCosts, shop) {
        { item ->
            val cost = unitCosts[item.id] ?: 0.0
            // A cost of zero is not a cost: it is "nobody typed one".
            if (cost <= 0.0) {
                null
            } else {
                val saleHt = if (shop.pricesIncludeVat && !shop.vatNotApplicable && shop.vatPercent > 0.0) {
                    item.price / (1.0 + shop.vatPercent / 100.0)
                } else {
                    item.price
                }
                val margin = saleHt - cost
                val percent = if (saleHt > 0.0) Math.round(margin / saleHt * 100.0).toInt() else 0
                MarginHint(
                    text = "Marge ${margin.asDirhams()} · $percent %",
                    negative = margin < 0.0,
                )
            }
        }
    }
    /*
     * The filter the list really applies. A folder can disappear while it is
     * the active filter (deleted from the folder screen, or its field switched
     * off in Paramètres), and filtering on a path that no longer exists would
     * leave an empty list under a chip that cannot clear itself.
     */
    val activeArticleFilter = articleFilter?.takeIf { path ->
        ItemField.FOLDER in itemFields &&
            (path.isEmpty() || folders.any { it.equals(path, ignoreCase = true) })
    }
    val articleList = remember(items, activeArticleFilter, reorderOnly) {
        filterToReorder(filterArticles(items, activeArticleFilter), reorderOnly)
    }
    val articleMatches = remember(items, query, reorderOnly) {
        val matched = if (query.isBlank()) emptyList() else items.filter { SavedItemStore.matches(it, query) }
        filterToReorder(matched, reorderOnly)
    }
    val subFolders = remember(folders, articleFolder) {
        // `""` is "Sans dossier", which has no shelves of its own. Without this,
        // children("") answered every top-level folder and put the whole
        // cabinet inside the unfiled pile.
        if (articleFolder.isNullOrEmpty()) {
            emptyList()
        } else {
            SavedItemStore.children(articleFolder, folders)
        }
    }
    val articleScopeItems = when {
        showAllArticles || articleFolder == null -> items
        else -> SavedItemStore.itemsIn(articleFolder, items)
    }

    // Up one level, not out of the app: the folder grid, then each parent, so
    // the system back gesture walks the same tree the tiles do — and at the
    // grid it leaves the folder screen for the article list.
    val goUp: () -> Unit = {
        val path = inFolder
        when {
            showAllArticles -> showAllArticles = false
            path == null -> foldersOpen = false
            path.contains(SavedItemStore.SEPARATOR) -> inFolder = SavedItemStore.parentOf(path)
            else -> inFolder = null
        }
    }
    BackHandler(enabled = managingFolders, onBack = goUp)

    /*
     * Where a folder made from here belongs. From the folder screen it is the
     * folder on screen (the root behind "Tous les articles"), and from the flat
     * list it is the folder the filter is on, so a shop filtering on Mobilier and
     * asking for a folder gets Mobilier/<name>, not a second Mobilier beside itself.
     */
    val newFolderHome = when {
        managingFolders -> if (showAllArticles) "" else inFolder.orEmpty()
        else -> activeArticleFilter.orEmpty()
    }
    val startNewFolder: (String) -> Unit = { parent ->
        newFolderParent = parent
        renamingFolder = null
        folderEditorOpen = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    when {
                        managingFolders -> Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    when {
                                        showAllArticles -> "Tous les articles"
                                        // The grid itself: the folders, not the
                                        // articles, so it is named for what it
                                        // manages rather than for the tab.
                                        articleFolder == null -> "Dossiers"
                                        articleFolder.isEmpty() -> "Sans dossier"
                                        // Just the folder's own name: the path
                                        // under it says where it lives.
                                        else -> SavedItemStore.nameOf(articleFolder)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .semantics { heading() },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    countLine(articleScopeItems.size, subFolders.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            // At the grid there is no path to show: the
                            // breadcrumb starts once a folder is open.
                            if (articleFolder != null || showAllArticles) {
                                Breadcrumb(
                                    path = articleFolder.orEmpty(),
                                    allArticles = showAllArticles,
                                    onNavigate = { target ->
                                        inFolder = target
                                        showAllArticles = false
                                        query = ""
                                    },
                                )
                            }
                        }

                        /*
                         * The Articles tab opens on the catalogue, not on a
                         * cabinet of folders, so its heading is the list itself
                         * and the count is what the current filter holds.
                         */
                        tab == HomeTab.ARTICLES -> Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "Articles",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    // A search is a different question, so the
                                    // count answers it: the filtered total
                                    // beside one visible row would read as a
                                    // list that lost four articles.
                                    countLine(
                                        if (query.isNotBlank()) articleMatches.size else articleList.size,
                                        0,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }

                        else -> Column {
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
                    }
                },
                navigationIcon = {
                    if (managingFolders) {
                        IconButton(onClick = goUp) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = if (articleFolder.orEmpty().contains(SavedItemStore.SEPARATOR)) {
                                    "Dossier parent"
                                } else {
                                    "Retour aux dossiers"
                                },
                            )
                        }
                    }
                },
                actions = {
                    /*
                     * Managing folders is rare work: it opens the existing
                     * folder screen, where create / rename / delete and the
                     * nesting still live. The tab itself no longer lands there.
                     */
                    if (tab == HomeTab.ARTICLES && !managingFolders && ItemField.FOLDER in itemFields) {
                        IconButton(
                            onClick = {
                                foldersOpen = true
                                inFolder = null
                                showAllArticles = false
                                query = ""
                            },
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = "Dossiers")
                        }
                    }
                    if (tab == HomeTab.ARTICLES && ItemField.STOCK in itemFields) {
                        IconButton(onClick = { purchasesOpen = true }) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Achats")
                        }
                        IconButton(onClick = { stocktakeOpen = true }) {
                            Icon(Icons.Filled.Checklist, contentDescription = "Inventaire")
                        }
                    }
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
            Box {
                FloatingActionButton(
                    onClick = {
                        when (tab) {
                            HomeTab.INVOICES -> onNewInvoice(null)
                            HomeTab.CLIENTS -> {
                                editingClient = null
                                clientEditorOpen = true
                            }
                            HomeTab.ARTICLES -> {
                                // Two things can be created here, and a shop that
                                // has just made a folder is one tap away from
                                // wanting an article in it. One button, a short
                                // menu, instead of a second floating button
                                // covering the list.
                                if (ItemField.FOLDER in itemFields) {
                                    addMenuOpen = true
                                } else {
                                    editingItem = null
                                    itemEditorOpen = true
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = fabLabel)
                }
                DropdownMenu(
                    expanded = addMenuOpen,
                    onDismissRequest = { addMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Nouvel article") },
                        leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                        onClick = {
                            addMenuOpen = false
                            editingItem = null
                            itemEditorOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (newFolderHome.isBlank()) "Nouveau dossier" else "Nouveau sous-dossier",
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            addMenuOpen = false
                            startNewFolder(newFolderHome)
                        },
                    )
                }
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

            if (tab == HomeTab.CLIENTS) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PartyRole.entries.forEachIndexed { index, candidate ->
                        ChoiceChip(
                            label = candidate.plural,
                            selected = partyRole == candidate,
                            onClick = { partyRoleIndex = index },
                        )
                    }
                }
            }

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
                    onShareReceipt = { record ->
                        scope.launch {
                            val outcome = viewModel.shareReceipt(record)
                            snackbar.showSnackbar(outcome.shareMessage())
                        }
                    },
                    onExportXml = ::exportXml,
                    onDuplicate = onOpenRecord,
                    onCreditNote = onCreditNote,
                    onConvertQuote = onConvertQuote,
                    onDelete = { deleteInvoice = it },
                    onPayment = { paymentRecord = it },
                )

                HomeTab.CLIENTS -> ClientsTab(
                    clients = clients,
                    records = records,
                    query = query,
                    role = partyRole,
                    onEdit = {
                        editingClient = it
                        clientEditorOpen = true
                    },
                    onInvoice = { onNewInvoice(it) },
                    onOrder = { onNewOrder(it) },
                    onStatement = { client ->
                        scope.launch {
                            val outcome = viewModel.shareStatement(client.name, records)
                            snackbar.showSnackbar(outcome.shareMessage())
                        }
                    },
                    onDelete = { deleteClient = it },
                )

                HomeTab.ARTICLES -> {
                    val onEditItem: (SavedItem) -> Unit = {
                        editingItem = it
                        itemEditorOpen = true
                    }
                    val onDeleteItem: (SavedItem) -> Unit = { deleteItem = it }

                    if (managingFolders) {
                        /*
                         * The folder screen: the grid, then one folder at each
                         * depth. A search typed on the grid looks through the
                         * whole catalogue, because from there the shop is asking
                         * "where did I put this?". Typed inside a folder it
                         * looks through that folder and everything below it,
                         * because there the question is "is it here?" and an article
                         * filed under Mobilier/Chaises is still in Mobilier.
                         */
                        val searching = query.isNotBlank()
                        val searchingEverything =
                            searching && (showAllArticles || articleFolder == null)
                        when {
                            searching -> {
                                val scopeItems = if (searchingEverything) {
                                    items
                                } else {
                                    SavedItemStore.itemsIn(
                                        articleFolder.orEmpty(),
                                        items,
                                        deep = true,
                                    )
                                }
                                ArticlesList(
                                    // One search rule for the whole app, the
                                    // same one the invoice picker uses, barcode
                                    // included.
                                    items = remember(scopeItems, query) {
                                        scopeItems.filter { SavedItemStore.matches(it, query) }
                                    },
                                    // A result can come from a sub-folder, and
                                    // an article the shop cannot place is an
                                    // article it will not find again.
                                    showFolder = ItemField.FOLDER in itemFields &&
                                        (searchingEverything || showAllArticles || subFolders.isNotEmpty()),
                                    fields = itemFields,
                                    emptyTitle = "Aucun résultat",
                                    emptyMessage = "Essayez un autre mot.",
                                    onEdit = onEditItem,
                                    onDelete = onDeleteItem,
                                    onMove = { movingItem = it },
                                    onHistory = { historyItem = it },
                                    marginOf = marginOf,
                                )
                            }

                            showAllArticles || articleFolder != null -> FolderContents(
                                articles = articleScopeItems,
                                subFolders = subFolders,
                                allItems = items,
                                allArticles = showAllArticles,
                                nested = !articleFolder.isNullOrEmpty(),
                                onOpenFolder = { path ->
                                    inFolder = path
                                    query = ""
                                },
                                onCreateFolder = {
                                    startNewFolder(if (articleFolder == null) "" else articleFolder)
                                },
                                onRenameFolder = {
                                    renamingFolder = it
                                    folderEditorOpen = true
                                },
                                onDeleteFolder = { deleteFolder = it },
                                onEdit = onEditItem,
                                onDelete = onDeleteItem,
                                onMove = { movingItem = it },
                                onHistory = { historyItem = it },
                                marginOf = marginOf,
                            )

                            else -> ArticlesFoldersGrid(
                                items = items,
                                folders = folders,
                                onOpenAll = { showAllArticles = true },
                                onOpenFolder = { path ->
                                    inFolder = path
                                    query = ""
                                },
                                onCreateFolder = {
                                    startNewFolder(if (articleFolder == null) "" else articleFolder)
                                },
                                onRenameFolder = {
                                    renamingFolder = it
                                    folderEditorOpen = true
                                },
                                onDeleteFolder = { deleteFolder = it },
                            )
                        }
                    } else if (query.isNotBlank()) {
                        /*
                         * From the flat list, a search reaches the whole
                         * catalogue — barcode included — because the article
                         * the shop wants may be filed somewhere it forgot.
                         */
                        ArticlesList(
                            items = articleMatches,
                            showFolder = ItemField.FOLDER in itemFields,
                            fields = itemFields,
                            emptyTitle = "Aucun résultat",
                            emptyMessage = "Essayez un autre mot.",
                            onEdit = onEditItem,
                            onDelete = onDeleteItem,
                            onMove = { movingItem = it },
                            onHistory = { historyItem = it },
                            marginOf = marginOf,
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            if (ItemField.FOLDER in itemFields || ItemField.STOCK in itemFields) {
                                ArticleFilterChips(
                                    folders = folders,
                                    selected = activeArticleFilter,
                                    showFolders = ItemField.FOLDER in itemFields,
                                    onSelect = {
                                        articleFilter = it
                                        // A chip is a new question; a search
                                        // left over from the previous one would
                                        // answer a different one.
                                        query = ""
                                    },
                                    reorderCount = items.count { it.needsReorder },
                                    reorderOnly = reorderOnly,
                                    onToggleReorder = { reorderOnly = !reorderOnly },
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                ArticlesList(
                                    items = articleList,
                                    showFolder = ItemField.FOLDER in itemFields,
                                    fields = itemFields,
                                    emptyTitle = when {
                                        items.isEmpty() -> "Aucun article enregistré"
                                        reorderOnly -> "Aucun article à commander"
                                        else -> "Aucun article ici"
                                    },
                                    emptyMessage = when {
                                        items.isEmpty() ->
                                            "Ajoutez ce que vous vendez souvent — une prestation, " +
                                                "un produit, une pièce — et retrouvez-le en un geste " +
                                                "sur chaque facture."
                                        reorderOnly ->
                                            "Tous les articles de cette sélection sont au-dessus " +
                                                "de leur seuil de réapprovisionnement."
                                        else -> "Essayez un autre filtre."
                                    },
                                    onEdit = onEditItem,
                                    onDelete = onDeleteItem,
                                    onMove = { movingItem = it },
                                    onHistory = { historyItem = it },
                                    marginOf = marginOf,
                                )
                            }
                            ArticlesSummary(items = articleList, fields = itemFields)
                        }
                    }
                }
            }
        }
    }

    if (clientEditorOpen) {
        ClientEditorDialog(
            initial = editingClient,
            onDismiss = { clientEditorOpen = false },
            onSave = { updated ->
                val previous = editingClient
                val target = updated.name.trim()
                // A client is keyed by name and role, which is the pair the
                // store replaces on, so a rename onto a name the directory
                // already holds would overwrite that party — its ICE, its
                // address — instead of adding a second one.
                if (clients.nameTaken(target, updated.role, excluding = previous)) {
                    val kind = if (updated.role == PartyRole.CLIENT) "clients" else "fournisseurs"
                    scope.launch {
                        snackbar.showSnackbar("« $target » existe déjà dans vos $kind.")
                    }
                } else {
                    if (previous != null &&
                        (!previous.name.equals(updated.name, ignoreCase = true) ||
                            previous.role != updated.role)
                    ) {
                        // Renamed, or moved to the other half of the directory:
                        // either way the old key must go or the shop keeps a ghost.
                        viewModel.clients.delete(previous)
                    }
                    viewModel.clients.save(updated)
                    clientEditorOpen = false
                }
            },
            defaultRole = editingClient?.role ?: partyRole,
        )
    }

    if (itemEditorOpen) {
        ItemEditorDialog(
            initial = editingItem,
            folders = folders,
            // A new article lands where the shop is looking: in the folder it
            // opened from the folder screen, or in the folder its filter is on.
            defaultFolder = when {
                managingFolders && !showAllArticles -> inFolder.orEmpty()
                managingFolders -> ""
                else -> activeArticleFilter.orEmpty()
            },
            fields = itemFields,
            // The sale price is asked for in the basis the shop quotes, so the
            // number typed here is the number the customer is told.
            pricesIncludeVat = shop.pricesIncludeVat,
            vatPercent = if (shop.vatNotApplicable) 0.0 else shop.vatPercent,
            onDismiss = { itemEditorOpen = false },
            onSave = { updated ->
                val previous = editingItem
                val target = updated.label.trim()
                // The label is the catalogue's key. Saving under a name that is
                // already taken replaces that article — including the quantity
                // on its shelf — with a fresh row at zero, so a rename onto an
                // existing name is refused exactly like a fresh duplicate.
                if (items.labelTaken(target, excluding = previous)) {
                    scope.launch {
                        snackbar.showSnackbar(
                            "« $target » existe déjà. Ouvrez-le pour le modifier.",
                        )
                    }
                } else {
                    // The id travels with the edit, so a rename keeps its
                    // shelf, its ledger entries and its threshold instead of
                    // becoming a new article under the new name.
                    viewModel.savedItems.save(
                        if (previous != null) updated.copy(id = previous.id) else updated,
                    )
                    itemEditorOpen = false
                }
            },
        )
    }

    historyItem?.let { item ->
        // Read once per opening: the ledger is a file, and the row is a record
        // of what already happened, not a live counter.
        val entries = remember(item.id) { viewModel.stockEntries(item.id) }
        StockHistoryDialog(
            item = item,
            entries = entries,
            onDismiss = { historyItem = null },
        )
    }

    if (stocktakeOpen) {
        StocktakeDialog(
            items = items,
            // The ledger, not the cached field: a count must not inherit a
            // drift nobody has looked at yet.
            expectedOf = { item -> viewModel.savedItems.ledger.balance(item.id) },
            onDismiss = { stocktakeOpen = false },
            onConfirm = { counts, reason ->
                val corrected = viewModel.recordStocktake(counts, reason)
                stocktakeOpen = false
                scope.launch {
                    snackbar.showSnackbar(
                        if (corrected == 0) {
                            "Inventaire terminé : aucun écart."
                        } else {
                            "Inventaire enregistré : $corrected écart${if (corrected > 1) "s" else ""} corrigé${if (corrected > 1) "s" else ""}."
                        },
                    )
                }
            },
        )
    }

    if (purchasesOpen) {
        PurchasesDialog(
            purchases = purchases,
            onDismiss = { purchasesOpen = false },
            onNew = {
                purchasesOpen = false
                purchaseEditorOpen = true
            },
            onDelete = { purchase ->
                val removed = viewModel.deletePurchase(purchase)
                scope.launch {
                    snackbar.showSnackbar(
                        if (removed) {
                            "Bon ${purchase.number} supprimé : le stock a été rendu."
                        } else {
                            "Le bon ${purchase.number} n'a pas pu être supprimé."
                        },
                    )
                }
            },
        )
    }

    if (purchaseEditorOpen) {
        PurchaseEditorDialog(
            number = viewModel.nextPurchaseNumber(),
            // Only the articles the shop counts: a service has no shelf, and
            // offering it would let a receipt write stock to nothing.
            items = items,
            suppliers = clients.filter { it.role == PartyRole.SUPPLIER },
            onDismiss = { purchaseEditorOpen = false },
            onSave = { purchase ->
                when (val outcome = viewModel.recordPurchase(purchase)) {
                    is PurchaseOutcome.Saved -> {
                        purchaseEditorOpen = false
                        scope.launch {
                            snackbar.showSnackbar(
                                "Réception ${purchase.number} enregistrée : " +
                                    "${purchase.unitCount} unité" +
                                    (if (purchase.unitCount > 1) "s" else "") +
                                    " ajoutée" +
                                    (if (purchase.unitCount > 1) "s" else "") +
                                    " au stock.",
                            )
                        }
                    }

                    is PurchaseOutcome.UnknownItem -> scope.launch {
                        snackbar.showSnackbar(
                            "« ${outcome.label} » n'existe plus dans vos articles. " +
                                "Retirez-le du bon ou recréez l'article.",
                        )
                    }

                    is PurchaseOutcome.Invalid -> scope.launch {
                        snackbar.showSnackbar(outcome.message)
                    }

                    PurchaseOutcome.WriteFailed -> scope.launch {
                        snackbar.showSnackbar("La réception n'a pas pu être enregistrée.")
                    }
                }
            },
        )
    }

    detailRecord?.let { record ->
        // Read once per opening rather than on every recomposition: the ledger
        // is a file, and the row only answers "is this PDF really kept?".
        val pdfArchived = remember(record.data.number) {
            runCatching { viewModel.archive.hasPdf(record.data.number) }.getOrDefault(false)
        }
        InvoiceDetailSheet(
            record = record,
            pdfArchived = pdfArchived,
            reminderAvailable = PaymentReminder.available(record),
            onDismiss = { detailRecord = null },
            onReminder = {
                detailRecord = null
                if (!viewModel.shareReminder(record)) {
                    scope.launch {
                        snackbar.showSnackbar("Rien à relancer sur ce document.")
                    }
                }
            },
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
            onCreditNote = {
                detailRecord = null
                onCreditNote(record)
            },
            onConvertQuote = {
                detailRecord = null
                onConvertQuote(record)
            },
        )
    }

    paymentRecord?.let { opened ->
        // Re-read from the store so removing an entry updates the list in place
        // instead of leaving the dialog on the copy it was opened with.
        val record = records.firstOrNull { it.id == opened.id } ?: opened
        PaymentDialog(
            record = record,
            onAdd = { payment ->
                viewModel.addPayment(record.id, payment)
                paymentRecord = null
            },
            onRemove = { index -> viewModel.removePayment(record.id, index) },
            onDismiss = { paymentRecord = null },
        )
    }

    deleteInvoice?.let { record ->
        // Only a draft reaches this dialog: an issued document is corrected by
        // an avoir, so the branch that used to talk about the customer keeping
        // "the PDF already sent" no longer exists.
        ConfirmDeleteDialog(
            title = "Supprimer ce brouillon ?",
            message = "${record.data.number} · ${record.title} — ${record.data.total.asDirhams()}. " +
                "Le brouillon disparaît de l'historique.",
            onDismiss = { deleteInvoice = null },
            onConfirm = {
                viewModel.deleteRecord(record.id)
                deleteInvoice = null
                scope.launch { snackbar.showSnackbar("Brouillon supprimé.") }
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

    movingItem?.let { item ->
        FolderPickerDialog(
            item = item,
            folders = folders,
            onDismiss = { movingItem = null },
            onPick = { path ->
                movingItem = null
                // Picking the folder it is already in is not a move: no write,
                // no message, no reordering of the list under the finger.
                if (!path.equals(item.folder, ignoreCase = true)) {
                    viewModel.savedItems.save(item.copy(folder = path))
                    scope.launch {
                        snackbar.showSnackbar(
                            if (path.isBlank()) {
                                "« ${item.label} » est maintenant sans dossier."
                            } else {
                                "« ${item.label} » rangé dans « ${SavedItemStore.display(path)} »."
                            },
                        )
                    }
                }
            },
        )
    }

    if (folderEditorOpen) {
        val previous = renamingFolder
        FolderEditorDialog(
            initial = previous?.let { SavedItemStore.nameOf(it) },
            parent = if (previous == null) newFolderParent else SavedItemStore.parentOf(previous),
            onSave = { name ->
                if (previous == null) {
                    val created = viewModel.savedItems.createFolder(name, newFolderParent)
                    if (created != null) {
                        // A folder made while standing in another one opens, so
                        // the shop can put something in it straight away. Made
                        // from the flat list, it opens the folder screen too:
                        // a folder nobody can see is a folder the shop will
                        // create twice.
                        inFolder = created
                        showAllArticles = false
                        // The new folder is what the shop should see, not the
                        // results of a search typed before it existed.
                        query = ""
                        if (!managingFolders) foldersOpen = true
                        folderEditorOpen = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Ce dossier existe déjà ici.") }
                    }
                } else {
                    val renamed = viewModel.savedItems.renameFolder(previous, name)
                    if (renamed != null) {
                        // Renaming the folder that is open (or one above it)
                        // keeps the shop where it was standing instead of
                        // dropping it back on the grid.
                        inFolder?.let { open ->
                            if (SavedItemStore.isIn(open, previous)) {
                                inFolder = SavedItemStore.rebase(open, previous, renamed)
                            }
                        }
                        folderEditorOpen = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Ce nom est déjà utilisé ici.") }
                    }
                }
            },
            onDismiss = { folderEditorOpen = false },
        )
    }

    deleteFolder?.let { folder ->
        val inSubtree = items.count { SavedItemStore.isIn(it.folder, folder) }
        val subs = folders.count { it != folder && SavedItemStore.isIn(it, folder) }
        val parent = SavedItemStore.parentOf(folder)
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("Supprimer ce dossier ?") },
            text = {
                Text(
                    listOf(
                        "« ${SavedItemStore.nameOf(folder)} » sera supprimé" +
                            if (subs == 0) {
                                "."
                            } else {
                                " avec ${if (subs == 1) "son sous-dossier" else "ses $subs sous-dossiers"}."
                            },
                        when (inSubtree) {
                            0 -> "Aucun article n'y est rangé."
                            1 -> "1 article sera conservé dans « ${folderLabel(parent)} »."
                            else -> "$inSubtree articles seront conservés dans « ${folderLabel(parent)} »."
                        },
                    ).joinToString(" "),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.savedItems.deleteFolder(folder)
                        // Deleting the folder that is open, or one above it,
                        // would leave the tab standing on a name that no longer
                        // exists.
                        inFolder?.let { open ->
                            if (SavedItemStore.isIn(open, folder)) inFolder = parent.ifEmpty { null }
                        }
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

/** "Sans dossier" for the root, the folder's own name otherwise. */
private fun folderLabel(path: String): String =
    if (path.isEmpty()) "Sans dossier" else SavedItemStore.nameOf(path)

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

/**
 * The margin line under an article's price.
 *
 * [negative] is carried separately rather than read off the text: a margin of
 * "−12 DH · −8 %" is the number the shop must act on, and formatting it as an
 * error colour is a decision for the row, not for the arithmetic.
 */
private data class MarginHint(val text: String, val negative: Boolean)

@Composable
private fun InvoicesTab(
    records: List<InvoiceRecord>,
    query: String,
    onOpenRecord: (InvoiceRecord) -> Unit,
    onShowDetail: (InvoiceRecord) -> Unit,
    onShare: (InvoiceRecord) -> Unit,
    onShareReceipt: (InvoiceRecord) -> Unit,
    onExportXml: (InvoiceRecord) -> Unit,
    onDuplicate: (InvoiceRecord) -> Unit,
    onCreditNote: (InvoiceRecord) -> Unit,
    onConvertQuote: (InvoiceRecord) -> Unit,
    onDelete: (InvoiceRecord) -> Unit,
    onPayment: (InvoiceRecord) -> Unit,
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
                    onShareReceipt = { onShareReceipt(record) },
                    onExportXml = { onExportXml(record) },
                    onDuplicate = { onDuplicate(record) },
                    onCreditNote = { onCreditNote(record) },
                    onConvertQuote = { onConvertQuote(record) },
                    onDelete = { onDelete(record) },
                    onPayment = { onPayment(record) },
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
                    onShareReceipt = { onShareReceipt(record) },
                    onExportXml = { onExportXml(record) },
                    onDuplicate = { onDuplicate(record) },
                    onCreditNote = { onCreditNote(record) },
                    onConvertQuote = { onConvertQuote(record) },
                    onDelete = { onDelete(record) },
                    onPayment = { onPayment(record) },
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
    val creditedThisMonth = remember(records, monthStamp) {
        records.creditedInMonth(monthStamp)
    }
    // Less the credit notes: a facture cancelled by an avoir billed nothing.
    val total = remember(records, monthStamp) {
        records.billedTotalInMonth(monthStamp)
    }
    val margin = remember(records, monthStamp) {
        MarginReport.forMonth(records, monthStamp)
    }
    // What the shop owes the tax office for the month: the same figure the
    // documents print, added up and net of the credit notes.
    val vatCollected = remember(records, monthStamp) {
        records.vatCollectedInMonth(monthStamp)
    }
    // The same figure broken down by rate, so a mixed-activity month shows both
    // taxes instead of one number nobody can reconcile with the paper.
    val vatByRate = remember(records, monthStamp) {
        records.vatByRateInMonth(monthStamp)
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            // The shop opens this screen to answer one question: who still
            // owes me money. "Facturé ce mois-ci" is the accountant's question,
            // so it moves down to a small line and the money still out there
            // becomes the dominant figure on the page.
            val outstanding = remember(records) { records.outstanding() }
            Text(
                "Reste à encaisser",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                outstanding.due.asDirhams(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFeatureSettings = "tnum",
                ),
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
                    when (outstanding.documents) {
                        0 -> "Aucun document en attente"
                        1 -> "1 document en attente"
                        else -> "${outstanding.documents} documents en attente"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
            if (outstanding.overdue > 0.0) {
                Text(
                    "Dont ${outstanding.overdue.asDirhams()} en retard",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Facturé ce mois-ci",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Text(
                total.asDirhams(),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The total is net of the credit notes, so the count has to
                // say so: "1 facture" next to a zero total reads as a bug
                // unless the avoir is named.
                when {
                    issuedThisMonth.isEmpty() && creditedThisMonth.isEmpty() ->
                        "Aucune facture émise ce mois-ci"

                    creditedThisMonth.isEmpty() && issuedThisMonth.size == 1 ->
                        "1 facture émise ce mois-ci"

                    creditedThisMonth.isEmpty() ->
                        "${issuedThisMonth.size} factures émises ce mois-ci"

                    else ->
                        "${issuedThisMonth.size} facture" +
                            (if (issuedThisMonth.size > 1) "s" else "") +
                            " et ${creditedThisMonth.size} avoir" +
                            (if (creditedThisMonth.size > 1) "s" else "") +
                            " ce mois-ci"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
            // The tax the month collected, net of its credit notes. A shop reads
            // this figure when it sets money aside, and hiding it under a detail
            // screen is exactly when it gets forgotten.
            if (vatCollected != 0.0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "TVA collectée : ${vatCollected.asDirhams()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
                if (vatByRate.size > 1) {
                    Text(
                        vatByRate.joinToString(" · ") { recap ->
                            val rate = if (recap.rate == Math.floor(recap.rate)) {
                                recap.rate.toInt().toString()
                            } else {
                                recap.rate.toString()
                            }
                            "TVA $rate % ${recap.amount.asDirhams()}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    )
                }
            }
            // The month's margin, under what was billed: a shop that sold a lot
            // and earned nothing needs to see both numbers together. Lines whose
            // cost is unknown are named instead of being counted as free goods.
            if (margin.linesWithCost > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Marge du mois",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                )
                Text(
                    buildString {
                        append(margin.margin.asDirhams())
                        margin.marginPercent?.let { append(" · ${Math.round(it).toInt()} %") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    "CA HT ${margin.revenueHt.asDirhams()} · coût ${margin.cost.asDirhams()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
                if (margin.linesWithoutCost > 0) {
                    Text(
                        if (margin.linesWithoutCost == 1) {
                            "1 ligne sans coût connu, exclue du calcul."
                        } else {
                            "${margin.linesWithoutCost} lignes sans coût connu, exclues du calcul."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    record: InvoiceRecord,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onShareReceipt: () -> Unit,
    onExportXml: () -> Unit,
    onDuplicate: () -> Unit,
    onCreditNote: () -> Unit,
    onConvertQuote: () -> Unit,
    onDelete: () -> Unit,
    onPayment: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val draft = record.isDraft
    // A credit note is money the shop gave back: shown like a sale, it reads as
    // one more invoice in the list and its total as turnover.
    val credit = record.data.isCredit

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
            // The amount gets a hard ceiling so a six-figure invoice cannot eat
            // the client's name, which is the only thing on this row a shop
            // reads at a glance. The status is one line under it, never a stack
            // of pills: three pills wrapped inside their own shape at 360 dp.
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 132.dp),
            ) {
                Text(
                    if (credit) "−${record.data.total.asDirhams()}" else record.data.total.asDirhams(),
                    style = MaterialTheme.typography.numerals,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (credit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val status = DocumentStatus.of(record)
                if (status.text.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        status.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = status.tone.color(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        if (record.data.paidAmount > 0.0) {
                            DropdownMenuItem(
                                text = { Text("Partager le reçu") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Receipt, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onShareReceipt()
                                },
                            )
                        }
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
                        // A devis, a proforma, a bon de commande or an avoir asks
                        // for no money, so it gets no payment action at all.
                        if (record.data.documentType.canBePaid &&
                            (record.data.balanceDue > 0.0 || record.data.paidAmount > 0.0)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            record.data.balanceDue > 0.0 &&
                                                record.data.paidAmount > 0.0 ->
                                                "Encaisser le reste…"
                                            record.data.balanceDue > 0.0 -> "Encaisser…"
                                            else -> "Paiements…"
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Payments, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onPayment()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Dupliquer") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        if (record.data.documentType == DocumentType.DEVIS) {
                            DropdownMenuItem(
                                text = { Text("Convertir en facture") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onConvertQuote()
                                },
                            )
                        }
                        if (record.data.documentType == DocumentType.DEVIS) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onConvertQuote,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Convertir en facture")
                }
            }
            if (record.data.documentType.canBeCredited) {
                            DropdownMenuItem(
                                text = { Text("Créer un avoir") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Remove, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onCreditNote()
                                },
                            )
                        }
                    }
                    // Only a draft can be deleted. An issued document has
                    // been handed to a customer and, under Moroccan rules,
                    // leaves the history through an avoir or a rectifying
                    // invoice — never by disappearing from the shop's own
                    // records, which is the difference between a correction and
                    // a document that never existed.
                    if (record.isDeletable) {
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
}

@Composable
private fun ClientsTab(
    clients: List<InvoiceClient>,
    records: List<InvoiceRecord>,
    query: String,
    role: PartyRole,
    onEdit: (InvoiceClient) -> Unit,
    onInvoice: (InvoiceClient) -> Unit,
    onOrder: (InvoiceClient) -> Unit,
    onStatement: (InvoiceClient) -> Unit,
    onDelete: (InvoiceClient) -> Unit,
) {
    val noun = role.label.lowercase()
    val saved = remember(clients, role) { clients.filter { it.role == role } }

    val filtered = remember(saved, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) saved else saved.filter {
            listOf(it.name, it.phone, it.email, it.ice).any { field -> field.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.People,
            title = if (saved.isEmpty()) "Aucun $noun enregistré" else "Aucun résultat",
            message = if (saved.isEmpty()) {
                when (role) {
                    PartyRole.CLIENT ->
                        "Enregistrez vos clients une fois. Sur chaque facture, leur nom, ICE et adresse " +
                            "se remplissent sans les retaper."
                    PartyRole.SUPPLIER ->
                        "Enregistrez vos fournisseurs une fois. Un bon de commande reprend leur nom, " +
                            "ICE et adresse sans les retaper."
                }
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
            SectionHeading(
                if (role == PartyRole.CLIENT) "Clients enregistrés" else "Fournisseurs enregistrés",
                trailing = "${filtered.size}",
            )
        }
        items(filtered, key = { "${role.id}-${it.name.lowercase()}" }) { client ->
            ClientCard(
                client = client,
                documentCount = records.count { record ->
                    !record.isDraft && when (role) {
                        PartyRole.CLIENT ->
                            record.data.documentType != DocumentType.BON_DE_COMMANDE &&
                                record.data.client.name.equals(client.name, ignoreCase = true)
                        PartyRole.SUPPLIER ->
                            record.data.documentType == DocumentType.BON_DE_COMMANDE &&
                                record.data.supplier?.name
                                    ?.equals(client.name, ignoreCase = true) == true
                    }
                },
                onClick = { onEdit(client) },
                onDocument = { if (role == PartyRole.CLIENT) onInvoice(client) else onOrder(client) },
                // A statement is a client document. It is only offered when
                // there is something to state: a client with a single devis
                // would get a button that produces nothing.
                onStatement = if (
                    role == PartyRole.CLIENT &&
                    ClientStatements.hasStatement(records, client.name)
                ) {
                    { onStatement(client) }
                } else {
                    null
                },
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
    onDocument: () -> Unit,
    onStatement: (() -> Unit)?,
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
                        text = {
                            Text(
                                if (client.role == PartyRole.SUPPLIER) {
                                    "Bon de commande à ce fournisseur"
                                } else {
                                    "Facturer ce client"
                                },
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDocument()
                        },
                    )
                    if (onStatement != null) {
                        DropdownMenuItem(
                            text = { Text("Relevé client") },
                            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onStatement()
                            },
                        )
                    }
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

/**
 * The line under the Articles title: what is on this level, not below it.
 *
 * "2 articles · 1 sous-dossier" is honest about a folder whose shelves hold
 * more, because the tiles underneath say how much each of those holds.
 */
private fun countLine(articles: Int, folders: Int): String {
    val items = when (articles) {
        0 -> "Aucun article"
        1 -> "1 article"
        else -> "$articles articles"
    }
    if (folders == 0) return items
    return "$items · $folders sous-dossier${if (folders > 1) "s" else ""}"
}

/** One step of the breadcrumb. `path` is null for the folder grid itself. */
private data class Crumb(val label: String, val path: String?)

/**
 * Every step from the Articles grid down to the folder on screen.
 *
 * The bar used to print the whole path in one Text, which ellipsized a deep
 * folder at whatever the bar could hold and could only be left one level at a
 * time. Here each step is tappable, so the shop jumps straight to any parent.
 */
private fun crumbsFor(path: String, allArticles: Boolean): List<Crumb> {
    val root = Crumb("Articles", null)
    if (allArticles) return listOf(root, Crumb("Tous les articles", null))
    if (path.isEmpty()) return listOf(root, Crumb("Sans dossier", ""))
    val parts = path.split(SavedItemStore.SEPARATOR).filter { it.isNotEmpty() }
    val crumbs = mutableListOf(root)
    parts.forEachIndexed { index, name ->
        crumbs += Crumb(
            name,
            parts.take(index + 1).joinToString(SavedItemStore.SEPARATOR),
        )
    }
    return crumbs
}

/**
 * The path to the open folder, on one scrollable line.
 *
 * Ancestors are buttons; the last step is the folder itself and does nothing.
 * The row opens scrolled to its end so the deepest folder — the one the shop is
 * actually standing in — is what a narrow screen shows, instead of the start.
 */
@Composable
private fun Breadcrumb(
    path: String,
    allArticles: Boolean,
    onNavigate: (String?) -> Unit,
) {
    val crumbs = remember(path, allArticles) { crumbsFor(path, allArticles) }
    val scroll = rememberScrollState()
    // maxValue is 0 until the row has been measured; reading it as a key makes
    // the effect run again once it knows how far it can scroll.
    LaunchedEffect(crumbs, scroll.maxValue) {
        scroll.scrollTo(scroll.maxValue)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            val current = index == crumbs.lastIndex
            Text(
                crumb.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                modifier = if (current) {
                    Modifier
                } else {
                    Modifier
                        .clickable { onNavigate(crumb.path) }
                        .padding(vertical = 2.dp)
                },
            )
        }
    }
}

/**
 * The Articles tab at rest: the folders, not the articles.
 *
 * A shop with a hundred articles cannot scan a hundred rows for "Montage", so
 * the folders come first the way a file manager does it, and the list behind a
 * tile is short by construction.
 *
 * "Tous" is a tile here rather than a filter chip. A chip would sit above the
 * folders and change what is drawn beneath it; a tile is somewhere the shop
 * goes and comes back from, which is what a folder and a full list have in
 * common.
 */
@Composable
private fun ArticlesFoldersGrid(
    items: List<SavedItem>,
    folders: List<String>,
    onOpenAll: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        /*
         * An empty catalogue still gets the grid, not an empty-state screen:
         * the "Nouveau dossier" tile is the only place a folder is created, so
         * hiding it behind an empty state left a fresh install with no way to
         * make the first one at all.
         */
        if (items.isEmpty() && folders.isEmpty()) {
            item(key = "empty-hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Aucun article enregistré pour l'instant. Vous pouvez déjà " +
                        "créer vos dossiers, puis y ranger vos articles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        item(key = "all") {
            FolderTile(
                title = "Tous",
                count = items.size,
                icon = Icons.Filled.Inventory2,
                onClick = onOpenAll,
            )
        }
        item(key = "unfiled") {
            FolderTile(
                title = "Sans dossier",
                count = items.count { it.folder.isBlank() },
                icon = Icons.Filled.Inbox,
                onClick = { onOpenFolder("") },
            )
        }
        gridItems(folders.filter { SavedItemStore.parentOf(it).isEmpty() }, key = { "folder-$it" }) { folder ->
            FolderTile(
                title = folder,
                // Everything the shelf holds, sub-shelves included: "Mobilier"
                // that says 2 while 3 more articles are filed under Mobilier/Chaises
                // would read as an inventory that lost stock.
                count = SavedItemStore.itemsIn(folder, items, deep = true).size,
                icon = Icons.Filled.Folder,
                onClick = { onOpenFolder(folder) },
                onRename = { onRenameFolder(folder) },
                onDelete = { onDeleteFolder(folder) },
            )
        }
        item(key = "new-folder") {
            NewFolderTile(onCreateFolder)
        }
    }
}

/**
 * One open folder: the shelves inside it first, then what sits on it directly.
 *
 * Both are drawn on one scrolling surface by one grid, because they are one
 * place: walking into "Mobilier" and seeing "Chaises" above the articles filed
 * straight there is how a cupboard looks. A second list would need a second
 * scroll position and a sentence explaining that it is not the whole folder.
 *
 * Sub-folder tiles carry everything inside them, sub-shelves included, while
 * the title above counts what is on this level — a tile that said "2" while
 * three more articles sat in Mobilier/Chaises would read as inventory that vanished.
 */
@Composable
private fun FolderContents(
    articles: List<SavedItem>,
    subFolders: List<String>,
    allItems: List<SavedItem>,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onEdit: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onMove: (SavedItem) -> Unit,
    /** Opening one article's stock history; nothing for a service. */
    onHistory: (SavedItem) -> Unit = {},
    /** The margin line under the price, when a cost is known. */
    marginOf: (SavedItem) -> MarginHint? = { null },
    /** The "Tous les articles" view: same grid, but nothing is a sub-folder. */
    allArticles: Boolean = false,
    /**
     * Whether a folder made here can be nested at all. "Sans dossier" has no
     * shelves of its own, so it offers a plain "Nouveau dossier" rather than
     * promising a sub-folder it would file at the root anyway.
     */
    nested: Boolean = true,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(subFolders, key = { "sub-$it" }) { path ->
            FolderTile(
                title = SavedItemStore.nameOf(path),
                count = SavedItemStore.itemsIn(path, allItems, deep = true).size,
                icon = Icons.Filled.Folder,
                onClick = { onOpenFolder(path) },
                onRename = { onRenameFolder(path) },
                onDelete = { onDeleteFolder(path) },
            )
        }
        item(key = "new-subfolder") {
            NewFolderTile(
                onCreateFolder,
                label = if (allArticles || !nested) "Nouveau dossier" else "Nouveau sous-dossier",
            )
        }
        if (articles.isNotEmpty()) {
            item(key = "articles-heading", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(
                    "Articles",
                    trailing = "${articles.size} ici",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            item(key = "no-articles", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (allArticles) {
                        "Aucun article enregistré."
                    } else {
                        "Aucun article directement dans ce dossier."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // The alias, because `items` inside a grid scope is the count-based
        // overload and would take the list for a number of blank cells.
        gridItems(
            articles,
            key = { "item-${it.id.ifBlank { it.label.lowercase() }}" },
            span = { GridItemSpan(maxLineSpan) },
        ) { item ->
            ItemCard(
                item = item,
                showFolder = false,
                onClick = { onEdit(item) },
                onDelete = { onDelete(item) },
                onMove = { onMove(item) },
                onHistory = { onHistory(item) },
                marginOf = marginOf,
            )
        }
    }
}

/**
 * One square in the folder grid.
 *
 * Rename and delete live on the tile itself, in a small overflow menu, rather
 * than in a separate "manage folders" dialog. The action belongs to the folder
 * it acts on, and a dialog listing every folder in order to edit one of them
 * was a second copy of this grid with different controls.
 */
@Composable
private fun FolderTile(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // The name sits at the bottom of the tile, the way a file
                // manager draws it: the icon is what the eye finds, the label is
                // what confirms it, and the gap between them is what makes a
                // grid of folders scan faster than a list of rows.
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (count) {
                            0 -> "Aucun article"
                            1 -> "1 article"
                            else -> "$count articles"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onRename != null && onDelete != null) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Actions sur « $title »",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Renommer") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
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
}

/** The "Nouveau dossier" square that closes the grid. */
@Composable
private fun NewFolderTile(onClick: () -> Unit, label: String = "Nouveau dossier") {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The one line under the list: how much there is.
 *
 * A shop counting its shelves does not want a report — it wants to know that
 * the filter it just tapped holds 46 articles and what they cost. Only the parts
 * whose fields are visible are said, so a shop that hides Stock is not told a
 * quantity it never records.
 */
@Composable
private fun ArticlesSummary(items: List<SavedItem>, fields: Set<ItemField>) {
    if (items.isEmpty()) return
    val summary = remember(items, fields) { summarizeArticles(items, fields) }
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (summary.count == 1) "1 article" else "${summary.count} articles",
                style = style,
                color = color,
            )
            if (summary.units > 0) {
                Text(
                    if (summary.units == 1) "1 unité" else "${summary.units} unités",
                    style = style,
                    color = color,
                )
            }
            if (summary.stockValue > 0.0) {
                Text(
                    "${summary.stockValue.asDirhams()} d'achat",
                    style = style,
                    color = color,
                )
            }
            // The number the filter chip also carries, but here it is always
            // visible: a shop reading the bottom line should not have to notice
            // a chip to learn that three shelves need refilling.
            if (summary.reorderCount > 0) {
                Text(
                    if (summary.reorderCount == 1) {
                        "1 à commander"
                    } else {
                        "${summary.reorderCount} à commander"
                    },
                    style = style,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Chooses the folder an article moves to.
 *
 * Kept as a dialog rather than a drag: moving is rare, and a list of paths is
 * unambiguous, where dropping a row onto a tile is not.
 */
@Composable
private fun FolderPickerDialog(
    item: SavedItem,
    folders: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Déplacer l'article") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "« ${item.label} » — choisissez son dossier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FolderChoice(
                    label = "Sans dossier",
                    selected = item.folder.isBlank(),
                    onClick = { onPick("") },
                )
                folders.forEach { path ->
                    FolderChoice(
                        label = SavedItemStore.display(path),
                        selected = item.folder.equals(path, ignoreCase = true),
                        onClick = { onPick(path) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun FolderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The folder filter above the Articles list.
 *
 * The tab lands on the whole catalogue, so a folder is a question the shop asks
 * of it — "what do I have in Mobilier?" — not a place it walks into. Only the
 * top-level folders earn a chip: a shop with thirty shelves would otherwise be
 * scrolling a row of chips instead of its articles, and the nested paths are
 * still one tap away in the menu.
 *
 * The chips wrap rather than scroll sideways: a filter nobody can see is a
 * filter nobody uses, and with the folders in a row the menu that reaches the
 * sub-folders sat off the right edge of the screen.
 *
 * `null` is "Tous", `""` is "Sans dossier", anything else is a path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleFilterChips(
    folders: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    /** False when the shop hid the folder field: only "Tous" and stock filters remain. */
    showFolders: Boolean = true,
    /** How many articles are at or below their threshold. */
    reorderCount: Int = 0,
    reorderOnly: Boolean = false,
    onToggleReorder: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val topLevel = remember(folders) {
        folders.filter { SavedItemStore.parentOf(it).isEmpty() }
    }
    val nested = remember(folders) {
        folders.filter { SavedItemStore.parentOf(it).isNotEmpty() }
    }
    // A nested folder chosen from the menu needs a chip of its own, or the row
    // would show nothing selected while the list is filtered.
    val activeNested = selected?.takeIf { path ->
        path.isNotEmpty() && topLevel.none { it.equals(path, ignoreCase = true) }
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ChoiceChip(
            label = "Tous",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        // A count in the label, because "à commander" with nothing behind it is
        // a filter the shop cannot evaluate before tapping it.
        if (reorderCount > 0 || reorderOnly) {
            ChoiceChip(
                label = if (reorderCount > 0) "À commander ($reorderCount)" else "À commander",
                selected = reorderOnly,
                onClick = onToggleReorder,
            )
        }
        if (showFolders) {
            ChoiceChip(
                label = "Sans dossier",
                selected = selected == "",
                onClick = { onSelect("") },
            )
            topLevel.forEach { path ->
                ChoiceChip(
                    label = path,
                    selected = selected.equals(path, ignoreCase = true),
                    onClick = { onSelect(path) },
                )
            }
            if (activeNested != null) {
                ChoiceChip(
                    label = SavedItemStore.display(activeNested),
                    selected = true,
                    onClick = {},
                )
            }
            if (nested.isNotEmpty()) {
                Box {
                    ChoiceChip(
                        label = "Tous les dossiers",
                        selected = false,
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        folders.forEach { path ->
                            DropdownMenuItem(
                                // The full path, because "Hiver" can exist under
                                // two different folders.
                                text = { Text(SavedItemStore.display(path)) },
                                onClick = {
                                    menuOpen = false
                                    onSelect(path)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One folder's articles, or the whole catalogue.
 *
 * The heading lives in the app bar now, so this is only the rows: the folder
 * grid cannot be reached from here by accident, and leaving is the back arrow
 * the app bar owns, which the system back gesture also triggers.
 */
@Composable
private fun ArticlesList(
    /** Already filtered by [SavedItemStore.matches]; this only draws them. */
    items: List<SavedItem>,
    showFolder: Boolean,
    /** Which optional fields the shop asked to see. */
    fields: Set<ItemField> = ItemField.entries.toSet(),
    emptyTitle: String,
    emptyMessage: String,
    onEdit: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onMove: (SavedItem) -> Unit,
    /** Opening one article's stock history. */
    onHistory: (SavedItem) -> Unit = {},
    /** The margin line under the price, when a cost is known. */
    marginOf: (SavedItem) -> MarginHint? = { null },
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            EmptyState(
                icon = Icons.Filled.Inventory2,
                title = emptyTitle,
                message = emptyMessage,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { "item-${it.id.ifBlank { it.label.lowercase() }}" }) { item ->
            ItemCard(
                item = item,
                showFolder = showFolder,
                fields = fields,
                onClick = { onEdit(item) },
                onDelete = { onDelete(item) },
                onMove = { onMove(item) },
                onHistory = { onHistory(item) },
                marginOf = marginOf,
            )
        }
    }
}

/**
 * One of the buttons a swiped row reveals.
 *
 * Icon only: three labels in 168 dp clipped "Supprimer" to "Supprime", and the
 * row already says what it is. The description is kept for TalkBack, which has
 * no icon to read.
 */
@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    /** Opens this article's stock history; only offered for a counted article. */
    onHistory: () -> Unit = {},
    /** Which optional fields the shop asked to see. */
    fields: Set<ItemField> = ItemField.entries.toSet(),
    /** False inside a folder, where repeating its name on every row is noise. */
    showFolder: Boolean = true,
    /** The margin line under the price, when a cost is known. */
    marginOf: (SavedItem) -> MarginHint? = { null },
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = listOf(
        // A hidden field is not shown, but it is still on the article: the
        // switch turns the label off, never the data.
        if (showFolder && ItemField.FOLDER in fields) SavedItemStore.display(item.folder) else "",
        if (ItemField.UNIT in fields) item.unit else "",
    )
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    /*
     * Slide the row left to reach its three actions, the way StoDo does it.
     * The card is moved rather than swapped for a menu: a shop that has used
     * any phone knows the gesture, and the actions stay visible while the row
     * it belongs to stays readable.
     */
    val density = LocalDensity.current
    /*
     * Moving is only offered while the folder field is on: a shop that hid
     * folders should not be asked where to file an article it never files.
     * Two actions at most — tapping the row already opens the editor, and a
     * third button pushed the card so far left that its label disappeared.
     */
    val canMove = ItemField.FOLDER in fields
    val actionWidth = 56.dp
    val revealWidth = with(density) { (actionWidth * (if (canMove) 2 else 1)).toPx() }
    val offsetX = remember(item.label) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { offsetX.animateTo(0f) } }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canMove) {
                QuickAction(
                    icon = Icons.Filled.FolderOpen,
                    label = "Déplacer « ${item.label} »",
                    modifier = Modifier.width(actionWidth),
                ) {
                    close()
                    onMove()
                }
            }
            QuickAction(
                icon = Icons.Filled.Delete,
                label = "Supprimer « ${item.label} »",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.width(actionWidth),
            ) {
                close()
                onDelete()
            }
        }

    Card(
        // An open row closes on a tap before it edits: the shop aimed at the
        // card it can still see, not at the button it just opened.
        onClick = { if (offsetX.value != 0f) close() else onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidth, 0f))
                    }
                },
                onDragStopped = {
                    // Past a third of the strip it stays open; below that it
                    // springs back, so a half-swipe never leaves a stuck row.
                    val target = if (offsetX.value < -revealWidth / 3f) -revealWidth else 0f
                    offsetX.animateTo(target)
                },
            ),
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
                    // Two lines, not one: at 150 % the label used to collapse to
                    // "Chaise en rotin…" while a column of empty space sat beside
                    // it. The price keeps a floor and the name gets the rest.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                modifier = Modifier.widthIn(min = 88.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    item.price.asDirhams(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                // The margin is only shown when the shop kept the purchase
                // price on the card and a cost is really known: an invented
                // margin is worse than none.
                if (ItemField.PURCHASE_PRICE in fields) {
                    marginOf(item)?.let { hint ->
                        Text(
                            hint.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hint.negative) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // A quantity is the difference between a catalogue and an
                // inventory, so it earns a line of its own rather than being
                // buried in the subtitle with the folder name. It disappears
                // with the Stock switch, like every other optional field.
                if (item.tracksStock && ItemField.STOCK in fields) {
                    Text(
                        when (val stock = item.stock) {
                            null -> ""
                            0 -> if (item.needsReorder) "Épuisé · à commander" else "Épuisé"
                            1 -> if (item.needsReorder) "1 en stock · à commander" else "1 en stock"
                            else -> if (item.needsReorder) {
                                "$stock en stock · à commander"
                            } else {
                                "$stock en stock"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.outOfStock || item.needsReorder) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
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
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    if (canMove) {
                        DropdownMenuItem(
                            text = { Text("Déplacer dans un dossier") },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMove()
                            },
                        )
                    }
                    // Only a counted article has a history to show; offering it
                    // on a service would open an empty screen and suggest the
                    // app lost something.
                    if (item.tracksStock) {
                        DropdownMenuItem(
                            text = { Text("Historique du stock") },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onHistory()
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
}


/*
 * Previews of the article row and its summary, before they are wired into a
 * screen. Both are private composables inside a 2 000-line file, so a preview
 * is the only way to see them without launching the app and creating data.
 */
private val previewArticle = SavedItem(
    label = "Chaise en rotin",
    price = 1050.0,
    unit = "unité",
    folder = "Mobilier/Chaises",
    barcode = "6111234500024",
    purchasePrice = 800.0,
    stock = 4,
)

@Preview(name = "Article", showBackground = true, widthDp = 380)
@Composable
private fun ItemCardPreview() {
    FactureMarocTheme {
        Box(Modifier.padding(16.dp)) {
            ItemCard(
                item = previewArticle,
                onClick = {},
                onDelete = {},
                onMove = {},
            )
        }
    }
}

@Preview(name = "Article — champs masqués", showBackground = true, widthDp = 380)
@Composable
private fun ItemCardWithoutOptionalFieldsPreview() {
    FactureMarocTheme {
        Box(Modifier.padding(16.dp)) {
            ItemCard(
                item = previewArticle,
                onClick = {},
                onDelete = {},
                onMove = {},
                fields = setOf(ItemField.UNIT),
            )
        }
    }
}

@Preview(name = "Résumé du stock", showBackground = true, widthDp = 380)
@Composable
private fun ArticlesSummaryPreview() {
    FactureMarocTheme {
        ArticlesSummary(
            items = listOf(previewArticle, previewArticle.copy(label = "Coussin", stock = 30)),
            fields = ItemField.entries.toSet(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceDetailSheet(
    record: InvoiceRecord,
    pdfArchived: Boolean,
    reminderAvailable: Boolean,
    onDismiss: () -> Unit,
    onReminder: () -> Unit,
    onShare: () -> Unit,
    onExportXml: () -> Unit,
    onDuplicate: () -> Unit,
    onCreditNote: () -> Unit,
    onConvertQuote: () -> Unit,
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
            // A small row, not a badge on every card: whether the PDF that was
            // handed out is still kept, which is a question the archive screen
            // answers in bulk and this one answers for this document.
            DetailRow("PDF dans l'archive", if (pdfArchived) "Oui" else "Non")
            // The same rows the document prints, in the same order: the paper
            // calls the figure before the discount "Total HT", so a screen that
            // showed the net one under that name gave two different HT totals
            // for the same invoice.
            DetailRow("Sous-total HT", record.data.subtotal.asDirhams())
            if (record.data.showsDiscount) {
                DetailRow(
                    if (record.data.discountInAmount) {
                        "Remise"
                    } else {
                        "Remise ${record.data.discountPercent} %"
                    },
                    "−${record.data.discountAmount.asDirhams()}",
                )
            }
            DetailRow("Total HT", record.data.totalExcludingVat.asDirhams())
            record.data.dueDatePrinted?.let { due ->
                DetailRow(
                    if (PaymentDue.isOverdue(due, record.data.paymentState)) {
                        "Échéance (dépassée)"
                    } else {
                        "Échéance"
                    },
                    due,
                )
            }
            if (record.data.surcharge > 0.0) {
                DetailRow(
                    record.data.surchargeLabel.ifBlank { "Livraison" },
                    record.data.surcharge.asDirhams(),
                )
            }
            // The same recap the document prints: one row per rate, so a mixed
            // 20 % / 10 % invoice is not summarised as a single figure the paper
            // never showed.
            val vatRows = record.data.vatPrintRows
            if (vatRows.isEmpty()) {
                DetailRow("TVA non applicable", record.data.vatAmount.asDirhams())
            } else {
                vatRows.forEach { recap ->
                    DetailRow(
                        "TVA ${recap.rate.trimZeros()} %",
                        recap.amount.asDirhams(),
                    )
                }
            }
            if (record.data.paidAmount > 0.0) {
                DetailRow("Encaissé", record.data.paidAmount.asDirhams())
                DetailRow("Reste à payer", record.data.balanceDue.asDirhams())
                for (entry in record.data.payments) {
                    DetailRow(
                        "Paiement ${entry.paidAt.ifBlank { "—" }}",
                        buildString {
                            append(entry.paidAmount.asDirhams())
                            entry.method?.let { append(" · ${it.french}") }
                            if (entry.note.isNotBlank()) append(" · ${entry.note}")
                        },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (reminderAvailable) {
                OutlinedButton(
                    onClick = onReminder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Rappel de paiement")
                }
                Spacer(Modifier.height(8.dp))
            }
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
            // No delete here on purpose: this sheet only opens issued
            // documents. The correction is an avoir, so the shop keeps the
            // paper it sent and the tax office keeps a trail.
            if (record.data.documentType.canBeCredited) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCreditNote,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Créer un avoir")
                }
            }
            Spacer(Modifier.height(12.dp))
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
