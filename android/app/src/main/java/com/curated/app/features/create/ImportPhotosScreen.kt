package com.curated.app.features.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton

/**
 * Picks photos and hands them to the draft: each group of photos taken near
 * each other becomes a stop, on the day it was taken.
 */
@Composable
fun ImportPhotosScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var launched by remember { mutableStateOf(false) }

    // Its stops come from where the photos were taken, so this picker keeps their GPS.
    val pickPhotos = rememberGeoPhotoPicker { uris ->
        launched = true
        viewModel.importPhotos(context, uris)
    }

    // Once the import finishes, the itinerary is where the work continues.
    LaunchedEffect(state.isImportingPhotos, launched) {
        if (launched && !state.isImportingPhotos) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "Pick the photos from this trip. We read the time and place each was taken, group them into stops, and put them on the right day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Photos without location data are skipped - you can add those places yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isImportingPhotos) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Reading your photos…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                PrimaryButton(
                    onClick = {
                        pickPhotos()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose photos")
                }
                SecondaryButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip - I'll add places myself")
                }
            }

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
