package com.azurlane.infra.auth

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class PasswordVerifyResult(
    val valid: Boolean,
    val isLegacyPlaintext: Boolean
)

object PasswordHasher {

    private const val SALT_LENGTH = 16
    private const val HASH_LENGTH = 32
    private const val MEMORY = 65536
    private const val ITERATIONS = 3
    private const val PARALLELISM = 1

    private val argon2 = Argon2Factory.create(Argon2Types.ARGON2id)

    fun hashPassword(password: String): String {
        return argon2.hash(
            ITERATIONS,
            MEMORY,
            PARALLELISM,
            password.toCharArray()
        )
    }

    fun verify(password: String, encoded: String): PasswordVerifyResult {
        return if (encoded.contains("$")) {
            PasswordVerifyResult(valid = argon2.verify(encoded, password.toCharArray()), isLegacyPlaintext = false)
        } else {
            PasswordVerifyResult(valid = password == encoded, isLegacyPlaintext = true)
        }
    }

    fun verifyPassword(password: String, encoded: String): Boolean {
        return verify(password, encoded).valid
    }
}
