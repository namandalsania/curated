package com.curated.app.features.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curated.app.core.model.PlanRole
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.PrimaryButton

@Composable
fun PlansScreen(onBack: () -> Unit, onOpenPlan: (String) -> Unit) {
    val context = LocalContext.current
    val viewModel: PlansViewModel = viewModel(factory = PlansViewModel.factory(context))
    val state by viewModel.state.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    // Reload on every visit so counts reflect edits made in the editor.
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.createdPlanId) {
        val id = state.createdPlanId ?: return@LaunchedEffect
        showCreate = false
        viewModel.createdPlanOpened()
        onOpenPlan(id)
    }

    if (showCreate) {
        NewPlanDialog(
            isCreating = state.isCreating,
            error = state.createError,
            onCreate = viewModel::create,
            onDismiss = { showCreate = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
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
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        PrimaryButton(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("New plan")
                        }
                    }
                    state.actionError?.let { message ->
                        item {
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (state.invitations.isNotEmpty()) {
                        item { SectionLabel("Invitations") }
                        items(state.invitations, key = { it.plan.id }) { invite ->
                            InvitationCard(
                                invitation = invite,
                                onAccept = { viewModel.acceptInvite(invite.plan.id) },
                                onDecline = { viewModel.declineInvite(invite.plan.id) }
                            )
                        }
                    }

                    if (state.isEmpty) {
                        item {
                            EmptyState(
                                headline = "No plans yet",
                                body = "Save places from other people's trips, then arrange them into days here.",
                                icon = Icons.Outlined.Map,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)
                            )
                        }
                    }

                    if (state.owned.isNotEmpty()) {
                        if (state.shared.isNotEmpty() || state.invitations.isNotEmpty()) item { SectionLabel("Your plans") }
                        items(state.owned, key = { it.plan.id }) { listing ->
                            PlanCard(listing = listing, onClick = { onOpenPlan(listing.plan.id) })
                        }
                    }
                    if (state.shared.isNotEmpty()) {
                        item { SectionLabel("Shared with you") }
                        items(state.shared, key = { it.plan.id }) { listing ->
                            PlanCard(listing = listing, onClick = { onOpenPlan(listing.plan.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

@Composable
private fun PlanCard(listing: PlanListing, onClick: () -> Unit) {
    HairlineCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                listing.plan.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(if (listing.placeCount == 1) "1 place" else "${listing.placeCount} places")
                    append(" · ")
                    append(if (listing.plan.dayCount == 1) "1 day" else "${listing.plan.dayCount} days")
                    // Your own plans don't need a role badge; shared ones do.
                    if (listing.role != PlanRole.OWNER) append(" · ").append(listing.role.label)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A plan someone invited you to: accept to join, decline to drop it. */
@Composable
private fun InvitationCard(invitation: PlanInvitation, onAccept: () -> Unit, onDecline: () -> Unit) {
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                invitation.plan.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${invitation.invitedBy?.displayName ?: "Someone"} invited you as " +
                    if (invitation.role == PlanRole.EDITOR) "an editor" else "a viewer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton(onClick = onAccept) { Text("Accept") }
                TextButton(onClick = onDecline) { Text("Decline") }
            }
        }
    }
}

/** Name and length up front, so the editor opens with the right number of days. */
@Composable
private fun NewPlanDialog(
    isCreating: Boolean,
    error: String?,
    onCreate: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableIntStateOf(3) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("New plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    placeholder = { Text("Italy this summer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Days",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { days-- }, enabled = days > 1) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Fewer days")
                    }
                    Text("$days", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { days++ }, enabled = days < PLAN_MAX_DAYS) {
                        Icon(Icons.Outlined.Add, contentDescription = "More days")
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, days) }, enabled = title.isNotBlank() && !isCreating) {
                Text(if (isCreating) "Creating…" else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
