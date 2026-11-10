package com.yassine.facturemaroc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yassine.facturemaroc.data.ClientStatements
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.InvoiceStatus
import com.yassine.facturemaroc.data.InvoiceTemplate
import com.yassine.facturemaroc.data.ItemAttribute
import com.yassine.facturemaroc.data.PaymentInfo
import com.yassine.facturemaroc.data.PaymentMethod
import com.yassine.facturemaroc.data.PaymentTerms
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.invoice.InvoicePdf
import com.yassine.facturemaroc.invoice.ReceiptPdf
import com.yassine.facturemaroc.invoice.StatementPdf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every template on a document that uses every feature at once.
 *
 * A renderer that handles one rate and no discount can still pass a simple
 * smoke test and then overlap itself the first time a shop sells a 20 % article
 * and a 10 % service, takes a deposit and prints a QR. This builds that worst
 * case once and asks each of the six templates to survive it: a sheet with no
 * ink is a document that silently lost its totals.
 */
@RunWith(AndroidJUnit4::class)
class InvoicePdfGoldenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val shop = ShopSettings(
        name = "Garage Atlas",
        address = "12 rue des Orfèvres, Casablanca",
        phone = "0522000000",
        email = "contact@atlas.ma",
        ice = "001234567000089",
        ifNumber = "40218765",
        rc = "123456",
        patente = "98765",
        cnss = "1234567",
        bankName = "Attijariwafa",
        bankRib = "011020001234567890123456",
        bankHolder = "Garage Atlas",
        latePenaltyClause = "Tout retard de paiement entraîne des intérêts au taux légal.",
    )

    private val client = InvoiceClient(
        name = "Société BTP Nord",
        address = "Zone industrielle, Tanger",
        ice = "009876543000012",
    )

    /**
     * One document with mixed rates, both discounts, a percentage surcharge, a
     * decimal quantity, a tier, an attribute, a deadline, a deposit and a
     * partial payment.
     */
    private fun rich(number: String, type: DocumentType = DocumentType.FACTURE) = InvoiceData(
        number = number,
        date = "10/11/2026",
        client = client,
        documentType = type,
        lines = listOf(
            InvoiceLine(
                description = "Câble électrique",
                quantity = 2.5,
                unitPrice = 80.0,
                unit = "m",
                vatPercent = 20.0,
                itemId = "cable",
                lineAttributes = listOf(ItemAttribute("Référence", "E-100")),
                tierFromQuantity = 10,
                appliedTierPrice = 72.0,
            ),
            InvoiceLine(
                description = "Main-d'œuvre",
                quantity = 1.5,
                unitPrice = 200.0,
                unit = "heure",
                vatPercent = 10.0,
            ),
            InvoiceLine(
                description = "Frais de dossier",
                quantity = 1.0,
                unitPrice = 50.0,
                lineDiscountFixed = 10.0,
            ),
        ),
        discountFixed = 25.0,
        surchargeLabel = "Livraison",
        surchargePercent = 2.0,
        dueDate = "31/12/2026",
        customerNote = "Merci de vérifier la livraison à réception.",
        termsNote = PaymentTerms.DAYS30.note,
        paymentTerms = PaymentTerms.DAYS30,
        latePenaltyClause = shop.latePenaltyClause,
        depositAmount = 100.0,
        depositReference = "AC-20261101-0001",
        payments = listOf(
            PaymentInfo(
                paidAmount = 200.0,
                paidAt = "15/11/2026",
                method = PaymentMethod.TRANSFER,
            ),
        ),
    )

    private fun write(data: InvoiceData, template: InvoiceTemplate): File {
        val file = InvoicePdf.createPdf(
            context,
            data,
            shop,
            InvoiceDesign(template = template, showPaymentQr = true),
        )
        assertNotNull("no PDF was written for ${template.id}", file)
        return file!!
    }

    private fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }

    /** Non-white pixels on one page, drawn small: enough to tell blank from written. */
    private fun inkOn(file: File, index: Int): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val width = 120
                    val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    var ink = 0
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            if (bitmap.getPixel(x, y) != Color.WHITE) ink += 1
                        }
                    }
                    bitmap.recycle()
                    ink
                }
            }
        }

    @Test
    fun everyTemplateRendersARichDocumentWithNoBlankPage() {
        val data = rich("F-20261110-9010")
        InvoiceTemplate.entries.forEach { template ->
            val file = write(data, template)
            val pages = pageCount(file)
            assertTrue("${template.id} produced no page", pages >= 1)
            for (index in 0 until pages) {
                assertTrue(
                    "${template.id} page ${index + 1} is blank",
                    inkOn(file, index) > 40,
                )
            }
        }
    }

    @Test
    fun aReceiptAndAStatementRenderForTheSameRichDocument() {
        val data = rich("F-20261110-9011")
        val receipt = ReceiptPdf.create(context, data, shop, InvoiceDesign())
        assertNotNull(receipt)
        assertTrue(pageCount(receipt!!) >= 1)
        assertTrue(inkOn(receipt, 0) > 40)

        val record = InvoiceRecord(
            id = "r1",
            data = data,
            status = InvoiceStatus.ISSUED,
            createdAt = 0,
            updatedAt = 0,
        )
        val statement = StatementPdf.create(
            context = context,
            statement = ClientStatements.forClient(listOf(record), client.name),
            client = client,
            shop = shop,
            design = InvoiceDesign(),
        )
        assertNotNull(statement)
        assertTrue(pageCount(statement!!) >= 1)
        assertTrue(inkOn(statement, 0) > 40)
    }
}
