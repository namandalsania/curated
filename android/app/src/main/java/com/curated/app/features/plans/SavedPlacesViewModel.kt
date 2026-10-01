package com.curated.app.features.plans

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.SavedPlacesRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.SavedPlace
import com.curated.app.core.model.StopCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedPlacesState(
    val isLoading: Boolean = true,
    val places: List<SavedPlace> = emptyList(),
    /** Null shows every category. */
    val category: StopCategory? = null,
    val error: String? = null,
    val actionError: String? = null
) {
    val visiblePlaces: List<SavedPlace> get() = places.filter { category == null || it.category == category }

    /** Categories that actually occur, so filter chips never lead to an empty list. */
    val categories: List<StopCategory> get() = StopCategory.entries.filter { c -> places.any { it.category == c } }
}

class SavedPlacesViewModel(
    private val authRepository: AuthRepository,
    private val savedPlacesRepository: SavedPlacesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SavedPlacesState())
    val state: StateFlow<SavedPlacesState> = _state

    fun load() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.places.isEmpty(), error = null) }
            try {
                val places = savedPlacesRepository.fetchAll(userId)
                _state.update { it.copy(isLoading = false, places = places) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load saved places", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load your saved places.") }
            }
        }
    }

    fun setCategory(category: StopCategory?) = _state.update { it.copy(category = category) }

    /** Plans keep their own copy of each place, so removing one here never touches a plan. */
    fun remove(place: SavedPlace) {
        _state.update { it.copy(places = it.places - place, actionError = null) }
        viewModelScope.launch {
            try {
                savedPlacesRepository.delete(place.id)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove saved place ${place.id}", e)
                _state.update { state ->
                    state.copy(
                        places = (state.places + place).sortedByDescending { it.createdAt },
                        actionError = "Couldn't remove ${place.name}. Try again."
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "SavedPlacesViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                SavedPlacesViewModel(AuthRepository(client), SavedPlacesRepository(client))
            }
        }
    }
}
