package com.curated.app.features.plans

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.PlanRepository
import com.curated.app.core.data.SavedPlacesRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.InviteStatus
import com.curated.app.core.model.Plan
import com.curated.app.core.model.PlanItem
import com.curated.app.core.model.PlanMember
import com.curated.app.core.model.PlanRole
import com.curated.app.core.model.SavedPlace
import com.curated.app.core.model.StopCategory
import com.curated.app.core.model.User
import com.curated.app.core.model.placeKey
import com.curated.app.core.model.toPlanItem
import com.curated.app.core.plan.PlanOrdering
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class PlanEditorState(
    val isLoading: Boolean = true,
    val plan: Plan? = null,
    val items: List<PlanItem> = emptyList(),
    /** This user's own saved places - what they can add. */
    val savedPlaces: List<SavedPlace> = emptyList(),
    /** Everyone invited, plus their profiles. The owner isn't in here. */
    val members: List<PlanMember> = emptyList(),
    val owner: User? = null,
    val myRole: PlanRole? = null,
    val myUserId: String? = null,
    val error: String? = null,
    val actionError: String? = null,
    /** Set when the plan is gone for this user: deleted, or they left it. */
    val closed: Boolean = false,
    val memberSearch: MemberSearchState = MemberSearchState()
) {
    val dayCount: Int get() = plan?.dayCount ?: 1
    val canEdit: Boolean get() = myRole?.canEdit == true
    val isOwner: Boolean get() = myRole == PlanRole.OWNER
    /** Names by user id, for "added by Leo". */
    val peopleById: Map<String, User>
        get() = (members.mapNotNull { it.user } + listOfNotNull(owner)).associateBy { it.id }

    val days: List<List<PlanItem>> get() = PlanOrdering.byDay(items, dayCount)

    /** Saved places not already in the plan - what "Add places" offers. */
    val addablePlaces: List<SavedPlace>
        get() {
            val inPlan = items.mapTo(HashSet()) { it.placeKey }
            return savedPlaces.filter { it.placeKey !in inPlan }
        }

    val acceptedMembers: List<PlanMember> get() = members.filter { it.status == InviteStatus.ACCEPTED }
    val pendingMembers: List<PlanMember> get() = members.filter { it.status == InviteStatus.PENDING }
}

data class MemberSearchState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val isSearching: Boolean = false
)

@OptIn(FlowPreview::class)
class PlanEditorViewModel(
    private val planId: String,
    private val authRepository: AuthRepository,
    private val planRepository: PlanRepository,
    private val savedPlacesRepository: SavedPlacesRepository,
    private val socialRepository: SocialRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlanEditorState())
    val state: StateFlow<PlanEditorState> = _state

    /** One write at a time, in the order the edits were made. */
    private val writeLock = Mutex()

    /** Writes in flight. Realtime echoes our own changes, so don't reload mid-edit. */
    private var pendingWrites = 0
    private var missedChange = false
    private var channel: RealtimeChannel? = null

    init {
        load()
        watchForChanges()
    }

    fun load() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.plan == null, error = null) }
            try {
                val plan = planRepository.fetchPlan(planId)
                if (plan == null) {
                    // Deleted by the owner, or we were removed from it.
                    _state.update { it.copy(isLoading = false, closed = true) }
                    return@launch
                }
                val items = planRepository.fetchItems(planId)
                val members = planRepository.fetchMembers(planId)
                val saved = savedPlacesRepository.fetchAll(userId)
                val owner = planRepository.fetchUsers(listOf(plan.userId)).firstOrNull()
                val role = when {
                    plan.userId == userId -> PlanRole.OWNER
                    else -> members.firstOrNull { it.userId == userId && it.status == InviteStatus.ACCEPTED }?.role
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        plan = plan,
                        items = items,
                        members = members,
                        owner = owner,
                        myRole = role,
                        myUserId = userId,
                        savedPlaces = saved
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load plan $planId", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load this plan.") }
            }
        }
    }

    /** Reload when a collaborator changes something, once our own writes have settled. */
    private fun watchForChanges() {
        val realtimeChannel = planRepository.openPlanChannel(planId)
        channel = realtimeChannel
        viewModelScope.launch {
            planRepository
                .observeChanges(realtimeChannel, planId) { itemId -> _state.value.items.any { it.id == itemId } }
                .debounce(400)
                .collect {
                    if (pendingWrites > 0) missedChange = true else load()
                }
        }
        viewModelScope.launch { runCatching { realtimeChannel.subscribe() } }
    }

    override fun onCleared() {
        val open = channel ?: return
        channel = null
        // viewModelScope is already cancelled by the time this runs, so the
        // channel has to be closed on a scope of our own. The Supabase client
        // is a singleton, so leaving it open would leak a subscription.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            planRepository.closeChannel(open)
        }
    }

    fun moveUp(itemId: String) = editItems { PlanOrdering.moveWithinDay(it, itemId, -1) }

    fun moveDown(itemId: String) = editItems { PlanOrdering.moveWithinDay(it, itemId, +1) }

    fun moveToDay(itemId: String, day: Int) = editItems { PlanOrdering.moveToDay(it, itemId, day) }

    fun remove(itemId: String) = editItems { PlanOrdering.remove(it, itemId) }

    fun addPlaces(savedPlaceIds: List<String>, day: Int) {
        val me = _state.value.myUserId ?: return
        val byId = _state.value.savedPlaces.associateBy { it.id }
        editItems { items ->
            PlanOrdering.append(
                items,
                savedPlaceIds.mapNotNull { placeId ->
                    // A copy, so it stays in the plan even if this user unsaves it,
                    // and collaborators can see it without reading anyone's saves.
                    byId[placeId]?.toPlanItem(UUID.randomUUID().toString(), planId, day, me)
                },
                day
            )
        }
    }

    /** A place someone typed in: a coffee shop, a restaurant, anything. */
    fun addCustomPlace(
        day: Int,
        name: String,
        category: StopCategory,
        latitude: Double? = null,
        longitude: Double? = null,
        city: String? = null
    ) {
        val me = _state.value.myUserId ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        editItems { items ->
            PlanOrdering.append(
                items,
                listOf(
                    PlanItem(
                        id = UUID.randomUUID().toString(),
                        planId = planId,
                        dayNumber = day,
                        position = 0,
                        name = clean,
                        category = category,
                        latitude = latitude,
                        longitude = longitude,
                        city = city,
                        addedBy = me
                    )
                ),
                day
            )
        }
    }

    fun addDay() {
        val plan = _state.value.plan ?: return
        if (!_state.value.canEdit || plan.dayCount >= PLAN_MAX_DAYS) return
        updatePlan(plan.copy(dayCount = plan.dayCount + 1))
    }

    fun removeDay(day: Int) {
        val plan = _state.value.plan ?: return
        if (!_state.value.canEdit || plan.dayCount <= 1) return
        val before = _state.value.items
        val after = PlanOrdering.removeDay(before, day)
        val newPlan = plan.copy(dayCount = plan.dayCount - 1)
        _state.update { it.copy(plan = newPlan, items = after, actionError = null) }
        persist {
            // Items first: shrinking day_count before moving later days down
            // would briefly leave items on a day that no longer exists.
            planRepository.applyChanges(planId, PlanOrdering.changedSince(before, after))
            planRepository.updatePlan(planId, newPlan.title, newPlan.dayCount)
        }
    }

    fun rename(title: String) {
        val plan = _state.value.plan ?: return
        val clean = title.trim()
        if (!_state.value.isOwner || clean.isEmpty() || clean == plan.title) return
        updatePlan(plan.copy(title = clean))
    }

    fun deletePlan() {
        if (!_state.value.isOwner) return
        persist {
            planRepository.deletePlan(planId)
            _state.update { it.copy(closed = true) }
        }
    }

    /** A member leaving a plan they were invited to. */
    fun leavePlan() {
        val me = _state.value.myUserId ?: return
        if (_state.value.isOwner) return
        persist {
            planRepository.removeMember(planId, me)
            _state.update { it.copy(closed = true) }
        }
    }

    // --- People --------------------------------------------------------------

    fun searchPeople(query: String) {
        _state.update { it.copy(memberSearch = it.memberSearch.copy(query = query, isSearching = query.isNotBlank())) }
        if (query.isBlank()) {
            _state.update { it.copy(memberSearch = it.memberSearch.copy(results = emptyList(), isSearching = false)) }
            return
        }
        viewModelScope.launch {
            val results = runCatching { socialRepository.searchUsers(query.trim()) }.getOrElse { emptyList() }
            // Don't offer the owner, existing members, or yourself.
            val taken = _state.value.members.map { it.userId } + listOfNotNull(_state.value.plan?.userId)
            _state.update { state ->
                if (state.memberSearch.query != query) state // a newer search won
                else state.copy(
                    memberSearch = state.memberSearch.copy(
                        results = results.filter { it.id !in taken },
                        isSearching = false
                    )
                )
            }
        }
    }

    fun invite(user: User, role: PlanRole) {
        val me = _state.value.myUserId ?: return
        if (!_state.value.isOwner) return
        _state.update { it.copy(memberSearch = MemberSearchState(), actionError = null) }
        persist {
            planRepository.invite(planId, user.id, role, me)
            runCatching { notificationRepository.notifyPlanInvite(actorId = me, recipientId = user.id, planId = planId) }
            load()
        }
    }

    fun changeRole(userId: String, role: PlanRole) {
        if (!_state.value.isOwner) return
        _state.update { state ->
            state.copy(members = state.members.map { if (it.userId == userId) it.copy(role = role) else it })
        }
        persist { planRepository.changeRole(planId, userId, role) }
    }

    fun removeMember(userId: String) {
        if (!_state.value.isOwner) return
        _state.update { state -> state.copy(members = state.members.filterNot { it.userId == userId }) }
        persist { planRepository.removeMember(planId, userId) }
    }

    // --- Plumbing ------------------------------------------------------------

    private fun updatePlan(newPlan: Plan) {
        _state.update { it.copy(plan = newPlan, actionError = null) }
        persist { planRepository.updatePlan(planId, newPlan.title, newPlan.dayCount) }
    }

    private fun editItems(transform: (List<PlanItem>) -> List<PlanItem>) {
        if (!_state.value.canEdit) return
        val before = _state.value.items
        val after = transform(before)
        val changes = PlanOrdering.changedSince(before, after)
        if (changes.isEmpty) return
        _state.update { it.copy(items = after, actionError = null) }
        persist { planRepository.applyChanges(planId, changes) }
    }

    /** Runs writes in order; on failure, reloads so the screen matches what's stored. */
    private fun persist(write: suspend () -> Unit) {
        pendingWrites++
        viewModelScope.launch {
            try {
                writeLock.withLock { write() }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save a change to plan $planId", e)
                _state.update { it.copy(actionError = "Couldn't save that change. Showing the plan as it's stored.") }
                load()
            } finally {
                pendingWrites--
                if (pendingWrites == 0 && missedChange) {
                    missedChange = false
                    load()
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlanEditorViewModel"

        fun factory(context: Context, planId: String) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                PlanEditorViewModel(
                    planId = planId,
                    authRepository = AuthRepository(client),
                    planRepository = PlanRepository(client),
                    savedPlacesRepository = SavedPlacesRepository(client),
                    socialRepository = SocialRepository(client),
                    notificationRepository = NotificationRepository(client)
                )
            }
        }
    }
}
