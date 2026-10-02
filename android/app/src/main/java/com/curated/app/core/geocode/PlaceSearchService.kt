package com.curated.app.core.geocode

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.curated.app.core.model.StopCategory
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One thing a search turned up, before its details are fetched. */
data class PlaceSuggestion(
    val id: String,
    val title: String,
    val subtitle: String?,
    /** Set when this came from the address geocoder, which already knows everything. */
    val resolved: PlaceDetails? = null
)

data class PlaceDetails(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: StopCategory
)

/**
 * Searching for real places - "Costco Hawaii", "fabrica coffee" - through
 * Google Places, which knows businesses.
 *
 * [GeocodingService] only resolves addresses and localities, so it answers
 * "Costco Hawaii" with "Hawaii". It stays as the fallback for when the Places
 * API isn't enabled on the project or a request fails, so search degrades to
 * what it used to do instead of breaking.
 */
class PlaceSearchService(context: Context) {

    private val appContext = context.applicationContext
    private val geocodingService = GeocodingService(appContext)

    /** Null when Places can't be set up; everything then falls back to the geocoder. */
    private val client: PlacesClient? by lazy { createClient() }

    /** Ties the typing and the pick together so Google bills them as one search. */
    private var sessionToken: AutocompleteSessionToken? = null

    val isBusinessSearchAvailable: Boolean get() = client != null

    private fun createClient(): PlacesClient? = try {
        val apiKey = appContext.packageManager
            .getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getString("com.google.android.geo.API_KEY")
        when {
            apiKey.isNullOrBlank() -> {
                Log.w(TAG, "No Maps API key in the manifest; place search falls back to the geocoder")
                null
            }
            else -> {
                if (!Places.isInitialized()) {
                    Places.initializeWithNewPlacesApiEnabled(appContext, apiKey)
                }
                Places.createClient(appContext)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Places isn't available; falling back to the geocoder", e)
        null
    }

    /**
     * Suggestions as someone types. Falls back to address search when Places can't answer.
     *
     * [regionsOnly] keeps to cities, regions and countries - what a trip's
     * destination is - and leaves out businesses and addresses.
     */
    suspend fun search(query: String, regionsOnly: Boolean = false): List<PlaceSuggestion> {
        if (query.isBlank()) return emptyList()
        val places = client
        if (places != null) {
            val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setSessionToken(token)
                .apply { if (regionsOnly) setTypesFilter(listOf(REGIONS)) }
                .build()
            val predictions = runCatching { places.findAutocompletePredictions(request).await() }
                .onFailure { error ->
                    // Typing quickly cancels the previous search; that isn't a failure.
                    if (error !is CancellationException) {
                        Log.w(TAG, "Places autocomplete failed; using the geocoder", error)
                    }
                }
                .getOrNull()
                ?.autocompletePredictions
            if (predictions != null) {
                return predictions.map {
                    PlaceSuggestion(
                        id = it.placeId,
                        title = it.getPrimaryText(null).toString(),
                        subtitle = it.getSecondaryText(null).toString().ifBlank { null }
                    )
                }
            }
        }
        return geocodingService.searchPlace(query).map { result ->
            PlaceSuggestion(
                id = "geo:${result.latitude},${result.longitude}",
                title = result.name,
                subtitle = result.address.ifBlank { null },
                resolved = PlaceDetails(
                    name = result.name,
                    address = result.address.ifBlank { null },
                    latitude = result.latitude,
                    longitude = result.longitude,
                    category = StopCategory.OTHER
                )
            )
        }
    }

    /** Where a suggestion actually is, and what kind of place it is. */
    suspend fun details(suggestion: PlaceSuggestion): PlaceDetails? {
        suggestion.resolved?.let { return it }
        val places = client ?: return null
        val request = FetchPlaceRequest.builder(
            suggestion.id,
            listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION, Place.Field.TYPES)
        ).apply { sessionToken?.let { setSessionToken(it) } }.build()

        val place = runCatching { places.fetchPlace(request).await() }
            .onFailure { Log.w(TAG, "Couldn't fetch place ${suggestion.id}", it) }
            .getOrNull()
            ?.place
            ?: return null
        // A session ends with the pick; the next search starts a new one.
        sessionToken = null

        val location = place.location ?: return null
        return PlaceDetails(
            name = place.displayName ?: suggestion.title,
            address = place.formattedAddress ?: suggestion.subtitle,
            latitude = location.latitude,
            longitude = location.longitude,
            category = categoryOf(place.placeTypes.orEmpty())
        )
    }

    companion object {
        private const val TAG = "PlaceSearchService"

        /** Places' collection of localities, administrative areas and countries. */
        private const val REGIONS = "(regions)"
    }
}

/** Google's place types, mapped onto the five kinds a stop can be. */
internal fun categoryOf(types: List<String>): StopCategory {
    val lower = types.map { it.lowercase() }
    return when {
        lower.any { it in FOOD_TYPES } -> StopCategory.FOOD
        lower.any { it in STAY_TYPES } -> StopCategory.HOTEL
        lower.any { it in TRANSPORT_TYPES } -> StopCategory.TRANSPORT
        lower.any { it in SIGHT_TYPES } -> StopCategory.SIGHT
        else -> StopCategory.OTHER
    }
}

private val FOOD_TYPES = setOf(
    "restaurant", "cafe", "coffee_shop", "bakery", "bar", "meal_takeaway", "meal_delivery",
    "ice_cream_shop", "food", "pub", "wine_bar", "diner", "fast_food_restaurant"
)
private val STAY_TYPES = setOf("lodging", "hotel", "motel", "hostel", "guest_house", "resort_hotel", "bed_and_breakfast")
private val TRANSPORT_TYPES = setOf(
    "airport", "train_station", "subway_station", "bus_station", "transit_station",
    "light_rail_station", "ferry_terminal", "taxi_stand", "car_rental"
)
private val SIGHT_TYPES = setOf(
    "tourist_attraction", "museum", "art_gallery", "park", "church", "mosque", "synagogue",
    "hindu_temple", "zoo", "aquarium", "amusement_park", "historical_landmark", "monument",
    "national_park", "beach", "castle", "cultural_landmark", "observation_deck"
)

/** Bridges a Play Services [Task] into a coroutine, without pulling in another library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
