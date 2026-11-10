package com.yassine.facturemaroc.data

import java.util.UUID

/**
 * Gives every article a stable id and links the old invoice lines back to it.
 *
 * Until now an article was keyed by its label. That worked while the label did
 * not change, but a shop renames things — "Chaise" becomes "Chaise en rotin
 * 2026" — and every line written before the rename kept only a string that no
 * longer named anything. The shelf stopped moving for that article, silently,
 * and no screen could explain why.
 *
 * This migration is the one moment the two sides can be matched: the label is
 * all an old line has, so it is used once, here, and then written down as an
 * [InvoiceLine.itemId]. It is deliberately conservative:
 *
 * - a line links only when **exactly one** article carries the label, compared
 *   case-insensitively and trimmed;
 * - a label held by two articles therefore links nothing, because choosing one
 *   would credit a shelf the shop did not sell from;
 * - a line with no matching article stays unlinked and keeps printing what it
 *   always printed — an issued document never becomes unreadable because the
 *   catalogue moved on.
 *
 * Running it twice changes nothing: articles already have ids, and linked lines
 * already carry them. That is what makes it safe to call on every launch rather
 * than trusting a marker file that a restore could roll back.
 */
object ItemMigration {

    /** The catalogue and history after the migration, and whether anything moved. */
    data class Plan(
        val items: List<SavedItem>,
        val records: List<InvoiceRecord>,
        val changed: Boolean,
    )

    /**
     * Assigns the missing ids, then links the lines that can be linked.
     *
     * [newId] is injectable so a test can assert on exact values instead of
     * matching random UUIDs; production uses [UUID.randomUUID].
     */
    fun plan(
        items: List<SavedItem>,
        records: List<InvoiceRecord>,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): Plan {
        var changed = false

        // A duplicate id would make two articles answer the same line. That can
        // only come from a hand-edited file or a broken restore, so the second
        // one gets a fresh id instead of being dropped with its stock.
        val seen = HashSet<String>()
        val identified = items.map { item ->
            val current = item.id.trim()
            val id = if (current.isNotEmpty() && seen.add(current)) {
                current
            } else {
                newId().also { seen.add(it) }
            }
            if (id != item.id) {
                changed = true
                item.copy(id = id)
            } else {
                item
            }
        }

        // Labels are normalized once: case and surrounding space were never
        // meaningful, and the store has always treated them as the same name.
        val byLabel = identified
            .groupBy { it.label.trim().lowercase() }
            .mapValues { (_, matches) -> matches.map { it.id } }

        val linked = records.map { record ->
            var recordChanged = false
            val lines = record.data.lines.map { line ->
                val existing = line.itemId
                if (!existing.isNullOrBlank()) {
                    line
                } else {
                    val candidates = byLabel[line.description.trim().lowercase()].orEmpty()
                    if (candidates.size == 1) {
                        recordChanged = true
                        line.copy(itemId = candidates.single())
                    } else {
                        line
                    }
                }
            }
            if (recordChanged) {
                changed = true
                record.copy(data = record.data.copy(lines = lines))
            } else {
                record
            }
        }

        return Plan(identified, linked, changed)
    }
}
