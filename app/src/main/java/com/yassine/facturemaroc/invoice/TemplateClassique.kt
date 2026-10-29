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
 * The sober layout: monochrome, logo and shop on the left, the document's
 * identity on the right, one four-column table under a pale grey head.
 *
 * Nothing here is coloured, which is the point. A shop that prints on a
 * monochrome laser or photocopies its invoices gets the document it designed,
 * not a grey slab where the colour used to be.
 *
 * The table is the part that can outgrow the paper, so its rows are measured
 * and split by [PageFlow] before anything is drawn.
 */
class ClassiqueRenderer : PdfRenderer {

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

        val ink = INK
        val muted = MUTED
        val shopName = PdfText.paint(14f, ink, bold = true)
        val title = PdfText.paint(22f, ink, bold = true)
        val lead = PdfText.paint(10.5f, ink, bold = true)
        val body = PdfText.paint(9f, ink)
        val bodyBold = PdfText.paint(9f, ink, bold = true)
        val small = PdfText.paint(8f, muted)
        val label = PdfText.paint(7.5f, muted, bold = true)
        val cell = PdfText.paint(9.5f, ink)
        val head = PdfText.paint(7.5f, muted, bold = true)
        val totalLabel = PdfText.paint(9f, ink)
        val totalLabelBold = PdfText.paint(11f, ink, bold = true)
        val totalPaint = PdfText.paint(13f, ink, bold = true)
        val words = PdfText.paint(8.5f, ink, bold = true)

        val hairline = Paint().apply { color = RULE; strokeWidth = 0.8f }
        val strongLine = Paint().apply { color = Color.rgb(0x8C, 0x8C, 0x8C); strokeWidth = 1f }
        val headFill = Paint().apply { color = Color.rgb(0xF2, 0xF1, 0xEF) }

        var canvas = surface.canvas

        // ---- masthead: who is writing, then what is being written
        var leftY = 40f
        var nameX = margin
        PdfText.loadShopLogo(context, design)?.let { logo ->
            nameX = PdfText.drawLogo(canvas, logo, margin, 58f, 38f) + 12f
        }
        val shopW = (right - 200f - nameX).toInt().coerceAtLeast(160)
        PdfText.draw(canvas, shop.name, nameX, leftY, shopName, shopW, maxLines = 2)
        leftY += 18f
        leftY = line(canvas, PdfText.oneline(shop.address), nameX, leftY, small, shopW, 1, 2f)
        if (design.showLegalIdentifiers) {
            leftY = line(
                canvas,
                shop.legalIdentifierLabels.joinToString("  ·  "),
                nameX,
                leftY,
                small,
                shopW,
                2,
                2f,
            )
        }

        PdfText.draw(
            canvas, data.header, right - 200f, 38f, title, 200,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        var metaY = 38f + PdfText.height(data.header, title, 200) + 8f
        PdfText.draw(
            canvas, "N° ${data.number}", right - 200f, metaY, bodyBold, 200,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        metaY += 13f
        PdfText.draw(
            canvas, data.date, right - 200f, metaY, small, 200,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        metaY += 12f

        var y = maxOf(leftY, metaY) + 8f
        canvas.drawLine(margin, y, right, y, strongLine)
        y += 16f

        // ---- party: the client, or the supplier on a bon de commande
        val partyTop = y
        PdfText.draw(canvas, data.partyLabel.uppercase(), margin, y, label, 120)
        y += 12f
        PdfText.draw(canvas, data.partyName, margin, y, lead, contentW - 180, maxLines = 2)
        y += 15f
        y = line(canvas, data.party.address, margin, y, body, contentW - 180, 2, 2f)
        y = line(canvas, data.party.phone, margin, y, small, contentW - 180, 1, 2f)
        if (design.showLegalIdentifiers) {
            y = line(
                canvas,
                data.party.legalIdentifierLabels.joinToString("  ·  "),
                margin,
                y,
                small,
                contentW - 180,
                2,
                2f,
            )
        }

        if (design.showPaymentMethod && data.showPaymentMethod) {
            PdfText.draw(
                canvas, "Règlement", right - 170f, partyTop, label, 170,
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            PdfText.draw(
                canvas, data.paymentMethod.french, right - 170f, partyTop + 12f, body, 170,
                Layout.Alignment.ALIGN_OPPOSITE,
            )
        }

        // ---- the table
        val qtyW = 32f
        val unitW = 76f
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

        var tableTop = y
        var lastRowEnd = tableTop + HEAD_H
        flow.pages.forEachIndexed { index, rows ->
            if (index > 0) {
                if (!surface.newPage()) return
                canvas = surface.canvas
                drawContinuationHeader(canvas, data, margin, right, bodyBold, hairline)
                tableTop = A4.RESUME_Y - HEAD_H
            }
            drawTableHead(canvas, margin, tableTop, right, headFill, head, descX, unitX, totalX, descW, unitW)
            var rowY = tableTop + HEAD_H
            rows.forEach { lineIndex ->
                val line = data.lines[lineIndex]
                val rowH = heights[lineIndex]
                // Drawn through PdfText, not Canvas.drawText: the latter takes a
                // baseline where every other cell takes a top, so the quantity
                // sat a line higher than the description beside it.
                PdfText.draw(canvas, line.quantity.toString(), margin + 6f, rowY + 5f, cell, 30)
                PdfText.draw(canvas, line.lineLabel, descX, rowY + 5f, cell, descW, maxLines = 2)
                PdfText.draw(
                    canvas, PdfText.money(line.unitPrice), unitX, rowY + 5f, cell, unitW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                PdfText.draw(
                    canvas, PdfText.money(line.total), totalX, rowY + 5f, cell, totalW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                rowY += rowH
                canvas.drawLine(margin, rowY, right, rowY, hairline)
            }
            lastRowEnd = rowY
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, index + 1, flow.totalPages)
        }

        // The totals are never split: they either follow the last row or they
        // get a sheet of their own, because an invoice whose total is on a
        // different page from its arithmetic is read as a mistake.
        if (flow.tailOnNewPage) {
            if (!surface.newPage()) return
            canvas = surface.canvas
            drawContinuationHeader(canvas, data, margin, right, bodyBold, hairline)
            lastRowEnd = A4.RESUME_Y - 6f
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, flow.totalPages, flow.totalPages)
        }

        // ---- totals, in two boxes that cannot touch
        val box = PdfTotals.box(right)
        var ty = lastRowEnd + 16f
        PdfTotals.row(canvas, box, "Total HT", PdfText.money(data.subtotal), ty, totalLabel, body)
        ty += 15f
        if (data.discountPercent > 0) {
            PdfTotals.row(
                canvas, box,
                "Remise −${PdfText.percent(data.discountPercent.toDouble())}%",
                "−${PdfText.money(data.discountAmount)}",
                ty, totalLabel, body,
            )
            ty += 15f
        }
        if (data.vatNotApplicable) {
            PdfTotals.label(canvas, box, "TVA non applicable", ty, totalLabel)
        } else {
            PdfTotals.row(
                canvas, box,
                "TVA ${PdfText.percent(data.effectiveVatPercent)}%",
                PdfText.money(data.vatAmount),
                ty, totalLabel, body,
            )
        }
        ty += 15f
        ty += 6f
        canvas.drawLine(box.left, ty, right, ty, hairline)
        ty += 6f
        PdfTotals.row(canvas, box, "Total TTC", PdfText.money(data.total), ty, totalLabelBold, totalPaint)
        ty += 26f

        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            PdfText.draw(canvas, "Incoterms : ${data.incoterm}", margin, ty, small, contentW, maxLines = 1)
            ty += 16f
        }

        if (design.showAmountWords) {
            PdfText.draw(canvas, data.amountInWords, margin, ty + 8f, words, contentW, maxLines = 4)
        }

        if (design.showFooter) {
            footer(canvas, margin, right, label)
        }
    }

    /** How much paper the totals and everything under them need after the last row. */
    private fun tailHeight(
        data: InvoiceData,
        design: InvoiceDesign,
        words: TextPaint,
        contentW: Int,
    ): Float {
        val rows = 2 + if (data.discountPercent > 0) 1 else 0
        var h = 54f + 15f * rows
        if (data.showIncoterm && data.incoterm.isNotBlank()) h += 16f
        if (design.showAmountWords) h += 8f + PdfText.height(data.amountInWords, words, contentW, 4)
        return h
    }

    private fun drawTableHead(
        canvas: Canvas,
        margin: Float,
        top: Float,
        right: Float,
        fill: Paint,
        head: TextPaint,
        descX: Float,
        unitX: Float,
        totalX: Float,
        descW: Int,
        unitW: Float,
    ) {
        canvas.drawRect(margin, top, right, top + HEAD_H, fill)
        PdfText.draw(canvas, "QTÉ", margin + 6f, top + 6f, head, 26)
        PdfText.draw(canvas, "DÉSIGNATION", descX, top + 6f, head, descW)
        PdfText.draw(
            canvas, "P.U.", unitX, top + 6f, head, unitW.toInt(),
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        PdfText.draw(
            canvas, "TOTAL", totalX, top + 6f, head, (right - totalX).toInt(),
            Layout.Alignment.ALIGN_OPPOSITE,
        )
    }

    /** Which document a second sheet belongs to; the shop block is not repeated. */
    private fun drawContinuationHeader(
        canvas: Canvas,
        data: InvoiceData,
        margin: Float,
        right: Float,
        bodyBold: TextPaint,
        hairline: Paint,
    ) {
        PdfText.draw(canvas, data.header, margin, 40f, bodyBold, 240)
        PdfText.draw(
            canvas, "N° ${data.number}", right - 240f, 40f, bodyBold, 240,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        canvas.drawLine(margin, 56f, right, 56f, hairline)
    }

    private fun footer(canvas: Canvas, margin: Float, right: Float, label: TextPaint) {
        PdfText.draw(
            canvas, "Merci de votre confiance", margin, 812f, label, (right - margin).toInt(),
            Layout.Alignment.ALIGN_CENTER,
        )
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
        const val HEAD_H = 20f
        val INK: Int = Color.rgb(0x1B, 0x1B, 0x1B)
        val MUTED: Int = Color.rgb(0x6E, 0x6E, 0x6E)
        val RULE: Int = Color.rgb(0xD8, 0xD8, 0xD8)
    }
}
