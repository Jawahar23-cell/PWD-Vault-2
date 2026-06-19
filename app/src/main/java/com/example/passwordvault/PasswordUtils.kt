package com.example.passwordvault

import kotlin.math.min

object PasswordUtils {

    enum class PasswordStrength {
        WEAK, MEDIUM, STRONG
    }

    /**
     * Assess password strength: WEAK (< 8 chars or no variety),
     * MEDIUM (8+ chars, some variety), STRONG (12+ chars, high variety).
     */
    fun assessStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK

        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSpecial = false

        for (c in password) {
            when {
                c.isLowerCase() -> hasLower = true
                c.isUpperCase() -> hasUpper = true
                c.isDigit() -> hasDigit = true
                !c.isLetterOrDigit() -> hasSpecial = true
            }
        }

        val varietyCount = listOf(hasLower, hasUpper, hasDigit, hasSpecial).count { it }

        return when {
            password.length >= 12 && varietyCount >= 3 -> PasswordStrength.STRONG
            varietyCount >= 2 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
    }

    /**
     * Generate a random password of specified length (default 16).
     * Includes uppercase, lowercase, digits, and special characters.
     */
    fun generatePassword(length: Int = 16): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?"
        val result = StringBuilder()
        repeat(length) {
            result.append(chars.random())
        }
        return result.toString()
    }

    /**
     * Get color code and label for strength level (for UI display).
     */
    fun getStrengthDisplay(strength: PasswordStrength): Pair<String, String> = when (strength) {
        PasswordStrength.WEAK -> Pair("ff006e", "Weak")
        PasswordStrength.MEDIUM -> Pair("ffbe0b", "Medium")
        PasswordStrength.STRONG -> Pair("8338ec", "Strong")
    }
}
