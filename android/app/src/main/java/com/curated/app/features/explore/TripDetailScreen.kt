package com.curated.app.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.core.data.ReportTarget
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.data.TripDaySection
import com.curated.app.core.format.displayText
import com.curated.app.core.format.formatDateRange
import com.curated.app.core.format.label
import com.curated.app.core.format.shortDayText
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripVisibility
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.SkeletonBox
import com.curated.app.designsystem.components.Tag
import com.curated.app.designsystem.components.TripCardSkeleton
import com.curated.app.features.moderation.ModerationDialogs
import com.curated.app.features.moderation.ModerationMenu
import com.curated.app.features.moderation.ModerationSubject
import com.curated.app.features.moderation.rememberModerationViewModel
import com.curated.app.features.trip.TripVisibilityPicker
import com.curated.app.features.trip.explanation
import com.curated.app.features.trip.label

@Composable
fun TripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit,
    onOpenSavedPlaces: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: TripDetailViewModel = viewModel(factory = TripDetailViewModel.factory(context))
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var openComments by remember { mutableStateOf<StopWithPhotos?>(null) }
    var showShare by remember { mutableStateOf(false) }
    var showVisibility by remember { mutableStateOf(false) }
    val moderation = rememberModerationViewModel(key = "trip-$tripId")
    val moderationState by moderation.state.collectAsState()
    // Blocking the author hides this trip from you, so there's nothing left to show.
    LaunchedEffect(moderationState.blockedUserId) {
        if (moderationState.blockedUserId != null) {
            moderation.consumeBlocked()
            onBack()
        }
    }
    ModerationDialogs(moderation)

    LaunchedEffect(tripId) { viewModel.load(tripId) }

    LaunchedEffect(state.snackbar) {
        val snackbar = state.snackbar ?: return@LaunchedEffect
        viewModel.snackbarShown()
        val result = snackbarHostState.showSnackbar(
            message = snackbar.text,
            actionLabel = if (snackbar.offerViewSaved) "View" else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) onOpenSavedPlaces()
    }

    val trip = state.trip
    openComments?.let { item ->
        if (trip != null) {
            CommentsSheet(
                stopId = item.stop.id,
                stopName = item.stop.name,
                tripId = trip.id,
                tripAuthorId = trip.authorId,
                onDismiss = {
                    openComments = null
                    viewModel.refreshCommentCounts(trip.id)
                }
            )
        }
    }
    if (showShare && trip != null) {
        ShareTripSheet(tripId = trip.id, onDismiss = { showShare = false })
    }
    if (showVisibility && trip != null && state.isOwner) {
        VisibilitySheet(
            selected = trip.visibility,
            onSelect = { visibility ->
                viewModel.setVisibility(visibility)
                showVisibility = false
            },
            onDismiss = { showVisibility = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.trip?.title ?: "Trip",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.trip != null) {
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Send this trip to someone")
                        }
                    }
                    val shown = state.trip
                    if (shown != null && !state.isOwner) {
                        ModerationMenu(
                            subject = ModerationSubject(
                                target = ReportTarget.TRIP,
                                targetId = shown.id,
                                userId = shown.authorId,
                                userName = shown.author?.let { "@${it.username}" } ?: "this person"
                            ),
                            viewModel = moderation
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    SkeletonBox(modifier = Modifier.width(220.dp).height(26.dp))
                    SkeletonBox(modifier = Modifier.width(160.dp).height(14.dp))
                    TripCardSkeleton()
                }
                state.error != null || trip == null -> ErrorState(
                    message = state.error ?: "Trip not found",
                    onRetry = { viewModel.load(tripId) },
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl)
                ) {
                    item(key = "header") {
                        TripHeader(
                            trip = trip,
                            dayCount = state.days.size,
                            isOwner = state.isOwner,
                            onAuthorClick = onAuthorClick,
                            onVisibilityClick = { showVisibility = true }
                        )
                    }
                    state.days.forEach { day ->
                        item(key = "day-${day.dayIndex}") { DayHeader(day) }
                        itemsIndexed(day.stops, key = { _, it -> it.stop.id }) { index, stopWithPhotos ->
                            StopCard(
                                item = stopWithPhotos,
                                number = index + 1,
                                isSaved = stopWithPhotos.stop.id in state.savedStopIds,
                                commentCount = state.commentCounts[stopWithPhotos.stop.id] ?: 0,
                                onToggleSave = { viewModel.toggleSave(stopWithPhotos) },
                                onCommentsClick = { openComments = stopWithPhotos },
                                modifier = Modifier.padding(bottom = Spacing.md)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripHeader(
    trip: Trip,
    dayCount: Int,
    isOwner: Boolean,
    onAuthorClick: (String) -> Unit,
    onVisibilityClick: () -> Unit
) {
    Column(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm)) {
        Text(
            trip.destination,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            trip.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xs)
        )
        Text(
            buildString {
                append(formatDateRange(trip.startDate, trip.endDate))
                if (dayCount > 0) append(" · $dayCount ${if (dayCount == 1) "day" else "days"}")
                trip.stopCount?.let { append(" · $it ${if (it == 1) "stop" else "stops"}") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs)
        )

        // Everyone else can only ever see a trip they're allowed to, so the
        // setting is only news to its owner.
        if (isOwner) VisibilityLabel(trip.visibility, onClick = onVisibilityClick)

        trip.author?.let { author ->
            Row(
                modifier = Modifier
                    .padding(top = Spacing.md)
                    .clip(RoundedCornerShape(CuratedCornerRadius))
                    .clickable { onAuthorClick(author.id) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    author.avatarUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column {
                    Text(
                        author.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "@${author.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "Unlisted · Only people with the link. Change" - the owner's view of who can see this. */
@Composable
private fun VisibilityLabel(visibility: TripVisibility, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = Spacing.sm)
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .clickable(onClickLabel = "Change who can see this trip", onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Tag(visibility.label())
        Text(
            visibility.explanation(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilitySheet(selected: TripVisibility, onSelect: (TripVisibility) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TripVisibilityPicker(
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl)
        )
    }
}

@Composable
private fun DayHeader(day: TripDaySection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            day.dayIndex?.let { "Day $it" } ?: "Unscheduled",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        day.date?.let {
            Text(
                it.shortDayText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * One stop, in TripCard's visual language: large edge-to-edge photo(s) on top
 * of a flat hairline card, then — in descending weight — name, time/category
 * metadata, caption body, and the tip as a tinted callout.
 */
@Composable
private fun StopCard(
    item: StopWithPhotos,
    number: Int,
    isSaved: Boolean,
    commentCount: Int,
    onToggleSave: () -> Unit,
    onCommentsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stop = item.stop
    val shape = RoundedCornerShape(CuratedCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        if (item.photoUrls.isNotEmpty()) {
            StopPhotoCarousel(urls = item.photoUrls, contentDescription = stop.name)
        }

        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StopNumberBadge(number, modifier = Modifier.padding(top = 3.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stop.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StopMetaLine(item)
                }
                // Save this one place - a stay, a castle, a pizza place - to
                // build your own plan from, separate from saving the whole trip.
                IconButton(onClick = onToggleSave, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isSaved) "Remove ${stop.name} from saved places" else "Save ${stop.name}",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            stop.caption?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            stop.tips?.takeIf { it.isNotBlank() }?.let { TipCallout(it) }

            // Comments live per place: "is this still open on Mondays?" belongs
            // here, not under the trip as a whole.
            TextButton(
                onClick = onCommentsClick,
                modifier = Modifier.offset(x = -Spacing.sm)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Comment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when (commentCount) {
                        0 -> "Comment"
                        1 -> "1 comment"
                        else -> "$commentCount comments"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
        }
    }
}

/** "5:30 PM · Food · Alfama" — small, muted, one line. */
@Composable
private fun StopMetaLine(item: StopWithPhotos) {
    val stop = item.stop
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stop.arrivalTime?.let { time ->
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = "Arrival time",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                time.displayText(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.xs)
            )
            Text(" · ", style = MaterialTheme.typography.labelMedium, color = muted)
        }
        val place = stop.placeName?.takeIf { it.isNotBlank() && it != stop.name }
        Text(
            listOfNotNull(stop.category.label(), place).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TipCallout(tip: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                "Tip",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                tip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun StopNumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** Swipeable 4:3 photos with dot indicators when there's more than one. */
@Composable
private fun StopPhotoCarousel(urls: List<String>, contentDescription: String) {
    val pagerState = rememberPagerState(pageCount = { urls.size })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = urls[page],
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (urls.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.sm)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                repeat(urls.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.45f)
                            )
                    )
                }
            }
        }
    }
}
