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
 * No block of colour anywhere: a hairline instead of a band, rules instead of
 * filled rows, and more air around the figures.
 *
 * The layout a shop picks when the invoice is going to a customer who reads it
 * carefully, or when it is photocopied in black and white — which is where a
 * filled header turns into a grey slab and the table becomes unreadable. It is
 * the only template that survives a mono printer without being altered by it.
 *
 * Ported from Tyre Finder, with two changes the original needed: the totals
 * block now uses shared geometry so a label and its amount cannot overlap, and
 * the table breaks across pages instead of running off the first one.
 */
class ModerneRenderer : PdfRenderer {

    override fun draw(
        context: Context,
        surface: PageSurface,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ) {
        val margin = MARGIN
        val right = A4.WIDTH - margin
        val contentW = (right - margin).toInt()
        val accent = design.accentColor

        val ink = Color.rgb(0x1C, 0x1B, 0x1A)
        val muted = Color.rgb(0x77, 0x6E, 0x69)
        val shopName = PdfText.paint(17f, ink, bold = true)
        val headline = PdfText.paint(26f, ink, bold = true)
        val label = PdfText.paint(8f, muted)
        val colHead = PdfText.paint(8f, muted, bold = true)
        val body = PdfText.paint(10f, ink)
        val bodyBold = PdfText.paint(10f, ink, bold = true)
        val cell = PdfText.paint(9.5f, ink)
        val cellRight = PdfText.paint(9.5f, ink, bold = true)
        val totalLabel = PdfText.paint(10f, ink)
        val totalBold = PdfText.paint(11f, muted)
        val totalPaint = PdfText.paint(17f, ink, bold = true)
        val words = PdfText.paint(9f, ink, bold = true)
        val accentSmall = PdfText.paint(8f, accent, bold = true)

        val hairline = Paint().apply { color = Color.rgb(0xDE, 0xD8, 0xD3); strokeWidth = 0.75f }
        val accentRule = Paint().apply { color = accent; strokeWidth = 2f }

        var canvas = surface.canvas

        // ---- masthead: shop identity on the left, the document's name on the right
        var nameX = margin
        PdfText.loadShopLogo(context, design)?.let { logo ->
            nameX = PdfText.drawLogo(canvas, logo, margin, 40f, 44f) + 14f
        }
        PdfText.draw(canvas, shop.name, nameX, 22f, shopName, (right - nameX - 160f).toInt(), maxLines = 2)
        PdfText.draw(
            canvas, data.header, right - 190f, 16f, headline, 190,
            Layout.Alignment.ALIGN_OPPOSITE,
        )

        var y = 84f
        canvas.drawLine(margin, y, right, y, hairline)
        y += 10f
        canvas.drawLine(margin, y, right, y, accentRule)
        y += 20f

        // ---- two identity columns, evenly set apart
        val half = contentW / 2 - 14
        var leftY = y
        if (design.showLegalIdentifiers) {
            leftY = line(canvas, shop.legalIdentifierLabels.joinToString("  ·  "), margin, leftY, label, half, 3, 3f)
        }
        leftY = line(canvas, shop.address, margin, leftY, label, half, 3, 3f)
        leftY = line(canvas, shop.phone, margin, leftY, label, half, 1, 3f)

        val clientX = margin + contentW / 2 + 14
        var rightY = y
        PdfText.draw(canvas, data.partyLabel.uppercase(), clientX, rightY, colHead, half)
        rightY += 12f
        rightY = line(canvas, data.partyName, clientX, rightY, bodyBold, half, 2, 2f)
        rightY = line(canvas, data.party.address, clientX, rightY, body, half, 2, 2f)
        rightY = line(canvas, data.party.phone, clientX, rightY, body, half, 1, 2f)
        if (design.showLegalIdentifiers) {
            data.party.ice.takeIf { it.isNotBlank() }?.let {
                PdfText.draw(canvas, "ICE ${it.trim()}", clientX, rightY, accentSmall, half, maxLines = 1)
                rightY += 13f
            }
        }

        y = maxOf(leftY, rightY) + 12f
        PdfText.draw(canvas, "DATE", margin, y, colHead, 60)
        PdfText.draw(canvas, data.date, margin + 40f, y, body, 160, maxLines = 1)
        PdfText.draw(
            canvas, "N° ${data.number}", right - 200f, y, bodyBold, 200,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        y += 24f

        // ---- the table: a rule under the headings, and one above the total
        val qtyW = 30f
        val unitW = 78f
        val totalW = 84f
        val descX = margin + qtyW + 6f
        val unitX = right - unitW - totalW
        val totalX = right - totalW
        val descW = (unitX - descX - 8f).toInt()

        val heights = data.lines.map { line ->
            kotlin.math.max(PdfText.height(line.lineLabel, cell, descW, 2) + 9f, 19f)
        }
        val flow = PageFlow.layout(
            heights = heights,
            firstY = y + HEAD_H,
            resumeY = A4.RESUME_Y,
            bottom = A4.BOTTOM,
            tailHeight = tailHeight(data, design, words, contentW),
        )

        var headTop = y
        var lastRowEnd = headTop + HEAD_H
        flow.pages.forEachIndexed { index, rows ->
            if (index > 0) {
                if (!surface.newPage()) return
                canvas = surface.canvas
                drawContinuationHeader(canvas, data, margin, right, bodyBold, hairline, accentRule)
                headTop = A4.RESUME_Y - HEAD_H
            }
            drawTableHead(canvas, margin, headTop, right, colHead, hairline, descX, unitX, totalX, descW, unitW)
            var rowY = headTop + HEAD_H
            rows.forEachIndexed { position, lineIndex ->
                val line = data.lines[lineIndex]
                PdfText.draw(canvas, line.quantity.toString(), margin, rowY, cell, qtyW.toInt())
                PdfText.draw(canvas, line.lineLabel, descX, rowY, cell, descW, maxLines = 2)
                PdfText.draw(
                    canvas, PdfText.money(line.unitPrice), unitX, rowY + 1f, cell, unitW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                PdfText.draw(
                    canvas, PdfText.money(line.total), totalX, rowY + 1f, cellRight, totalW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                rowY += heights[lineIndex]
                // A rule between rows rather than a fill behind them, which is
                // what makes this template readable in black and white.
                val lastInDocument = index == flow.pages.lastIndex && position == rows.last
                if (!lastInDocument) {
                    canvas.drawLine(margin, rowY - 3f, right, rowY - 3f, hairline)
                }
            }
            lastRowEnd = rowY
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, index + 1, flow.totalPages)
        }

        if (flow.tailOnNewPage) {
            if (!surface.newPage()) return
            canvas = surface.canvas
            drawContinuationHeader(canvas, data, margin, right, bodyBold, hairline, accentRule)
            lastRowEnd = A4.RESUME_Y - 6f
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, flow.totalPages, flow.totalPages)
        }

        // ---- totals
        val box = PdfTotals.box(right, valueWidth = 132f)
        var ty = lastRowEnd + 16f
        PdfTotals.row(canvas, box, "Total HT", PdfText.money(data.subtotal), ty, totalLabel, cellRight)
        ty += 16f
        if (data.discountPercent > 0) {
            PdfTotals.row(
                canvas, box,
                "Remise ${PdfText.percent(data.discountPercent.toDouble())}%",
                "−${PdfText.money(data.discountAmount)}",
                ty, totalLabel, cellRight,
            )
            ty += 16f
        }
        if (data.vatNotApplicable) {
            PdfTotals.label(canvas, box, "TVA non applicable", ty, totalLabel)
        } else {
            PdfTotals.row(
                canvas, box,
                "TVA ${PdfText.percent(data.effectiveVatPercent)}%",
                PdfText.money(data.vatAmount),
                ty, totalLabel, cellRight,
            )
        }
        ty += 18f
        canvas.drawLine(box.left, ty, right, ty, accentRule)
        ty += 8f
        PdfTotals.row(canvas, box, "Total TTC", PdfText.money(data.total), ty, totalBold, totalPaint)
        ty += 30f

        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            PdfText.draw(canvas, "Incoterms : ${data.incoterm}", margin, ty, label, contentW, maxLines = 1)
            ty += 16f
        }

        if (design.showAmountWords) {
            PdfText.draw(canvas, data.amountInWords, margin, ty, words, contentW, maxLines = 4)
            ty += PdfText.height(data.amountInWords, words, contentW, 4) + 10f
        }

        if (design.showPaymentMethod && data.showPaymentMethod) {
            PdfText.draw(
                canvas, "Mode de paiement : ${data.paymentMethod.french}",
                margin, ty, label, contentW, maxLines = 1,
            )
            ty += 14f
        }

        if (data.isDevis) {
            val validity = data.validityDate?.takeIf { it.isNotBlank() }
                ?.let { "Validité jusqu'au : $it" } ?: "Validité : —"
            PdfText.draw(canvas, validity, margin, ty, label, contentW, maxLines = 1)
            ty += 14f
            PdfText.draw(canvas, "Accepté le : ________________", margin, ty, label, contentW, maxLines = 1)
            ty += 14f
            PdfText.draw(canvas, "Signature : ________________", margin, ty, label, contentW, maxLines = 1)
        }

        if (design.showFooter) {
            PdfText.draw(
                canvas, "Merci de votre confiance", margin, 812f, label, contentW,
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
        var h = 56f + 16f * rows
        if (data.showIncoterm && data.incoterm.isNotBlank()) h += 16f
        if (design.showAmountWords) h += PdfText.height(data.amountInWords, words, contentW, 4) + 10f
        if (design.showPaymentMethod && data.showPaymentMethod) h += 14f
        if (data.isDevis) h += 28f
        return h
    }

    private fun drawTableHead(
        canvas: Canvas,
        margin: Float,
        top: Float,
        right: Float,
        colHead: TextPaint,
        hairline: Paint,
        descX: Float,
        unitX: Float,
        totalX: Float,
        descW: Int,
        unitW: Float,
    ) {
        PdfText.draw(canvas, "QTÉ", margin, top, colHead, 26)
        PdfText.draw(canvas, "DÉSIGNATION", descX, top, colHead, descW)
        PdfText.draw(
            canvas, "P.U.", unitX, top, colHead, unitW.toInt(),
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        PdfText.draw(
            canvas, "TOTAL", totalX, top, colHead, (right - totalX).toInt(),
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        canvas.drawLine(margin, top + 13f, right, top + 13f, hairline)
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        data: InvoiceData,
        margin: Float,
        right: Float,
        bodyBold: TextPaint,
        hairline: Paint,
        accentRule: Paint,
    ) {
        PdfText.draw(canvas, data.header, margin, 40f, bodyBold, 220)
        PdfText.draw(
            canvas, "N° ${data.number}", right - 220f, 40f, bodyBold, 220,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        canvas.drawLine(margin, 54f, right, 54f, hairline)
        canvas.drawLine(margin, 58f, right, 58f, accentRule)
    }

    private fun line(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        gap: Float,
    ): Float {
        if (text.isBlank() || width <= 0) return y
        PdfText.draw(canvas, text, x, y, paint, width, maxLines = maxLines)
        return y + PdfText.height(text, paint, width, maxLines) + gap
    }

    private companion object {
        const val MARGIN = 46f
        const val HEAD_H = 21f
    }
}
