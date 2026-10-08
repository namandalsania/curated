package com.curated.app.features.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.curated.app.features.activity.ActivityTab

/** Load the next page when this many cards from the end. */
private const val LOAD_MORE_THRESHOLD = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    onTripClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenActivity: (ActivityTab) -> Unit,
    onOpenSavedPlaces: () -> Unit,
    /** A live day: open its trip at that day. */
    onLiveDayClick: (tripId: String, dayIndex: Int) -> Unit,
    /** "Find people to follow", at the end of the feed. */
    onFindPeople: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val rawState by viewModel.state.collectAsState()
    // Drops blocked accounts' content already on screen; the database stops serving it.
    val blocked by BlockedAccounts.ids.collectAsState()
    val state = rawState.withoutAuthors(blocked)
    // Live days belong to Following; with nobody followed it shows Latest instead.
    val entries = remember(state.feed, state.liveDays, state.tab, state.isColdStart, state.canLoadMore) {
        val days = if (state.tab == FeedTab.FOLLOWING && !state.isColdStart) state.liveDays else emptyList()
        mergeFeed(state.feed, days, tripsComplete = !state.canLoadMore)
    }
    // Dismissing the saved-places banner is for good, not just this session.
    val prefs = remember { context.getSharedPreferences(HOME_PREFS, Context.MODE_PRIVATE) }
    var savedBannerDismissed by remember { mutableStateOf(prefs.getBoolean(KEY_SAVED_BANNER_DISMISSED, false)) }
    val listState = rememberLazyListState()
    // enterAlways: the bar leaves on the first downward scroll and comes back the
    // moment you scroll up, rather than waiting for the top of the list.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.startNotificationListener()
    }
    // Tapping Home while already on Home goes back to the top.
    LaunchedEffect(listState) {
        HomeReselect.events.collect {
            scrollBehavior.state.heightOffset = 0f
            listState.animateScrollToItem(0)
        }
    }
    // Back from Activity: what it marked read shouldn't still show as a badge.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCounts()
        onPauseOrDispose { }
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
                    IconButton(onClick = { onOpenActivity(ActivityTab.SENT) }) {
                        BadgedBox(badge = {
                            if (state.unreadShares > 0) Badge { Text(badgeText(state.unreadShares)) }
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Trips sent to you")
                        }
                    }
                    IconButton(onClick = { onOpenActivity(ActivityTab.ACTIVITY) }) {
                        BadgedBox(badge = {
                            if (state.unreadCount > 0) Badge { Text(badgeText(state.unreadCount)) }
                        }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Activity")
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
                    entries.isEmpty() -> EmptyState(
                        headline = if (state.tab == FeedTab.FOLLOWING) "Nothing from your people yet" else "No trips yet",
                        body = if (state.tab == FeedTab.FOLLOWING) {
                            "Follow a few travellers, or switch to Latest to see what's new."
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
                        contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.md)
                    ) {
                        // Only given a slot when it draws: an empty item still takes the
                        // list's spacing, which is what left a gap under the tabs.
                        if (!savedBannerDismissed && state.savedPlaceCount > 0) {
                            item(key = "saved-banner") {
                                SavedPlacesBanner(
                                    savedPlaceCount = state.savedPlaceCount,
                                    onClick = onOpenSavedPlaces,
                                    onDismiss = {
                                        savedBannerDismissed = true
                                        prefs.edit().putBoolean(KEY_SAVED_BANNER_DISMISSED, true).apply()
                                    }
                                )
                            }
                        }
                        if (state.isColdStart && state.tab == FeedTab.FOLLOWING) {
                            item(key = "cold-start") {
                                Text(
                                    "Latest trips — follow people to personalize this feed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                                )
                            }
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                            AnimatedListItem(index = index) {
                                when (entry) {
                                    is FeedEntry.TripEntry -> FeedItemCard(
                                        item = entry.item,
                                        onClick = { onTripClick(entry.item.trip.id) },
                                        onAuthorClick = { onAuthorClick(entry.item.trip.authorId) },
                                        onLikeToggle = { viewModel.toggleLike(entry.item.trip.id) },
                                        onSaveToggle = { viewModel.toggleSave(entry.item.trip.id) }
                                    )
                                    is FeedEntry.DayEntry -> LiveDayCard(
                                        item = entry.item,
                                        onClick = { onLiveDayClick(entry.item.trip.id, entry.item.day.dayIndex) },
                                        onAuthorClick = { onAuthorClick(entry.item.trip.authorId) }
                                    )
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading-more") {
                                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else if (!state.canLoadMore) {
                            item(key = "caught-up") { CaughtUp(onFindPeople = onFindPeople) }
                        }
                    }
                }
            }
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
            label = "Latest",
            selected = selected == FeedTab.LATEST || isColdStart,
            onClick = { onSelect(FeedTab.LATEST) }
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

/** A badge's count, capped so it stays a badge. */
internal fun badgeText(count: Int): String = if (count > 9) "9+" else count.toString()

/** The end of the feed: nothing more to load. */
@Composable
private fun CaughtUp(onFindPeople: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "You're all caught up",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onFindPeople) { Text("Find people to follow") }
    }
}

/** Taps on the Home tab while Home is already showing; the feed scrolls to the top. */
object HomeReselect {
    val events = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

private const val HOME_PREFS = "home"
private const val KEY_SAVED_BANNER_DISMISSED = "saved_banner_dismissed"
