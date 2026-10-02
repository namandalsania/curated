package com.curated.app.features.create

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard

/**
 * Where a new trip starts: two cards and nothing else. Each one goes straight
 * into its own path - there's no shared form behind them.
 *
 * "I already took this trip" opens a picker right here, so the photos come
 * first and the details are worked out from them. It's the system file picker
 * rather than the Photo Picker: the Photo Picker zeroes every photo's GPS, and
 * the destination and stops are built from it. On API 29+ it asks for
 * ACCESS_MEDIA_LOCATION first, which the file picker needs to keep the GPS.
 * The picker opens either way - a refusal only costs the location.
 */
@Composable
fun NewTripScreen(
    onTravelingNow: () -> Unit,
    onPhotosPicked: (List<Uri>) -> Unit
) {
    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        // Backing out of the picker leaves you here to choose again.
        if (uris.isNotEmpty()) onPhotosPicked(uris)
    }

    val context = LocalContext.current
    val launchPicker = {
        pickPhotos.launch(arrayOf("image/*"))
    }
    val requestMediaLocation = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { launchPicker() }

    NewTripChoices(
        onTravelingNow = onTravelingNow,
        onAlreadyTook = {
            val needsMediaLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            if (needsMediaLocation) {
                requestMediaLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else {
                launchPicker()
            }
        }
    )
}

@Composable
private fun NewTripChoices(onTravelingNow: () -> Unit, onAlreadyTook: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ChoiceCard(
            icon = Icons.Outlined.Sensors,
            title = "I'm traveling now",
            subtitle = "Post today, one day at a time",
            onClick = onTravelingNow
        )
        ChoiceCard(
            icon = Icons.Outlined.PhotoLibrary,
            title = "I already took this trip",
            subtitle = "Build it from your photos",
            onClick = onAlreadyTook
        )
    }
}

/** Half the screen each: a big, obvious target for the one decision on it. */
@Composable
private fun ColumnScope.ChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    HairlineCard(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.md)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun NewTripChoicesPreview() {
    CuratedTheme {
        NewTripChoices(onTravelingNow = {}, onAlreadyTook = {})
    }
}
