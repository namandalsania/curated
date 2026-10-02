package com.curated.app.features.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.curated.app.core.geocode.PlaceSearchService
import com.curated.app.core.geocode.PlaceSuggestion
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import kotlinx.coroutines.delay

/**
 * "I'm traveling now": one question, then straight into posting Day 1. The
 * trip starts today and is titled for you; both change later, not here.
 */
@Composable
fun LiveStartScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onStarted: (String) -> Unit
) {
    val context = LocalContext.current
    val placeSearch = remember { PlaceSearchService(context) }
    val state by viewModel.state.collectAsState()
    var destination by remember { mutableStateOf("") }
    // A picked suggestion fills the field; searching for it again would just repeat it.
    var picked by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }

    LaunchedEffect(destination) {
        if (destination.isBlank() || destination == picked) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = placeSearch.search(destination, regionsOnly = true)
    }

    LiveStartContent(
        destination = destination,
        suggestions = suggestions,
        isSaving = state.isSaving,
        error = state.error,
        onDestinationChange = { destination = it },
        onPick = { suggestion ->
            val text = suggestion.destinationText()
            picked = text
            destination = text
        },
        onStart = { viewModel.startLiveTrip(destination, onStarted) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveStartContent(
    destination: String,
    suggestions: List<PlaceSuggestion>,
    isSaving: Boolean,
    error: String?,
    onDestinationChange: (String) -> Unit,
    onPick: (PlaceSuggestion) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val canStart = destination.isNotBlank() && !isSaving
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            PrimaryButton(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(Spacing.md)
            ) {
                Text(if (isSaving) "Starting…" else "Start Day 1")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = { Text("Where are you?") },
                placeholder = { Text("Lisbon, Portugal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (canStart) onStart() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            suggestions.take(5).forEach { suggestion ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(suggestion) }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    suggestion.subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HairlineDivider()
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * "Lisbon, Portugal" from a suggestion. Places splits it into "Lisbon" and
 * "Portugal"; the geocoder's subtitle is the whole address, which already
 * starts with the name.
 */
private fun PlaceSuggestion.destinationText(): String {
    val rest = subtitle ?: return title
    return if (rest.startsWith(title)) rest else "$title, $rest"
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun LiveStartPreview() {
    CuratedTheme {
        LiveStartContent(
            destination = "Lis",
            suggestions = listOf(
                PlaceSuggestion(id = "1", title = "Lisbon", subtitle = "Portugal"),
                PlaceSuggestion(id = "2", title = "Lisburn", subtitle = "United Kingdom"),
                PlaceSuggestion(id = "3", title = "Lismore", subtitle = "NSW, Australia")
            ),
            isSaving = false,
            error = null,
            onDestinationChange = {},
            onPick = {},
            onStart = {},
            onBack = {}
        )
    }
}
