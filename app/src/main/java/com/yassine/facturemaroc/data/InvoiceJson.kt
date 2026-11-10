package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * An invoice written to JSON, and read back without losing a field.
 *
 * Kept separate from the store that owns the file so the same conversion can be
 * used by a backup, by an import, and by a test that does not need Android on
 * the classpath. Every field that prints on the document is written down: a
 * restored invoice has to say the same thing as the one that was issued, not a
 * re-calculation of it.
 */
object InvoiceJson {

    fun clientToJson(client: InvoiceClient): JSONObject = JSONObject().apply {
        put("name", client.name)
        put("address", client.address)
        put("ice", client.ice)
        put("ifNumber", client.ifNumber)
        put("rc", client.rc)
        put("patente", client.patente)
        put("cnss", client.cnss)
        put("phone", client.phone)
        put("email", client.email)
        put("vatExempt", client.vatExempt)
        put("role", client.role.id)
    }

    fun clientFromJson(obj: JSONObject?): InvoiceClient =
        if (obj == null) InvoiceClient(name = "") else InvoiceClient(
            name = obj.optString("name", ""),
            address = obj.optString("address", ""),
            ice = obj.optString("ice", ""),
            ifNumber = obj.optString("ifNumber", ""),
            rc = obj.optString("rc", ""),
            patente = obj.optString("patente", ""),
            cnss = obj.optString("cnss", ""),
            phone = obj.optString("phone", ""),
            email = obj.optString("email", ""),
            vatExempt = obj.optBoolean("vatExempt", false),
            // A record written before suppliers existed is a client: it was
            // offered in the client picker and nothing else.
            role = PartyRole.fromId(obj.optString("role", PartyRole.CLIENT.id)),
        )

    fun lineToJson(line: InvoiceLine): JSONObject = JSONObject().apply {
        put("description", line.description)
        put("quantity", line.quantity)
        put("unitPrice", line.unitPrice)
        put("unit", line.unit)
        put("lineDiscountPercent", line.lineDiscountPercent)
        // Written only when used, so an older file stays as it was and a line
        // without a fixed discount says so by saying nothing.
        if (line.lineDiscountFixed > 0.0) put("lineDiscountFixed", line.lineDiscountFixed)
        put("taxExempt", line.taxExempt)
        // Written only when the line carries its own rate: a line that follows
        // the shop setting must keep following it, and an old file stays
        // byte-for-byte what it was.
        line.vatPercent?.let { put("vatPercent", it) }
        // Written only when the shop quoted TTC, so an HT line stays exactly
        // what it is: a number that does not depend on who is buying.
        line.ttcPrice?.let { put("ttcPrice", it) }
    }

    fun lineFromJson(obj: JSONObject): InvoiceLine = InvoiceLine(
        description = obj.optString("description", ""),
        quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
        unitPrice = obj.optDouble("unitPrice", 0.0).let { if (it.isNaN()) 0.0 else it },
        unit = obj.optString("unit", ""),
        lineDiscountPercent = obj.optInt("lineDiscountPercent", 0).coerceIn(0, 100),
        lineDiscountFixed = obj.optDouble("lineDiscountFixed", 0.0)
            .let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceAtLeast(0.0) },
        taxExempt = obj.optBoolean("taxExempt", false),
        vatPercent = obj.optDouble("vatPercent", Double.NaN).let { if (it.isNaN()) null else it.coerceAtLeast(0.0) },
        ttcPrice = obj.optDouble("ttcPrice", Double.NaN).let { if (it.isNaN()) null else it },
    )

    fun dataToJson(data: InvoiceData): JSONObject = JSONObject().apply {
        put("number", data.number)
        put("date", data.date)
        put("client", clientToJson(data.client))
        put(
            "lines",
            JSONArray().apply { data.lines.forEach { put(lineToJson(it)) } },
        )
        put("discountPercent", data.discountPercent)
        if (data.discountFixed > 0.0) put("discountFixed", data.discountFixed)
        put("vatPercent", data.vatPercent)
        put("paymentMethod", data.paymentMethod.id)
        put("documentType", data.documentType.id)
        if (data.validityDate.isNullOrBlank()) {
            put("validityDate", JSONObject.NULL)
        } else {
            put("validityDate", data.validityDate)
        }
        if (data.supplier == null) {
            put("supplier", JSONObject.NULL)
        } else {
            put("supplier", clientToJson(data.supplier))
        }
        put("referenceNumber", data.referenceNumber)
        put("incoterm", data.incoterm)
        put("vatNotApplicable", data.vatNotApplicable)
        put("customerNote", data.customerNote)
        put("termsNote", data.termsNote)
        put("thankYouNote", data.thankYouNote)
        // Omitted on a draft, so the file says "no snapshot" by saying nothing.
        data.shop?.let { put("shop", shopSnapshotToJson(it)) }
        data.signature?.let {
            put("signature", Base64.getEncoder().encodeToString(it.bytes))
        }
        put("surchargeLabel", data.surchargeLabel)
        put("surchargeAmount", data.surchargeAmount)
        if (data.surchargePercent > 0.0) put("surchargePercent", data.surchargePercent)
        // Omitted when nothing was received, so an unpaid document says so by
        // saying nothing and old files stay byte-for-byte compatible.
        data.payment?.takeIf { !it.isEmpty || it.paidAt.isNotBlank() }?.let {
            put(
                "payment",
                JSONObject().apply {
                    put("paidAmount", it.paidAmount)
                    put("paidAt", it.paidAt)
                    put("note", it.note)
                },
            )
        }
    }

    /** The issue-time shop copy, as stored with a document. */
    fun shopSnapshotToJson(snapshot: ShopSnapshot): JSONObject = JSONObject().apply {
        put("name", snapshot.name)
        put("address", snapshot.address)
        put("phone", snapshot.phone)
        put("email", snapshot.email)
        put("ice", snapshot.ice)
        put("ifNumber", snapshot.ifNumber)
        put("rc", snapshot.rc)
        put("patente", snapshot.patente)
        put("cnss", snapshot.cnss)
        put("vatPercent", snapshot.vatPercent)
        put("vatNotApplicable", snapshot.vatNotApplicable)
        put("pricesIncludeVat", snapshot.pricesIncludeVat)
        snapshot.logoHash?.takeIf { it.isNotBlank() }?.let { put("logoHash", it) }
        // Only records written by 0.9.0 still carry the bytes; new snapshots
        // point at LogoStore. Written back when present so nothing is lost if
        // the file is read by a build that predates the migration.
        snapshot.logo?.let { put("logo", Base64.getEncoder().encodeToString(it.bytes)) }
    }

    /**
     * Reads the shop copy, or null when the record predates it.
     *
     * Null is the honest answer for an old document: inventing a snapshot from
     * today's settings would claim the invoice always carried them.
     */
    fun shopSnapshotFromJson(obj: JSONObject?): ShopSnapshot? {
        if (obj == null) return null
        val logo = runCatching {
            obj.optString("logo", "").takeIf { it.isNotBlank() }
                ?.let { Base64.getDecoder().decode(it) }
        }.getOrNull()
        return ShopSnapshot(
            name = obj.optString("name", ""),
            address = obj.optString("address", ""),
            phone = obj.optString("phone", ""),
            email = obj.optString("email", ""),
            ice = obj.optString("ice", ""),
            ifNumber = obj.optString("ifNumber", ""),
            rc = obj.optString("rc", ""),
            patente = obj.optString("patente", ""),
            cnss = obj.optString("cnss", ""),
            vatPercent = obj.optDouble("vatPercent", 20.0).let { if (it.isNaN()) 20.0 else it },
            vatNotApplicable = obj.optBoolean("vatNotApplicable", false),
            pricesIncludeVat = obj.optBoolean("pricesIncludeVat", false),
            logoHash = obj.optString("logoHash", "").takeIf { it.isNotBlank() },
            logo = logo?.takeIf { it.isNotEmpty() }?.let(::ImageBytes),
        )
    }

    /**
     * One history record, with the document it kept and the moment it was made.
     *
     * Lives here rather than in [InvoiceStore] so a backup writes the same
     * bytes the store does, and a restore cannot drift into a second, slightly
     * different format.
     */
    fun recordToJson(record: InvoiceRecord): JSONObject = JSONObject().apply {
        put("id", record.id)
        put("status", record.status.id)
        put("createdAt", record.createdAt)
        put("updatedAt", record.updatedAt)
        // Written only when it is true, so a file saved before the inventory
        // existed reads back as a document that moved no stock — which is the
        // truth about it, and the reason deleting it must not credit the shelf.
        if (record.stockApplied) put("stockApplied", true)
        put("data", dataToJson(record.data))
    }

    fun recordFromJson(obj: JSONObject): InvoiceRecord? {
        val data = obj.optJSONObject("data") ?: return null
        return InvoiceRecord(
            id = obj.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
            data = dataFromJson(data),
            status = InvoiceStatus.fromId(obj.optString("status", null)),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            stockApplied = obj.optBoolean("stockApplied", false),
        )
    }

    fun dataFromJson(obj: JSONObject): InvoiceData {
        val linesArray = obj.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until linesArray.length()).mapNotNull { index ->
            linesArray.optJSONObject(index)?.let(::lineFromJson)
        }
        return InvoiceData(
            number = obj.optString("number", ""),
            date = obj.optString("date", ""),
            client = clientFromJson(obj.optJSONObject("client")),
            lines = lines,
            discountPercent = obj.optInt("discountPercent", 0).coerceIn(0, 100),
            discountFixed = obj.optDouble("discountFixed", 0.0)
                .let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceAtLeast(0.0) },
            vatPercent = obj.optDouble("vatPercent", 20.0).let { if (it.isNaN()) 20.0 else it },
            paymentMethod = PaymentMethod.fromId(obj.optString("paymentMethod", null)),
            documentType = DocumentType.fromId(obj.optString("documentType", null)),
            validityDate = if (obj.isNull("validityDate")) {
                null
            } else {
                obj.optString("validityDate", "").ifBlank { null }
            },
            supplier = if (obj.isNull("supplier")) null else obj.optJSONObject("supplier")?.let(::clientFromJson),
            referenceNumber = obj.optString("referenceNumber", ""),
            incoterm = obj.optString("incoterm", ""),
            vatNotApplicable = obj.optBoolean("vatNotApplicable", false),
            shop = shopSnapshotFromJson(obj.optJSONObject("shop")),
            customerNote = obj.optString("customerNote", ""),
            termsNote = obj.optString("termsNote", ""),
            thankYouNote = obj.optString("thankYouNote", ""),
            signature = runCatching {
                obj.optString("signature", "").takeIf { it.isNotBlank() }
                    ?.let { Base64.getDecoder().decode(it) }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let(::ImageBytes),
            surchargeLabel = obj.optString("surchargeLabel", "Livraison"),
            surchargeAmount = obj.optDouble("surchargeAmount", 0.0)
                .let { if (it.isNaN() || it.isInfinite()) 0.0 else it },
            surchargePercent = obj.optDouble("surchargePercent", 0.0)
                .let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceIn(0.0, 100.0) },
            payment = obj.optJSONObject("payment")?.let { payment ->
                PaymentInfo(
                    paidAmount = payment.optDouble("paidAmount", 0.0)
                        .let { if (it.isNaN() || it.isInfinite()) 0.0 else it },
                    paidAt = payment.optString("paidAt", ""),
                    note = payment.optString("note", ""),
                )
            },
        )
    }
}
