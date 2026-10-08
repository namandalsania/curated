package com.curated.app.core.data

import io.github.jan.supabase.exceptions.RestException

/** What the server says about the signed-in account. */
enum class AccountStatus {
    /** The server answered and knows the account. */
    EXISTS,

    /** The server answered that the account doesn't exist - deleted elsewhere. Sign out. */
    GONE,

    /** No usable answer: offline, timed out, a server error, or an error that isn't "no such user". Change nothing. */
    UNKNOWN
}

/** Supabase Auth's error_code for a token whose user no longer exists (HTTP 403 from /user). */
internal const val USER_NOT_FOUND = "user_not_found"

/**
 * The decision, apart from the exception types so it can be tested. Only an
 * auth error that says, in so many words, that the user doesn't exist counts
 * as gone. A refresh failing with refresh_token_not_found doesn't: signing out
 * on another device causes that too.
 */
internal fun accountStatusOf(succeeded: Boolean, authErrorCode: String?): AccountStatus = when {
    succeeded -> AccountStatus.EXISTS
    authErrorCode == USER_NOT_FOUND -> AccountStatus.GONE
    else -> AccountStatus.UNKNOWN
}

/** [failure] from an auth call (null if it succeeded) as an [AccountStatus]. */
internal fun accountStatusOf(failure: Throwable?): AccountStatus =
    accountStatusOf(
        succeeded = failure == null,
        // A Supabase error response carries the server's error_code in
        // RestException.error (AuthRestException, and the RestException a failed
        // refresh reports, both). Anything else - IO, timeouts, ktor's
        // HttpRequestException - has none, so it's UNKNOWN.
        authErrorCode = (failure as? RestException)?.error
    )
