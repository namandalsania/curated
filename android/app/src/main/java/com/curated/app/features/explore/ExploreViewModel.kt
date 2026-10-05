package com.curated.app.features.explore

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.CommentRepository
import com.curated.app.core.data.EngagementRepository
import com.curated.app.core.data.LikeSummary
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.data.TripCompleteness
import com.curated.app.core.data.TripMapPin
import com.curated.app.core.data.TripRepository
import com.curated.app.core.model.BudgetTag
import com.curated.app.core.model.SeasonTag
import com.curated.app.core.model.Trip
import com.curated.app.features.home.FeedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 350L
private const val MAX_SUGGESTIONS = 6

data class ExploreUiState(
    val isLoading: Boolean = false,
    /** Pins load after the list is ready, so they can lag behind it. */
    val isMapLoading: Boolean = false,
    val viewMode: ExploreViewMode = ExploreViewMode.MAP,
    val searchQuery: String = "",
    val filters: ExploreFilters = ExploreFilters(),
    /** One pin per trip (its first stop), in the same order as [listItems]. */
    val tripPins: List<TripMapPin> = emptyList(),
    /**
     * The search text and filters [tripPins] were loaded for (see [resultsKey]).
     * The map refits when this changes, not on a reload of the same results.
     */
    val pinsKey: String? = null,
    val listItems: List<FeedItem> = emptyList(),
    val destinationSuggestions: List<String> = emptyList(),
    val error: String? = null
)

class ExploreViewModel(
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val tripRepository: TripRepository,
    private val engagementRepository: EngagementRepository,
    private val commentRepository: CommentRepository,
    private val filterStore: ExploreFilterStore
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState(filters = filterStore.load()))
    val state: StateFlow<ExploreUiState> = _state

    private var searchJob: Job? = null

    fun refresh() = runSearchNow()

    fun setViewMode(mode: ExploreViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch()
        }
    }

    fun selectDestinationSuggestion(destination: String) {
        _state.update { it.copy(searchQuery = destination, viewMode = ExploreViewMode.LIST) }
        runSearchNow()
    }

    fun setTripLength(length: TripLength?) = updateFilters { it.copy(tripLength = length) }
    fun setBudgetTag(tag: BudgetTag?) = updateFilters { it.copy(budgetTag = tag) }
    fun setSeasonTag(tag: SeasonTag?) = updateFilters { it.copy(seasonTag = tag) }
    fun setFollowScope(scope: FollowScope) = updateFilters { it.copy(followScope = scope) }
    fun clearFilters() = updateFilters { ExploreFilters() }

    private fun updateFilters(transform: (ExploreFilters) -> ExploreFilters) {
        val updated = transform(_state.value.filters)
        _state.update { it.copy(filters = updated) }
        filterStore.save(updated)
        runSearchNow()
    }

    private fun runSearchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch() }
    }

    private suspend fun performSearch() {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val myId = authRepository.currentUserId()
            val filters = _state.value.filters
            val query = _state.value.searchQuery.trim()
            val key = filters.resultsKey(query)

            val authorIds = if (filters.followScope == FollowScope.FOLLOWING) {
                myId?.let { socialRepository.fetchFollowingIds(it) } ?: emptyList()
            } else {
                null
            }

            if (filters.followScope == FollowScope.FOLLOWING && authorIds.isNullOrEmpty()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isMapLoading = false,
                        tripPins = emptyList(),
                        pinsKey = key,
                        listItems = emptyList(),
                        destinationSuggestions = emptyList()
                    )
                }
                return
            }

            val candidates = tripRepository.searchTrips(filters.toSearchParams(query, authorIds))
            val lengthFiltered = candidates.filter { filters.keeps(it) }

            val suggestions = if (query.isNotBlank()) {
                lengthFiltered.map { it.destination }.distinct().take(MAX_SUGGESTIONS)
            } else {
                emptyList()
            }

            val tripIds = lengthFiltered.map { it.id }
            val authors = socialRepository.fetchUsers(lengthFiltered.map { it.authorId }.distinct()).associateBy { it.id }
            val completeness = tripRepository.fetchTripCompleteness(tripIds)
            val likeSummaries = myId?.let { engagementRepository.fetchLikeSummaries(tripIds, it) } ?: emptyMap()
            val savedIds = myId?.let { engagementRepository.fetchSavedTrips(it).map { trip -> trip.id }.toSet() } ?: emptySet()
            val commentCounts = runCatching { commentRepository.countsForTrips(tripIds) }.getOrDefault(emptyMap())

            val ranked = lengthFiltered.sortedByDescending { trip ->
                rankingScore(trip, completeness[trip.id] ?: TripCompleteness(0, 0))
            }

            val listItems = ranked.map { trip ->
                FeedItem(
                    trip = trip.copy(author = authors[trip.authorId]),
                    stopCount = completeness[trip.id]?.stopCount ?: 0,
                    likeSummary = likeSummaries[trip.id] ?: LikeSummary(0, false),
                    isSaved = trip.id in savedIds,
                    commentCount = commentCounts[trip.id] ?: 0,
                    stopNames = completeness[trip.id]?.stopNames.orEmpty()
                )
            }

            // Publish the list first; the pins need another query.
            _state.update {
                it.copy(
                    isLoading = false,
                    isMapLoading = true,
                    listItems = listItems,
                    destinationSuggestions = suggestions
                )
            }

            val tripPins = tripRepository.fetchTripMapPinsFor(ranked)
            _state.update { it.copy(isMapLoading = false, tripPins = tripPins, pinsKey = key) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, isMapLoading = false, error = e.message ?: "Failed to load trips") }
        }
    }

    fun toggleLike(tripId: String) {
        val myId = authRepository.currentUserId() ?: return
        val current = _state.value.listItems.find { it.trip.id == tripId } ?: return
        val wasLiked = current.likeSummary.likedByMe

        applyListUpdate(tripId) { item ->
            item.copy(
                likeSummary = item.likeSummary.copy(
                    likedByMe = !wasLiked,
                    likeCount = item.likeSummary.likeCount + if (wasLiked) -1 else 1
                )
            )
        }

        viewModelScope.launch {
            try {
                if (wasLiked) engagementRepository.unlike(myId, tripId) else engagementRepository.like(myId, tripId)
            } catch (e: Exception) {
                applyListUpdate(tripId) { item ->
                    item.copy(
                        likeSummary = item.likeSummary.copy(
                            likedByMe = wasLiked,
                            likeCount = item.likeSummary.likeCount + if (wasLiked) 1 else -1
                        )
                    )
                }
            }
        }
    }

    fun toggleSave(tripId: String) {
        val myId = authRepository.currentUserId() ?: return
        val current = _state.value.listItems.find { it.trip.id == tripId } ?: return
        val wasSaved = current.isSaved

        applyListUpdate(tripId) { it.copy(isSaved = !wasSaved) }

        viewModelScope.launch {
            try {
                if (wasSaved) engagementRepository.unsave(myId, tripId) else engagementRepository.save(myId, tripId)
            } catch (e: Exception) {
                applyListUpdate(tripId) { it.copy(isSaved = wasSaved) }
            }
        }
    }

    private fun applyListUpdate(tripId: String, transform: (FeedItem) -> FeedItem) {
        _state.update { state ->
            state.copy(listItems = state.listItems.map { if (it.trip.id == tripId) transform(it) else it })
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val client = SupabaseProvider.client(appContext)
                ExploreViewModel(
                    authRepository = AuthRepository(client),
                    socialRepository = SocialRepository(client),
                    tripRepository = TripRepository(client),
                    engagementRepository = EngagementRepository(client),
                    commentRepository = CommentRepository(client),
                    filterStore = ExploreFilterStore(appContext)
                )
            }
        }
    }
}
