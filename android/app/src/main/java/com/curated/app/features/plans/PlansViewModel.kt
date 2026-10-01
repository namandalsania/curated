package com.curated.app.features.plans

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.PlanRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.InviteStatus
import com.curated.app.core.model.Plan
import com.curated.app.core.model.PlanRole
import com.curated.app.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PLAN_MAX_DAYS = 60

/** A plan in the list, with what this user can do with it. */
data class PlanListing(val plan: Plan, val role: PlanRole, val placeCount: Int)

/** A plan someone has invited this user to, not yet accepted. */
data class PlanInvitation(val plan: Plan, val role: PlanRole, val invitedBy: User?)

data class PlansState(
    val isLoading: Boolean = true,
    val invitations: List<PlanInvitation> = emptyList(),
    val owned: List<PlanListing> = emptyList(),
    val shared: List<PlanListing> = emptyList(),
    val error: String? = null,
    val actionError: String? = null,
    val isCreating: Boolean = false,
    val createError: String? = null,
    /** Set once a new plan exists; the screen opens it and calls [PlansViewModel.createdPlanOpened]. */
    val createdPlanId: String? = null
) {
    val isEmpty: Boolean get() = invitations.isEmpty() && owned.isEmpty() && shared.isEmpty()
}

class PlansViewModel(
    private val authRepository: AuthRepository,
    private val planRepository: PlanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlansState())
    val state: StateFlow<PlansState> = _state

    fun load() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.isEmpty, error = null) }
            try {
                // RLS returns exactly the plans this user owns, joined, or was invited to.
                val plans = planRepository.fetchVisiblePlans()
                val memberships = planRepository.fetchMyMemberships(userId).associateBy { it.planId }
                val counts = planRepository.itemCounts(plans.map { it.id })

                val pending = plans.mapNotNull { plan ->
                    memberships[plan.id]?.takeIf { it.status == InviteStatus.PENDING }?.let { plan to it }
                }
                val inviters = planRepository.fetchUsers(pending.mapNotNull { it.second.invitedBy }).associateBy { it.id }

                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        invitations = pending.map { (plan, member) ->
                            PlanInvitation(plan, member.role, member.invitedBy?.let(inviters::get))
                        },
                        owned = plans.filter { it.userId == userId }
                            .map { PlanListing(it, PlanRole.OWNER, counts[it.id] ?: 0) },
                        shared = plans.mapNotNull { plan ->
                            memberships[plan.id]
                                ?.takeIf { it.status == InviteStatus.ACCEPTED }
                                ?.let { PlanListing(plan, it.role, counts[plan.id] ?: 0) }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load plans", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load your plans.") }
            }
        }
    }

    fun create(title: String, dayCount: Int) {
        val userId = authRepository.currentUserId() ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, createError = null) }
            try {
                val plan = planRepository.createPlan(userId, cleanTitle, dayCount.coerceIn(1, PLAN_MAX_DAYS))
                _state.update {
                    it.copy(
                        isCreating = false,
                        owned = listOf(PlanListing(plan, PlanRole.OWNER, 0)) + it.owned,
                        createdPlanId = plan.id
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't create plan", e)
                _state.update { it.copy(isCreating = false, createError = "Couldn't create the plan. Try again.") }
            }
        }
    }

    fun acceptInvite(planId: String) = respondToInvite(planId, accept = true)

    fun declineInvite(planId: String) = respondToInvite(planId, accept = false)

    private fun respondToInvite(planId: String, accept: Boolean) {
        val userId = authRepository.currentUserId() ?: return
        _state.update { it.copy(invitations = it.invitations.filterNot { invite -> invite.plan.id == planId }, actionError = null) }
        viewModelScope.launch {
            try {
                if (accept) planRepository.acceptInvite(planId) else planRepository.removeMember(planId, userId)
                load()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't respond to invite for plan $planId", e)
                _state.update { it.copy(actionError = "Couldn't answer that invite. Try again.") }
                load()
            }
        }
    }

    fun createdPlanOpened() = _state.update { it.copy(createdPlanId = null) }

    companion object {
        private const val TAG = "PlansViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                PlansViewModel(AuthRepository(client), PlanRepository(client))
            }
        }
    }
}
