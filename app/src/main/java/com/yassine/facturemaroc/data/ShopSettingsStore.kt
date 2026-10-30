package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted shop settings — company info, legal IDs, logo bytes, TVA default.
 *
 * Stored in SharedPreferences (logo bytes as Base64). Written temp-then-rename
 * is not possible with SharedPreferences, but writes are atomic at the
 * framework level and the file is small.
 *
 * Reading and writing are separate calls on purpose: [InvoiceViewModel] holds
 * the settings in its own state, and a second copy living here would go stale
 * the moment a screen saved through the companion object.
 */
class ShopSettingsStore(private val context: Context) {

    fun read(): ShopSettings = load(context)

    private fun load(context: Context): ShopSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ShopSettings(
            name = prefs.getString("name", "") ?: "",
            address = prefs.getString("address", "") ?: "",
            phone = prefs.getString("phone", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            ice = prefs.getString("ice", "") ?: "",
            ifNumber = prefs.getString("ifNumber", "") ?: "",
            rc = prefs.getString("rc", "") ?: "",
            patente = prefs.getString("patente", "") ?: "",
            cnss = prefs.getString("cnss", "") ?: "",
            logoBytes = prefs.getString("logoBytes", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                ?.takeIf { it.isNotEmpty() },
            vatPercent = prefs.getFloat("vatPercent", 20.0f).toDouble(),
            vatNotApplicable = prefs.getBoolean("vatNotApplicable", false),
            pricesIncludeVat = prefs.getBoolean("pricesIncludeVat", false),
        )
    }

    companion object {
        private const val PREFS = "shop_settings"

        fun write(context: Context, settings: ShopSettings) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("name", settings.name)
                putString("address", settings.address)
                putString("phone", settings.phone)
                putString("email", settings.email)
                putString("ice", settings.ice)
                putString("ifNumber", settings.ifNumber)
                putString("rc", settings.rc)
                putString("patente", settings.patente)
                putString("cnss", settings.cnss)
                putString("logoBytes", settings.logoBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) })
                putFloat("vatPercent", settings.vatPercent.toFloat())
                putBoolean("vatNotApplicable", settings.vatNotApplicable)
                putBoolean("pricesIncludeVat", settings.pricesIncludeVat)
                apply()
            }
        }
    }
}

/** Client store — saved clients for quick selection on invoices. */
class ClientStore(private val context: Context) {

    private val _clients = MutableStateFlow(load(context))
    val clients: StateFlow<List<InvoiceClient>> = _clients.asStateFlow()

    fun save(client: InvoiceClient) {
        if (client.name.isBlank()) return
        // Keyed by name *and* role: a garage can be a supplier and a customer,
        // and the two records say different things about the relationship.
        val key = key(client.name, client.role)
        val existing = _clients.value.toMutableList()
        val index = existing.indexOfFirst { key(it.name, it.role) == key }
        if (index >= 0) {
            existing[index] = client.copy(name = client.name.trim())
        } else {
            existing.add(client.copy(name = client.name.trim()))
        }
        persist(existing)
    }

    fun delete(client: InvoiceClient) {
        val key = key(client.name, client.role)
        persist(_clients.value.filterNot { key(it.name, it.role) == key })
    }

    /** The saved parties of one kind, in the order they were saved. */
    fun of(role: PartyRole): List<InvoiceClient> = _clients.value.filter { it.role == role }

    fun read(context: Context): List<InvoiceClient> = load(context)

    /** Replaces the whole list, for a backup restore. */
    fun replaceAll(clients: List<InvoiceClient>) {
        persist(
            clients
                .filter { it.name.isNotBlank() }
                .distinctBy { key(it.name, it.role) },
        )
    }

    private fun key(name: String, role: PartyRole): String =
        "${role.id}:${name.trim().lowercase()}"

    private fun persist(list: List<InvoiceClient>) {
        _clients.value = list
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        list.forEach { c ->
            array.put(org.json.JSONObject().apply {
                put("name", c.name)
                put("address", c.address)
                put("ice", c.ice)
                put("ifNumber", c.ifNumber)
                put("rc", c.rc)
                put("patente", c.patente)
                put("cnss", c.cnss)
                put("phone", c.phone)
                put("email", c.email)
                put("vatExempt", c.vatExempt)
                put("role", c.role.id)
            })
        }
        prefs.edit().putString("clients", array.toString()).apply()
    }

    private fun load(context: Context): List<InvoiceClient> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString("clients", "") ?: ""
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { obj ->
                    InvoiceClient(
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
                        role = PartyRole.fromId(obj.optString("role", PartyRole.CLIENT.id)),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS = "client_store"
    }
}

/**
 * Whether [name] already belongs to a party other than [excluding].
 *
 * A client is stored under name and role, which is the pair that decides what a
 * save replaces. Renaming onto a name the directory already holds would
 * overwrite that party — its ICE, its address — instead of adding a second one,
 * so the screen asks this before it saves.
 *
 * The role is part of the answer on purpose: a garage can be a client and a
 * supplier at once, and the two records say different things.
 */
fun List<InvoiceClient>.nameTaken(
    name: String,
    role: PartyRole,
    excluding: InvoiceClient? = null,
): Boolean {
    val target = name.trim()
    val kept = excluding?.takeIf { it.role == role }?.name?.trim()
    return any {
        it.role == role &&
            it.name.trim().equals(target, ignoreCase = true) &&
            !(kept != null && it.name.trim().equals(kept, ignoreCase = true))
    }
}
