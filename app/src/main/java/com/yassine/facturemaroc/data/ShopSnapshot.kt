package com.yassine.facturemaroc.data

/**
 * The shop's details as they looked when a document was issued.
 *
 * An invoice is evidence of what was true on its date, not a live view of the
 * shop file. Without this copy, changing the ICE, the address or the logo
 * after the fact silently rewrote every old document the shop reprinted — a
 * legal document that changes after it was handed over is not a copy of
 * anything.
 *
 * Only the fields that print are kept. The article-form switches in
 * [ShopSettings] decide how the *next* item is entered and have no place on a
 * document that was already written.
 */
data class ShopSnapshot(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val ice: String = "",
    val ifNumber: String = "",
    val rc: String = "",
    val patente: String = "",
    val cnss: String = "",
    val vatPercent: Double = 20.0,
    val vatNotApplicable: Boolean = false,
    val pricesIncludeVat: Boolean = false,
    /** The logo as it was at issue time; the live file may have changed since. */
    val logo: ImageBytes? = null,
) {
    /** The same values in the shape the renderers already read. */
    fun toSettings(): ShopSettings = ShopSettings(
        name = name,
        address = address,
        phone = phone,
        email = email,
        ice = ice,
        ifNumber = ifNumber,
        rc = rc,
        patente = patente,
        cnss = cnss,
        logoBytes = logo?.bytes,
        vatPercent = vatPercent,
        vatNotApplicable = vatNotApplicable,
        pricesIncludeVat = pricesIncludeVat,
    )

    companion object {
        /** Snapshots the printed fields of [settings], with [logo] already read. */
        fun of(settings: ShopSettings, logo: ByteArray? = settings.logoBytes): ShopSnapshot =
            ShopSnapshot(
                name = settings.name,
                address = settings.address,
                phone = settings.phone,
                email = settings.email,
                ice = settings.ice,
                ifNumber = settings.ifNumber,
                rc = settings.rc,
                patente = settings.patente,
                cnss = settings.cnss,
                vatPercent = settings.vatPercent,
                vatNotApplicable = settings.vatNotApplicable,
                pricesIncludeVat = settings.pricesIncludeVat,
                logo = logo?.takeIf { it.isNotEmpty() }?.let(::ImageBytes),
            )
    }
}
