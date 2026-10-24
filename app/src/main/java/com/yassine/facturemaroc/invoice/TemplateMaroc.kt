package com.yassine.facturemaroc.invoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.ShopSettings

class MarocRenderer : PdfRenderer {

    override fun draw(
        context: Context,
        canvas: Canvas,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ) {
        val margin = 40f
        val w = 595f - margin * 2
        val cell = PdfText.paint(9f, Color.BLACK)
        val cellBold = PdfText.paint(9f, Color.BLACK, bold = true)
        val cellRight = PdfText.paint(9f, Color.BLACK)
        cellRight.textAlign = android.graphics.Paint.Align.RIGHT
        val titlePaint = PdfText.paint(14f, Color.BLACK, bold = true)

        var y = 40f

        // Header
        canvas.drawText(data.header, margin, y, titlePaint)
        y += 10f

        // Shop info block
        canvas.drawText(shop.name, margin, y, cellBold)
        y += 10f
        canvas.drawText(shop.address, margin, y, cell)
        y += 10f
        val legalIds = shop.legalIdentifierList.joinToString(" | ")
        if (legalIds.isNotBlank()) {
            canvas.drawText(legalIds, margin, y, cell)
            y += 14f
        }

        // Client info block
        canvas.drawText("Client:", margin, y, cellBold)
        y += 10f
        canvas.drawText(data.client.name, margin, y, cell)
        y += 10f
        if (data.client.address.isNotBlank()) {
            canvas.drawText(data.client.address, margin, y, cell)
            y += 10f
        }
        val clientIds = data.client.legalIdentifierList.joinToString(" | ")
        if (clientIds.isNotBlank()) {
            canvas.drawText(clientIds, margin, y, cell)
            y += 14f
        }

        y += 10f

        // Invoice number and date
        canvas.drawText("${data.number}", margin, y, cellBold)
        canvas.drawText(data.date, margin + 200f, y, cellRight)
        y += 20f

        if (data.showReferenceField && data.referenceNumber.isNotBlank()) {
            canvas.drawText(
                "Annule et remplace la facture N° ${data.referenceNumber}",
                margin,
                y,
                cell,
            )
            y += 20f
        }

        // Gridded line items table
        val colQty = 40f
        val colDesc = w - colQty - 140f
        val colUnit = 60f
        val colTotal = 80f

        // Header row
        canvas.drawRect(margin, y, margin + w, y + 16f, Paint().apply { color = Color.parseColor("#1E429F") })
        canvas.drawText("Qty", margin + 6f, y + 11f, PdfText.paint(8f, Color.WHITE, bold = true))
        canvas.drawText("Description", margin + colQty + 6f, y + 11f, PdfText.paint(8f, Color.WHITE, bold = true))
        canvas.drawText("Price", margin + colQty + colDesc + 8f, y + 11f, PdfText.paint(8f, Color.WHITE, bold = true).apply { textAlign = android.graphics.Paint.Align.RIGHT })
        canvas.drawText("Total", margin + colQty + colDesc + colUnit + 12f, y + 11f, PdfText.paint(8f, Color.WHITE, bold = true).apply { textAlign = android.graphics.Paint.Align.RIGHT })
        y += 16f

        data.lines.forEachIndexed { index, line ->
            val rowH = kotlin.math.max(PdfText.height(line.lineLabel, cell, (colDesc - 12).toInt(), 2) + 7f, 18f)
            // Alternating row shading
            if (index % 2 == 1) {
                canvas.drawRect(margin, y, margin + w, y + rowH, Paint().apply { color = Color.parseColor("#F5F5F5") })
            }
            canvas.drawText(line.quantity.toString(), margin + 6f, y + 4f, cell)
            PdfText.draw(canvas, line.lineLabel, margin + colQty + 6f, y + 4f, cell, (colDesc - 12).toInt(), maxLines = 2)
            PdfText.draw(canvas, PdfText.money(line.unitPrice), margin + colQty + colDesc + 8f, y + 5f, cellRight, (colUnit - 8).toInt())
            PdfText.draw(canvas, PdfText.money(line.total), margin + colQty + colDesc + colUnit + 12f, y + 5f, cellRight, (colTotal - 8).toInt())
            y += rowH
        }

        y += 10f

        // Totals block - right aligned grid
        val totalsStart = margin + colQty + colDesc + colUnit
        canvas.drawText("Subtotal", totalsStart, y, cellRight)
        canvas.drawText(PdfText.money(data.subtotal), margin + colQty + colDesc + colUnit + 12f, y, cellRight)
        y += 14f

        if (data.discountPercent > 0) {
            canvas.drawText("Discount −${trimPercent(data.discountPercent.toDouble())}%", totalsStart, y, cellRight)
            canvas.drawText("−${PdfText.money(data.discountAmount)}", margin + colQty + colDesc + colUnit + 12f, y, cellRight)
            y += 14f
        }

        if (data.vatNotApplicable) {
            canvas.drawText("TVA non applicable", totalsStart, y, cellRight)
            y += 14f
        } else {
            canvas.drawText("TVA ${trimPercent(data.effectiveVatPercent)}%", totalsStart, y, cellRight)
            canvas.drawText(PdfText.money(data.vatAmount), margin + colQty + colDesc + colUnit + 12f, y, cellRight)
            y += 16f
        }

        canvas.drawText("Total TTC", totalsStart, y, cellBold)
        canvas.drawText(PdfText.money(data.total), margin + colQty + colDesc + colUnit + 12f, y, cellBold)
        y += 20f

        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            canvas.drawText("Incoterms : ${data.incoterm}", margin, y, cell)
            y += 20f
        }

        // Signature space
        canvas.drawText("Signature", margin, y + 10f, cellBold)
        canvas.drawLine(margin, y + 30f, margin + 200f, y + 30f, Paint().apply { color = Color.BLACK })
        y += 50f

        // Amount in words
        canvas.drawText(data.amountInWords, margin, y, cell)
    }

    private fun trimPercent(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", v)
}
