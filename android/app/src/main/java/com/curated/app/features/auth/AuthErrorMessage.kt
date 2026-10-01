package com.curated.app.features.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import java.io.IOException

/**
 * Turns a sign-in or sign-up failure into one short sentence.
 *
 * supabase-kt builds its exception messages for logs, not for people: a wrong
 * password arrives as the error code, the description, the request URL, every
 * request header and the HTTP method, spread over five lines. Showing that
 * verbatim was unreadable and put request headers on screen, so we map the
 * error codes worth naming and fall back to something plain.
 */
fun authErrorMessage(error: Throwable, mode: AuthMode): String = when (error) {
    is AuthRestException -> when (error.errorCode) {
        AuthErrorCode.InvalidCredentials -> "Email or password is incorrect."
        AuthErrorCode.EmailNotConfirmed -> EMAIL_NOT_CONFIRMED
        AuthErrorCode.UserAlreadyExists,
        AuthErrorCode.EmailExists -> "That email already has an account. Sign in instead."
        AuthErrorCode.WeakPassword -> "Pick a stronger password — at least ${AuthRules.MIN_PASSWORD_LENGTH} characters."
        AuthErrorCode.EmailAddressInvalid,
        AuthErrorCode.ValidationFailed -> "That doesn't look like a valid email address."
        AuthErrorCode.OverRequestRateLimit,
        AuthErrorCode.OverEmailSendRateLimit -> RATE_LIMITED
        AuthErrorCode.UserBanned -> "This account has been disabled."
        AuthErrorCode.SignupDisabled,
        AuthErrorCode.EmailProviderDisabled -> "New accounts aren't being accepted right now."
        // Some GoTrue versions report bad credentials without a code we know.
        // Don't name a cause we aren't sure of - just point at the two fields.
        else -> fallback(mode)
    }
    // HttpRequestException extends IOException, as do Ktor's socket failures.
    is IOException -> OFFLINE
    else -> fallback(mode)
}

/** Whether a sign-in failed only because the email hasn't been confirmed - worth its own UI. */
fun isEmailNotConfirmed(error: Throwable): Boolean =
    error is AuthRestException && error.errorCode == AuthErrorCode.EmailNotConfirmed

/**
 * Password reset failures. Deliberately never says whether an address has an
 * account: "no such user" and "sent" must look the same from outside.
 */
fun resetErrorMessage(error: Throwable, step: ResetStep): String = when (error) {
    is AuthRestException -> when (error.errorCode) {
        AuthErrorCode.OtpExpired -> "That code is wrong or has expired. Check the latest email, or send a new code."
        AuthErrorCode.SamePassword -> "That's your current password. Choose a different one."
        AuthErrorCode.WeakPassword -> "Pick a stronger password — at least ${AuthRules.MIN_PASSWORD_LENGTH} characters."
        AuthErrorCode.OverRequestRateLimit,
        AuthErrorCode.OverEmailSendRateLimit -> RATE_LIMITED
        else -> resetFallback(step)
    }
    is IOException -> OFFLINE
    else -> resetFallback(step)
}

enum class ResetStep { SEND_CODE, VERIFY_CODE, SAVE_PASSWORD }

private fun resetFallback(step: ResetStep) = when (step) {
    ResetStep.SEND_CODE -> "Couldn't send a code. Try again."
    ResetStep.VERIFY_CODE -> "That code is wrong or has expired. Check the latest email, or send a new code."
    ResetStep.SAVE_PASSWORD -> "Couldn't save your new password. Try again."
}

private fun fallback(mode: AuthMode) = when (mode) {
    AuthMode.SIGN_IN -> "Couldn't sign you in. Check your email and password."
    AuthMode.SIGN_UP -> "Couldn't create your account. Try again."
}

const val EMAIL_NOT_CONFIRMED = "Your email isn't confirmed yet. Open the link we sent you, then sign in."
private const val RATE_LIMITED = "Too many attempts. Wait a minute, then try again."
private const val OFFLINE = "Can't reach the server. Check your connection."
