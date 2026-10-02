package com.curated.app.core.geocode

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * A country as the geocoder reports it. [name] is in the device's language
 * ("Japan", "日本"); [code] is the ISO 3166-1 alpha-2 code ("JP"), the same in
 * every locale, so it's what to match on.
 */
data class CountryRef(val code: String?, val name: String)

/** A point's city and country. [city] is null out in the countryside with no locality. */
data class PlaceRef(val country: CountryRef, val city: String?)

data class PlaceResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Thin wrapper around the platform [Geocoder]. It's a network-backed system
 * service outside our control, so every call is defensively caught and
 * degrades to "no suggestion" rather than crashing the create flow.
 */
class GeocodingService(private val context: Context) {

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocation(latitude, longitude, 1)
            results?.firstOrNull()?.let { address ->
                // featureName is often just the house number ("178", "17B"),
                // which names nothing; the street does better.
                address.featureName?.takeIf { it != address.subThoroughfare }
                    ?: address.thoroughfare
                    ?: address.locality
                    ?: address.subAdminArea
                    ?: address.countryName
            }
        }.getOrNull()
    }

    suspend fun reverseGeocodeCountry(latitude: Double, longitude: Double): CountryRef? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let { address ->
                address.countryName?.let { name ->
                    CountryRef(code = address.countryCode?.uppercase(), name = name)
                }
            }
        }.getOrNull()
    }

    /** City and country for a point, from one lookup. */
    suspend fun reverseGeocodePlace(latitude: Double, longitude: Double): PlaceRef? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let { address ->
                address.countryName?.let { name ->
                    PlaceRef(
                        country = CountryRef(code = address.countryCode?.uppercase(), name = name),
                        city = address.locality ?: address.subAdminArea
                    )
                }
            }
        }.getOrNull()
    }

    suspend fun searchPlace(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocationName(query, 5)?.map { address ->
                PlaceResult(
                    name = address.featureName ?: query,
                    address = (0..address.maxAddressLineIndex)
                        .joinToString(", ") { address.getAddressLine(it) },
                    latitude = address.latitude,
                    longitude = address.longitude
                )
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }
}
