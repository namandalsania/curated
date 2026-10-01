package com.curated.app.core.data

import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AuthRepository(private val client: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    /**
     * Creates the account and carries the chosen username and display name as
     * user metadata, so the profile row can be written on first sign-in even
     * when email confirmation means there's no session yet.
     *
     * Returns true if a session came back (confirmation off), false if the
     * person has to confirm their email first.
     */
    suspend fun signUp(email: String, password: String, username: String, displayName: String): Boolean {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put(META_USERNAME, username)
                put(META_DISPLAY_NAME, displayName)
            }
        }
        return client.auth.currentSessionOrNull() != null
    }

    /** The username and display name picked at sign-up, if this account has them. */
    fun pendingProfile(): Pair<String, String>? {
        val meta = client.auth.currentUserOrNull()?.userMetadata ?: return null
        val username = meta[META_USERNAME]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val displayName = meta[META_DISPLAY_NAME]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return username to displayName
    }

    /** Readable signed out too - users are public. Racy by nature: the insert is the real check. */
    suspend fun isUsernameAvailable(username: String): Boolean =
        client.postgrest.from("users")
            .select(columns = Columns.list("id")) {
                filter { eq("username", username) }
                limit(1)
            }
            .decodeList<IdRow>()
            .isEmpty()

    suspend fun resendSignUpConfirmation(email: String) {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    /**
     * Sends a recovery email. With the Reset Password template showing
     * {{ .Token }}, it carries a 6-digit code rather than relying on a link.
     * Supabase answers the same whether or not the address has an account.
     */
    suspend fun sendPasswordResetCode(email: String) {
        client.auth.resetPasswordForEmail(email, redirectUrl = null)
    }

    /** Checks the emailed code. Success signs the person in - see [PasswordRecovery]. */
    suspend fun verifyRecoveryCode(email: String, code: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
    }

    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser(redirectUrl = null) { password = newPassword }
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun fetchProfile(userId: String): User? =
        client.postgrest.from("users")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull()

    suspend fun createProfile(userId: String, username: String, displayName: String, avatarUrl: String?): User {
        return client.postgrest.from("users")
            .insert(
                NewUserRow(
                    id = userId,
                    username = username,
                    displayName = displayName,
                    avatarUrl = avatarUrl
                )
            ) { select() }
            .decodeSingle()
    }

    /** Username stays fixed here: it's unique and other people link to it. */
    suspend fun updateProfile(userId: String, displayName: String, bio: String?, avatarUrl: String?): User =
        client.postgrest.from("users")
            .update(ProfileUpdateRow(displayName = displayName, bio = bio, avatarUrl = avatarUrl)) {
                select()
                filter { eq("id", userId) }
            }
            .decodeSingle()
}

private const val META_USERNAME = "username"
private const val META_DISPLAY_NAME = "display_name"

/**
 * True while a password reset is between "code verified" and "new password
 * saved". Verifying a recovery code signs the person in, and without this the
 * auth gate would take them into the app before they'd set a new password.
 */
object PasswordRecovery {
    val inProgress = MutableStateFlow(false)
}

@Serializable
private data class IdRow(val id: String)

// No defaults, so a null bio or avatar is sent explicitly and clears the column.
@Serializable
private data class ProfileUpdateRow(
    @SerialName("display_name") val displayName: String,
    val bio: String?,
    @SerialName("avatar_url") val avatarUrl: String?
)

@Serializable
private data class NewUserRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)
