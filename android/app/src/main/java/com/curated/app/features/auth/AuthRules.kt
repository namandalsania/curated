package com.curated.app.features.auth

/** Field rules for the auth screens, checked as you type rather than only on submit. */
object AuthRules {

    const val MIN_PASSWORD_LENGTH = 8
    const val CODE_LENGTH = 6
    const val RESEND_COOLDOWN_SECONDS = 60

    private val usernamePattern = Regex("^[a-z0-9_.]{3,20}$")
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Usernames are stored lowercase, so "Maya" and "maya" can't both exist. */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    /** Why [username] (already normalized) isn't allowed, or null if it is. */
    fun usernameProblem(username: String): String? = when {
        username.length < 3 -> "At least 3 characters."
        username.length > 20 -> "20 characters at most."
        !usernamePattern.matches(username) -> "Letters, numbers, dots and underscores only."
        username.startsWith('.') || username.endsWith('.') -> "Can't start or end with a dot."
        else -> null
    }

    fun isEmailPlausible(email: String): Boolean = emailPattern.matches(email.trim())

    fun passwordProblem(password: String): String? =
        if (password.length < MIN_PASSWORD_LENGTH) "At least $MIN_PASSWORD_LENGTH characters." else null

    fun isCodeComplete(code: String): Boolean = code.length == CODE_LENGTH && code.all(Char::isDigit)
}
