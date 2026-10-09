package com.curated.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.features.profile.VisitedCountriesCard
import com.curated.app.features.profile.sampleCountryVisits

/**
 * The profile's countries card with made-up visits, to look at sizes no real
 * account has. Debug builds only; extras: `count` (0-45), `longName` (boolean).
 */
class VisitedCountriesPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val count = intent.getIntExtra("count", 6)
        val longName = intent.getBooleanExtra("longName", false)
        setContent {
            CuratedTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                        .padding(Spacing.md)
                ) {
                    Text(
                        "Preview: $count made-up countries" + if (longName) ", long name" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                    VisitedCountriesCard(
                        visits = sampleCountryVisits(count, longName),
                        isLoading = false,
                        isOwnProfile = true
                    )
                }
            }
        }
    }
}
