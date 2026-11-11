package com.yassine.facturemaroc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.ClientStatement
import com.yassine.facturemaroc.data.ClientStatements
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.Money
import com.yassine.facturemaroc.data.PartyContact
import com.yassine.facturemaroc.data.PartyRole
import com.yassine.facturemaroc.data.Purchase

/**
 * One client or supplier, with everything that happened with them.
 *
 * Tapping a card used to open the *editor*, which answers "who is this?" but not
 * the question the shop actually has: "what did I give them, and what do they
 * owe me?" The page is the same for both roles — a party is a party — and only
 * the figures and the document list change with the role.
 *
 * Supplier figures show **purchases**, never a balance: the app has no supplier
 * ledger, and inventing a "solde fournisseur" from orders alone would be a
 * number nobody could reconcile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyDetailScreen(
    client: InvoiceClient,
    records: List<InvoiceRecord>,
    purchases: List<Purchase>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onNewDocument: () -> Unit,
    /** Client only, and only when there is something to state. */
    onStatement: (() -> Unit)? = null,
    /** What to say when the phone has no dialler or WhatsApp is absent. */
    onNotify: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val isClient = client.role == PartyRole.CLIENT
    val statement: ClientStatement = remember(records, client.name, isClient) {
        if (isClient) ClientStatements.forClient(records, client) else ClientStatement(client.name, emptyList())
    }
    val receipts = remember(purchases, client.name) {
        purchases
            .filter { it.supplierName.trim().equals(client.name.trim(), ignoreCase = true) }
            .sortedByDescending { it.createdAt }
    }
    val orders = remember(records, client.name) {
        records.filter { record ->
            !record.isDraft &&
                record.data.documentType == DocumentType.BON_DE_COMMANDE &&
                record.data.supplier?.name?.trim()?.equals(client.name.trim(), ignoreCase = true) == true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text(
                        client.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    if (PartyContact.hasNumber(client.phone)) {
                        IconButton(
                            onClick = {
                                if (!PartyActions.dial(context, client.phone)) {
                                    onNotify("Aucune application pour appeler sur cet appareil.")
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Appeler ${client.name}")
                        }
                        IconButton(
                            onClick = {
                                if (!PartyActions.whatsApp(context, client.phone)) {
                                    onNotify("WhatsApp n'est pas installé sur cet appareil.")
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Chat,
                                contentDescription = "Message WhatsApp à ${client.name}",
                            )
                        }
                    }
                    if (onStatement != null) {
                        IconButton(onClick = onStatement) {
                            Icon(Icons.Filled.Description, contentDescription = "Relevé")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Modifier")
                    }
                    Button(
                        onClick = onNewDocument,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(if (isClient) "Nouvelle facture" else "Nouveau bon")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "identity") {
                IdentityBlock(client)
            }

            item(key = "figures") {
                if (isClient) ClientFigures(statement) else SupplierFigures(receipts, orders.size)
            }

            if (isClient) {
                item(key = "documents-heading") {
                    Text(
                        "Documents",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (statement.lines.isEmpty()) {
                    item(key = "documents-empty") {
                        Hint("Aucune facture ni avoir à ce nom pour l'instant.")
                    }
                } else {
                    items(statement.lines.asReversed(), key = { "st-${it.number}" }) { line ->
                        DocumentRow(
                            title = line.number,
                            subtitle = line.date,
                            amount = if (line.isCredit) "\u2212${Money.dirhams(line.total)}" else Money.dirhams(line.total),
                            note = when {
                                line.isCredit -> "Avoir"
                                line.isLate -> "En retard"
                                line.balance <= 0.0 -> "Payée"
                                line.paid > 0.0 -> "${Money.dirhams(line.balance)} dû"
                                else -> ""
                            },
                            credit = line.isCredit,
                            urgent = line.isLate,
                        )
                    }
                }
            } else {
                item(key = "orders-heading") {
                    Text(
                        "Bons de commande",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (orders.isEmpty()) {
                    item(key = "orders-empty") { Hint("Aucun bon de commande à ce nom.") }
                } else {
                    items(orders, key = { "po-${it.id}" }) { record ->
                        DocumentRow(
                            title = record.data.number,
                            subtitle = record.data.date,
                            amount = Money.dirhams(record.data.total),
                        )
                    }
                }

                item(key = "receipts-heading") {
                    Text(
                        "Réceptions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (receipts.isEmpty()) {
                    item(key = "receipts-empty") { Hint("Aucune réception enregistrée à ce nom.") }
                } else {
                    items(receipts, key = { "br-${it.id}" }) { purchase ->
                        DocumentRow(
                            title = purchase.number,
                            subtitle = purchase.date,
                            amount = Money.dirhams(purchase.totalHt),
                            note = "HT",
                        )
                    }
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun IdentityBlock(client: InvoiceClient) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(client.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(client.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        client.role.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (client.address.isNotBlank()) {
                Text(
                    client.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val contact = listOf(client.phone, client.email).filter { it.isNotBlank() }
            if (contact.isNotEmpty()) {
                Text(
                    contact.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val legal = client.legalIdentifierLabels
            if (legal.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                legal.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (client.vatExempt) {
                Text(
                    "Exonéré de TVA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ClientFigures(statement: ClientStatement) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Solde dû",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Text(
                Money.dirhams(statement.balance),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Figure("Facturé", statement.billed)
                Figure("Payé", statement.paid)
            }
        }
    }
}

@Composable
private fun SupplierFigures(receipts: List<Purchase>, orderCount: Int) {
    val bought = receipts.sumOf { it.totalHt }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Achats HT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Text(
                Money.dirhams(bought),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append("${receipts.size} réception${if (receipts.size > 1) "s" else ""}")
                    append(" · $orderCount bon${if (orderCount > 1) "s" else ""} de commande")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: Double) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
        )
        Text(
            Money.dirhams(value),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun DocumentRow(
    title: String,
    subtitle: String,
    amount: String,
    note: String = "",
    credit: Boolean = false,
    urgent: Boolean = false,
) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    amount,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = if (credit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (urgent) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** `Atelier Atlas` → `AA`, the same monogram the client card shows. */
private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
