package com.yassine.facturemaroc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.InvoiceStatus
import com.yassine.facturemaroc.data.InvoiceTemplate
import com.yassine.facturemaroc.data.ImageBytes
import com.yassine.facturemaroc.data.ClientStatements
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.PaymentInfo
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.data.ShopSnapshot
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
 * How many sheets a real PDF comes out with, counted by the platform's own
 * reader rather than by the code that wrote it.
 *
 * The layout arithmetic is covered by `PageFlowTest` on the JVM. What that
 * cannot prove is that the renderers actually ask for the pages the arithmetic
 * decided on — a template that ignores the result and keeps drawing on page one
 * would pass every unit test and still hand out an invoice with no total. This
 * test is the renderer and the writer agreeing in the same place.
 */
@RunWith(AndroidJUnit4::class)
class InvoicePdfPagesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val shop = ShopSettings(
        name = "Garage Atlas",
        address = "12 rue des Orfèvres, Casablanca",
        ice = "001234567000089",
        ifNumber = "40218765",
    )

    private val client = InvoiceClient(
        name = "Société BTP Nord",
        address = "Zone industrielle, Tanger",
        ice = "009876543000012",
    )

    private fun invoice(number: String, lineCount: Int): InvoiceData = InvoiceData(
        number = number,
        date = "29/10/2026",
        client = client,
        lines = List(lineCount) { index ->
            InvoiceLine(
                description = "Chaise en rotin ${index + 1}",
                quantity = 1,
                unitPrice = 950.0 + index,
            )
        },
    )

    private fun write(data: InvoiceData, template: InvoiceTemplate): File {
        val file = InvoicePdf.createPdf(context, data, shop, InvoiceDesign(template = template))
        assertNotNull("no PDF was written for ${template.id}", file)
        return file!!
    }

    /**
     * A durable copy of a just-written PDF.
     *
     * [InvoicePdf] names the file after the document number, so two renders of
     * the same invoice (one with notes, one without) land on the same path and
     * the second silently replaces the first. Comparing the two needs the
     * first one kept before the second is written.
     */
    private fun preserve(file: File, name: String): File =
        File(file.parentFile, name).also { file.copyTo(it, overwrite = true) }

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
    fun aShortInvoiceStaysOnOneSheet() {
        val data = invoice("F-20261029-9001", 3)
        InvoiceTemplate.entries.forEach { template ->
            val file = write(data, template)
            assertEquals("${template.id} split a three-line sale", 1, pageCount(file))
            file.delete()
        }
    }

    @Test
    fun aLongInvoiceGetsASecondSheet() {
        val data = invoice("F-20261029-9002", 40)
        val file = write(data, InvoiceTemplate.CLASSIQUE)
        assertTrue("forty lines did not paginate", pageCount(file) >= 2)
        file.delete()
    }

    @Test
    fun everySheetCarriesWriting() {
        val data = invoice("F-20261029-9003", 40)
        val file = write(data, InvoiceTemplate.CLASSIQUE)
        val pages = pageCount(file)
        for (page in 0 until pages) {
            assertTrue("page ${page + 1} of $pages came out blank", inkOn(file, page) > 100)
        }
        file.delete()
    }

    @Test
    fun theGriddedLayoutPaginatesToo() {
        val data = invoice("F-20261029-9004", 40)
        val file = write(data, InvoiceTemplate.MAROC)
        assertTrue("the gridded layout did not paginate", pageCount(file) >= 2)
        file.delete()
    }

    @Test
    fun everySheetOfEveryLayoutCarriesWriting() {
        // Each A4 template decides for itself when to call newPage and what to
        // draw at the top of the next one. A layout that paginates but leaves
        // page two blank passes the page count and still hands over a document
        // with no total on it, so the ink is checked on every sheet.
        val data = invoice("F-20261029-9006", 40)
        InvoiceTemplate.entries.filterNot { it.isRoll }.forEach { template ->
            val file = write(data, template)
            val pages = pageCount(file)
            assertTrue("${template.id} did not paginate", pages >= 2)
            for (page in 0 until pages) {
                assertTrue(
                    "${template.id} page ${page + 1} of $pages came out blank",
                    inkOn(file, page) > 100,
                )
            }
            file.delete()
        }
    }

    @Test
    fun theReceiptNeverPaginates() {
        // A roll is one page whatever is on it: its height is computed from the
        // sale before the page is started, so a second page would be a second
        // receipt rather than a continuation.
        val data = invoice("T-20261029-9005", 40)
        val file = write(data, InvoiceTemplate.TICKET)
        assertEquals(1, pageCount(file))
        file.delete()
    }

    @Test
    fun notesAndSignaturePrintWithoutOverflowingTheSheet() {
        // The extras are the only content whose height is not known before the
        // sale is measured, so a long note is exactly what pushes a one-sheet
        // invoice onto a second sheet with the totals stranded.
        val plain = invoice("F-20261029-9007", 3)
        val annotated = plain.copy(
            customerNote = "Merci de vérifier la marchandise avant de quitter l'atelier. " +
                "Aucune réclamation ne sera acceptée après le départ du véhicule.",
            termsNote = "Paiement comptant. Marchandise vendue en l'état.",
            thankYouNote = "Merci de votre confiance.",
            signature = signatureBytes(),
        )

        InvoiceTemplate.entries.forEach { template ->
            val plainFile = write(plain, template)
            val before = preserve(plainFile, "before-${template.id}.pdf")
            val after = write(annotated, template)

            assertEquals(
                "${template.id} added a sheet for the notes",
                pageCount(before),
                pageCount(after),
            )
            assertTrue(
                "${template.id} printed nothing for the notes",
                differingPixels(after, before) > 200,
            )
            before.delete()
            after.delete()
        }
    }

    @Test
    fun aMixedRateInvoiceStaysOnOneSheetInEveryLayout() {
        // The VAT recap grows by one row per rate. If a template still reserved
        // room for a single row, the totals would be pushed past the foot of the
        // sheet and land alone on page two — a total on a different page from
        // its arithmetic.
        val data = invoice("F-20261029-9012", 3).copy(
            lines = listOf(
                InvoiceLine("Chaise en rotin", 2, 950.0),
                InvoiceLine("Montage à domicile", 1, 450.0, vatPercent = 10.0),
                InvoiceLine("Livraison express", 1, 120.0, vatPercent = 10.0),
            ),
            surchargeAmount = 80.0,
        )

        InvoiceTemplate.entries.forEach { template ->
            val file = write(data, template)
            assertEquals("${template.id} split a mixed-rate sale", 1, pageCount(file))
            assertTrue("${template.id} printed a blank sheet", inkOn(file, 0) > 100)
            file.delete()
        }
    }

    @Test
    fun anIssuedDocumentPrintsItsOwnShopDetails() {
        // Two shop identities, one document holding the first as a snapshot.
        // The printed page must not follow the live settings: comparing the
        // ink of the two renders is enough to prove the page changed, and the
        // value-level assertion that it kept the old name lives in the JVM
        // `ShopSnapshotTest`.
        val snapshot = ShopSnapshot.of(
            ShopSettings(
                name = "Ancien Garage",
                address = "1 rue du Souk, Fès",
                ice = "111222333000044",
            ),
        )
        val base = invoice("F-20261029-9008", 2)

        val snapshotFile = write(base.copy(shop = snapshot), InvoiceTemplate.CLASSIQUE)
        val withSnapshot = preserve(snapshotFile, "with-snapshot.pdf")
        val withoutSnapshot = write(base, InvoiceTemplate.CLASSIQUE)

        assertTrue(
            "the snapshot did not change what was printed",
            differingPixels(withSnapshot, withoutSnapshot) > 100,
        )
        assertEquals(1, pageCount(withSnapshot))
        withSnapshot.delete()
        withoutSnapshot.delete()
    }

    @Test
    fun aPaymentAndASurchargePrintWithoutOverflowingTheSheet() {
        val plain = invoice("F-20261029-9010", 3)
        val annotated = plain.copy(
            surchargeLabel = "Livraison",
            surchargeAmount = 100.0,
            payment = PaymentInfo(paidAmount = 500.0, paidAt = "10/11/2026", note = "Chèque 123"),
        )

        InvoiceTemplate.entries.filterNot { it.isRoll }.forEach { template ->
            val plainFile = write(plain, template)
            val before = preserve(plainFile, "plain-${template.id}.pdf")
            val after = write(annotated, template)

            assertEquals(
                "${template.id} added a sheet for the payment",
                pageCount(before),
                pageCount(after),
            )
            assertTrue(
                "${template.id} printed nothing for the payment",
                differingPixels(after, before) > 200,
            )
            before.delete()
            after.delete()
        }
    }

    @Test
    fun aSettledInvoicePrintsTheAcquittal() {
        val settled = invoice("F-20261029-9011", 2).copy(
            payment = PaymentInfo(paidAmount = 2280.0, paidAt = "10/11/2026"),
        )

        val file = write(settled, InvoiceTemplate.CLASSIQUE)

        assertEquals(1, pageCount(file))
        assertTrue(inkOn(file, 0) > 100)
        file.delete()
    }

    /** A small render of one page, for comparing two documents pixel by pixel. */
    private fun renderPage(file: File): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val width = 200
                    val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    /** How many pixels two renders disagree on; 0 means the pages are identical. */
    private fun differingPixels(a: File, b: File): Int {
        val first = renderPage(a)
        val second = renderPage(b)
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        var differences = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) differences += 1
            }
        }
        first.recycle()
        second.recycle()
        return differences
    }

    /** A small black signature image, encoded the way the app stores one. */
    private fun signatureBytes(): ImageBytes {
        val bitmap = Bitmap.createBitmap(160, 60, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
            isAntiAlias = true
        }
        canvas.drawLine(12f, 45f, 60f, 10f, paint)
        canvas.drawLine(60f, 10f, 100f, 45f, paint)
        canvas.drawLine(100f, 45f, 148f, 12f, paint)
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return ImageBytes(out.toByteArray())
    }

    @Test
    fun aDepositReceiptIsWrittenAsOnePage() {
        val data = InvoiceData(
            number = "F-20261110-0007",
            date = "10/11/2026",
            client = InvoiceClient(name = "Client", ice = "009876543000012"),
            lines = listOf(InvoiceLine("Chaise", 2, 950.0)),
            payment = PaymentInfo(paidAmount = 1_000.0, paidAt = "12/11/2026"),
        )

        val file = ReceiptPdf.create(context, data, shop, InvoiceDesign())
        assertNotNull(file)

        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(1, renderer.pageCount)
        }
    }

    @Test
    fun aStatementForThreeDocumentsIsWrittenAsOnePage() {
        val records = listOf(
            record("F-20261110-9201", DocumentType.FACTURE),
            record("F-20261110-9202", DocumentType.FACTURE, date = "05/11/2026"),
            record("A-20261110-9203", DocumentType.AVOIR, date = "08/11/2026"),
        )
        val statement = ClientStatements.forClient(records, client.name)
        assertEquals(3, statement.lines.size)

        val file = StatementPdf.create(context, statement, client, shop, InvoiceDesign())
        assertNotNull("no statement PDF was written", file)
        val written = file!!

        PdfRenderer(
            ParcelFileDescriptor.open(written, ParcelFileDescriptor.MODE_READ_ONLY),
        ).use { renderer ->
            assertEquals(1, renderer.pageCount)
            assertTrue(inkOn(written, 0) > 100)
        }
        written.delete()
    }

    /** One issued document of a given type, enough to appear on a statement. */
    private fun record(
        number: String,
        type: DocumentType,
        date: String = "29/10/2026",
    ): InvoiceRecord = InvoiceRecord(
        id = number,
        data = InvoiceData(
            number = number,
            date = date,
            client = client,
            lines = listOf(InvoiceLine("Chaise en rotin", 1, 950.0)),
            documentType = type,
        ),
        status = InvoiceStatus.ISSUED,
        createdAt = 0,
        updatedAt = 0,
    )
}
