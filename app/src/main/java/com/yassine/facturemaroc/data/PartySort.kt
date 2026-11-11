package com.yassine.facturemaroc.data

/**
 * How a shop asks the directory two different questions.
 *
 * « Who is in my book? » is a name order — that is what the tab shows today and
 * what a shop expects from a directory. « Who owes me money? » is not a name
 * order at all, and answering it by scrolling 200 clients is where an offline
 * app starts losing to a notebook.
 */
enum class PartySort(val label: String) {
    NAME("Nom"),

    /** Largest debt first; a supplier has none, so he falls back to recency. */
    BALANCE("Solde"),

    /** Whoever was invoiced last, first. */
    RECENT("Récent"),
}

/** The triage chips. Only clients can owe or be late. */
enum class PartyFilter(val label: String) {
    ALL("Tous"),
    OWES("Doit de l'argent"),
    LATE("En retard"),
}

fun sortParties(
    parties: List<InvoiceClient>,
    summaries: Map<String, PartySummary>,
    sort: PartySort,
): List<InvoiceClient> {
    fun summary(party: InvoiceClient): PartySummary =
        summaries[partyKey(party)] ?: PartySummary()

    return when (sort) {
        PartySort.NAME -> parties.sortedWith(byName)

        // A negative balance is money the shop owes (more avoirs than
        // invoices): it is not « owes nothing », it is a debt in the other
        // direction, so it sorts right after the largest debts rather than
        // with them.
        PartySort.BALANCE -> parties.sortedWith(
            compareByDescending<InvoiceClient> { summary(it).due }.then(byName),
        )

        PartySort.RECENT -> parties.sortedWith(
            compareByDescending<InvoiceClient> { activityKey(summary(it).lastActivity) }
                .then(byName),
        )
    }
}

fun filterParties(
    parties: List<InvoiceClient>,
    summaries: Map<String, PartySummary>,
    filter: PartyFilter,
    role: PartyRole,
): List<InvoiceClient> {
    // A supplier is never « en retard » in this app: there is no supplier
    // ledger to be late on. The chips are not drawn for him either, so a filter
    // left over from the client side must not empty his list.
    if (role == PartyRole.SUPPLIER || filter == PartyFilter.ALL) return parties

    return parties.filter { party ->
        val summary = summaries[partyKey(party)]
        when (filter) {
            PartyFilter.ALL -> true
            PartyFilter.OWES -> (summary?.due ?: 0.0) > 0.004
            PartyFilter.LATE -> (summary?.overdue ?: 0.0) > 0.004
        }
    }
}

private val byName: Comparator<InvoiceClient> =
    compareBy(String.CASE_INSENSITIVE_ORDER) { party: InvoiceClient -> party.name }

/**
 * `dd/MM/yyyy` → `yyyyMMdd`, so dates sort as text.
 *
 * Kept local rather than parsing a `Date`: the app stores dates as the strings
 * it prints, and a missing or half-typed date must sort last instead of
 * crashing a list.
 */
private fun activityKey(text: String): String {
    val parts = text.trim().split('/')
    if (parts.size != 3) return ""
    val (day, month, year) = parts
    if (year.length != 4) return ""
    return year + month.padStart(2, '0') + day.padStart(2, '0')
}
