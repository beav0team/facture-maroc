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
 * The coloured one: a solid band across the top carrying the shop, and the
 * total in a band of the same colour at the foot.
 *
 * The band is the accent the shop chose, and it is the only fill on the page —
 * the table keeps hairline rules underneath. That is deliberate: a template
 * that fills the table head *and* the totals *and* stripes the rows turns a
 * colour laser into a page of mud, and photocopies into grey.
 */
class BandeauRenderer : PdfRenderer {

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
        val accent = design.accentColor

        val ink = Color.rgb(0x1A, 0x1A, 0x1A)
        val muted = Color.rgb(0x6B, 0x6B, 0x6B)
        val bandSoft = Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
        val bandFaint = Color.argb(0x99, 0xFF, 0xFF, 0xFF)

        val shopName = PdfText.paint(20f, Color.WHITE, bold = true)
        val title = PdfText.paint(22f, Color.WHITE, bold = true)
        val bandSmall = PdfText.paint(8.5f, bandSoft)
        val bandLegal = PdfText.paint(7.5f, bandFaint)
        val lead = PdfText.paint(10.5f, ink, bold = true)
        val body = PdfText.paint(9f, ink)
        val small = PdfText.paint(8f, muted)
        val label = PdfText.paint(7.5f, accent, bold = true)
        val head = PdfText.paint(7.5f, accent, bold = true)
        val cell = PdfText.paint(9.5f, ink)
        val cellRight = PdfText.paint(9.5f, ink)
        val rowLabel = PdfText.paint(9f, ink)
        val rowValue = PdfText.paint(9.5f, ink)
        val bandTotalLabel = PdfText.paint(8.5f, Color.WHITE, bold = true)
        val bandTotal = PdfText.paint(15f, Color.WHITE, bold = true)
        val words = PdfText.paint(8.5f, ink)

        val band = Paint().apply { color = accent }
        val tint = Paint().apply { color = PdfColor.tint(accent, 0.88f) }
        val hairline = Paint().apply { color = Color.rgb(0xE4, 0xE4, 0xE4); strokeWidth = 0.8f }

        var canvas = surface.canvas

        // ---- the band
        canvas.drawRect(0f, 0f, A4.WIDTH, BAND_H, band)

        var nameX = margin
        PdfText.loadShopLogo(context, design)?.let { logo ->
            // A white plate behind the logo: shop logos are drawn for white
            // paper, and a dark one on the accent would vanish into it.
            canvas.drawRect(margin, 27f, margin + 58f, 85f, Paint().apply { color = Color.WHITE })
            PdfText.drawLogo(canvas, logo, margin + 6f, 56f, 46f)
            nameX = margin + 72f
        }
        PdfText.draw(canvas, shop.name, nameX, 28f, shopName, (right - 190f - nameX).toInt(), maxLines = 2)
        PdfText.draw(canvas, PdfText.oneline(shop.address), nameX, 56f, bandSmall, (right - 190f - nameX).toInt(), maxLines = 1)
        if (design.showLegalIdentifiers) {
            // Two lines: five identifiers (ICE, IF, RC, patente, CNSS) do not
            // fit on one, and a clipped "Patente : 3577…" is worse than neither.
            PdfText.draw(
                canvas, shop.legalIdentifierLabels.joinToString("   ·   "),
                nameX, 68f, bandLegal, (right - 190f - nameX).toInt(), maxLines = 2,
            )
        }

        PdfText.draw(
            canvas, data.header, right - 190f, 26f, title, 190,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        PdfText.draw(
            canvas, "N° ${data.number}", right - 190f, 58f, bandSmall, 190,
            Layout.Alignment.ALIGN_OPPOSITE,
        )
        PdfText.draw(
            canvas, data.date, right - 190f, 71f, bandSmall, 190,
            Layout.Alignment.ALIGN_OPPOSITE,
        )

        var y = BAND_H + 20f

        // ---- party
        val partyTop = y
        PdfText.draw(canvas, data.partyLabel.uppercase(), margin, y, label, 140)
        y += 13f
        PdfText.draw(canvas, data.partyName, margin, y, lead, contentW - 180, maxLines = 2)
        y += 16f
        y = line(canvas, data.party.address, margin, y, body, contentW - 180, 2, 2f)
        y = line(canvas, data.party.phone, margin, y, small, contentW - 180, 1, 2f)
        if (design.showLegalIdentifiers) {
            y = line(
                canvas, data.party.legalIdentifierLabels.joinToString("   ·   "),
                margin, y, small, contentW - 180, 2, 2f,
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

        // ---- the table
        val qtyW = 30f
        val unitW = 78f
        val totalW = 86f
        val descX = margin + qtyW + 6f
        val unitX = right - unitW - totalW
        val totalX = right - totalW
        val descW = (unitX - descX - 8f).toInt()

        val heights = data.lines.map { line ->
            kotlin.math.max(PdfText.height(line.lineLabel, cell, descW, 2) + 9f, 20f)
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
                drawContinuationHeader(canvas, data, margin, right, band, shopName, bandSmall)
                headTop = A4.RESUME_Y - HEAD_H
            }
            canvas.drawRect(margin, headTop, right, headTop + HEAD_H, tint)
            PdfText.draw(canvas, "QTÉ", margin + 6f, headTop + 6f, head, 24)
            PdfText.draw(canvas, "DÉSIGNATION", descX, headTop + 6f, head, descW)
            PdfText.draw(
                canvas, "P.U.", unitX, headTop + 6f, head, unitW.toInt(),
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            PdfText.draw(
                canvas, "TOTAL", totalX, headTop + 6f, head, totalW.toInt(),
                Layout.Alignment.ALIGN_OPPOSITE,
            )

            var rowY = headTop + HEAD_H
            rows.forEach { lineIndex ->
                val line = data.lines[lineIndex]
                PdfText.draw(canvas, line.quantity.toString(), margin + 6f, rowY + 5f, cell, 24)
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
                canvas.drawLine(margin, rowY, right, rowY, hairline)
            }
            lastRowEnd = rowY
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, index + 1, flow.totalPages)
        }

        if (flow.tailOnNewPage) {
            if (!surface.newPage()) return
            canvas = surface.canvas
            drawContinuationHeader(canvas, data, margin, right, band, shopName, bandSmall)
            lastRowEnd = A4.RESUME_Y - 6f
            PdfFooter.draw(canvas, margin, right - margin, A4.HEIGHT, flow.totalPages, flow.totalPages)
        }

        // ---- totals in a panel, the last row in the band colour
        val box = PdfTotals.Box(right - 258f, right - 8f)
        var ty = lastRowEnd + 22f
        val panelTop = ty - 10f
        val rowCount = 2 + if (data.discountPercent > 0) 1 else 0
        val bandTop = ty + 16f * rowCount + 2f

        // The panel goes down before the rows. A fill painted afterwards covers
        // the labels it is supposed to sit behind — which is exactly what the
        // first version did, leaving an invoice with an empty grey box where
        // "Total HT" and "TVA" should have been.
        canvas.drawRect(right - 268f, panelTop, right, bandTop, tint)
        canvas.drawRect(right - 268f, bandTop, right, bandTop + 30f, band)
        PdfText.draw(canvas, "TOTAL TTC", right - 268f + 10f, bandTop + 11f, bandTotalLabel, 120)
        PdfText.draw(
            canvas, PdfText.money(data.total), right - 134f, bandTop + 7f, bandTotal, 124,
            Layout.Alignment.ALIGN_OPPOSITE,
        )

        PdfTotals.row(canvas, box, "Total HT", PdfText.money(data.subtotal), ty, rowLabel, rowValue)
        ty += 16f
        if (data.discountPercent > 0) {
            PdfTotals.row(
                canvas, box,
                "Remise ${PdfText.percent(data.discountPercent.toDouble())}%",
                "−${PdfText.money(data.discountAmount)}",
                ty, rowLabel, rowValue,
            )
            ty += 16f
        }
        if (data.vatNotApplicable) {
            PdfTotals.label(canvas, box, "TVA non applicable", ty, rowLabel)
        } else {
            PdfTotals.row(
                canvas, box,
                "TVA ${PdfText.percent(data.effectiveVatPercent)}%",
                PdfText.money(data.vatAmount),
                ty, rowLabel, rowValue,
            )
        }
        ty = bandTop + 30f + 14f

        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            PdfText.draw(canvas, "Incoterms : ${data.incoterm}", margin, ty, small, contentW, maxLines = 1)
            ty += 16f
        }

        if (design.showAmountWords) {
            PdfText.draw(canvas, data.amountInWords, margin, ty + 8f, words, contentW, maxLines = 4)
        }

        if (design.showFooter) {
            PdfText.draw(
                canvas, "Merci de votre confiance", margin, 812f, small, contentW,
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
        var h = 22f + 16f * rows + 2f + 30f + 14f
        if (data.showIncoterm && data.incoterm.isNotBlank()) h += 16f
        if (design.showAmountWords) h += 8f + PdfText.height(data.amountInWords, words, contentW, 4)
        return h
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        data: InvoiceData,
        margin: Float,
        right: Float,
        band: Paint,
        shopName: TextPaint,
        bandSmall: TextPaint,
    ) {
        canvas.drawRect(0f, 0f, A4.WIDTH, 56f, band)
        PdfText.draw(canvas, data.header, margin, 18f, shopName, 260)
        PdfText.draw(
            canvas, "N° ${data.number}", right - 260f, 22f, bandSmall, 260,
            Layout.Alignment.ALIGN_OPPOSITE,
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
        const val BAND_H = 112f
        const val HEAD_H = 20f
    }
}
