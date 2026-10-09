package com.curated.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing

// The countries card at the sizes that matter: empty, one, exactly the chip
// limit, one over it, many, and every sample - plus a name too long for a chip.
// All made-up data (VisitedCountriesSamples.kt).

@Composable
private fun CardPreview(count: Int, longName: Boolean = false) {
    CuratedTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            VisitedCountriesCard(
                visits = sampleCountryVisits(count, longName),
                isLoading = false,
                isOwnProfile = true
            )
        }
    }
}

@Preview(name = "0 countries", showBackground = true, widthDp = 390)
@Composable
private fun NoCountriesPreview() = CardPreview(0)

@Preview(name = "1 country", showBackground = true, widthDp = 390)
@Composable
private fun OneCountryPreview() = CardPreview(1)

@Preview(name = "5 countries", showBackground = true, widthDp = 390)
@Composable
private fun FiveCountriesPreview() = CardPreview(5)

@Preview(name = "6 countries", showBackground = true, widthDp = 390)
@Composable
private fun SixCountriesPreview() = CardPreview(6)

@Preview(name = "25 countries", showBackground = true, widthDp = 390)
@Composable
private fun TwentyFiveCountriesPreview() = CardPreview(25)

@Preview(name = "45 countries", showBackground = true, widthDp = 390)
@Composable
private fun FortyFiveCountriesPreview() = CardPreview(45)

@Preview(name = "Long country name", showBackground = true, widthDp = 390)
@Composable
private fun LongNamePreview() = CardPreview(6, longName = true)

@Preview(name = "All countries sheet, 25", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AllCountriesSheetPreview() {
    CuratedTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            AllCountriesSheetContent(
                // No continent table in a preview: a few by hand.
                groups = groupByContinent(
                    sampleCountryVisits(25),
                    mapOf("PT" to "EU", "IT" to "EU", "FR" to "EU", "ES" to "EU", "JP" to "AS", "TH" to "AS", "MX" to "NA", "US" to "NA")
                ),
                total = 25,
                onCountryClick = {}
            )
        }
    }
}
