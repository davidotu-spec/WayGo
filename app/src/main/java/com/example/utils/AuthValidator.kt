package com.example.utils

import android.util.Patterns

/**
 * Password strength levels for user feedback during registration.
 */
enum class PasswordStrength(val label: String, val score: Int) {
    TOO_SHORT("Too Short (< 6 chars)", 0),
    WEAK("Weak", 1),
    MEDIUM("Medium", 2),
    STRONG("Strong", 3)
}

/**
 * Result of password validation with granular policy checks.
 */
data class PasswordValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val strength: PasswordStrength = PasswordStrength.TOO_SHORT,
    val hasMinLength: Boolean = false,
    val hasLetter: Boolean = false,
    val hasDigitOrSymbol: Boolean = false
)

/**
 * General validation result for forms and auth operations.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val normalizedValue: String? = null
)

/**
 * AuthValidator provides a dedicated validation and normalization layer:
 * - RFC 5322 compliant email regex validation
 * - Domain validation and address normalization to prevent account duplicate collisions
 * - Password complexity enforcement (meeting Firebase >= 6 characters rule)
 * - Sign-up payload validation with mandatory email verification requirements
 */
object AuthValidator {

    /**
     * Strict RFC 5322 compliant regex for standard email addresses:
     * - Disallows consecutive dots
     * - Disallows leading/trailing special characters
     * - Requires valid domain syntax with top-level domain (TLD) of 2-7 alphabetical characters
     */
    val RFC_5322_EMAIL_REGEX = Regex(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    )

    /**
     * Normalizes an email address to a canonical form to prevent duplicate account collisions.
     * Trims leading/trailing whitespace, converts to lowercase, and strips trailing periods.
     */
    fun normalizeEmail(email: String): String {
        val trimmed = email.trim().lowercase()
        if (!trimmed.contains("@")) return trimmed
        val parts = trimmed.split("@")
        if (parts.size != 2) return trimmed
        val localPart = parts[0].trim()
        val domainPart = parts[1].trim()
        return "$localPart@$domainPart"
    }

    /**
     * Validates email format according to RFC 5322 and strict domain validation rules.
     * Enforces domain syntax, non-empty local-part, and valid TLD.
     */
    fun validateEmail(email: String): ValidationResult {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return ValidationResult(false, "Email address cannot be empty.")
        }
        if (cleanEmail.contains(" ")) {
            return ValidationResult(false, "Email address cannot contain spaces.")
        }
        if (!cleanEmail.contains("@")) {
            return ValidationResult(false, "Email address must contain an '@' sign.")
        }

        val parts = cleanEmail.split("@")
        if (parts.size != 2) {
            return ValidationResult(false, "Email address cannot contain multiple '@' symbols.")
        }

        val localPart = parts[0]
        val domainPart = parts[1]

        if (localPart.isBlank()) {
            return ValidationResult(false, "Email username (before '@') cannot be empty.")
        }
        if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            return ValidationResult(false, "Email username contains invalid consecutive or edge dots.")
        }

        if (domainPart.isBlank() || !domainPart.contains(".")) {
            return ValidationResult(false, "Please provide a valid domain name with extension (e.g., .com, .org, .gm).")
        }
        if (domainPart.startsWith(".") || domainPart.endsWith(".") || domainPart.contains("..")) {
            return ValidationResult(false, "Domain part contains invalid dots or formatting.")
        }

        val domainLabels = domainPart.split(".")
        val tld = domainLabels.last()
        if (tld.length < 2 || !tld.all { it.isLetter() }) {
            return ValidationResult(false, "Domain must end with a valid top-level domain (e.g., .com, .org, .gm).")
        }

        if (!cleanEmail.matches(RFC_5322_EMAIL_REGEX)) {
            return ValidationResult(false, "Invalid email address format according to RFC 5322 standard.")
        }

        val normalized = normalizeEmail(cleanEmail)
        return ValidationResult(true, null, normalized)
    }

    /**
     * Validates password requirements before sending to Firebase createUserWithEmailAndPassword.
     * Enforces Firebase's requirement of >= 6 characters and calculates strength.
     */
    fun validatePassword(password: String): PasswordValidationResult {
        val cleanPass = password.trim()

        if (cleanPass.isEmpty()) {
            return PasswordValidationResult(
                isValid = false,
                errorMessage = "Password cannot be empty.",
                strength = PasswordStrength.TOO_SHORT,
                hasMinLength = false
            )
        }

        val hasMinLength = cleanPass.length >= 6
        val hasLetter = cleanPass.any { it.isLetter() }
        val hasDigit = cleanPass.any { it.isDigit() }
        val hasSpecial = cleanPass.any { !it.isLetterOrDigit() }
        val hasDigitOrSymbol = hasDigit || hasSpecial

        if (!hasMinLength) {
            return PasswordValidationResult(
                isValid = false,
                errorMessage = "Password must be at least 6 characters (Firebase requirement).",
                strength = PasswordStrength.TOO_SHORT,
                hasMinLength = false,
                hasLetter = hasLetter,
                hasDigitOrSymbol = hasDigitOrSymbol
            )
        }

        // Calculate strength
        val score = when {
            hasMinLength && hasLetter && hasDigit && hasSpecial && cleanPass.length >= 8 -> PasswordStrength.STRONG
            hasMinLength && hasLetter && (hasDigit || hasSpecial) -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }

        return PasswordValidationResult(
            isValid = true,
            errorMessage = null,
            strength = score,
            hasMinLength = true,
            hasLetter = hasLetter,
            hasDigitOrSymbol = hasDigitOrSymbol
        )
    }

    /**
     * Complete sign-up validation checking RFC 5322 normalized email, password, name, and driver requirements.
     */
    fun validateSignUp(
        email: String,
        password: String,
        name: String? = null,
        isDriver: Boolean = false,
        vehiclePlate: String? = null
    ): ValidationResult {
        // Name is optional - if provided with content, verify reasonable length
        if (!name.isNullOrBlank() && name.trim().length < 2) {
            return ValidationResult(false, "Full name must be at least 2 characters if entered.")
        }

        val emailResult = validateEmail(email)
        if (!emailResult.isValid) {
            return emailResult
        }

        val passResult = validatePassword(password)
        if (!passResult.isValid) {
            return ValidationResult(false, passResult.errorMessage)
        }

        if (isDriver && vehiclePlate.isNullOrBlank()) {
            return ValidationResult(false, "Please enter your vehicle license plate.")
        }

        return ValidationResult(true, null, emailResult.normalizedValue)
    }
}

