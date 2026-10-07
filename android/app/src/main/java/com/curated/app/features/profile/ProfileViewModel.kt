package com.curated.app.features.profile

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.EngagementRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.data.TripRepository
import com.curated.app.core.geocode.GeocodingService
import com.curated.app.core.map.CityAggregator
import com.curated.app.core.map.CountryVisit
import com.curated.app.core.model.Trip
import com.curated.app.core.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profileUser: User? = null,
    val isOwnProfile: Boolean = false,
    val isFollowing: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val trips: List<Trip> = emptyList(),
    val savedTrips: List<Trip> = emptyList(),
    /** Unfinished trips, your own profile only. */
    val drafts: List<Trip> = emptyList(),
    /** Trips under way, your own profile only. */
    val liveTrips: List<Trip> = emptyList(),
    val countryVisits: List<CountryVisit> = emptyList(),
    /** Visits need a reverse-geocode per area of stops, so they arrive after the rest of the profile. */
    val isVisitsLoading: Boolean = true,
    /** The profile couldn't load at all - replaces the screen. */
    val error: String? = null,
    /** A follow/unfollow failed - shown inline, the profile stays up. */
    val actionError: String? = null
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val tripRepository: TripRepository,
    private val engagementRepository: EngagementRepository,
    private val notificationRepository: NotificationRepository,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    private var visitsJob: Job? = null

    /**
     * [showSkeleton] is false for a refresh after editing the profile, so the
     * page updates in place instead of flashing back to placeholders.
     */
    fun load(profileUserId: String, showSkeleton: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = showSkeleton || it.profileUser == null, error = null, actionError = null) }
            try {
                val myId = authRepository.currentUserId()
                val user = socialRepository.fetchUser(profileUserId)
                    ?: throw IllegalStateException("No profile row for $profileUserId")
                val isMine = myId == profileUserId
                // Your own grid shows unlisted and private trips too, labelled as such.
                val trips = tripRepository.fetchTripsByAuthor(profileUserId, includeNonPublic = isMine)
                val followerCount = socialRepository.followerCount(profileUserId)
                val followingCount = socialRepository.followingCount(profileUserId)
                val isFollowing = myId != null && myId != profileUserId &&
                    socialRepository.isFollowing(myId, profileUserId)
                val savedTrips = if (isMine) engagementRepository.fetchSavedTrips(profileUserId) else emptyList()
                val drafts = if (isMine) tripRepository.fetchDrafts(profileUserId) else emptyList()
                val liveTrips = if (isMine) tripRepository.fetchLiveTrips(profileUserId) else emptyList()

                _state.update {
                    it.copy(
                        isLoading = false,
                        profileUser = user,
                        isOwnProfile = myId == profileUserId,
                        isFollowing = isFollowing,
                        followerCount = followerCount,
                        followingCount = followingCount,
                        trips = trips,
                        savedTrips = savedTrips,
                        drafts = drafts,
                        liveTrips = liveTrips
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load profile $profileUserId", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load this profile.") }
                return@launch
            }
            loadVisits(_state.value.trips)
        }
    }

    private fun loadVisits(trips: List<Trip>) {
        visitsJob?.cancel()
        visitsJob = viewModelScope.launch {
            _state.update { it.copy(isVisitsLoading = true) }
            val visits = try {
                computeCountryVisits(trips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The map is a nice-to-have; an empty one beats an error screen.
                Log.w(TAG, "Couldn't work out visited countries", e)
                emptyList()
            }
            _state.update { it.copy(isVisitsLoading = false, countryVisits = visits) }
        }
    }

    /** Every stop, not just each trip's first, so a Rome-Florence-Venice trip shows all three cities. */
    private suspend fun computeCountryVisits(trips: List<Trip>): List<CountryVisit> =
        CityAggregator.aggregate(tripRepository.fetchStopPointsFor(trips), geocodingService)

    fun toggleFollow() {
        val myId = authRepository.currentUserId() ?: return
        val targetId = _state.value.profileUser?.id ?: return
        val wasFollowing = _state.value.isFollowing

        viewModelScope.launch {
            try {
                if (wasFollowing) {
                    socialRepository.unfollow(myId, targetId)
                    _state.update {
                        it.copy(isFollowing = false, followerCount = (it.followerCount - 1).coerceAtLeast(0))
                    }
                } else {
                    socialRepository.follow(myId, targetId)
                    runCatching { notificationRepository.notifyFollow(actorId = myId, recipientId = targetId) }
                    _state.update { it.copy(isFollowing = true, followerCount = it.followerCount + 1) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Follow toggle failed", e)
                _state.update {
                    // Neutral on purpose: a follow refused because of a block must
                    // read exactly like any other failure.
                    it.copy(actionError = if (wasFollowing) "Couldn't unfollow this account." else "Couldn't follow this account.")
                }
            }
        }
    }

    fun deleteDraft(tripId: String) {
        _state.update { state -> state.copy(drafts = state.drafts.filterNot { it.id == tripId }) }
        viewModelScope.launch {
            runCatching { tripRepository.deleteTrip(tripId) }
                .onFailure {
                    Log.w(TAG, "Couldn't delete draft $tripId", it)
                    _state.update { it.copy(actionError = "Couldn't delete that draft.") }
                }
        }
    }

    companion object {
        private const val TAG = "ProfileViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val client = SupabaseProvider.client(appContext)
                ProfileViewModel(
                    authRepository = AuthRepository(client),
                    socialRepository = SocialRepository(client),
                    tripRepository = TripRepository(client),
                    engagementRepository = EngagementRepository(client),
                    notificationRepository = NotificationRepository(client),
                    geocodingService = GeocodingService(appContext)
                )
            }
        }
    }
}
