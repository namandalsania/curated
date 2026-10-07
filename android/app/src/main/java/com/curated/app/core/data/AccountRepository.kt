package com.curated.app.core.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText

/** How a call to the delete-account Edge Function ended. */
enum class DeleteAccountOutcome {
    /** The account is gone (now, or by an earlier call whose answer was lost). */
    DELETED,

    /** The sign-in was too old; ask for the password again. */
    REAUTH_REQUIRED,

    /** The session wasn't accepted at all. */
    NOT_SIGNED_IN,

    /** Something failed partway. Calling again picks up where it stopped. */
    FAILED
}

/**
 * Reads the function's answer. The status code decides; the body only tells
 * "deleted" from "already deleted", which the app treats the same.
 */
fun deleteAccountOutcome(status: Int, body: String?): DeleteAccountOutcome = when {
    status == 200 && body != null && (body.contains("\"deleted\"") || body.contains("\"already_deleted\"")) ->
        DeleteAccountOutcome.DELETED
    status == 403 && body?.contains("reauthentication_required") == true -> DeleteAccountOutcome.REAUTH_REQUIRED
    status == 401 -> DeleteAccountOutcome.NOT_SIGNED_IN
    else -> DeleteAccountOutcome.FAILED
}

/**
 * Deleting your own account. All the work happens in the delete-account Edge
 * Function, which identifies you from your session - nothing here names an
 * account, and the app never holds the service role key.
 */
class AccountRepository(private val client: SupabaseClient) {

    suspend fun deleteAccount(): DeleteAccountOutcome =
        try {
            // No body: the function never reads one.
            val response = client.functions.invoke(FUNCTION)
            deleteAccountOutcome(response.status.value, response.bodyAsText())
        } catch (e: RestException) {
            // functions-kt throws for any non-2xx, with the body as the error.
            deleteAccountOutcome(e.statusCode, e.error)
        }

    private companion object {
        const val FUNCTION = "delete-account"
    }
}
