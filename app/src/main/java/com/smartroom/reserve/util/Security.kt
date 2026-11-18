package com.smartroom.reserve.util

import java.security.MessageDigest

object Security {
    /** SHA-256 of the password, salted with the (lower-cased) email. Plain passwords are never stored. */
    fun hash(email: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("${email.lowercase()}:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
