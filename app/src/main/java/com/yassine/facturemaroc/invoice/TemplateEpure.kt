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
 * The typographic layout: no boxes, no fills, and the paper left mostly empty.
 *
 * Small letterspaced capitals do the work a coloured band would do in another
 * template, and one accent hairline above the total is the only colour on the
 * page. It is the quietest of the five and the one that reads best as a
 * photograph of a document rather than as a form.
 */
class EpureRenderer : PdfRenderer {

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

        val ink = Color.rgb(0x18, 0x18, 0x18)
        val muted = Color.rgb(0x7A, 0x7A, 0x7A)
        val shopName = spaced(PdfText.paint(11f, ink, bold = true), 0.14f)
        val docTitle = spaced(PdfText.paint(10f, accent, bold = true), 0.20f)
        val lead = PdfText.paint(12f, ink, bold = true)
        val body = PdfText.paint(9.5f, ink)
        val small = PdfText.paint(8f, muted)
        val label = spaced(PdfText.paint(7f, muted, bold = true), 0.16f)
        val head = spaced(PdfText.paint(7f, muted, bold = true), 0.14f)
        val cell = PdfText.paint(9.5f, ink)
        val cellRight = PdfText.paint(9.5f, ink)
        val totalLabel = spaced(PdfText.paint(7.5f, muted, bold = true), 0.14f)
        val totalPaint = PdfText.paint(19f, ink, bold = true)
        val words = PdfText.paint(8.5f, muted)

        val hairline = Paint().apply { color = Color.rgb(0xE6, 0xE6, 0xE6); strokeWidth = 0.75f }
        val accentRule = Paint().apply { color = accent; strokeWidth = 1.4f }

        var canvas = surface.canvas

        // ---- masthead: an accent tick, the shop, and the document's identity
        canvas.drawRect(margin, 44f, margin + 26f, 47f, Paint().apply { color = accent })
        var leftY = 56f
        PdfText.draw(canvas, shop.name.uppercase(), margin, leftY, shopName, (contentW - 190), maxLines = 2)
        leftY += 19f
        leftY = line(canvas, shop.address, margin, leftY, small, contentW - 190, 2, 2f)
        if (design.showLegalIdentifiers) {
            leftY = line(
                canvas,
                shop.legalIdentifierLabels.joinToString("   ·   "),
                margin,
                leftY,
                small,
                contentW - 190,
                2,
                2f,
            )
        }

        PdfText.draw(
            canvas, data.header.uppercase(), right - 170f, 40f, docTitle, 170,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        PdfText.draw(
            canvas, "N° ${data.number}", right - 170f, 56f, small, 170,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        PdfText.draw(
            canvas, data.date, right - 170f, 68f, small, 170,
            Layout.Alignment.ALIGN_OPPOSITE,
        )

        var y = maxOf(leftY, 82f) + 8f
        canvas.drawLine(margin, y, right, y, hairline)
        y += 20f

        // ---- party
        val partyTop = y
        PdfText.draw(canvas, data.partyLabel.uppercase(), margin, y, label, 140)
        y += 13f
        PdfText.draw(canvas, data.partyName, margin, y, lead, contentW - 180, maxLines = 2)
        y += 17f
        y = line(canvas, data.party.address, margin, y, body, contentW - 180, 2, 2f)
        y = line(canvas, data.party.phone, margin, y, small, contentW - 180, 1, 2f)
        if (design.showLegalIdentifiers) {
            y = line(
                canvas,
                data.party.legalIdentifierLabels.joinToString("   ·   "),
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
                canvas, "RÈGLEMENT", right - 160f, partyTop, label, 160,
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            PdfText.draw(
                canvas, data.paymentMethod.french, right - 160f, partyTop + 13f, body, 160,
                Layout.Alignment.ALIGN_OPPOSITE,
            )
        }

        y += 26f

        // ---- the table: capitals, one rule, and white space between rows
        val qtyW = 28f
        val unitW = 80f
        val totalW = 88f
        val descX = margin + qtyW + 6f
        val unitX = right - unitW - totalW
        val totalX = right - totalW
        val descW = (unitX - descX - 10f).toInt()

        val heights = data.lines.map { line ->
            kotlin.math.max(PdfText.height(line.lineLabel, cell, descW, 2) + 10f, 22f)
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
                drawContinuationHeader(canvas, data, margin, right, docTitle, hairline)
                headTop = A4.RESUME_Y - HEAD_H
            }
            PdfText.draw(canvas, "QTÉ", margin, headTop, head, 24)
            PdfText.draw(canvas, "DÉSIGNATION", descX, headTop, head, descW)
            PdfText.draw(
                canvas, "P.U.", unitX, headTop, head, unitW.toInt(),
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            PdfText.draw(
                canvas, "TOTAL", totalX, headTop, head, totalW.toInt(),
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            canvas.drawLine(margin, headTop + 13f, right, headTop + 13f, hairline)

            var rowY = headTop + HEAD_H
            rows.forEach { lineIndex ->
                val line = data.lines[lineIndex]
                PdfText.draw(canvas, line.quantity.toString(), margin, rowY + 5f, cell, 24)
                PdfText.draw(canvas, line.lineLabel, descX, rowY + 5f, cell, descW, maxLines = 2)
                PdfText.draw(
                    canvas, PdfText.money(line.unitPrice), unitX, rowY + 5f, cell, unitW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                PdfText.draw(
                    canvas, PdfText.money(line.total), totalX, rowY + 5f, cellRight, totalW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
                rowY += heights[lineIndex]
                canvas.drawLine(descX, rowY, right, rowY, hairline)
            }
            lastRowEnd = rowY
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, index + 1, flow.totalPages)
        }

        if (flow.tailOnNewPage) {
            if (!surface.newPage()) return
            canvas = surface.canvas
            drawContinuationHeader(canvas, data, margin, right, docTitle, hairline)
            lastRowEnd = A4.RESUME_Y - 8f
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, flow.totalPages, flow.totalPages)
        }

        // ---- totals: labels in capitals, one accent rule, one big figure
        val box = PdfTotals.box(right, valueWidth = 150f)
        var ty = lastRowEnd + 26f
        PdfTotals.row(canvas, box, "Total HT", PdfText.money(data.subtotal), ty, label, cellRight)
        ty += 16f
        if (data.discountPercent > 0) {
            PdfTotals.row(
                canvas, box,
                "Remise ${PdfText.percent(data.discountPercent.toDouble())}%",
                "−${PdfText.money(data.discountAmount)}",
                ty, label, cellRight,
            )
            ty += 16f
        }
        if (data.vatNotApplicable) {
            PdfTotals.label(canvas, box, "TVA NON APPLICABLE", ty, label)
        } else {
            PdfTotals.row(
                canvas, box,
                "TVA ${PdfText.percent(data.effectiveVatPercent)}%",
                PdfText.money(data.vatAmount),
                ty, label, cellRight,
            )
        }
        ty += 18f
        canvas.drawLine(box.left, ty, right, ty, accentRule)
        ty += 12f
        PdfTotals.label(canvas, box, "TOTAL TTC", ty, totalLabel)
        PdfTotals.value(canvas, box, PdfText.money(data.total), ty - 4f, totalPaint)
        ty += 32f

        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            PdfText.draw(canvas, "Incoterms : ${data.incoterm}", margin, ty, small, contentW, maxLines = 1)
            ty += 16f
        }

        if (design.showAmountWords) {
            PdfText.draw(canvas, data.amountInWords, margin, ty + 8f, words, contentW, maxLines = 4)
        }

        if (design.showFooter) {
            canvas.drawLine(margin, 820f, right, 820f, Paint().apply { color = accent; strokeWidth = 0.6f })
            PdfText.draw(
                canvas, "Merci de votre confiance", margin, 802f, small, contentW,
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
        var h = 26f + 16f * rows + 18f + 12f + 32f
        if (data.showIncoterm && data.incoterm.isNotBlank()) h += 16f
        if (design.showAmountWords) h += 8f + PdfText.height(data.amountInWords, words, contentW, 4)
        return h
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        data: InvoiceData,
        margin: Float,
        right: Float,
        docTitle: TextPaint,
        hairline: Paint,
    ) {
        PdfText.draw(canvas, data.header.uppercase(), margin, 40f, docTitle, 320)
        PdfText.draw(
            canvas, "N° ${data.number}", right - 240f, 40f, docTitle, 240,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        canvas.drawLine(margin, 58f, right, 58f, hairline)
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

    /** Letterspaced capitals, the one typographic trick this layout owns. */
    private fun spaced(paint: TextPaint, amount: Float): TextPaint = paint.apply {
        letterSpacing = amount
    }

    private companion object {
        const val MARGIN = 46f
        const val HEAD_H = 24f
    }
}
