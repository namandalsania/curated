package com.curated.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.BuildConfig
import com.curated.app.core.data.ReportTarget
import com.curated.app.core.format.formatDateRange
import com.curated.app.core.format.monthYearText
import com.curated.app.core.format.shortDayText
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripVisibility
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.ProfileHeaderSkeleton
import com.curated.app.designsystem.components.SecondaryButton
import com.curated.app.designsystem.components.SkeletonBox
import com.curated.app.designsystem.components.Tag
import com.curated.app.features.moderation.ModerationDialogs
import com.curated.app.features.moderation.ModerationMenu
import com.curated.app.features.moderation.ModerationSubject
import com.curated.app.features.moderation.rememberModerationViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ProfileScreen(
    userId: String,
    /** Bumped by Edit profile after a save; triggers an in-place refresh. */
    reloadKey: Long,
    onTripClick: (String) -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSavedPlaces: () -> Unit,
    onResumeDraft: (String) -> Unit,
    onOpenLiveTrip: (String) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenBlockedAccounts: () -> Unit,
    /** Leaves someone else's profile - after blocking them, there's nothing to show. */
    onBack: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(context))
    val state by viewModel.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val moderation = rememberModerationViewModel(key = "profile-$userId")
    val moderationState by moderation.state.collectAsState()
    LaunchedEffect(moderationState.blockedUserId) {
        if (moderationState.blockedUserId != null) {
            moderation.consumeBlocked()
            onBack()
        }
    }
    ModerationDialogs(moderation)

    // One effect, so returning from an edit loads once, not once per key.
    LaunchedEffect(userId, reloadKey) {
        viewModel.load(userId, showSkeleton = reloadKey == 0L)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.profileUser?.let { "@${it.username}" } ?: "Profile") },
                actions = {
                    if (state.isOwnProfile) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit profile") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onEditProfile()
                                    }
                                )
                                if (BuildConfig.CRASH_TEST_ENABLED) {
                                    DropdownMenuItem(
                                        text = { Text("Test crash (debug)") },
                                        onClick = {
                                            // Uncaught on purpose: Crashlytics reports it on the next launch.
                                            throw RuntimeException("Test crash from the Profile menu")
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Blocked accounts") },
                                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenBlockedAccounts()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sign out") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmSignOut = true
                                    }
                                )
                            }
                        }
                    } else {
                        state.profileUser?.let { user ->
                            ModerationMenu(
                                subject = ModerationSubject(
                                    target = ReportTarget.PROFILE,
                                    targetId = user.id,
                                    userId = user.id,
                                    userName = "@${user.username}"
                                ),
                                viewModel = moderation
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Column {
                    ProfileHeaderSkeleton()
                    SkeletonBox(
                        modifier = Modifier
                            .padding(horizontal = Spacing.md)
                            .fillMaxWidth()
                            .height(72.dp)
                    )
                }
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(userId) },
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
                    item {
                        ProfileHeader(
                            state = state,
                            onFollowToggle = viewModel::toggleFollow,
                            onFollowersClick = onFollowersClick,
                            onFollowingClick = onFollowingClick,
                            onEditProfile = onEditProfile
                        )
                    }

                    if (state.isOwnProfile) {
                        item { SectionTitle("Planning") }
                        item {
                            PlanningCard(
                                onOpenSavedPlaces = onOpenSavedPlaces,
                                onOpenPlans = onOpenPlans,
                                modifier = Modifier.padding(horizontal = Spacing.md)
                            )
                        }
                    }

                    // Someone else's empty map says nothing, so only show it when
                    // it has countries - or it's yours and invites you to fill it.
                    val showVisited = state.isOwnProfile || state.isVisitsLoading || state.countryVisits.isNotEmpty()
                    if (showVisited) {
                        item { SectionTitle("Visited") }
                        item {
                            VisitedCountriesCard(
                                visits = state.countryVisits,
                                isLoading = state.isVisitsLoading,
                                isOwnProfile = state.isOwnProfile,
                                modifier = Modifier.padding(horizontal = Spacing.md)
                            )
                        }
                    }

                    if (state.isOwnProfile && state.liveTrips.isNotEmpty()) {
                        item { SectionTitle("Live now") }
                        items(state.liveTrips, key = { "live-${it.id}" }) { trip ->
                            DraftRow(trip = trip, onResume = { onOpenLiveTrip(trip.id) }, onDelete = null, isLive = true)
                        }
                    }

                    if (state.isOwnProfile && state.drafts.isNotEmpty()) {
                        item { SectionTitle("Drafts · ${state.drafts.size}") }
                        items(state.drafts, key = { "draft-${it.id}" }) { draft ->
                            DraftRow(
                                trip = draft,
                                onResume = { onResumeDraft(draft.id) },
                                onDelete = { viewModel.deleteDraft(draft.id) }
                            )
                        }
                    }

                    item { SectionTitle(if (state.trips.isEmpty()) "Trips" else "Trips · ${state.trips.size}") }
                    if (state.trips.isEmpty()) {
                        item {
                            EmptyState(
                                headline = if (state.isOwnProfile) "No trips yet" else "Nothing published yet",
                                body = if (state.isOwnProfile) {
                                    "Publish a trip to see it here."
                                } else {
                                    "${state.profileUser?.displayName ?: "This person"} hasn't published a trip yet."
                                },
                                icon = Icons.Outlined.CameraAlt,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Plain rows rather than a nested lazy grid: a lazy grid
                        // inside a LazyColumn needs a guessed fixed height, which
                        // left a blank row or clipped the last one.
                        items(state.trips.chunked(2), key = { row -> row.first().id }) { row ->
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = Spacing.md)
                                    .padding(bottom = Spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                row.forEach { trip ->
                                    TripTile(
                                        trip = trip,
                                        onClick = { onTripClick(trip.id) },
                                        showVisibility = state.isOwnProfile,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (state.isOwnProfile) {
                        item { SectionTitle(if (state.savedTrips.isEmpty()) "Saved" else "Saved · ${state.savedTrips.size}") }
                        if (state.savedTrips.isEmpty()) {
                            item {
                                EmptyState(
                                    headline = "Nothing saved yet",
                                    body = "Bookmark trips you love to find them here later.",
                                    icon = Icons.Outlined.BookmarkBorder,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = Spacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    items(state.savedTrips, key = { it.id }) { trip ->
                                        TripTile(
                                            trip = trip,
                                            onClick = { onTripClick(trip.id) },
                                            modifier = Modifier.width(160.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need your email and password to sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                    onSignedOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    onFollowToggle: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onEditProfile: () -> Unit
) {
    val user = state.profileUser
    Column(
        modifier = Modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (user?.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "${user.displayName}'s photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Initial instead of an empty grey circle.
                    Text(
                        user?.displayName?.firstOrNull()?.uppercase().orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    user?.displayName.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "@${user?.username.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                user?.createdAt?.let { joined ->
                    Text(
                        "Joined ${joined.toLocalDateTime(TimeZone.currentSystemDefault()).date.monthYearText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val bio = user?.bio?.takeIf { it.isNotBlank() }
        when {
            bio != null -> Text(bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            state.isOwnProfile -> Text(
                "+ Add a bio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(CuratedCornerRadius))
                    .clickable(onClick = onEditProfile)
            )
        }

        HairlineCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ProfileStat(count = state.trips.size, label = if (state.trips.size == 1) "Trip" else "Trips", modifier = Modifier.weight(1f))
                StatDivider()
                ProfileStat(count = state.followerCount, label = "Followers", onClick = onFollowersClick, modifier = Modifier.weight(1f))
                StatDivider()
                ProfileStat(count = state.followingCount, label = "Following", onClick = onFollowingClick, modifier = Modifier.weight(1f))
            }
        }

        if (state.isOwnProfile) {
            SecondaryButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Text("Edit profile")
            }
        } else if (state.isFollowing) {
            SecondaryButton(onClick = onFollowToggle, modifier = Modifier.fillMaxWidth()) { Text("Following") }
        } else {
            PrimaryButton(onClick = onFollowToggle, modifier = Modifier.fillMaxWidth()) { Text("Follow") }
        }

        state.actionError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Your saved places and plans - where trips you've browsed turn into your own. */
@Composable
private fun PlanningCard(onOpenSavedPlaces: () -> Unit, onOpenPlans: () -> Unit, modifier: Modifier = Modifier) {
    HairlineCard(modifier = modifier.fillMaxWidth()) {
        PlanningRow(
            icon = Icons.Outlined.BookmarkBorder,
            title = "Saved places",
            subtitle = "Stays, sights and food you've saved from trips",
            onClick = onOpenSavedPlaces
        )
        HairlineDivider(modifier = Modifier.padding(start = Spacing.md + 24.dp + Spacing.md))
        PlanningRow(
            icon = Icons.Outlined.Map,
            title = "My plans",
            subtitle = "Arrange saved places into your own days",
            onClick = onOpenPlans
        )
    }
}

@Composable
private fun PlanningRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A count over a label, one third of the stats card; tappable when [onClick] is set. */
@Composable
private fun ProfileStat(count: Int, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "View $label", onClick = onClick) else Modifier)
            .padding(vertical = Spacing.sm + Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$count", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = Spacing.sm)
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * An unfinished trip: tap to carry on, or throw it away. A live trip has no
 * delete here ([onDelete] null) - it may already have posted days people saw.
 */
@Composable
private fun DraftRow(trip: Trip, onResume: () -> Unit, onDelete: (() -> Unit)?, isLive: Boolean = false) {
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onResume).padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trip.title.ifBlank { "Untitled trip" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isLive) {
                        "${trip.destination} · since ${trip.startDate.shortDayText()}"
                    } else {
                        "${trip.destination} · ${formatDateRange(trip.startDate, trip.endDate)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onResume) { Text(if (isLive) "Open" else "Continue") }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete draft",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Cover, title and "Lisbon · Sep 2026" - used for both the trips grid and the saved row.
 *
 * [showVisibility] labels unlisted and private trips. Only your own grid can hold
 * them, so only your own grid asks.
 */
@Composable
private fun TripTile(
    trip: Trip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showVisibility: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(CuratedCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            trip.coverPhotoUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = trip.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            val visibilityLabel = when (trip.visibility) {
                TripVisibility.PUBLIC -> null
                TripVisibility.UNLISTED -> "Unlisted"
                TripVisibility.PRIVATE -> "Private"
            }
            if (showVisibility && visibilityLabel != null) {
                Tag(visibilityLabel, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm))
            }
        }
        Text(
            trip.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm)
        )
        Text(
            "${trip.destination} · ${trip.startDate.monthYearText()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm)
    )
}
