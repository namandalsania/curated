package com.curated.app.features.profile

import com.curated.app.core.map.CountryVisit
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitedCountriesTest {

    private fun visit(name: String, trips: Int, lastTripEnd: LocalDate? = null, code: String? = null) =
        CountryVisit(country = name, countryCode = code, latitude = 0.0, longitude = 0.0, tripCount = trips, lastTripEnd = lastTripEnd)

    @Test
    fun `chips for 0, 1, 5, 6, 25 and 45 countries`() {
        // count -> (chips shown, "+N more")
        val expected = mapOf(0 to (0 to 0), 1 to (1 to 0), 5 to (5 to 0), 6 to (5 to 1), 25 to (5 to 20), 45 to (5 to 40))
        expected.forEach { (count, want) ->
            val chips = countryChips(sampleCountryVisits(count))
            assertEquals("shown for $count", want.first, chips.shown.size)
            assertEquals("more for $count", want.second, chips.more)
        }
        assertEquals(45, SAMPLE_COUNTRY_MAX)
    }

    @Test
    fun `most trips first, then the most recent trip, then the name`() {
        val ranked = rankCountries(
            listOf(
                visit("Austria", 2, LocalDate(2026, 3, 1)),
                visit("Belgium", 5),
                visit("Chile", 2, LocalDate(2026, 9, 1)),
                visit("Denmark", 2),
                visit("Estonia", 2, LocalDate(2026, 9, 1))
            )
        )
        assertEquals(listOf("Belgium", "Chile", "Estonia", "Austria", "Denmark"), ranked.map { it.country })
    }

    @Test
    fun `the chips are the top of the ranking`() {
        val visits = sampleCountryVisits(25)
        assertEquals(rankCountries(visits).take(5), countryChips(visits).shown)
    }

    @Test
    fun `the sheet groups every country once, bigger continents first, unplaced last`() {
        val visits = listOf(
            visit("Portugal", 1, code = "PT"),
            visit("Italy", 3, code = "IT"),
            visit("Japan", 2, code = "JP"),
            visit("Atlantis", 1, code = "XX"),
            visit("Nowhere", 1)
        )
        val groups = groupByContinent(visits, mapOf("PT" to "EU", "IT" to "EU", "JP" to "AS"))
        assertEquals(listOf("Europe", "Asia", UNPLACED_GROUP_TITLE), groups.map { it.title })
        assertEquals(listOf("Italy", "Portugal"), groups[0].countries.map { it.country })
        assertEquals(visits.map { it.country }.sorted(), groups.flatMap { it.countries }.map { it.country }.sorted())
    }

    @Test
    fun `45 countries all land in the sheet`() {
        val visits = sampleCountryVisits(45)
        val groups = groupByContinent(visits, emptyMap())
        assertEquals(45, groups.sumOf { it.countries.size })
    }

    @Test
    fun `the long-name sample is a real chip candidate`() {
        val visits = sampleCountryVisits(6, longName = true)
        assertTrue(countryChips(visits).shown.any { it.country == SAMPLE_LONG_COUNTRY_NAME })
    }
}
