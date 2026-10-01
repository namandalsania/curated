package com.curated.app.core.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.curated.app.core.data.StopPoint
import com.curated.app.core.geocode.CountryRef
import com.curated.app.core.geocode.GeocodingService
import com.curated.app.core.geocode.PlaceRef
import com.curated.app.core.util.haversineMeters
import com.google.android.gms.maps.model.LatLng

/** One country's worth of trips, aggregated for display as a single sized pin. */
data class CountryVisit(
    /** Display name, in the device's language. */
    val country: String,
    /** ISO 3166-1 alpha-2, when the geocoder gave one; what the world map fills by. */
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
    val tripCount: Int,
    /** Only filled by [CityAggregator] (the profile); Explore works at country level. */
    val cities: List<CityVisit> = emptyList()
)

/** One city within a [CountryVisit]: where the stops actually were. */
data class CityVisit(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val tripCount: Int
)

/**
 * Groups every stop of a set of trips into cities, and cities into countries.
 *
 * Geocoding each stop would be a network call per stop - hundreds for an
 * active profile - so stops are first merged into small areas (every stop
 * within [AREA_RADIUS_METERS] of an area's first stop) and only each area is
 * looked up. Areas that come back as the same city merge, since a big city
 * spans several.
 */
object CityAggregator {
    private const val AREA_RADIUS_METERS = 8_000.0

    suspend fun aggregate(stops: List<StopPoint>, geocodingService: GeocodingService): List<CountryVisit> {
        if (stops.isEmpty()) return emptyList()

        class Area(val anchorLatitude: Double, val anchorLongitude: Double) {
            val stops = mutableListOf<StopPoint>()
        }
        val areas = mutableListOf<Area>()
        for (stop in stops) {
            val area = areas.firstOrNull {
                haversineMeters(it.anchorLatitude, it.anchorLongitude, stop.latitude, stop.longitude) <= AREA_RADIUS_METERS
            } ?: Area(stop.latitude, stop.longitude).also { areas += it }
            area.stops += stop
        }

        class LocatedArea(val place: PlaceRef, val stops: List<StopPoint>)
        val located = areas.mapNotNull { area ->
            geocodingService.reverseGeocodePlace(
                latitude = area.stops.map { it.latitude }.average(),
                longitude = area.stops.map { it.longitude }.average()
            )?.let { LocatedArea(it, area.stops) }
        }

        return located.groupBy { it.place.country.code ?: it.place.country.name }.map { (_, inCountry) ->
            val countryStops = inCountry.flatMap { it.stops }
            val cities = inCountry
                .mapNotNull { area -> area.place.city?.let { it to area.stops } }
                .groupBy({ it.first }, { it.second })
                .map { (name, stopGroups) ->
                    val cityStops = stopGroups.flatten()
                    CityVisit(
                        name = name,
                        latitude = cityStops.map { it.latitude }.average(),
                        longitude = cityStops.map { it.longitude }.average(),
                        tripCount = cityStops.distinctBy { it.tripId }.size
                    )
                }
                .sortedWith(compareByDescending<CityVisit> { it.tripCount }.thenBy { it.name })
            val place = inCountry.first().place.country
            CountryVisit(
                country = place.name,
                countryCode = place.code,
                latitude = countryStops.map { it.latitude }.average(),
                longitude = countryStops.map { it.longitude }.average(),
                tripCount = countryStops.distinctBy { it.tripId }.size,
                cities = cities
            )
        }
    }
}

/**
 * Groups arbitrary (lat, lng) points — one per trip — into country-level
 * visits by reverse-geocoding each, then averaging position and counting
 * per country. Shared by Profile's "visited places" map and Explore's map.
 */
object CountryAggregator {
    suspend fun aggregate(
        points: List<Pair<Double, Double>>,
        geocodingService: GeocodingService
    ): List<CountryVisit> {
        if (points.isEmpty()) return emptyList()

        data class LocatedPoint(val country: CountryRef, val latitude: Double, val longitude: Double)

        val located = points.mapNotNull { (latitude, longitude) ->
            geocodingService.reverseGeocodeCountry(latitude, longitude)?.let { country ->
                LocatedPoint(country, latitude, longitude)
            }
        }

        // Group on the ISO code where there is one: names can differ for the
        // same country (locale, "USA" vs "United States"), codes don't.
        return located.groupBy { it.country.code ?: it.country.name }.map { (_, group) ->
            CountryVisit(
                country = group.first().country.name,
                countryCode = group.first().country.code,
                latitude = group.map { it.latitude }.average(),
                longitude = group.map { it.longitude }.average(),
                tripCount = group.size
            )
        }
    }
}

fun CountryVisit.toMapPin() = MapPin(
    id = country,
    position = LatLng(latitude, longitude),
    title = country,
    weight = tripCount
)

/** A small filled circle sized by trip count — the visual language for a "country pin." */
@Composable
fun CountryPinBadge(tripCount: Int) {
    val diameter = (28 + (tripCount.coerceAtMost(20) * 2)).dp
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            // Cream ring so the pin separates from the similarly warm map.
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tripCount.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
