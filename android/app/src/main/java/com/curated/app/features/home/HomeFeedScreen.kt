package com.curated.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curated.app.core.data.BlockedAccounts
import com.curated.app.core.model.Notification
import com.curated.app.core.model.NotificationType
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.AnimatedListItem
import com.curated.app.designsystem.components.CuratedFilterChip
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.TripCardSkeleton

/** Load the next page when this many cards from the end. */
private const val LOAD_MORE_THRESHOLD = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    onTripClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSavedPlaces: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val rawState by viewModel.state.collectAsState()
    // Drops blocked accounts' content already on screen; the database stops serving it.
    val blocked by BlockedAccounts.ids.collectAsState()
    val state = rawState.withoutAuthors(blocked)
    var showNotifications by remember { mutableStateOf(false) }
    var savedBannerDismissed by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // enterAlways: the bar leaves on the first downward scroll and comes back the
    // moment you scroll up, rather than waiting for the top of the list.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.startNotificationListener()
    }

    // Fetch the next page as the end of the list comes into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 1 - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text("Home") },
                actions = {
                    IconButton(onClick = onOpenInbox) {
                        BadgedBox(badge = {
                            if (state.unreadShares > 0) {
                                Badge { Text("${state.unreadShares}") }
                            }
                        }) {
                            Icon(Icons.Outlined.MoveToInbox, contentDescription = "Trips sent to you")
                        }
                    }
                    IconButton(onClick = {
                        showNotifications = true
                        viewModel.markNotificationsRead()
                    }) {
                        BadgedBox(badge = {
                            if (state.unreadCount > 0) {
                                Badge { Text("${state.unreadCount}") }
                            }
                        }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                        }
                    }
                }
            )
            // enterAlways shrinks the app bar but not its siblings, so the tab
            // row is collapsed by hand in step with it.
            val collapsed = scrollBehavior.state.collapsedFraction
            FeedTabs(
                selected = state.tab,
                isColdStart = state.isColdStart,
                onSelect = viewModel::selectTab,
                modifier = Modifier
                    .height(TabRowHeight * (1f - collapsed))
                    .graphicsLayer { alpha = 1f - collapsed }
                    .clipToBounds()
            )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh(isPullToRefresh = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading -> Column(modifier = Modifier.fillMaxSize()) {
                        repeat(3) {
                            TripCardSkeleton(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
                        }
                    }
                    state.error != null -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.feed.isEmpty() -> EmptyState(
                        headline = if (state.tab == FeedTab.FOLLOWING) "Nothing from your people yet" else "No trips yet",
                        body = if (state.tab == FeedTab.FOLLOWING) {
                            "Follow a few travellers, or switch to Trending to see what's new."
                        } else {
                            "Publish a trip to get things started."
                        },
                        icon = Icons.Outlined.Explore,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Full-bleed cards need breathing room between them, not around
                        // them: a full gutter of background is what separates one
                        // photograph from the next.
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        contentPadding = PaddingValues(bottom = Spacing.md)
                    ) {
                        if (!savedBannerDismissed) {
                            item(key = "saved-banner") {
                                SavedPlacesBanner(
                                    savedPlaceCount = state.highlights.savedPlaceCount,
                                    onClick = onOpenSavedPlaces,
                                    onDismiss = { savedBannerDismissed = true }
                                )
                            }
                        }
                        if (state.highlights.hasStrip) {
                            item(key = "highlights") {
                                HomeHighlightsCard(highlights = state.highlights, onOpenTrip = onTripClick)
                            }
                        }
                        if (state.isColdStart && state.tab == FeedTab.FOLLOWING) {
                            item(key = "cold-start") {
                                Text(
                                    "Trending trips — follow people to personalize this feed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                                )
                            }
                        }
                        itemsIndexed(state.feed, key = { _, item -> item.trip.id }) { index, item ->
                            AnimatedListItem(index = index) {
                                FeedItemCard(
                                    item = item,
                                    onClick = { onTripClick(item.trip.id) },
                                    onAuthorClick = { onAuthorClick(item.trip.authorId) },
                                    onLikeToggle = { viewModel.toggleLike(item.trip.id) },
                                    onSaveToggle = { viewModel.toggleSave(item.trip.id) }
                                )
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading-more") {
                                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showNotifications = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NotificationsList(
                notifications = state.notifications,
                onPlanInviteClick = {
                    showNotifications = false
                    onOpenPlans()
                },
                onTripClick = { tripId ->
                    showNotifications = false
                    onTripClick(tripId)
                }
            )
        }
    }
}

@Composable
private fun FeedTabs(
    selected: FeedTab,
    isColdStart: Boolean,
    onSelect: (FeedTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.md)
    ) {
        FeedTab(
            label = "Following",
            selected = selected == FeedTab.FOLLOWING && !isColdStart,
            onClick = { onSelect(FeedTab.FOLLOWING) }
        )
        FeedTab(
            label = "Trending",
            selected = selected == FeedTab.TRENDING || isColdStart,
            onClick = { onSelect(FeedTab.TRENDING) }
        )
    }
}

/** A text tab with a 2dp underline, rather than a filled chip. */
@Composable
private fun FeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(TabIndicatorWidth)
                .background(if (selected) color else Color.Transparent)
        )
    }
}

/** Wide enough to underline either label without measuring the text. */
private val TabIndicatorWidth = 64.dp

/** The tab row's natural height, collapsed towards zero as the app bar retracts. */
private val TabRowHeight = 48.dp

@Composable
private fun NotificationsList(
    notifications: List<Notification>,
    onPlanInviteClick: () -> Unit,
    onTripClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        Text(
            "Notifications",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(Spacing.md)
        )
        if (notifications.isEmpty()) {
            Text(
                "Nothing yet — you'll see follows, new trips, comments and plan invites here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
        } else {
            notifications.forEach { notification ->
                val open: (() -> Unit)? = when (notification.type) {
                    NotificationType.PLAN_INVITE -> onPlanInviteClick
                    NotificationType.NEW_TRIP,
                    NotificationType.STOP_COMMENT,
                    NotificationType.TRIP_SHARE -> notification.tripId?.let { id -> { onTripClick(id) } }
                    NotificationType.FOLLOW -> null
                }
                Text(
                    notification.describe(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (open != null) Modifier.clickable(onClick = open) else Modifier)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
                HairlineDivider()
            }
        }
    }
}

private fun Notification.describe(): String {
    val actorName = actor?.displayName ?: "Someone"
    return when (type) {
        NotificationType.FOLLOW -> "$actorName started following you."
        NotificationType.NEW_TRIP -> "$actorName published ${trip?.title ?: "a new trip"}."
        NotificationType.PLAN_INVITE ->
            "$actorName invited you to plan ${plan?.title ?: "a trip"}. Tap to answer."
        NotificationType.STOP_COMMENT ->
            "$actorName commented on a place in ${trip?.title ?: "your trip"}."
        NotificationType.TRIP_SHARE ->
            "$actorName sent you ${trip?.title ?: "a trip"}."
    }
}
