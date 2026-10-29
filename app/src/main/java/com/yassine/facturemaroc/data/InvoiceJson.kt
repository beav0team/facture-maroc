package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject

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
        )

    fun lineToJson(line: InvoiceLine): JSONObject = JSONObject().apply {
        put("description", line.description)
        put("quantity", line.quantity)
        put("unitPrice", line.unitPrice)
        put("unit", line.unit)
        put("lineDiscountPercent", line.lineDiscountPercent)
        put("taxExempt", line.taxExempt)
    }

    fun lineFromJson(obj: JSONObject): InvoiceLine = InvoiceLine(
        description = obj.optString("description", ""),
        quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
        unitPrice = obj.optDouble("unitPrice", 0.0).let { if (it.isNaN()) 0.0 else it },
        unit = obj.optString("unit", ""),
        lineDiscountPercent = obj.optInt("lineDiscountPercent", 0).coerceIn(0, 100),
        taxExempt = obj.optBoolean("taxExempt", false),
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
        )
    }
}
