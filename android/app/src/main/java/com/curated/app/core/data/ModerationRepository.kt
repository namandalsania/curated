package com.curated.app.core.data

import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a report is about. Matches reports.target_type. */
enum class ReportTarget(val dbValue: String) {
    TRIP("trip"),
    STOP("stop"),
    COMMENT("comment"),
    PROFILE("profile")
}

/** Why. Matches reports.reason; the labels are what the report sheet shows. */
enum class ReportReason(val dbValue: String, val label: String) {
    SPAM("spam", "Spam"),
    INAPPROPRIATE("inappropriate", "Inappropriate content"),
    HARASSMENT("harassment", "Harassment or bullying"),
    MISLEADING("misleading", "Misleading or fake"),
    OTHER("other", "Something else")
}

/** reports.note's limit, enforced by the table too. */
const val REPORT_NOTE_MAX_LENGTH = 500

/**
 * The accounts you've blocked, app-wide, so every screen can drop their
 * content from what it already has loaded.
 *
 * This is only a convenience: the database's policies already hide a blocked
 * person's trips, comments and notifications in both directions. What it adds
 * is that something loaded before the block disappears straight away rather
 * than on the next refresh.
 */
object BlockedAccounts {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids

    internal fun set(ids: Set<String>) { _ids.value = ids }
    internal fun add(id: String) = _ids.update { it + id }
    internal fun remove(id: String) = _ids.update { it - id }
    fun clear() { _ids.value = emptySet() }
}

/** Reporting content and blocking accounts. */
class ModerationRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    /**
     * Files a report. A second report on the same thing is treated as done -
     * the table allows one per person per target, and from the reporter's side
     * "already reported" and "reported" are the same outcome.
     */
    suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason, note: String?) {
        val trimmed = note?.trim()?.take(REPORT_NOTE_MAX_LENGTH)?.ifEmpty { null }
        try {
            // reporter_id is left to the column default, auth.uid().
            postgrest.from("reports").insert(NewReportRow(target.dbValue, targetId, reason.dbValue, trimmed))
        } catch (e: PostgrestRestException) {
            if (!e.isDuplicate()) throw e
        }
    }

    /** Blocks [userId]. Blocking someone already blocked is a no-op. */
    suspend fun block(userId: String) {
        try {
            postgrest.from("blocks").insert(NewBlockRow(userId))
        } catch (e: PostgrestRestException) {
            if (!e.isDuplicate()) throw e
        }
        BlockedAccounts.add(userId)
    }

    suspend fun unblock(userId: String) {
        postgrest.from("blocks").delete { filter { eq("blocked_id", userId) } }
        BlockedAccounts.remove(userId)
    }

    /** Refreshes [BlockedAccounts] from the table. Policies limit it to your own blocks. */
    suspend fun refreshBlockedIds() {
        val ids = postgrest.from("blocks")
            .select(columns = Columns.raw("blocked_id"))
            .decodeList<BlockedIdRow>()
            .map { it.blockedId }
            .toSet()
        BlockedAccounts.set(ids)
    }

    /** Everyone you've blocked, most recent first, with their profiles. */
    suspend fun fetchBlockedUsers(): List<User> {
        val rows = postgrest.from("blocks")
            .select(columns = Columns.raw("blocked_id,created_at")) {
                order("created_at", Order.DESCENDING)
            }
            .decodeList<BlockedIdRow>()
        BlockedAccounts.set(rows.map { it.blockedId }.toSet())
        if (rows.isEmpty()) return emptyList()
        val users = postgrest.from("users")
            .select { filter { isIn("id", rows.map { it.blockedId }) } }
            .decodeList<User>()
            .associateBy { it.id }
        return rows.mapNotNull { users[it.blockedId] }
    }
}

/** Postgres unique_violation, as PostgREST reports it. */
private fun PostgrestRestException.isDuplicate(): Boolean = code == "23505"

@Serializable
private data class NewReportRow(
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val note: String?
)

@Serializable
private data class NewBlockRow(@SerialName("blocked_id") val blockedId: String)

@Serializable
private data class BlockedIdRow(@SerialName("blocked_id") val blockedId: String)
