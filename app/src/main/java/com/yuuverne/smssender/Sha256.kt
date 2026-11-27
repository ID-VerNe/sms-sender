package com.yuuverne.smssender

import java.security.MessageDigest

/** Pure SHA-256 helpers shared by deduplication and privacy-sensitive persistence. */
internal object Sha256 {

    /** Hashes one raw UTF-8 value and returns lowercase hexadecimal. */
    fun hash(value: String): String = digest(value.toByteArray(Charsets.UTF_8)).toHex()

    /** Hashes length-prefixed UTF-8 parts so field boundaries cannot collide. */
    fun hash(vararg parts: String): String = digestParts(*parts).toHex()

    /** Uses the first eight digest bytes as a stable Long fingerprint. */
    fun hashToLong(vararg parts: String): Long = digestParts(*parts).toLong()

    private fun digestParts(vararg parts: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toBytes())
            digest.update(bytes)
        }
        return digest.digest()
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun Int.toBytes(): ByteArray = byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        this.toByte()
    )

    private fun ByteArray.toLong(): Long {
        var value = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            value = (value shl 8) or (this[index].toLong() and 0xFF)
        }
        return value
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
