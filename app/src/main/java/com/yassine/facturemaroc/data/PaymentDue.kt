package com.yassine.facturemaroc.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * When a payment is late.
 *
 * A document is late the day *after* its deadline, not on it: a shop that told
 * its customer "30 jours fin de mois" cannot call them late on the 31st and
 * charge a penalty for a day they still had. The comparison is done on whole
 * days, so the hour the app happens to be opened never changes the answer.
 */
object PaymentDue {

    /** `dd/MM/yyyy`, the only date format this app writes. */
    fun parse(text: String?): Date? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        // Strict: `31/02/2026` is a typo, not 3 March. A lenient parser rolls
        // it over silently, and the shop would chase a customer on a date
        // nobody wrote.
        return runCatching {
            formatter().apply { isLenient = false }.parse(value)
        }.getOrNull()
    }

    /**
     * Whether [dueDate] has passed for a document that is not settled.
     *
     * [today] is a parameter rather than `new Date()` so a test can name the
     * day it is asking about, and so an export of past documents can be
     * checked against a fixed reference.
     */
    fun isOverdue(
        dueDate: String?,
        state: PaymentState,
        today: Date = Date(),
    ): Boolean {
        if (state == PaymentState.PAID) return false
        val due = parse(dueDate) ?: return false
        return startOfDay(today).after(startOfDay(due))
    }

    /** Whole days between [today] and [dueDate]; negative once it has passed. */
    fun daysUntil(dueDate: String?, today: Date = Date()): Int? {
        val due = parse(dueDate) ?: return null
        val millis = startOfDay(due).time - startOfDay(today).time
        return Math.round(millis / 86_400_000.0).toInt()
    }

    private fun startOfDay(date: Date): Date =
        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    private fun formatter() = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
}
