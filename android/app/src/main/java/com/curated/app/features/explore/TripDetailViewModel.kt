package com.curated.app.features.explore

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.CommentRepository
import com.curated.app.core.data.SavedPlacesRepository
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.data.TripDaySection
import com.curated.app.core.data.TripRepository
import com.curated.app.core.geocode.GeocodingService
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    /** The viewer wrote this trip, so its visibility is theirs to see and change. */
    val isOwner: Boolean = false,
    val days: List<TripDaySection> = emptyList(),
    val error: String? = null,
    /** Stops on this trip the viewer has saved to their places. */
    val savedStopIds: Set<String> = emptySet(),
    /** Comments per stop id, for the count on each place card. */
    val commentCounts: Map<String, Int> = emptyMap(),
    /** One-shot snackbar text; cleared by [TripDetailViewModel.snackbarShown]. */
    val snackbar: TripDetailSnackbar? = null
)

data class TripDetailSnackbar(val text: String, val offerViewSaved: Boolean = false)

class TripDetailViewModel(
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val savedPlacesRepository: SavedPlacesRepository,
    private val commentRepository: CommentRepository,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _state = MutableStateFlow(TripDetailUiState())
    val state: StateFlow<TripDetailUiState> = _state

    fun load(tripId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = tripRepository.fetchTripDetail(tripId)
                val isOwner = detail.trip.authorId == authRepository.currentUserId()
                _state.update { it.copy(isLoading = false, trip = detail.trip, days = detail.days, isOwner = isOwner) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = tripLoadError(e)) }
                return@launch
            }
            loadSavedStops()
            refreshCommentCounts(tripId)
        }
    }

    /** Called again when a comment thread closes, so the counts stay honest. */
    fun refreshCommentCounts(tripId: String) {
        viewModelScope.launch {
            runCatching { commentRepository.countsForTrip(tripId) }
                .onSuccess { counts -> _state.update { it.copy(commentCounts = counts) } }
                .onFailure { Log.w(TAG, "Couldn't load comment counts", it) }
        }
    }

    private suspend fun loadSavedStops() {
        val userId = authRepository.currentUserId() ?: return
        val stopIds = _state.value.days.flatMap { day -> day.stops.map { it.stop.id } }
        // Icons default to "not saved"; a failure here just leaves them that way.
        runCatching { savedPlacesRepository.savedStopIds(userId, stopIds) }
            .onSuccess { saved -> _state.update { it.copy(savedStopIds = saved) } }
            .onFailure { Log.w(TAG, "Couldn't load saved places for this trip", it) }
    }

    fun toggleSave(item: StopWithPhotos) {
        if (item.stop.id in _state.value.savedStopIds) unsave(item.stop.id) else save(item)
    }

    private fun save(item: StopWithPhotos) {
        val userId = authRepository.currentUserId() ?: return
        val trip = _state.value.trip ?: return
        val stopId = item.stop.id
        _state.update { it.copy(savedStopIds = it.savedStopIds + stopId) }
        viewModelScope.launch {
            try {
                // City/country once, now, so Saved places can group without re-geocoding.
                val place = geocodingService.reverseGeocodePlace(item.stop.latitude, item.stop.longitude)
                savedPlacesRepository.save(userId, trip, item, place)
                _state.update { it.copy(snackbar = TripDetailSnackbar("Saved to your places", offerViewSaved = true)) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save stop $stopId", e)
                _state.update {
                    it.copy(savedStopIds = it.savedStopIds - stopId, snackbar = TripDetailSnackbar("Couldn't save that place. Try again."))
                }
            }
        }
    }

    /** Plans keep their own copy of each place, so unsaving never touches a plan. */
    private fun unsave(stopId: String) {
        val userId = authRepository.currentUserId() ?: return
        _state.update { it.copy(savedStopIds = it.savedStopIds - stopId) }
        viewModelScope.launch {
            try {
                savedPlacesRepository.unsaveStop(userId, stopId)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't unsave stop $stopId", e)
                _state.update {
                    it.copy(savedStopIds = it.savedStopIds + stopId, snackbar = TripDetailSnackbar("Couldn't remove that place. Try again."))
                }
            }
        }
    }

    /** Owner only. Shown straight away, and put back if the save fails. */
    fun setVisibility(visibility: TripVisibility) {
        val trip = _state.value.trip ?: return
        if (!_state.value.isOwner || trip.visibility == visibility) return
        val previous = trip.visibility
        _state.update { it.copy(trip = trip.copy(visibility = visibility)) }
        viewModelScope.launch {
            try {
                tripRepository.updateTripVisibility(trip.id, visibility)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't change visibility of ${trip.id}", e)
                _state.update {
                    it.copy(
                        trip = it.trip?.copy(visibility = previous),
                        snackbar = TripDetailSnackbar("Couldn't change who can see this trip. Try again.")
                    )
                }
            }
        }
    }

    fun snackbarShown() = _state.update { it.copy(snackbar = null) }

    companion object {
        private const val TAG = "TripDetailViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val client = SupabaseProvider.client(appContext)
                TripDetailViewModel(
                    tripRepository = TripRepository(client),
                    authRepository = AuthRepository(client),
                    savedPlacesRepository = SavedPlacesRepository(client),
                    commentRepository = CommentRepository(client),
                    geocodingService = GeocodingService(appContext)
                )
            }
        }
    }
}

/**
 * What to say when a trip won't load. A trip the database no longer returns -
 * deleted, made private, or its author blocked either way - comes back as an
 * empty result, which used to surface as "List is empty". It gets one neutral
 * message, the same whatever the reason, so it never reveals a block.
 */
internal fun tripLoadError(e: Throwable): String =
    if (e is NoSuchElementException) "This trip isn't available." else "Couldn't load this trip. Check your connection and try again."
