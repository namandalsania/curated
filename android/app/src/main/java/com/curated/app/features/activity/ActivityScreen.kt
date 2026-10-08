package com.curated.app.features.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.core.data.BlockedAccounts
import com.curated.app.core.model.Notification
import com.curated.app.core.model.NotificationType
import com.curated.app.core.model.Trip
import com.curated.app.core.model.User
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.SecondaryButton
import com.curated.app.features.home.InboxViewModel
import com.curated.app.features.home.SentToYouList
import com.curated.app.features.home.TRIP_UNAVAILABLE
import com.curated.app.features.home.relativeTime
import com.curated.app.features.home.withoutAuthors
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class ActivityTab { ACTIVITY, SENT }

/**
 * Activity: what happened around you, and trips people sent you. The bell
 * opens the first tab, the send icon the second.
 */
@Composable
fun ActivityScreen(
    initialTab: ActivityTab,
    onBack: () -> Unit,
    onOpenTrip: (tripId: String, dayId: String?) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPlans: () -> Unit
) {
    val context = LocalContext.current
    val activity: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(context))
    val inbox: InboxViewModel = viewModel(factory = InboxViewModel.factory(context))
    val activityState by activity.state.collectAsState()
    val inboxState by inbox.state.collectAsState()
    // The database stops serving blocked accounts' notifications and shares;
    // this drops any already on screen.
    val blocked by BlockedAccounts.ids.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable: () -> Unit = { scope.launch { snackbar.showSnackbar(TRIP_UNAVAILABLE) } }

    // Each tab marks its own rows read when it's first shown.
    LaunchedEffect(tab) {
        if (tab == ActivityTab.ACTIVITY.ordinal) activity.load() else inbox.load()
    }
    LaunchedEffect(activityState.actionError) {
        activityState.actionError?.let { snackbar.showSnackbar(it) }
    }

    ActivityContent(
        tab = tab,
        onTabChange = { tab = it },
        state = activityState.copy(notifications = activityState.notifications.filter { it.actorId !in blocked }),
        onBack = onBack,
        onRetry = activity::load,
        onFollowBack = activity::followBack,
        onOpen = { target ->
            when (target) {
                is ActivityTarget.Profile -> onOpenProfile(target.userId)
                is ActivityTarget.Trip -> onOpenTrip(target.tripId, target.dayId)
                ActivityTarget.Plans -> onOpenPlans()
                ActivityTarget.Unavailable -> unavailable()
            }
        },
        snackbar = snackbar,
        sentToYou = {
            SentToYouList(
                state = inboxState.withoutAuthors(blocked),
                onRetry = inbox::load,
                onOpenTrip = { onOpenTrip(it, null) },
                onUnavailable = unavailable,
                onRemove = inbox::remove
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityContent(
    tab: Int,
    onTabChange: (Int) -> Unit,
    state: ActivityState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFollowBack: (String) -> Unit,
    onOpen: (ActivityTarget) -> Unit,
    snackbar: SnackbarHostState,
    sentToYou: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Activity") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { onTabChange(0) }, text = { Text("Activity") })
                    Tab(selected = tab == 1, onClick = { onTabChange(1) }, text = { Text("Sent to you") })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tab == 1) {
                sentToYou()
            } else {
                ActivityList(state = state, onRetry = onRetry, onFollowBack = onFollowBack, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ActivityList(
    state: ActivityState,
    onRetry: () -> Unit,
    onFollowBack: (String) -> Unit,
    onOpen: (ActivityTarget) -> Unit,
    now: Instant = Clock.System.now()
) {
    val sections = remember(state.notifications, state.unreadIds, now) {
        buildActivity(state.notifications, state.unreadIds, now)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            state.error != null -> ErrorState(message = state.error, onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
            sections.isEmpty() -> EmptyState(
                headline = "No activity yet",
                body = "New followers, likes, comments and trips from people you follow show up here.",
                icon = Icons.Outlined.Notifications,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
                sections.forEach { (section, rows) ->
                    item(key = "header-${section.name}") {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs)
                        )
                    }
                    items(rows, key = { it.key }) { item ->
                        ActivityRow(
                            item = item,
                            now = now,
                            showFollowBack = item.canFollowBack(state.following),
                            onFollowBack = { item.actorIds.firstOrNull()?.let(onFollowBack) },
                            onClick = { onOpen(item.target) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    now: Instant,
    showFollowBack: Boolean,
    onFollowBack: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.isUnread) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AsyncImage(
            model = item.firstActor?.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(item.actorsLabel) }
                    append(" ")
                    append(item.action)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    relativeTime(item.latestAt, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.isUnread) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        when {
            showFollowBack -> SecondaryButton(onClick = onFollowBack) { Text("Follow back") }
            item.tripId != null -> Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(CuratedCornerRadius / 2))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                item.thumbnailUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 720, name = "Activity")
@Composable
private fun ActivityPreview() {
    val now = Clock.System.now()
    val epoch = Instant.parse("2026-01-01T00:00:00Z")
    fun u(id: String, name: String) = User(id = id, username = id, displayName = name, createdAt = epoch)
    val trip = Trip(
        id = "t", authorId = "me", title = "Tokyo Escape", destination = "Tokyo",
        startDate = LocalDate(2026, 9, 1), endDate = LocalDate(2026, 9, 5), createdAt = epoch, updatedAt = epoch
    )
    var i = 0
    fun n(type: NotificationType, actor: User, at: Instant, withTrip: Boolean = false) = Notification(
        id = "n${i++}", recipientId = "me", actorId = actor.id, type = type,
        tripId = if (withTrip) trip.id else null, createdAt = at, actor = actor, trip = if (withTrip) trip else null
    )
    val diego = u("diego", "Diego Martins"); val sofia = u("sofia", "Sofia Almeida")
    val kai = u("kai", "Kai Nakamura"); val jo = u("jo", "Jordan Reyes")
    val notifications = listOf(
        n(NotificationType.FOLLOW, diego, now - 1.hours), n(NotificationType.FOLLOW, kai, now - 2.hours),
        n(NotificationType.FOLLOW, jo, now - 3.hours),
        n(NotificationType.LIKE, sofia, now - 4.hours, true), n(NotificationType.LIKE, kai, now - 5.hours, true),
        n(NotificationType.STOP_COMMENT, jo, now - 2.days, true),
        n(NotificationType.FOLLOW, sofia, now - 9.days)
    )
    CuratedTheme {
        ActivityContent(
            tab = 0, onTabChange = {},
            state = ActivityState(isLoading = false, notifications = notifications, unreadIds = notifications.take(3).map { it.id }.toSet()),
            onBack = {}, onRetry = {}, onFollowBack = {}, onOpen = {},
            snackbar = remember { SnackbarHostState() },
            sentToYou = {}
        )
    }
}
