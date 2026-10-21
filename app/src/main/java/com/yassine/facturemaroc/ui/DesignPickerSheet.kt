package com.yassine.facturemaroc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceTemplate

/**
 * Chooses the document's paper layout, by looking at it.
 *
 * Four thumbnails rather than four names: "Facture Maroc" and "Classique" mean
 * nothing to a shop until it has seen both, and a template chosen by name is
 * chosen by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignPickerSheet(
    design: InvoiceDesign,
    onSelect: (InvoiceDesign) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templates = InvoiceTemplate.entries

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text("Modèle de document", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                "Le modèle s'applique à tous les documents. Le type de document reste choisi sur la facture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            templates.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { template ->
                        TemplateCard(
                            template = template,
                            selected = template == design.template,
                            onClick = { onSelect(design.copy(template = template)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Afficher le logo", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Le logo du magasin apparaît sur le document.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = design.logoEnabled,
                    onCheckedChange = { onSelect(design.copy(logoEnabled = it)) },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TemplateCard(
    template: InvoiceTemplate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                contentAlignment = Alignment.Center,
            ) {
                val pageModifier = if (template.isRoll) {
                    Modifier
                        .width(58.dp)
                        .aspectRatio(0.52f)
                } else {
                    Modifier
                        .width(72.dp)
                        .aspectRatio(0.72f)
                }
                Surface(
                    modifier = pageModifier,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    TemplateSketch(template)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Modèle choisi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                templateDescription(template),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
            )
        }
    }
}

@Composable
private fun TemplateSketch(template: InvoiceTemplate) {
    val accent = MaterialTheme.colorScheme.primary
    val softAccent = accent.copy(alpha = 0.16f)
    val ink = Color(0xFF4A5568)
    val hair = Color(0xFFE2E8F0)

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val u = w / 100f

        fun block(x: Float, y: Float, width: Float, height: Float, color: Color) {
            drawRect(color, Offset(x * u, y * h), Size(width * u, height * h))
        }

        // Header: logo mark and the shop's two lines.
        block(5f, 4f, 15f, 7f, accent)
        block(24f, 5f, 44f, 3.5f, ink)
        block(24f, 11f, 30f, 2.6f, hair)
        block(84f, 5f, 11f, 3f, hair)

        when (template) {
            InvoiceTemplate.MAROC -> {
                block(0f, 20f, 100f, 11f, softAccent)
                block(28f, 23.5f, 44f, 4.5f, accent)
            }

            InvoiceTemplate.MODERNE -> {
                block(0f, 20f, 100f, 7f, accent)
            }

            InvoiceTemplate.TICKET -> {
                block(5f, 21f, 90f, 1.2f, ink)
                block(30f, 25f, 40f, 3f, ink)
            }

            InvoiceTemplate.CLASSIQUE -> {
                block(5f, 20f, 90f, 1.4f, ink)
                block(5f, 24f, 38f, 3f, ink)
            }
        }

        // The table's head and three rows.
        block(5f, 38f, 90f, 5.5f, hair)
        repeat(3) { row ->
            val y = 46f + row * 10f
            block(5f, y, 46f, 3f, ink)
            block(70f, y, 12f, 3f, hair)
            block(86f, y, 9f, 3f, ink)
        }

        // Totals, right aligned, with the TTC in the accent colour.
        block(58f, 76f, 37f, 13f, hair)
        block(60f, 78f, 14f, 2.6f, ink)
        block(78f, 78f, 15f, 2.6f, ink)
        block(60f, 83.5f, 12f, 2.6f, ink)
        block(78f, 83.5f, 15f, 2.6f, accent)
    }
}

private fun templateDescription(template: InvoiceTemplate): String = when (template) {
    InvoiceTemplate.CLASSIQUE -> "Sobre et universel, en-tête simple."
    InvoiceTemplate.MODERNE -> "Bandeau de couleur, lignes aérées."
    InvoiceTemplate.MAROC -> "Cadre TVA et mentions légales."
    InvoiceTemplate.TICKET -> "Reçu étroit pour imprimante 80 mm."
}
