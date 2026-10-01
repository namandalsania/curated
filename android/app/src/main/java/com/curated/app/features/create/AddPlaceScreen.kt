package com.curated.app.features.create

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.curated.app.core.geocode.PlaceSearchService
import com.curated.app.core.geocode.PlaceSuggestion
import com.curated.app.core.model.StopCategory
import com.curated.app.core.map.CuratedPlacePin
import com.curated.app.core.map.rememberCuratedMapProperties
import com.curated.app.core.map.rememberCuratedMapUiSettings
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Search for a place or drop a pin, then add it to a particular day. */
@Composable
fun AddPlaceScreen(
    viewModel: CreateTripViewModel,
    dayIndex: Int,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val placeSearch = remember { PlaceSearchService(context) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(StopCategory.OTHER) }
    var pin by remember { mutableStateOf<LatLng?>(null) }

    // Search as you type, once the typing pauses.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        isSearching = true
        results = placeSearch.search(query)
        isSearching = false
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a place to Day $dayIndex") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search for a place") },
                placeholder = { Text("Costco Hawaii") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                trailingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            if (isSearching) {
                Text(
                    "Searching…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            results.take(6).forEach { suggestion ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val details = placeSearch.details(suggestion) ?: return@launch
                                val location = LatLng(details.latitude, details.longitude)
                                pin = location
                                placeName = details.name
                                category = details.category
                                query = details.name
                                results = emptyList()
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(location, 16f))
                            }
                        }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    suggestion.subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HairlineDivider(modifier = Modifier.padding(top = Spacing.sm))
                }
            }

            if (!placeSearch.isBusinessSearchAvailable && query.isNotBlank()) {
                Text(
                    "Searching addresses only - business search needs the Places API turned on for this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Or tap the map to drop a pin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(CuratedCornerRadius))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CuratedCornerRadius))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = rememberCuratedMapProperties(),
                    uiSettings = rememberCuratedMapUiSettings(),
                    onMapClick = { latLng -> pin = latLng }
                ) {
                    pin?.let { location ->
                        val markerState = remember(location) { MarkerState(position = location) }
                        MarkerComposable(location, state = markerState) { CuratedPlacePin() }
                    }
                }
            }

            OutlinedTextField(
                value = placeName,
                onValueChange = { placeName = it },
                label = { Text("Place name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            PrimaryButton(
                onClick = {
                    val location = pin ?: return@PrimaryButton
                    viewModel.addPlace(
                        dayIndex = dayIndex,
                        placeName = placeName.trim().ifEmpty { "Untitled place" },
                        latitude = location.latitude,
                        longitude = location.longitude,
                        category = category
                    )
                    onDone()
                },
                enabled = pin != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (pin == null) "Pick a place first" else "Add to Day $dayIndex")
            }
        }
    }
}
