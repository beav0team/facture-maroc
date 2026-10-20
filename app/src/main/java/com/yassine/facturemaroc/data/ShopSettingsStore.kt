package com.yassine.facturemaroc.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted shop settings — company info, legal IDs, logo bytes, TVA default.
 *
 * Stored in SharedPreferences (logo bytes as Base64). Written temp-then-rename
 * is not possible with SharedPreferences, but writes are atomic at the
 * framework level and the file is small.
 */
class ShopSettingsStore(context: Context) {

    private const val PREFS = "shop_settings"

    private val _settings = MutableStateFlow(load(context))
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    fun read(context: Context): ShopSettings = load(context)

    fun write(context: Context, settings: ShopSettings) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("name", settings.name)
            putString("address", settings.address)
            putString("ice", settings.ice)
            putString("ifNumber", settings.ifNumber)
            putString("rc", settings.rc)
            putString("patente", settings.patente)
            putString("cnss", settings.cnss)
            putString("logoBytes", settings.logoBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) })
            putFloat("vatPercent", settings.vatPercent.toFloat())
            apply()
        }
        _settings.value = settings
    }

    private fun load(context: Context): ShopSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ShopSettings(
            name = prefs.getString("name", "") ?: "",
            address = prefs.getString("address", "") ?: "",
            ice = prefs.getString("ice", "") ?: "",
            ifNumber = prefs.getString("ifNumber", "") ?: "",
            rc = prefs.getString("rc", "") ?: "",
            patente = prefs.getString("patente", "") ?: "",
            cnss = prefs.getString("cnss", "") ?: "",
            logoBytes = prefs.getString("logoBytes", "")?.let {
                android.util.Base64.decode(it, android.util.Base64.DEFAULT)
            },
            vatPercent = prefs.getFloat("vatPercent", 20.0f).toDouble(),
        )
    }

    companion object {
        private const val PREFS = "shop_settings"
    }
}

/** Client store — saved clients for quick selection on invoices. */
class ClientStore(context: Context) {

    private const val PREFS = "client_store"

    private val _clients = MutableStateFlow(load(context))
    val clients: StateFlow<List<InvoiceClient>> = _clients.asStateFlow()

    fun save(client: InvoiceClient) {
        if (client.name.isBlank()) return
        val key = client.name.trim().lowercase()
        val existing = _clients.value.toMutableList()
        val index = existing.indexOfFirst { it.name.trim().lowercase() == key }
        if (index >= 0) {
            existing[index] = client.copy(name = client.name.trim())
        } else {
            existing.add(client.copy(name = client.name.trim()))
        }
        persist(existing, context)
    }

    fun delete(client: InvoiceClient) {
        val key = client.name.trim().lowercase()
        persist(_clients.value.filterNot { it.name.trim().lowercase() == key }, context)
    }

    fun read(context: Context): List<InvoiceClient> = load(context)

    private fun persist(list: List<InvoiceClient>, context: Context) {
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
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS = "client_store"
    }
}
