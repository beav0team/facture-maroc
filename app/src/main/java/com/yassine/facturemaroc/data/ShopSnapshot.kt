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
    /**
     * The logo as it was at issue time, referred to by content hash.
     *
     * The bytes live once in [LogoStore]; see that file for why they are not
     * copied into every record.
     */
    val logoHash: String? = null,
    /**
     * Logo bytes written by 0.9.0, kept only so those records still print.
     *
     * New snapshots never fill this: [InvoiceStore] moves legacy bytes into
     * [LogoStore] at startup and rewrites the record with [logoHash].
     */
    val logo: ImageBytes? = null,
) {
    /** The same values in the shape the renderers already read. */
    fun toSettings(logoBytes: ByteArray? = logo?.bytes): ShopSettings = ShopSettings(
        name = name,
        address = address,
        phone = phone,
        email = email,
        ice = ice,
        ifNumber = ifNumber,
        rc = rc,
        patente = patente,
        cnss = cnss,
        logoBytes = logoBytes,
        vatPercent = vatPercent,
        vatNotApplicable = vatNotApplicable,
        pricesIncludeVat = pricesIncludeVat,
    )

    companion object {
        /** Snapshots the printed fields of [settings]; the logo is stored by [LogoStore]. */
        fun of(settings: ShopSettings, logoHash: String? = null): ShopSnapshot =
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
                logoHash = logoHash?.takeIf { it.isNotBlank() },
            )
    }
}
