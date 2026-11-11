package com.yassine.facturemaroc.data

import java.util.UUID

/**
 * Who a document belongs to, once a party can be renamed.
 *
 * Until 0.12 a client was identified by the string typed in the name field, so
 * renaming « Karim » into « Karim El Fassi » detached every invoice already
 * issued from him. A party now carries an [InvoiceClient.id], and this object
 * holds the three rules that make the change safe:
 *
 *  - a party that has no id gets one, once, and keeps it;
 *  - a document written before ids existed is stamped with the id of the party
 *    it names, so old history reattaches instead of splitting in two;
 *  - a document whose party genuinely has no id — a walk-in client typed on the
 *    invoice — keeps matching by name, which is how every document was matched
 *    before, and must keep working forever.
 */
object PartyIdentity {

    /** Every party with an id; the ones that already have one are left alone. */
    fun withIds(clients: List<InvoiceClient>): List<InvoiceClient> {
        val seen = mutableSetOf<String>()
        return clients.map { client ->
            // A restored file can carry two rows with the same id if it was
            // hand-edited; the second one loses its id rather than sharing a
            // history with the first.
            val kept = client.id.trim().takeIf { it.isNotEmpty() && seen.add(it) }
            client.copy(id = kept ?: UUID.randomUUID().toString().also { seen.add(it) })
        }
    }

    /**
     * Stamps the party snapshot of every issued document with the id of the
     * party it names.
     *
     * Only documents whose snapshot has no id are touched, and only when
     * exactly one party carries that name in that role — an ambiguous name is
     * left alone rather than attached to the wrong account.
     */
    fun stamp(records: List<InvoiceRecord>, clients: List<InvoiceClient>): List<InvoiceRecord> {
        if (clients.isEmpty()) return records
        var changed = false
        val stamped = records.map { record ->
            val party = record.data.supplier ?: record.data.client
            val fixed = stampParty(party, clients) ?: return@map record
            changed = true
            if (record.data.supplier != null) {
                record.copy(data = record.data.copy(supplier = fixed))
            } else {
                record.copy(data = record.data.copy(client = fixed))
            }
        }
        return if (changed) stamped else records
    }

    /** The same rule for goods receipts, which keep the supplier's name only. */
    fun stampPurchases(purchases: List<Purchase>, clients: List<InvoiceClient>): List<Purchase> {
        var changed = false
        val stamped = purchases.map { purchase ->
            if (purchase.supplierId.isNotBlank()) return@map purchase
            val match = clients.singleOrNull {
                it.role == PartyRole.SUPPLIER &&
                    it.name.trim().equals(purchase.supplierName.trim(), ignoreCase = true)
            } ?: return@map purchase
            changed = true
            purchase.copy(supplierId = match.id)
        }
        return if (changed) stamped else purchases
    }

    /**
     * Whether [recordParty] is [client].
     *
     * The id decides whenever both sides have one: that is what makes a rename
     * keep its invoices. Otherwise the name decides, as it always did.
     */
    fun matches(recordParty: InvoiceClient, client: InvoiceClient): Boolean {
        val recordId = recordParty.id.trim()
        val clientId = client.id.trim()
        if (recordId.isNotEmpty() && clientId.isNotEmpty()) return recordId == clientId
        return recordParty.name.trim().equals(client.name.trim(), ignoreCase = true) &&
            recordParty.role == client.role
    }

    private fun stampParty(party: InvoiceClient, clients: List<InvoiceClient>): InvoiceClient? {
        if (party.id.isNotBlank()) return null
        val match = clients.singleOrNull {
            it.role == party.role &&
                it.name.trim().equals(party.name.trim(), ignoreCase = true)
        } ?: return null
        return party.copy(id = match.id)
    }
}
