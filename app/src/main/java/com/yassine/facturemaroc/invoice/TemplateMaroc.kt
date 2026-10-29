package com.yassine.facturemaroc.invoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.TextPaint
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.ShopSettings

/**
 * The layout a Moroccan shop is used to seeing: a boxed header carrying the
 * legal identifiers, a table with a full grid around every cell, and a signature
 * space at the foot for the stamp.
 *
 * Denser and more formal than [ModerneRenderer] on purpose. This is the shape a
 * customer has been handed a hundred times, so it reads as a familiar document
 * rather than a design choice, and the grid means a row cannot be read as
 * belonging to the line above it.
 *
 * The signature box is drawn but never filled in. On a Moroccan invoice the
 * shop's stamp and signature are part of the document, and this app cannot
 * reproduce them, so it leaves the space rather than pretending to.
 *
 * Ported from Tyre Finder, adapted to paginate and to print suppliers on a bon
 * de commande.
 */
class MarocRenderer : PdfRenderer {

    override fun draw(
        context: Context,
        surface: PageSurface,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ) {
        val margin = A4.MARGIN
        val right = A4.WIDTH - margin
        val contentW = (right - margin).toInt()

        val ink = Color.rgb(0x1A, 0x1A, 0x1A)
        val muted = Color.rgb(0x66, 0x66, 0x66)
        val accent = design.accentColor
        val grid = Paint().apply {
            color = Color.rgb(0x9A, 0x9A, 0x9A)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val tint = Paint().apply { color = PdfColor.tint(accent, 0.90f) }

        val title = PdfText.paint(16f, ink, bold = true)
        val docTitle = PdfText.paint(13f, accent, bold = true)
        val label = PdfText.paint(8f, muted)
        val body = PdfText.paint(9.5f, ink)
        val bodyBold = PdfText.paint(9.5f, ink, bold = true)
        val head = PdfText.paint(8.5f, ink, bold = true)
        val cell = PdfText.paint(9.5f, ink)
        val cellRight = PdfText.paint(9.5f, ink, bold = true)
        val totalPaint = PdfText.paint(13f, ink, bold = true)
        val words = PdfText.paint(9f, ink, bold = true)

        var canvas = surface.canvas

        // ---- boxed header
        val headerTop = 40f
        val headerH = 84f
        canvas.drawRect(margin, headerTop, right, headerTop + headerH, tint)

        var nameX = margin + 10f
        PdfText.loadShopLogo(context, design)?.let { logo ->
            nameX = PdfText.drawLogo(canvas, logo, margin + 10f, headerTop + 26f, 34f) + 10f
        }
        PdfText.draw(canvas, shop.name, nameX, headerTop + 12f, title, (right - nameX - 140f).toInt(), maxLines = 2)
        PdfText.draw(
            canvas, data.header, right - 140f, headerTop + 12f, docTitle, 130,
            Layout.Alignment.ALIGN_OPPOSITE,
        )

        // Identifiers on one line, the shop's address and phone on the next.
        // All of it used to be one joined string, and a stored address with a
        // line break in it pushed the city into an ellipsis.
        if (design.showLegalIdentifiers) {
            val legal = shop.legalIdentifierLabels.joinToString("   ·   ")
            if (legal.isNotBlank()) {
                PdfText.draw(canvas, legal, margin + 10f, headerTop + 38f, label, contentW - 20, maxLines = 1)
            }
        }
        val contact = (listOf(PdfText.oneline(shop.address)) + shop.contactList)
            .filter { it.isNotBlank() }
            .joinToString("   ·   ")
        if (contact.isNotBlank()) {
            PdfText.draw(canvas, contact, margin + 10f, headerTop + 50f, label, contentW - 20, maxLines = 1)
        }
        PdfText.draw(canvas, "N° ${data.number}", margin + 10f, headerTop + 66f, bodyBold, 220, maxLines = 1)
        PdfText.draw(
            canvas, "Date : ${data.date}", right - 200f, headerTop + 66f, bodyBold, 190,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        canvas.drawRect(margin, headerTop, right, headerTop + headerH, grid)

        // ---- party block, boxed like the rest of the document
        var y = headerTop + headerH + 14f
        val partyH = 56f
        canvas.drawRect(margin, y, right, y + partyH, grid)
        PdfText.draw(canvas, data.partyLabel.uppercase(), margin + 8f, y + 7f, label, 120)
        PdfText.draw(canvas, data.partyName, margin + 8f, y + 20f, bodyBold, contentW - 20, maxLines = 1)
        val partyDetail = listOf(
            PdfText.oneline(data.party.address),
            data.party.phone,
            data.party.legalIdentifierLabels.joinToString("   ·   "),
        ).filter { it.isNotBlank() }.joinToString("   ·   ")
        if (partyDetail.isNotBlank()) {
            PdfText.draw(canvas, partyDetail, margin + 8f, y + 35f, body, contentW - 20, maxLines = 1)
        }
        y += partyH + 18f

        // ---- the gridded table
        val qtyW = 36f
        val unitW = 92f
        val totalW = 96f
        val descX = margin + qtyW
        val unitX = right - unitW - totalW
        val totalX = right - totalW
        val descW = (unitX - descX - 12f).toInt()

        val heights = data.lines.map { line ->
            kotlin.math.max(PdfText.height(line.lineLabel, cell, descW, 2) + 8f, 20f)
        }
        val flow = PageFlow.layout(
            heights = heights,
            firstY = y + HEAD_H,
            resumeY = A4.RESUME_Y,
            bottom = A4.BOTTOM,
            tailHeight = tailHeight(data, design, words, contentW),
        )

        var tableTop = y
        var lastRowEnd = tableTop + HEAD_H
        flow.pages.forEachIndexed { index, rows ->
            if (index > 0) {
                if (!surface.newPage()) return
                canvas = surface.canvas
                drawContinuationHeader(canvas, data, margin, right, bodyBold, grid)
                tableTop = A4.RESUME_Y - HEAD_H
            }
            canvas.drawRect(margin, tableTop, right, tableTop + HEAD_H, tint)
            PdfText.draw(canvas, "Qté", margin + 6f, tableTop + 6f, head, 26)
            PdfText.draw(canvas, "Désignation", descX + 6f, tableTop + 6f, head, descW)
            PdfText.draw(
                canvas, "P.U. (DH)", unitX + 4f, tableTop + 6f, head, (unitW - 8f).toInt(),
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            PdfText.draw(
                canvas, "Total (DH)", totalX + 4f, tableTop + 6f, head, (totalW - 8f).toInt(),
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            canvas.drawLine(margin, tableTop + HEAD_H, right, tableTop + HEAD_H, grid)

            var rowY = tableTop + HEAD_H
            rows.forEach { lineIndex ->
                val line = data.lines[lineIndex]
                val rowH = heights[lineIndex]
                PdfText.draw(canvas, line.quantity.toString(), margin + 6f, rowY + 5f, cell, 28)
                PdfText.draw(canvas, line.lineLabel, descX + 6f, rowY + 4f, cell, descW, maxLines = 2)
                PdfText.draw(
                    canvas, PdfText.money(line.unitPrice), unitX + 4f, rowY + 5f, cellRight, (unitW - 8f).toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                PdfText.draw(
                    canvas, PdfText.money(line.total), totalX + 4f, rowY + 5f, cellRight, (totalW - 8f).toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                rowY += rowH
                // Vertical rules, and a horizontal one under the row, so every
                // cell is closed. This is the whole character of the template.
                canvas.drawLine(margin, rowY, right, rowY, grid)
                canvas.drawLine(descX, rowY - rowH, descX, rowY, grid)
                canvas.drawLine(unitX, rowY - rowH, unitX, rowY, grid)
                canvas.drawLine(totalX, rowY - rowH, totalX, rowY, grid)
            }
            canvas.drawRect(margin, tableTop, right, rowY, grid)
            lastRowEnd = rowY
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, index + 1, flow.totalPages)
        }

        if (flow.tailOnNewPage) {
            if (!surface.newPage()) return
            canvas = surface.canvas
            drawContinuationHeader(canvas, data, margin, right, bodyBold, grid)
            lastRowEnd = A4.RESUME_Y - 6f
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, flow.totalPages, flow.totalPages)
        }

        // ---- totals, boxed on the right
        val box = PdfTotals.box(right)
        var ty = lastRowEnd + 18f
        val boxTop = ty - 8f
        PdfTotals.row(canvas, box, "Total HT", PdfText.money(data.subtotal), ty, body, cellRight)
        ty += 16f
        if (data.discountPercent > 0) {
            PdfTotals.row(
                canvas, box,
                "Remise ${PdfText.percent(data.discountPercent.toDouble())}%",
                "−${PdfText.money(data.discountAmount)}",
                ty, body, cellRight,
            )
            ty += 16f
        }
        if (data.vatNotApplicable) {
            PdfTotals.label(canvas, box, "TVA non applicable", ty, body)
        } else {
            PdfTotals.row(
                canvas, box,
                "TVA ${PdfText.percent(data.effectiveVatPercent)}%",
                PdfText.money(data.vatAmount),
                ty, body, cellRight,
            )
        }
        ty += 16f

        canvas.drawRect(box.left, ty - 2f, right, ty + 24f, tint)
        PdfTotals.label(canvas, box, "Total TTC", ty + 4f, PdfText.paint(10f, ink, bold = true))
        PdfTotals.value(canvas, box, PdfText.money(data.total), ty, totalPaint)
        canvas.drawRect(box.left, boxTop, right, ty + 24f, grid)
        ty += 26f

        // ---- the amount in words, then the signature space
        var sigY = ty
        if (design.showAmountWords) {
            PdfText.draw(canvas, data.amountInWords, margin, ty + 12f, words, contentW, maxLines = 3)
            ty += 12f + PdfText.height(data.amountInWords, words, contentW, 3)
        }

        // ---- signature space, left empty for the shop's stamp
        val sigW = 190f
        val sigH = 54f
        sigY = if (ty + 18f <= 640f) 640f else ty + 18f
        val sigX = right - sigW
        canvas.drawRect(sigX, sigY, sigX + sigW, sigY + sigH, grid)
        PdfText.draw(canvas, "Signature et cachet", sigX + 8f, sigY + sigH - 14f, label, (sigW - 16f).toInt())

        if (design.showPaymentMethod && data.showPaymentMethod) {
            PdfText.draw(
                canvas, "Mode de paiement : ${data.paymentMethod.french}",
                margin, sigY + 10f, label, (sigX - margin - 20f).toInt(), maxLines = 1,
            )
        }
        if (data.isDevis) {
            val validity = data.validityDate?.takeIf { it.isNotBlank() }
                ?.let { "Validité jusqu'au : $it" } ?: "Validité : —"
            PdfText.draw(canvas, validity, margin, sigY + 10f, label, (sigX - margin - 20f).toInt(), maxLines = 1)
        }
        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            PdfText.draw(canvas, "Incoterms : ${data.incoterm}", margin, sigY + 28f, label, (sigX - margin - 20f).toInt(), maxLines = 1)
        }

        if (design.showFooter) {
            PdfText.draw(
                canvas, "Merci de votre confiance", margin, 812f, body, contentW,
                Layout.Alignment.ALIGN_CENTER,
            )
        }
    }

    private fun tailHeight(
        data: InvoiceData,
        design: InvoiceDesign,
        words: TextPaint,
        contentW: Int,
    ): Float {
        val rows = 2 + if (data.discountPercent > 0) 1 else 0
        var h = 18f + 16f * rows + 26f
        if (design.showAmountWords) {
            h += 12f + PdfText.height(data.amountInWords, words, contentW, 3)
        }
        // The signature box is part of the tail: a page that ends just above it
        // would push the stamp onto the next sheet with the totals behind.
        h += 18f + 54f
        return h
    }

    /** Which document a second sheet belongs to; the shop block is not repeated. */
    private fun drawContinuationHeader(
        canvas: Canvas,
        data: InvoiceData,
        margin: Float,
        right: Float,
        bodyBold: TextPaint,
        grid: Paint,
    ) {
        PdfText.draw(canvas, data.header, margin, 40f, bodyBold, 220)
        PdfText.draw(
            canvas, "N° ${data.number}", right - 220f, 40f, bodyBold, 220,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        canvas.drawLine(margin, 58f, right, 58f, grid)
    }

    private companion object {
        const val HEAD_H = 20f
    }
}
