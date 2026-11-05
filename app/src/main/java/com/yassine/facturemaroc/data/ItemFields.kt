package com.yassine.facturemaroc.data

/**
 * The optional fields an article can carry.
 *
 * [SavedItem] always keeps all of them — a barcode hidden today is still on the
 * article tomorrow. What the shop switches off is the *asking*, not the storing:
 * a plumber who sells three services should type a name and a price, not be
 * shown a barcode box he will never fill.
 */
enum class ItemField {
    BARCODE,
    PURCHASE_PRICE,
    UNIT,
    STOCK,
    FOLDER,
}

/**
 * Which of them the shop has asked to see.
 *
 * One function rather than five `if (shop.showItem…)` scattered through the
 * card, the editor and the picker: a switch that hides a field in the editor but
 * not on the card is worse than no switch, because the shop cannot tell which
 * one is lying.
 */
fun visibleItemFields(shop: ShopSettings): Set<ItemField> = buildSet {
    if (shop.showItemBarcode) add(ItemField.BARCODE)
    if (shop.showItemPurchasePrice) add(ItemField.PURCHASE_PRICE)
    if (shop.showItemUnit) add(ItemField.UNIT)
    if (shop.showItemStock) add(ItemField.STOCK)
    if (shop.showItemFolder) add(ItemField.FOLDER)
}

/**
 * The articles the Articles tab shows for a chosen filter.
 *
 * `null` is "Tous", `""` is "Sans dossier", and any other value is a folder
 * path whose sub-folders are included — filtering on "Pneus" while three tyres
 * sit in "Pneus/Hiver" would read as stock that went missing.
 */
fun filterArticles(items: List<SavedItem>, folder: String?): List<SavedItem> = when (folder) {
    null -> items
    else -> SavedItemStore.itemsIn(folder, items, deep = folder.isNotEmpty())
}.sortedBy { it.label.lowercase() }
