package com.baytna.app.data.sync

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Invite codes: 20 Crockford base32 characters (100 random bits), shown as XXXX-XXXX-XXXX-XXXX-XXXX.
 * Only a salted SHA-256 hash is stored in the cloud, so the code cannot be read back from the database.
 */
object InviteCodes {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 20
    const val VALID_DAYS = 7L

    fun generate(random: SecureRandom = SecureRandom()): String =
        format(String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))

    /** Canonical 20-character form, or null if [input] is not a valid code. Accepts dashes, spaces, lower case and Arabic digits. */
    fun normalize(input: String): String? {
        val sb = StringBuilder()
        for (ch in input) {
            val c = when {
                ch == '-' || ch.isWhitespace() -> continue
                ch.isDigit() -> Character.forDigit(Character.digit(ch, 10), 10)
                else -> ch.uppercaseChar()
            }
            val mapped = when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c }
            if (mapped !in ALPHABET) return null
            sb.append(mapped)
        }
        return sb.toString().takeIf { it.length == LENGTH }
    }

    fun format(normalized: String): String = normalized.chunked(4).joinToString("-")

    fun hash(normalized: String): String =
        MessageDigest.getInstance("SHA-256").digest("baytna-invite-v1:$normalized".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
