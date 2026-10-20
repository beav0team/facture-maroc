package com.yassine.facturemaroc.invoice

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class ClassiqueRenderer : PdfRenderer {

    override fun draw(
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

        // Header block
        canvas.drawText(data.header, margin, y, titlePaint)
        y += 20f

        // Shop info
        canvas.drawText(shop.name, margin, y, cellBold)
        y += 12f
        if (shop.address.isNotBlank()) {
            canvas.drawText(shop.address, margin, y, cell)
            y += 12f
        }
        val legalIds = shop.legalIdentifierList.joinToString(" · ")
        if (legalIds.isNotBlank()) {
            canvas.drawText(legalIds, margin, y, cell)
            y += 16f
        }

        y += 10f

        // Client info
        canvas.drawText("Client:", margin, y, cellBold)
        y += 12f
        canvas.drawText(data.client.name, margin, y, cell)
        y += 10f
        if (data.client.address.isNotBlank()) {
            canvas.drawText(data.client.address, margin, y, cell)
            y += 10f
        }
        val clientIds = data.client.legalIdentifierList.joinToString(" · ")
        if (clientIds.isNotBlank()) {
            canvas.drawText(clientIds, margin, y, cell)
            y += 16f
        }

        y += 10f

        // Invoice number and date
        canvas.drawText("${data.number}", margin, y, cellBold)
        canvas.drawText(data.date, margin + 200f, y, cellRight)
        y += 20f

        // Table header
        val colQty = 40f
        val colDesc = w - colQty - 120f
        val colUnit = 60f
        val colTotal = 60f

        canvas.drawText("Qty", margin, y, cellBold)
        canvas.drawText("Description", margin + colQty + 6f, y, cellBold)
        canvas.drawText("Price", margin + colQty + colDesc + 8f, y, cellRight)
        canvas.drawText("Total", margin + colQty + colDesc + colUnit + 12f, y, cellRight)
        y += 16f

        // Line items
        data.lines.forEach { line ->
            val rowH = kotlin.math.max(PdfText.height(line.lineLabel, cell, colDesc, 2) + 8f, 20f)
            canvas.drawText(line.quantity.toString(), margin, y + 4f, cell)
            PdfText.draw(canvas, line.lineLabel, margin + colQty + 6f, y + 4f, cell, colDesc, maxLines = 2)
            PdfText.draw(canvas, PdfText.money(line.unitPrice), margin + colQty + colDesc + 8f, y + 5f, cellRight, colUnit)
            PdfText.draw(canvas, PdfText.money(line.total), margin + colQty + colDesc + colUnit + 12f, y + 5f, cellRight, colTotal)
            y += rowH
        }

        y += 10f

        // Totals
        canvas.drawText("Subtotal", margin + colQty + colDesc + colUnit, y, cellRight)
        canvas.drawText(PdfText.money(data.subtotal), margin + colQty + colDesc + colUnit + 12f, y, cellRight)
        y += 14f

        if (data.discountPercent > 0) {
            canvas.drawText("Discount −${trimPercent(data.discountPercent)}%", margin + colQty + colDesc + colUnit, y, cellRight)
            canvas.drawText("−${PdfText.money(data.discountAmount)}", margin + colQty + colDesc + colUnit + 12f, y, cellRight)
            y += 14f
        }

        canvas.drawText("TVA ${trimPercent(data.effectiveVatPercent)}%", margin + colQty + colDesc + colUnit, y, cellRight)
        canvas.drawText(PdfText.money(data.vatAmount), margin + colQty + colDesc + colUnit + 12f, y, cellRight)
        y += 16f

        canvas.drawText("Total TTC", margin + colQty + colDesc + colUnit, y, cellBold)
        canvas.drawText(PdfText.money(data.total), margin + colQty + colDesc + colUnit + 12f, y, cellRight)
        y += 20f

        // Amount in words
        canvas.drawText(data.amountInWords, margin, y, cell)
    }

    private fun trimPercent(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", v)
}
