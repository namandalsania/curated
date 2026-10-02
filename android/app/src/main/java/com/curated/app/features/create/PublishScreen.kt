package com.curated.app.features.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.curated.app.core.format.formatDateRange
import com.curated.app.core.model.TripVisibility
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.features.trip.TripVisibilityPicker

@Composable
fun PublishScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onPublished: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.publishedTrip) {
        state.publishedTrip?.let { trip ->
            viewModel.reset()
            onPublished(trip.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Publish")
                        Text(
                            "Last look before it goes up",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            HairlineCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        state.title.ifBlank { "Untitled trip" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        state.destination,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val start = state.startDate
                    val end = state.endDate
                    if (start != null && end != null) {
                        Text(
                            formatDateRange(start, end),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${if (state.stopCount == 1) "1 place" else "${state.stopCount} places"} across " +
                            if (state.dayCount == 1) "1 day" else "${state.dayCount} days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TripVisibilityPicker(
                selected = state.visibility,
                onSelect = viewModel::setVisibility,
                enabled = !state.isPublishing
            )

            Text(
                when (state.visibility) {
                    TripVisibility.PUBLIC ->
                        "Publishing puts this on your profile and in the feeds of people who follow you."
                    TripVisibility.UNLISTED ->
                        "Publishing makes it open to anyone you send the link to. It won't show on your profile or in feeds."
                    TripVisibility.PRIVATE ->
                        "Publishing finishes it for you alone. Nobody else can open it."
                } + " You can keep editing it as a draft until then.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (state.isPublishing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                PrimaryButton(
                    onClick = viewModel::publish,
                    enabled = state.canPublish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Publish trip")
                }
            }
        }
    }
}
