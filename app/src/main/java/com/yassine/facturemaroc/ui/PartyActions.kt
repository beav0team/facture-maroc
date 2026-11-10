package com.yassine.facturemaroc.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yassine.facturemaroc.data.PartyContact

/**
 * Tapping a phone number.
 *
 * `ACTION_DIAL` opens the dialler pre-filled and needs no permission;
 * `ACTION_CALL` would need `CALL_PHONE` and would put a permission prompt in
 * front of a shopkeeper who only wanted the number on screen. Both functions
 * return false rather than throwing: a tablet without a dialler is a real device
 * and must not crash the directory.
 */
object PartyActions {

    private const val WHATSAPP = "com.whatsapp"

    fun dial(context: Context, rawNumber: String): Boolean {
        val uri = PartyContact.dialUri(rawNumber) ?: return false
        return launch(context, Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
    }

    /**
     * Opens the chat with [rawNumber], in WhatsApp when it is installed and in
     * whatever handles the link otherwise.
     *
     * WhatsApp first, as a package-targeted intent, because `wa.me` also opens
     * in a browser when WhatsApp is absent, and landing on a web page that asks
     * to install an app is a worse answer than saying so in a snackbar.
     */
    fun whatsApp(context: Context, rawNumber: String): Boolean {
        val uri = PartyContact.whatsAppUri(rawNumber) ?: return false
        val targeted = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(WHATSAPP)
        if (launch(context, targeted)) return true
        // Not installed: try whatever else can take the link (WhatsApp Business,
        // a browser). Failing that, the caller says so.
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            // A profile can forbid starting this intent; same answer as absent.
            false
        }
    }
}
