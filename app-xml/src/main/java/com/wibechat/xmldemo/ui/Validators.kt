package com.wibechat.xmldemo.ui

/** Same rules as the Flutter and Compose apps. Returns an error message, or null when valid. */
object Validators {
    private val emailRegex = Regex("""^[\w.\-]+@([\w\-]+\.)+[\w\-]{2,4}$""")

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !emailRegex.matches(value.trim()) -> "Enter a valid email"
        else -> null
    }

    fun password(value: String): String? = when {
        value.isEmpty() -> "Password is required"
        value.length < 6 -> "Minimum 6 characters"
        else -> null
    }
}
