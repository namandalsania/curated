package com.curated.app.features.profile

import com.curated.app.core.map.CityVisit
import com.curated.app.core.map.CountryVisit
import kotlinx.datetime.LocalDate

// Made-up visits for the countries card's previews, the debug preview screen
// and tests - never anyone's real travel, and nothing from the database.

private data class SampleCountry(val code: String, val name: String, val lat: Double, val lng: Double, val cities: List<String>)

private val sampleCountries = listOf(
    SampleCountry("PT", "Portugal", 38.72, -9.14, listOf("Lisbon", "Porto", "Sintra")),
    SampleCountry("IT", "Italy", 41.90, 12.50, listOf("Rome", "Florence", "Venice", "Milan")),
    SampleCountry("JP", "Japan", 35.68, 139.69, listOf("Tokyo", "Kyoto")),
    SampleCountry("MX", "Mexico", 19.43, -99.13, listOf("Ciudad de México", "Oaxaca")),
    SampleCountry("TH", "Thailand", 13.76, 100.50, listOf("Bangkok")),
    SampleCountry("FR", "France", 48.86, 2.35, listOf("Paris", "Lyon")),
    SampleCountry("ES", "Spain", 40.42, -3.70, listOf("Madrid", "Barcelona", "Seville")),
    SampleCountry("US", "United States", 40.71, -74.01, listOf("New York", "San Francisco")),
    SampleCountry("PE", "Peru", -13.53, -71.97, listOf("Cusco")),
    SampleCountry("MA", "Morocco", 31.63, -7.99, listOf("Marrakesh")),
    SampleCountry("VN", "Vietnam", 21.03, 105.85, listOf("Hanoi", "Hội An")),
    SampleCountry("AU", "Australia", -33.87, 151.21, listOf("Sydney")),
    SampleCountry("GR", "Greece", 37.98, 23.73, listOf("Athens")),
    SampleCountry("DE", "Germany", 52.52, 13.40, listOf("Berlin")),
    SampleCountry("NL", "Netherlands", 52.37, 4.90, listOf("Amsterdam")),
    SampleCountry("AR", "Argentina", -34.60, -58.38, listOf("Buenos Aires")),
    SampleCountry("ZA", "South Africa", -33.92, 18.42, listOf("Cape Town")),
    SampleCountry("IN", "India", 28.61, 77.21, listOf("Delhi", "Jaipur")),
    SampleCountry("KE", "Kenya", -1.29, 36.82, listOf("Nairobi")),
    SampleCountry("NZ", "New Zealand", -36.85, 174.76, listOf("Auckland")),
    SampleCountry("CA", "Canada", 49.28, -123.12, listOf("Vancouver")),
    SampleCountry("CL", "Chile", -33.45, -70.67, listOf("Santiago")),
    SampleCountry("TR", "Turkey", 41.01, 28.98, listOf("Istanbul")),
    SampleCountry("EG", "Egypt", 30.04, 31.24, listOf("Cairo")),
    SampleCountry("KR", "South Korea", 37.57, 126.98, listOf("Seoul")),
    SampleCountry("ID", "Indonesia", -8.65, 115.22, listOf("Denpasar")),
    SampleCountry("CO", "Colombia", 4.71, -74.07, listOf("Bogotá")),
    SampleCountry("IS", "Iceland", 64.15, -21.94, listOf("Reykjavík")),
    SampleCountry("NO", "Norway", 59.91, 10.75, listOf("Oslo")),
    SampleCountry("CZ", "Czechia", 50.08, 14.44, listOf("Prague")),
    SampleCountry("HR", "Croatia", 42.65, 18.09, listOf("Dubrovnik")),
    SampleCountry("BR", "Brazil", -22.91, -43.17, listOf("Rio de Janeiro")),
    SampleCountry("CU", "Cuba", 23.11, -82.37, listOf("Havana")),
    SampleCountry("TZ", "Tanzania", -6.16, 39.19, listOf("Zanzibar City")),
    SampleCountry("NP", "Nepal", 27.72, 85.32, listOf("Kathmandu")),
    SampleCountry("FJ", "Fiji", -17.71, 178.07, emptyList()),
    SampleCountry("IE", "Ireland", 53.35, -6.26, listOf("Dublin")),
    SampleCountry("HU", "Hungary", 47.50, 19.04, listOf("Budapest")),
    SampleCountry("JO", "Jordan", 30.33, 35.44, listOf("Petra")),
    SampleCountry("GT", "Guatemala", 14.56, -90.73, listOf("Antigua Guatemala")),
    SampleCountry("PH", "Philippines", 14.60, 120.98, listOf("Manila")),
    SampleCountry("SN", "Senegal", 14.72, -17.47, listOf("Dakar")),
    SampleCountry("BO", "Bolivia", -16.50, -68.15, listOf("La Paz")),
    SampleCountry("MC", "Monaco", 43.74, 7.42, listOf("Monaco")),
    SampleCountry("SG", "Singapore", 1.35, 103.82, listOf("Singapore"))
)

/** How many made-up countries there are to ask for. */
internal val SAMPLE_COUNTRY_MAX = sampleCountries.size

/** Swapped in for the first country by [sampleCountryVisits] with `longName`. */
internal const val SAMPLE_LONG_COUNTRY_NAME = "Saint Vincent and the Grenadines"

/**
 * [count] made-up countries (at most [SAMPLE_COUNTRY_MAX]), with trip counts
 * from 1 to 4 and a spread of trip dates so ranking has ties to break.
 * [longName] makes the first one [SAMPLE_LONG_COUNTRY_NAME].
 */
internal fun sampleCountryVisits(count: Int, longName: Boolean = false): List<CountryVisit> =
    sampleCountries.take(count).mapIndexed { index, country ->
        val sample = if (longName && index == 0) {
            SampleCountry("VC", SAMPLE_LONG_COUNTRY_NAME, 13.16, -61.23, listOf("Kingstown"))
        } else {
            country
        }
        val trips = 4 - index % 4
        CountryVisit(
            country = sample.name,
            countryCode = sample.code,
            latitude = sample.lat,
            longitude = sample.lng,
            tripCount = trips,
            cities = sample.cities.map { CityVisit(it, sample.lat, sample.lng, tripCount = 1) },
            lastTripEnd = LocalDate(2026, 1 + index % 9, 1 + index % 27)
        )
    }
