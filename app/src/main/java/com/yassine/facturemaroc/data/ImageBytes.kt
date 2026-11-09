package com.yassine.facturemaroc.data

/**
 * Image bytes that compare by content, so a data class holding one can.
 *
 * A plain `ByteArray` compares by reference, so [InvoiceData] (which is a data
 * class and is compared in tests and by `copy`) would report two identical
 * documents as different the moment either carried a signature or a logo
 * snapshot. Copying the bytes in and overriding [equals] keeps the value
 * semantics the rest of the model relies on.
 */
class ImageBytes(bytes: ByteArray) {

    val bytes: ByteArray = bytes.copyOf()

    val size: Int get() = bytes.size

    override fun equals(other: Any?): Boolean =
        other is ImageBytes && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ImageBytes(${bytes.size} bytes)"
}
