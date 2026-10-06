package com.curated.app.features.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.CommentRepository
import com.curated.app.core.data.EngagementRepository
import com.curated.app.core.data.LikeSummary
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.ShareRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.data.SavedPlacesRepository
import com.curated.app.core.data.TripRepository
import com.curated.app.core.geocode.GeocodingService
import com.curated.app.core.map.CityAggregator
import com.curated.app.core.map.CountryVisit
import com.curated.app.core.model.Notification
import com.curated.app.core.model.Trip
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Which feed you're looking at. */
enum class FeedTab { FOLLOWING, TRENDING }

/** How many trips load at a time, and what one more scroll fetches. */
private const val FEED_PAGE_SIZE = 10L

data class FeedItem(
    val trip: Trip,
    val stopCount: Int,
    val likeSummary: LikeSummary,
    val isSaved: Boolean,
    val commentCount: Int,
    /** In itinerary order; the card previews the first few. */
    val stopNames: List<String> = emptyList()
)

/** A trip that was running on this date in an earlier year. */
data class OnThisDay(val tripId: String, val title: String, val destination: String, val yearsAgo: Int)

/**
 * The personal line above the feed: what you've done, rather than what other
 * people are doing. Deliberately separate from the feed's loading state so a
 * slow geocode never holds the trips back.
 */
data class HomeHighlights(
    val visits: List<CountryVisit> = emptyList(),
    val savedPlaceCount: Int = 0,
    val onThisDay: OnThisDay? = null
) {
    /** Nothing worth taking up space for yet. */
    val isEmpty: Boolean get() = visits.isEmpty() && savedPlaceCount == 0 && onThisDay == null

    /**
     * Whether the highlights card will actually draw. The list has to know: an
     * item that renders nothing still takes a slot in the arrangement's spacing,
     * which is what put a double gap under the saved-places banner.
     */
    val hasStrip: Boolean get() = visits.isNotEmpty() || onThisDay != null
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** False once a page comes back short - nothing left to scroll to. */
    val canLoadMore: Boolean = true,
    val tab: FeedTab = FeedTab.FOLLOWING,
    /** True when you follow nobody: Following has nothing to show. */
    val isColdStart: Boolean = false,
    val feed: List<FeedItem> = emptyList(),
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val unreadShares: Int = 0,
    val highlights: HomeHighlights = HomeHighlights(),
    val error: String? = null
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val tripRepository: TripRepository,
    private val engagementRepository: EngagementRepository,
    private val notificationRepository: NotificationRepository,
    private val commentRepository: CommentRepository,
    private val shareRepository: ShareRepository,
    private val savedPlacesRepository: SavedPlacesRepository,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var notificationChannel: RealtimeChannel? = null

    fun refresh(tab: FeedTab = _state.value.tab, isPullToRefresh: Boolean = false) {
        val myId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    tab = tab,
                    isLoading = !isPullToRefresh && it.feed.isEmpty(),
                    isRefreshing = isPullToRefresh,
                    error = null
                )
            }
            try {
                val followingIds = socialRepository.fetchFollowingIds(myId)
                val isColdStart = followingIds.isEmpty()
                // Following with nobody followed would just be blank, so fall back.
                val effectiveTab = if (tab == FeedTab.FOLLOWING && isColdStart) FeedTab.TRENDING else tab
                val trips = loadPage(effectiveTab, followingIds, offset = 0)
                val feed = toFeedItems(trips, myId)

                loadHighlights(myId)

                val notifications = notificationRepository.fetchNotifications(myId)
                val unreadShares = runCatching { shareRepository.unreadCount(myId) }.getOrDefault(0)
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        tab = tab,
                        isColdStart = isColdStart,
                        feed = feed,
                        canLoadMore = trips.size.toLong() == FEED_PAGE_SIZE,
                        notifications = notifications,
                        unreadCount = notifications.count { n -> n.readAt == null },
                        unreadShares = unreadShares
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Failed to load feed")
                }
            }
        }
    }

    fun selectTab(tab: FeedTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, feed = emptyList(), canLoadMore = true) }
        refresh(tab)
    }

    /** Called as the last card comes into view. */
    fun loadMore() {
        val myId = authRepository.currentUserId() ?: return
        val current = _state.value
        if (current.isLoadingMore || !current.canLoadMore || current.feed.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val followingIds = socialRepository.fetchFollowingIds(myId)
                val effectiveTab =
                    if (current.tab == FeedTab.FOLLOWING && followingIds.isEmpty()) FeedTab.TRENDING else current.tab
                val trips = loadPage(effectiveTab, followingIds, offset = current.feed.size.toLong())
                // Guard against a trip appearing twice if something was published mid-scroll.
                val known = current.feed.mapTo(HashSet()) { it.trip.id }
                val fresh = trips.filter { it.id !in known }
                val more = toFeedItems(fresh, myId)
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        feed = it.feed + more,
                        canLoadMore = trips.size.toLong() == FEED_PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                // Silent: the feed you already have still works.
                _state.update { it.copy(isLoadingMore = false, canLoadMore = false) }
            }
        }
    }

    private var highlightsJob: Job? = null

    /**
     * Your own countries, saved places and anniversaries. Runs in its own job and
     * swallows its errors: the strip is a bonus, and a feed that waits on a
     * geocode to draw would be a bad trade.
     */
    private fun loadHighlights(myId: String) {
        if (highlightsJob?.isActive == true) return
        highlightsJob = viewModelScope.launch {
            val highlights = try {
                // Where you've been counts whoever the trip was shared with.
                val myTrips = tripRepository.fetchTripsByAuthor(myId, includeNonPublic = true)
                val visits = if (myTrips.isEmpty()) {
                    emptyList()
                } else {
                    CityAggregator.aggregate(tripRepository.fetchStopPointsFor(myTrips), geocodingService)
                }
                val savedCount = runCatching { savedPlacesRepository.fetchAll(myId).size }.getOrDefault(0)
                HomeHighlights(
                    visits = visits,
                    savedPlaceCount = savedCount,
                    onThisDay = myTrips.findOnThisDay(today())
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't build home highlights", e)
                HomeHighlights()
            }
            _state.update { it.copy(highlights = highlights) }
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private suspend fun loadPage(tab: FeedTab, followingIds: List<String>, offset: Long): List<Trip> = when (tab) {
        FeedTab.FOLLOWING -> tripRepository.fetchTripsByAuthors(followingIds, FEED_PAGE_SIZE, offset)
        FeedTab.TRENDING -> tripRepository.fetchRecentPublicTrips(FEED_PAGE_SIZE, offset)
    }

    private suspend fun toFeedItems(trips: List<Trip>, myId: String): List<FeedItem> {
        if (trips.isEmpty()) return emptyList()
        val tripIds = trips.map { it.id }
        val authors = socialRepository.fetchUsers(trips.map { it.authorId }.distinct()).associateBy { it.id }
        val stopSummaries = tripRepository.fetchStopSummaries(tripIds)
        val likeSummaries = engagementRepository.fetchLikeSummaries(tripIds, myId)
        val savedTripIds = engagementRepository.fetchSavedTrips(myId).map { it.id }.toSet()
        val commentCounts = runCatching { commentRepository.countsForTrips(tripIds) }.getOrDefault(emptyMap())

        return trips.map { trip ->
            FeedItem(
                trip = trip.copy(author = authors[trip.authorId]),
                stopCount = stopSummaries[trip.id]?.count ?: 0,
                likeSummary = likeSummaries[trip.id] ?: LikeSummary(0, false),
                isSaved = trip.id in savedTripIds,
                commentCount = commentCounts[trip.id] ?: 0,
                stopNames = stopSummaries[trip.id]?.names.orEmpty()
            )
        }
    }

    private var listenerStarted = false

    fun startNotificationListener() {
        val myId = authRepository.currentUserId() ?: return
        if (listenerStarted) return
        listenerStarted = true

        viewModelScope.launch {
            val channel = runCatching { notificationRepository.openNotificationsChannel(myId) }
                .getOrElse {
                    Log.w(TAG, "Couldn't open the notifications channel", it)
                    return@launch
                }
            notificationChannel = channel

            // Register the change listener before subscribing, per supabase-kt's realtime
            // pattern: postgresChangeFlow must be bound before the channel opens its
            // socket, or the server-side filter is never registered. Creating the flow
            // is what binds it, so that happens here, before subscribe().
            val inserts = notificationRepository.observeInserts(channel, myId)
            launch {
                inserts.collect { notification ->
                    _state.update {
                        it.copy(
                            notifications = listOf(notification) + it.notifications,
                            unreadCount = it.unreadCount + 1
                        )
                    }
                }
            }
            runCatching { channel.subscribe() }
        }
    }

    fun toggleLike(tripId: String) {
        val myId = authRepository.currentUserId() ?: return
        val current = _state.value.feed.find { it.trip.id == tripId } ?: return
        val wasLiked = current.likeSummary.likedByMe

        applyFeedUpdate(tripId) { item ->
            item.copy(
                likeSummary = item.likeSummary.copy(
                    likedByMe = !wasLiked,
                    likeCount = item.likeSummary.likeCount + if (wasLiked) -1 else 1
                )
            )
        }

        viewModelScope.launch {
            try {
                if (wasLiked) engagementRepository.unlike(myId, tripId) else engagementRepository.like(myId, tripId)
            } catch (e: Exception) {
                applyFeedUpdate(tripId) { item ->
                    item.copy(
                        likeSummary = item.likeSummary.copy(
                            likedByMe = wasLiked,
                            likeCount = item.likeSummary.likeCount + if (wasLiked) 1 else -1
                        )
                    )
                }
            }
        }
    }

    fun toggleSave(tripId: String) {
        val myId = authRepository.currentUserId() ?: return
        val current = _state.value.feed.find { it.trip.id == tripId } ?: return
        val wasSaved = current.isSaved

        applyFeedUpdate(tripId) { it.copy(isSaved = !wasSaved) }

        viewModelScope.launch {
            try {
                if (wasSaved) engagementRepository.unsave(myId, tripId) else engagementRepository.save(myId, tripId)
            } catch (e: Exception) {
                applyFeedUpdate(tripId) { it.copy(isSaved = wasSaved) }
            }
        }
    }

    private fun applyFeedUpdate(tripId: String, transform: (FeedItem) -> FeedItem) {
        _state.update { state ->
            state.copy(feed = state.feed.map { if (it.trip.id == tripId) transform(it) else it })
        }
    }

    fun markNotificationsRead() {
        val myId = authRepository.currentUserId() ?: return
        _state.update { it.copy(unreadCount = 0) }
        viewModelScope.launch {
            runCatching { notificationRepository.markAllRead(myId) }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                HomeViewModel(
                    authRepository = AuthRepository(client),
                    socialRepository = SocialRepository(client),
                    tripRepository = TripRepository(client),
                    engagementRepository = EngagementRepository(client),
                    notificationRepository = NotificationRepository(client),
                    commentRepository = CommentRepository(client),
                    shareRepository = ShareRepository(client),
                    savedPlacesRepository = SavedPlacesRepository(client),
                    geocodingService = GeocodingService(context.applicationContext)
                )
            }
        }
    }
}

/**
 * The most recent earlier-year trip that was under way on this calendar day.
 * Matching the whole range rather than just the start date means a two-week trip
 * can surface on any of its days, not only the one it began.
 */
private fun List<Trip>.findOnThisDay(today: LocalDate): OnThisDay? = this
    .filter { it.startDate.year < today.year }
    .filter { trip ->
        // Walk the trip's days; ranges are short, and this sidesteps the
        // year-boundary and leap-day traps of comparing month/day arithmetic.
        generateSequence(trip.startDate) { day ->
            if (day < trip.endDate) day.plus(1, DateTimeUnit.DAY) else null
        }.any { it.month == today.month && it.day == today.day }
    }
    .maxByOrNull { it.startDate }
    ?.let { trip ->
        OnThisDay(
            tripId = trip.id,
            title = trip.title,
            destination = trip.destination,
            yearsAgo = today.year - trip.startDate.year
        )
    }

/** This state without [authorIds]' trips and notifications. */
internal fun HomeUiState.withoutAuthors(authorIds: Set<String>): HomeUiState =
    if (authorIds.isEmpty()) this
    else copy(
        feed = feed.filter { it.trip.authorId !in authorIds },
        notifications = notifications.filter { it.actorId !in authorIds }
    )
