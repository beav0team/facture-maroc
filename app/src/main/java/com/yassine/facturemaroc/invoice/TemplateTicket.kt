package com.yassine.facturemaroc.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.text.Layout
import android.text.TextPaint
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.ShopSettings

/**
 * The 80mm receipt.
 *
 * It is the one template that never paginates: its height is computed from the
 * sale before a page is started, so the roll is exactly as long as the receipt.
 *
 * That height is not a second calculation. The first version advanced a counter
 * in [InvoicePdf] mirroring the drawing, and the two drifted the moment the
 * words line started wrapping — the last lines of the receipt were cut off. So
 * the same drawing routine runs twice: once on a one-pixel canvas it cannot
 * see, which is enough to measure every wrapped line, and once on the real
 * page. One code path, so the roll cannot be shorter than its contents.
 *
 * No shop logo: an 80mm till printer has one colour, and a logo in it is a
 * grey smudge. The design's accent is deliberately ignored for the same reason.
 */
class TicketRenderer : PdfRenderer {

    override fun draw(
        context: Context,
        surface: PageSurface,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ) {
        drawReceipt(surface.canvas, data, shop, design)
    }

    /**
     * Draws the whole receipt and returns where the paper ends.
     *
     * The caller that only wants the height runs this on a 1x1 canvas and
     * reads the return value. Nothing in here touches the context or the
     * surface, so both callers go through exactly the same arithmetic.
     */
    private fun drawReceipt(
        canvas: Canvas,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ): Float {
        val margin = 15f
        val right = WIDTH - margin
        val contentW = (right - margin).toInt()

        val name = PdfText.paint(9.5f, Color.BLACK, bold = true)
        val title = PdfText.paint(8f, Color.BLACK, bold = true)
        val small = PdfText.paint(7.5f, Color.BLACK)
        val smallBold = PdfText.paint(7.5f, Color.BLACK, bold = true)
        val item = PdfText.paint(7.5f, Color.BLACK)
        val itemSmall = PdfText.paint(7f, Color.BLACK)
        val money = PdfText.paint(8f, Color.BLACK)
        val moneyBold = PdfText.paint(9.5f, Color.BLACK, bold = true)
        val words = PdfText.paint(7f, Color.BLACK)
        val dashed = Paint().apply {
            color = Color.BLACK
            strokeWidth = 0.6f
            pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        }

        var y = 14f

        PdfText.draw(canvas, shop.name, margin, y, name, contentW, Layout.Alignment.ALIGN_CENTER, maxLines = 2)
        y += PdfText.height(shop.name, name, contentW, 2) + 2f
        if (shop.address.isNotBlank()) {
            PdfText.draw(canvas, shop.address, margin, y, small, contentW, Layout.Alignment.ALIGN_CENTER, maxLines = 2)
            y += PdfText.height(shop.address, small, contentW, 2) + 1f
        }
        if (design.showLegalIdentifiers) {
            val legal = shop.legalIdentifierLabels.joinToString("   ·   ")
            if (legal.isNotBlank()) {
                PdfText.draw(canvas, legal, margin, y, small, contentW, Layout.Alignment.ALIGN_CENTER, maxLines = 2)
                y += PdfText.height(legal, small, contentW, 2) + 1f
            }
        }
        y += 4f
        canvas.drawLine(margin, y, right, y, dashed)
        y += 7f

        PdfText.draw(canvas, data.header.uppercase(), margin, y, title, contentW, Layout.Alignment.ALIGN_CENTER)
        y += 12f
        PdfText.draw(canvas, data.number, margin, y, smallBold, (contentW * 0.58f).toInt(), maxLines = 1)
        PdfText.draw(canvas, data.date, right - 86f, y, small, 86, Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1)
        y += 10f

        if (data.showReferenceField && data.referenceNumber.isNotBlank()) {
            val reference = "Annule et remplace la facture N° ${data.referenceNumber}"
            PdfText.draw(canvas, reference, margin, y, small, contentW, maxLines = 2)
            y += PdfText.height(reference, small, contentW, 2) + 2f
        }

        val party = "${data.partyLabel} : ${data.partyName}"
        PdfText.draw(canvas, party, margin, y, small, contentW, maxLines = 2)
        y += PdfText.height(party, small, contentW, 2) + 2f

        y += 4f
        canvas.drawLine(margin, y, right, y, dashed)
        y += 7f

        // ---- articles
        // The amount column has to hold "14 304,00 DH" at 8pt. At the first
        // 34 points it clipped to "14 304,0…" on every line of a real sale.
        val amountW = 58f
        // The quantity column is measured rather than fixed: 11 points holds one
        // digit, so a sale of 120 valves printed "1…" beside the line. The
        // column is as wide as the longest count on this receipt.
        val countW = data.lines
            .maxOfOrNull { smallBold.measureText(it.quantity.toString()) }
            ?.let { kotlin.math.ceil(it.toDouble()).toFloat() + 1f }
            ?.coerceAtLeast(11f) ?: 11f
        val labelX = margin + countW + 3f
        val labelW = (right - amountW - 4f - labelX).toInt()
        data.lines.forEach { line ->
            PdfText.draw(canvas, line.quantity.toString(), margin, y, smallBold, countW.toInt())
            PdfText.draw(canvas, line.lineLabel, labelX, y, item, labelW, maxLines = 2)
            PdfText.draw(
                canvas, PdfText.money(line.total), right - amountW, y, money, amountW.toInt(),
                Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1,
            )
            y += kotlin.math.max(PdfText.height(line.lineLabel, item, labelW, 2) + 2f, 11f)
            if (line.unitPrice > 0) {
                PdfText.draw(canvas, "@ ${PdfText.money(line.unitPrice)}", labelX, y, itemSmall, labelW, maxLines = 1)
                y += 8f
            }
        }

        y += 3f
        canvas.drawLine(margin, y, right, y, dashed)
        y += 7f

        // ---- totals
        fun totalRow(label: String, value: String, labelPaint: TextPaint, valuePaint: TextPaint) {
            PdfText.draw(canvas, label, margin, y, labelPaint, contentW - 70)
            PdfText.draw(
                canvas, value, right - 70f, y, valuePaint, 70,
                Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1,
            )
            y += 10f
        }

        totalRow("Total HT", PdfText.money(data.subtotal), small, money)
        if (data.discountPercent > 0) {
            totalRow(
                "Remise ${PdfText.percent(data.discountPercent.toDouble())}%",
                "−${PdfText.money(data.discountAmount)}",
                small,
                money,
            )
        }
        if (data.vatNotApplicable) {
            PdfText.draw(canvas, "TVA non applicable", margin, y, small, contentW)
            y += 10f
        } else {
            totalRow("TVA ${PdfText.percent(data.effectiveVatPercent)}%", PdfText.money(data.vatAmount), small, money)
        }
        y += 2f
        totalRow("TOTAL TTC", PdfText.money(data.total), smallBold, moneyBold)
        y += 4f

        if (data.showIncoterm && data.incoterm.isNotBlank()) {
            PdfText.draw(canvas, "Incoterms : ${data.incoterm}", margin, y, small, contentW, maxLines = 1)
            y += 9f
        }
        if (design.showPaymentMethod && data.showPaymentMethod) {
            PdfText.draw(canvas, "Règlement : ${data.paymentMethod.french}", margin, y, small, contentW, maxLines = 1)
            y += 9f
        }

        if (design.showAmountWords) {
            y += 4f
            PdfText.draw(canvas, data.amountInWords, margin, y, words, contentW, maxLines = 6)
            y += PdfText.height(data.amountInWords, words, contentW, 6) + 4f
        }

        if (design.showFooter) {
            PdfText.draw(
                canvas, "Merci de votre visite", margin, y, small, contentW,
                Layout.Alignment.ALIGN_CENTER, maxLines = 1,
            )
            y += 9f
        }

        return y + 6f
    }

    companion object {
        const val WIDTH = 226f

        /** How long the roll has to be for this sale. */
        fun height(data: InvoiceData, shop: ShopSettings, design: InvoiceDesign): Int {
            val probe = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            val end = TicketRenderer().drawReceipt(probe, data, shop, design)
            return kotlin.math.ceil(end.toDouble()).toInt().coerceAtLeast(120)
        }
    }
}
