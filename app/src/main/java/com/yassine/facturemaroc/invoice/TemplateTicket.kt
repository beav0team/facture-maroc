package com.yassine.facturemaroc.invoice

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class TicketRenderer : PdfRenderer {

    override fun draw(
        canvas: Canvas,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ) {
        val margin = 15f
        val w = 226f - margin * 2
        val cell = PdfText.paint(8f, Color.BLACK)
        val cellBold = PdfText.paint(8f, Color.BLACK, bold = true)
        val cellRight = PdfText.paint(8f, Color.BLACK)
        cellRight.textAlign = android.graphics.Paint.Align.RIGHT

        var y = 10f

        // Shop name at top
        canvas.drawText(shop.name, margin, y, cellBold)
        y += 8f
        if (shop.address.isNotBlank()) {
            canvas.drawText(shop.address, margin, y, cell)
            y += 7f
        }
        y += 4f

        // Invoice number and date
        canvas.drawText("${data.number}", margin, y, cellBold)
        canvas.drawText(data.date, margin + 90f, y, cellRight)
        y += 14f

        // Client
        canvas.drawText(data.client.name, margin, y, cell)
        y += 10f

        y += 4f

        // Line items
        data.lines.forEach { line ->
            canvas.drawText(line.quantity.toString(), margin, y, cellBold)
            val itemName = PdfText.paint(7f, Color.BLACK)
            PdfText.draw(canvas, line.lineLabel, margin + 12f, y, itemName, w - 25f - 4, maxLines = 2)
            val lineHeight = PdfText.height(line.lineLabel, itemName, w - 25f - 4, 2).toFloat()
            PdfText.draw(canvas, PdfText.money(line.total), w - margin - 25f, y, cellRight, 25f)
            y += kotlin.math.max(lineHeight, 11f)

            // Price per unit on second line if there's room
            if (line.unitPrice > 0) {
                canvas.drawText("@ ${PdfText.money(line.unitPrice)}", margin + 12f, y, cell)
                y += 7f
            }
        }

        y += 6f

        // Totals
        canvas.drawText("Subtotal", margin, y, cell)
        canvas.drawText(PdfText.money(data.subtotal), w - margin - 30f, y, cellRight)
        y += 8f

        if (data.discountPercent > 0) {
            canvas.drawText("Discount −${trimPercent(data.discountPercent)}%", margin, y, cell)
            canvas.drawText("−${PdfText.money(data.discountAmount)}", w - margin - 30f, y, cellRight)
            y += 8f
        }

        canvas.drawText("TVA ${trimPercent(data.effectiveVatPercent)}%", margin, y, cell)
        canvas.drawText(PdfText.money(data.vatAmount), w - margin - 30f, y, cellRight)
        y += 10f

        canvas.drawText("Total TTC", margin, y, cellBold)
        canvas.drawText(PdfText.money(data.total), w - margin - 30f, y, cellRight)
        y += 14f

        // Footer
        canvas.drawText("Thank you for your business", margin, y, cell)
        y += 8f
        canvas.drawText(data.amountInWords, margin, y, cell)
    }

    private fun trimPercent(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", v)
}
