package com.curated.app.core.data

import com.curated.app.core.model.Plan
import com.curated.app.core.model.PlanItem
import com.curated.app.core.model.PlanMember
import com.curated.app.core.model.PlanRole
import com.curated.app.core.model.User
import com.curated.app.core.plan.PlanChanges
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/**
 * Day-by-day plans built from saved places, owned by one person and shared
 * with invited viewers and editors. RLS decides who sees and changes what;
 * this class just asks.
 */
class PlanRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    /** Every plan the user can see: their own, ones they've joined, ones they're invited to. */
    suspend fun fetchVisiblePlans(): List<Plan> =
        postgrest.from("plans")
            .select { order("updated_at", Order.DESCENDING) }
            .decodeList()

    /** The user's own membership rows (accepted and pending) across all plans. */
    suspend fun fetchMyMemberships(userId: String): List<PlanMember> =
        postgrest.from("plan_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList()

    /** Place count per plan, for "5 places · 3 days" in the list. */
    suspend fun itemCounts(planIds: List<String>): Map<String, Int> {
        if (planIds.isEmpty()) return emptyMap()
        return postgrest.from("plan_items")
            .select(columns = Columns.raw("plan_id")) { filter { isIn("plan_id", planIds) } }
            .decodeList<PlanIdOnlyRow>()
            .groupingBy { it.planId }
            .eachCount()
    }

    suspend fun fetchPlan(planId: String): Plan? =
        postgrest.from("plans").select { filter { eq("id", planId) } }.decodeSingleOrNull()

    suspend fun fetchItems(planId: String): List<PlanItem> =
        postgrest.from("plan_items").select { filter { eq("plan_id", planId) } }.decodeList()

    suspend fun createPlan(userId: String, title: String, dayCount: Int): Plan =
        postgrest.from("plans")
            .insert(NewPlanRow(userId = userId, title = title, dayCount = dayCount)) { select() }
            .decodeSingle()

    suspend fun updatePlan(planId: String, title: String, dayCount: Int) {
        postgrest.from("plans").update(
            PlanUpdateRow(title = title, dayCount = dayCount, updatedAt = Clock.System.now().toString())
        ) { filter { eq("id", planId) } }
    }

    /**
     * Writes an editor change. Deletes go first so a place removed and re-added
     * doesn't trip the one-stop-per-plan rule; moves go through
     * move_plan_items() in one transaction.
     */
    suspend fun applyChanges(planId: String, changes: PlanChanges) {
        if (changes.isEmpty) return
        if (changes.deletedIds.isNotEmpty()) {
            postgrest.from("plan_items").delete { filter { isIn("id", changes.deletedIds) } }
        }
        if (changes.inserts.isNotEmpty()) {
            postgrest.from("plan_items").insert(changes.inserts)
        }
        if (changes.moves.isNotEmpty()) {
            postgrest.rpc(
                "move_plan_items",
                MovePlanItemsParams(planId, changes.moves.map { ItemMove(it.id, it.dayNumber, it.position) })
            )
        } else {
            postgrest.from("plans").update(TouchRow(Clock.System.now().toString())) { filter { eq("id", planId) } }
        }
    }

    suspend fun deletePlan(planId: String) {
        postgrest.from("plans").delete { filter { eq("id", planId) } }
    }

    // --- Members -------------------------------------------------------------

    /** Everyone invited to the plan, with their profiles. The owner comes from [Plan.userId]. */
    suspend fun fetchMembers(planId: String): List<PlanMember> {
        val members = postgrest.from("plan_members")
            .select {
                filter { eq("plan_id", planId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<PlanMember>()
        if (members.isEmpty()) return members
        val users = fetchUsers(members.map { it.userId }).associateBy { it.id }
        return members.map { it.copy(user = users[it.userId]) }
    }

    suspend fun fetchUsers(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()
        return postgrest.from("users").select { filter { isIn("id", userIds.distinct()) } }.decodeList()
    }

    suspend fun invite(planId: String, userId: String, role: PlanRole, invitedBy: String) {
        postgrest.from("plan_members").insert(
            NewMemberRow(planId = planId, userId = userId, role = role, invitedBy = invitedBy)
        )
    }

    suspend fun changeRole(planId: String, userId: String, role: PlanRole) {
        postgrest.from("plan_members").update(RoleRow(role)) {
            filter {
                eq("plan_id", planId)
                eq("user_id", userId)
            }
        }
    }

    /** Owner removing someone, or a member declining an invite / leaving. */
    suspend fun removeMember(planId: String, userId: String) {
        postgrest.from("plan_members").delete {
            filter {
                eq("plan_id", planId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun acceptInvite(planId: String) {
        postgrest.rpc("accept_plan_invite", PlanIdParams(planId))
    }

    // --- Live updates --------------------------------------------------------

    fun openPlanChannel(planId: String): RealtimeChannel = client.channel("plan-$planId")

    /**
     * Fires whenever anyone changes this plan, its items or its members.
     * Realtime can't filter deletes by column, so item deletes arrive for
     * every plan the user can see and are narrowed by [isMyItem].
     */
    fun observeChanges(channel: RealtimeChannel, planId: String, isMyItem: (String) -> Boolean): Flow<Unit> {
        val itemChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "plan_items"
            filter("plan_id", FilterOperator.EQ, planId)
        }
        val itemDeletes = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "plan_items"
        }.filter { delete -> delete.oldRecord["id"]?.jsonPrimitive?.content?.let(isMyItem) == true }
        val planChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "plans"
            filter("id", FilterOperator.EQ, planId)
        }
        val memberChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "plan_members"
            filter("plan_id", FilterOperator.EQ, planId)
        }
        return merge(itemChanges, itemDeletes, planChanges, memberChanges).map { }
    }

    suspend fun closeChannel(channel: RealtimeChannel) {
        runCatching { channel.unsubscribe() }
    }
}

@Serializable
private data class PlanIdOnlyRow(@SerialName("plan_id") val planId: String)

@Serializable
private data class NewPlanRow(
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("day_count") val dayCount: Int
)

/** Timestamps as ISO-8601 strings, as NotificationRepository sends read_at. */
@Serializable
private data class PlanUpdateRow(
    val title: String,
    @SerialName("day_count") val dayCount: Int,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class TouchRow(@SerialName("updated_at") val updatedAt: String)

@Serializable
private data class ItemMove(
    val id: String,
    @SerialName("day_number") val dayNumber: Int,
    val position: Int
)

@Serializable
private data class MovePlanItemsParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_moves") val moves: List<ItemMove>
)

@Serializable
private data class PlanIdParams(@SerialName("p_plan_id") val planId: String)

@Serializable
private data class NewMemberRow(
    @SerialName("plan_id") val planId: String,
    @SerialName("user_id") val userId: String,
    val role: PlanRole,
    @SerialName("invited_by") val invitedBy: String
)

@Serializable
private data class RoleRow(val role: PlanRole)
