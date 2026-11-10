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
        // Written as a number, so a whole quantity stays `2` on disk exactly as
        // older files wrote it, and a decimal one needs no new key.
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
        // Absent on a line typed by hand and on every line written before ids
        // existed, so those files stay byte-for-byte what they were.
        line.itemId?.takeIf { it.isNotBlank() }?.let { put("itemId", it) }
        // Written only when a cost was frozen, so a document issued before
        // costing existed keeps saying what it said and a service never
        // pretends to have a cost of zero.
        line.unitCostHt?.let { put("unitCostHt", it) }
    }

    fun lineFromJson(obj: JSONObject): InvoiceLine = InvoiceLine(
        description = obj.optString("description", ""),
        // An integer written by an older build reads as a Double here, which is
        // why nothing on disk had to be migrated. Only a missing, non-finite or
        // non-positive value falls back to 1 — a 0,5 must stay a 0,5.
        quantity = obj.optDouble("quantity", 1.0)
            .let { if (it.isNaN() || it.isInfinite() || it <= 0.0) 1.0 else it },
        unitPrice = obj.optDouble("unitPrice", 0.0).let { if (it.isNaN()) 0.0 else it },
        unit = obj.optString("unit", ""),
        lineDiscountPercent = obj.optInt("lineDiscountPercent", 0).coerceIn(0, 100),
        lineDiscountFixed = obj.optDouble("lineDiscountFixed", 0.0)
            .let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceAtLeast(0.0) },
        taxExempt = obj.optBoolean("taxExempt", false),
        vatPercent = obj.optDouble("vatPercent", Double.NaN).let { if (it.isNaN()) null else it.coerceAtLeast(0.0) },
        ttcPrice = obj.optDouble("ttcPrice", Double.NaN).let { if (it.isNaN()) null else it },
        itemId = obj.optString("itemId", "").trim().takeIf { it.isNotEmpty() },
        // Absent from every line written before costing existed, and from a
        // line whose cost was unknown at issue.
        unitCostHt = if (obj.has("unitCostHt") && !obj.isNull("unitCostHt")) {
            obj.optDouble("unitCostHt", 0.0)
                .let { if (it.isNaN() || it.isInfinite() || it < 0.0) null else it }
        } else {
            null
        },
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
        if (data.dueDate.isNullOrBlank()) {
            put("dueDate", JSONObject.NULL)
        } else {
            put("dueDate", data.dueDate)
        }
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
        if (data.paymentTerms != PaymentTerms.CUSTOM) {
            put("paymentTerms", data.paymentTerms.id)
        }
        if (data.latePenaltyClause.isNotBlank()) {
            put("latePenaltyClause", data.latePenaltyClause)
        }
        put("thankYouNote", data.thankYouNote)
        // Omitted on a draft, so the file says "no snapshot" by saying nothing.
        data.shop?.let { put("shop", shopSnapshotToJson(it)) }
        data.signature?.let {
            put("signature", Base64.getEncoder().encodeToString(it.bytes))
        }
        put("surchargeLabel", data.surchargeLabel)
        put("surchargeAmount", data.surchargeAmount)
        if (data.surchargePercent > 0.0) put("surchargePercent", data.surchargePercent)
        if (data.depositAmount > 0.0) {
            put("depositAmount", data.depositAmount)
            if (data.depositReference.isNotBlank()) put("depositReference", data.depositReference)
        }
        // Omitted when nothing was received, so an unpaid document says so by
        // saying nothing and old files stay byte-for-byte compatible.
        data.payment?.takeIf { !it.isEmpty || it.paidAt.isNotBlank() }?.let {
            put(
                "payment",
                JSONObject().apply {
                    put("paidAmount", it.paidAmount)
                    put("paidAt", it.paidAt)
                    put("note", it.note)
                    it.method?.let { method -> put("method", method.id) }
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
        if (snapshot.bankName.isNotBlank()) put("bankName", snapshot.bankName)
        if (snapshot.bankRib.isNotBlank()) put("bankRib", snapshot.bankRib)
        if (snapshot.bankHolder.isNotBlank()) put("bankHolder", snapshot.bankHolder)
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
            bankName = obj.optString("bankName", ""),
            bankRib = obj.optString("bankRib", ""),
            bankHolder = obj.optString("bankHolder", ""),
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
            dueDate = if (obj.isNull("dueDate")) {
                null
            } else {
                obj.optString("dueDate", "").ifBlank { null }
            },
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
            // Absent on every document written before the presets existed:
            // the note is still read, and the preset is derived from it.
            paymentTerms = obj.optString("paymentTerms", "")
                .takeIf { it.isNotBlank() }
                ?.let(PaymentTerms::fromId)
                ?: PaymentTerms.fromNote(obj.optString("termsNote", "")),
            latePenaltyClause = obj.optString("latePenaltyClause", ""),
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
            depositAmount = obj.optDouble("depositAmount", 0.0)
                .let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceAtLeast(0.0) },
            depositReference = obj.optString("depositReference", ""),
            payment = obj.optJSONObject("payment")?.let { payment ->
                PaymentInfo(
                    paidAmount = payment.optDouble("paidAmount", 0.0)
                        .let { if (it.isNaN() || it.isInfinite()) 0.0 else it },
                    paidAt = payment.optString("paidAt", ""),
                    note = payment.optString("note", ""),
                    // Absent on every payment written before the field
                    // existed: the document's own method still applies.
                    method = payment.optString("method", "")
                        .takeIf { it.isNotBlank() }
                        ?.let(PaymentMethod::fromId),
                )
            },
        )
    }
}
