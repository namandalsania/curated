package com.curated.app.features.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curated.app.core.format.flagEmoji
import com.curated.app.core.format.label
import com.curated.app.core.model.SavedPlace
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.CuratedFilterChip
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineDivider

@Composable
fun SavedPlacesScreen(
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenPlans: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SavedPlacesViewModel = viewModel(factory = SavedPlacesViewModel.factory(context))
    val state by viewModel.state.collectAsState()

    // Reload on every visit: places get saved from trips while this is in the back stack.
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved places") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = onOpenPlans) { Text("My plans") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.places.isEmpty() -> EmptyState(
                    headline = "No saved places yet",
                    body = "Tap the bookmark on any place in a trip - a stay, a castle, a pizza place - and it lands here, ready to plan with.",
                    icon = Icons.Outlined.BookmarkBorder,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
                    if (state.categories.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                item {
                                    CuratedFilterChip(
                                        selected = state.category == null,
                                        onClick = { viewModel.setCategory(null) },
                                        label = "All"
                                    )
                                }
                                items(state.categories) { category ->
                                    CuratedFilterChip(
                                        selected = state.category == category,
                                        onClick = { viewModel.setCategory(if (state.category == category) null else category) },
                                        label = category.label()
                                    )
                                }
                            }
                        }
                    }

                    state.actionError?.let { message ->
                        item {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            )
                        }
                    }

                    groupByCountry(state.visiblePlaces).forEach { (country, places) ->
                        item(key = "country-$country") {
                            Text(
                                country,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs)
                            )
                        }
                        items(places, key = { it.id }) { place ->
                            PlaceRow(
                                place = place.toDisplay(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        place.sourceTripId?.let { tripId -> Modifier.clickable { onOpenTrip(tripId) } }
                                            ?: Modifier
                                    )
                                    .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
                            ) {
                                IconButton(onClick = { viewModel.remove(place) }) {
                                    Icon(
                                        Icons.Filled.Bookmark,
                                        contentDescription = "Remove ${place.name} from saved places",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HairlineDivider(modifier = Modifier.padding(start = Spacing.md + 56.dp + Spacing.sm + Spacing.xs))
                        }
                    }
                }
            }
        }
    }
}

/** "🇮🇹 Italy" -> places sorted by city, countries by how much was saved there. */
internal fun groupByCountry(places: List<SavedPlace>): List<Pair<String, List<SavedPlace>>> =
    places
        .groupBy { place ->
            val name = place.countryName ?: "Somewhere"
            val flag = place.countryCode?.let(::flagEmoji)
            if (flag != null) "$flag $name" else name
        }
        .map { (country, inCountry) -> country to inCountry.sortedWith(compareBy({ it.city ?: "" }, { it.name })) }
        .sortedWith(compareByDescending<Pair<String, List<SavedPlace>>> { it.second.size }.thenBy { it.first })
