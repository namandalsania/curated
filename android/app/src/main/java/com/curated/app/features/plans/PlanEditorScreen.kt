package com.curated.app.features.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.core.format.Noun
import com.curated.app.core.format.countText
import com.curated.app.core.format.label
import com.curated.app.core.geocode.PlaceSearchService
import com.curated.app.core.geocode.PlaceSuggestion
import com.curated.app.core.model.InviteStatus
import com.curated.app.core.model.PlanItem
import com.curated.app.core.model.PlanMember
import com.curated.app.core.model.PlanRole
import com.curated.app.core.model.SavedPlace
import com.curated.app.core.model.StopCategory
import com.curated.app.core.model.User
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.CuratedFilterChip
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlanEditorScreen(
    planId: String,
    onBack: () -> Unit,
    onOpenSavedPlaces: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlanEditorViewModel = viewModel(key = planId, factory = PlanEditorViewModel.factory(context, planId))
    val state by viewModel.state.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var addingToDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var addingCustomToDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmRemoveDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var showPeople by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.closed) { if (state.closed) onBack() }

    val plan = state.plan

    if (renaming && plan != null) {
        RenameDialog(current = plan.title, onRename = { viewModel.rename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (confirmDelete && plan != null) {
        ConfirmDialog(
            title = "Delete \"${plan.title}\"?",
            body = "It goes away for everyone on it. Saved places stay saved.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; viewModel.deletePlan() },
            onDismiss = { confirmDelete = false }
        )
    }
    if (confirmLeave && plan != null) {
        ConfirmDialog(
            title = "Leave \"${plan.title}\"?",
            body = "You'll lose access unless the owner invites you again.",
            confirmLabel = "Leave",
            onConfirm = { confirmLeave = false; viewModel.leavePlan() },
            onDismiss = { confirmLeave = false }
        )
    }
    confirmRemoveDay?.let { day ->
        val count = state.days.getOrNull(day - 1)?.size ?: 0
        ConfirmDialog(
            title = "Remove Day $day?",
            body = "Its ${if (count == 1) "place leaves" else "$count places leave"} this plan (they stay saved). Later days move up one.",
            confirmLabel = "Remove day",
            onConfirm = { confirmRemoveDay = null; viewModel.removeDay(day) },
            onDismiss = { confirmRemoveDay = null }
        )
    }
    addingToDay?.let { day ->
        AddPlacesSheet(
            day = day,
            places = state.addablePlaces,
            hasSavedPlaces = state.savedPlaces.isNotEmpty(),
            onAdd = { ids -> viewModel.addPlaces(ids, day); addingToDay = null },
            onAddCustom = { addingToDay = null; addingCustomToDay = day },
            onOpenSavedPlaces = { addingToDay = null; onOpenSavedPlaces() },
            onDismiss = { addingToDay = null }
        )
    }
    addingCustomToDay?.let { day ->
        AddCustomPlaceSheet(
            day = day,
            onAdd = { name, category, latitude, longitude, city ->
                viewModel.addCustomPlace(day, name, category, latitude, longitude, city)
                addingCustomToDay = null
            },
            onDismiss = { addingCustomToDay = null }
        )
    }
    if (showPeople && plan != null) {
        PeopleSheet(
            state = state,
            onInvite = viewModel::invite,
            onSearch = viewModel::searchPeople,
            onChangeRole = viewModel::changeRole,
            onRemoveMember = viewModel::removeMember,
            onLeave = { showPeople = false; confirmLeave = true },
            onDismiss = { showPeople = false; viewModel.searchPeople("") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        plan?.title ?: "Plan",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = state.isOwner, onClickLabel = "Rename plan") { renaming = true }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (plan != null) {
                        IconButton(onClick = { showPeople = true }) {
                            Icon(Icons.Outlined.People, contentDescription = "People on this plan")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Plan options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (state.isOwner) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                                    DropdownMenuItem(text = { Text("Delete plan") }, onClick = { menuOpen = false; confirmDelete = true })
                                } else {
                                    DropdownMenuItem(text = { Text("Leave plan") }, onClick = { menuOpen = false; confirmLeave = true })
                                }
                            }
                        }
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
                state.error != null || plan == null -> ErrorState(
                    message = state.error ?: "Couldn't load this plan.",
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        PlanSummary(
                            placeCount = state.items.size,
                            dayCount = plan.dayCount,
                            people = 1 + state.acceptedMembers.size,
                            myRole = state.myRole,
                            actionError = state.actionError
                        )
                    }

                    state.days.forEachIndexed { index, dayItems ->
                        val day = index + 1
                        item(key = "day-$day") {
                            DaySection(
                                day = day,
                                dayCount = plan.dayCount,
                                items = dayItems,
                                canEdit = state.canEdit,
                                peopleById = state.peopleById,
                                myUserId = state.myUserId,
                                onMoveUp = viewModel::moveUp,
                                onMoveDown = viewModel::moveDown,
                                onMoveToDay = viewModel::moveToDay,
                                onRemove = viewModel::remove,
                                onAddPlaces = { addingToDay = day },
                                onRemoveDay = {
                                    if (dayItems.isEmpty()) viewModel.removeDay(day) else confirmRemoveDay = day
                                }
                            )
                        }
                    }

                    if (state.canEdit) {
                        item {
                            SecondaryButton(
                                onClick = viewModel::addDay,
                                enabled = plan.dayCount < PLAN_MAX_DAYS,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
                            ) {
                                Text("Add a day")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSummary(
    placeCount: Int,
    dayCount: Int,
    people: Int,
    myRole: PlanRole?,
    actionError: String?
) {
    Column(modifier = Modifier.padding(top = Spacing.xs)) {
        Text(
            buildString {
                append(countText(placeCount, Noun.PLACE))
                append(" · ")
                append(countText(dayCount, Noun.DAY))
                if (people > 1) append(" · ").append(countText(people, Noun.PERSON))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (myRole == PlanRole.VIEWER) {
            Text(
                "You can view this plan. Ask the owner for editing access to change it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
        actionError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

@Composable
private fun DaySection(
    day: Int,
    dayCount: Int,
    items: List<PlanItem>,
    canEdit: Boolean,
    peopleById: Map<String, User>,
    myUserId: String?,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onMoveToDay: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAddPlaces: () -> Unit,
    onRemoveDay: () -> Unit
) {
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Day $day",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (canEdit && dayCount > 1) {
                    var dayMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { dayMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Day $day options")
                        }
                        DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                            DropdownMenuItem(text = { Text("Remove day") }, onClick = { dayMenu = false; onRemoveDay() })
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Text(
                    "Nothing planned yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }

            items.forEachIndexed { index, item ->
                if (index > 0) HairlineDivider(modifier = Modifier.padding(start = Spacing.md))
                // Only worth saying who added it once more than one person can.
                val addedBy = item.addedBy
                    ?.takeIf { it != myUserId && peopleById.size > 1 }
                    ?.let { peopleById[it]?.displayName }
                PlaceRow(
                    place = item.toDisplay(),
                    note = addedBy?.let { "added by $it" },
                    modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs)
                        .padding(end = if (canEdit) 0.dp else Spacing.md)
                ) {
                    if (canEdit) {
                        Column {
                            IconButton(
                                onClick = { onMoveUp(item.id) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move ${item.name} up")
                            }
                            IconButton(
                                onClick = { onMoveDown(item.id) },
                                enabled = index < items.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move ${item.name} down")
                            }
                        }
                        var itemMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { itemMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "${item.name} options")
                            }
                            DropdownMenu(expanded = itemMenu, onDismissRequest = { itemMenu = false }) {
                                (1..dayCount).filter { it != day }.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text("Move to Day $target") },
                                        onClick = { itemMenu = false; onMoveToDay(item.id, target) }
                                    )
                                }
                                if (dayCount > 1) HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Remove from plan") },
                                    onClick = { itemMenu = false; onRemove(item.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (canEdit) {
                TextButton(onClick = onAddPlaces, modifier = Modifier.padding(start = Spacing.xs)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add places", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }
    }
}

/** Saved places not yet in the plan, grouped by country, checked off and added in one go. */
@Composable
private fun AddPlacesSheet(
    day: Int,
    places: List<SavedPlace>,
    hasSavedPlaces: Boolean,
    onAdd: (List<String>) -> Unit,
    onAddCustom: () -> Unit,
    onOpenSavedPlaces: () -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
            Text(
                "Add to Day $day",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            // Anything at all - a coffee shop, a restaurant, "laundry" - even if
            // nobody has published a trip with it.
            SecondaryButton(
                onClick = onAddCustom,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add a place of your own", modifier = Modifier.padding(start = Spacing.xs))
            }
            if (places.isNotEmpty()) {
                Text(
                    "Or pick from your saved places",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
            if (places.isEmpty()) {
                Text(
                    if (hasSavedPlaces) {
                        "Every place you've saved is already in this plan. Save more from other people's trips."
                    } else {
                        "You haven't saved any places yet. Tap the bookmark on a place in someone's trip to save it."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
                TextButton(onClick = onOpenSavedPlaces, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                    Text("See saved places")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    groupByCountry(places).forEach { (country, inCountry) ->
                        item(key = "country-$country") {
                            Text(
                                country,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs)
                            )
                        }
                        items(inCountry, key = { it.id }) { place ->
                            val checked = place.id in selected
                            val toggle = { selected = if (checked) selected - place.id else selected + place.id }
                            PlaceRow(
                                place = place.toDisplay(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = toggle)
                                    .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { toggle() },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
                PrimaryButton(
                    onClick = { onAdd(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Text(
                        when (selected.size) {
                            0 -> "Choose places"
                            1 -> "Add 1 place"
                            else -> "Add ${selected.size} places"
                        }
                    )
                }
            }
        }
    }
}

/** Who's on the plan. The owner can invite by username, change roles and remove people. */
@Composable
private fun PeopleSheet(
    state: PlanEditorState,
    onInvite: (User, PlanRole) -> Unit,
    onSearch: (String) -> Unit,
    onChangeRole: (String, PlanRole) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    var inviteRole by rememberSaveable { mutableStateOf(PlanRole.EDITOR) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                "People",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )

            state.owner?.let { owner ->
                PersonRow(user = owner, trailingText = "Owner")
            }
            state.members.forEach { member ->
                MemberRow(
                    member = member,
                    canManage = state.isOwner,
                    onChangeRole = { role -> onChangeRole(member.userId, role) },
                    onRemove = { onRemoveMember(member.userId) }
                )
            }

            if (state.isOwner) {
                HairlineDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                Text(
                    "Invite someone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    RoleChoiceChip("Editor", inviteRole == PlanRole.EDITOR) { inviteRole = PlanRole.EDITOR }
                    RoleChoiceChip("Viewer", inviteRole == PlanRole.VIEWER) { inviteRole = PlanRole.VIEWER }
                }
                OutlinedTextField(
                    value = state.memberSearch.query,
                    onValueChange = onSearch,
                    label = { Text("Search by username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)
                )
                if (state.memberSearch.query.isNotBlank() && state.memberSearch.results.isEmpty() && !state.memberSearch.isSearching) {
                    Text(
                        "Nobody found with that username.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(state.memberSearch.results, key = { it.id }) { user ->
                        PersonRow(
                            user = user,
                            onClick = { onInvite(user, inviteRole) },
                            trailingText = "Invite"
                        )
                    }
                }
            } else {
                HairlineDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                TextButton(onClick = onLeave, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                    Text("Leave plan", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: PlanMember,
    canManage: Boolean,
    onChangeRole: (PlanRole) -> Unit,
    onRemove: () -> Unit
) {
    val user = member.user ?: return
    val pending = member.status == InviteStatus.PENDING
    Row(verticalAlignment = Alignment.CenterVertically) {
        PersonRow(
            user = user,
            modifier = Modifier.weight(1f),
            trailingText = member.role.label + if (pending) " · invited" else ""
        )
        if (canManage) {
            var menu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.padding(end = Spacing.xs)) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "${user.displayName} options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (member.role != PlanRole.EDITOR) {
                        DropdownMenuItem(text = { Text("Make editor") }, onClick = { menu = false; onChangeRole(PlanRole.EDITOR) })
                    }
                    if (member.role != PlanRole.VIEWER) {
                        DropdownMenuItem(text = { Text("Make viewer") }, onClick = { menu = false; onChangeRole(PlanRole.VIEWER) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (pending) "Cancel invite" else "Remove from plan") },
                        onClick = { menu = false; onRemove() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    user: User,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    user.displayName.firstOrNull()?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailingText?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoleChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * Add any place to a plan: a name is enough. Searching for it just pins it to
 * a spot on the map, which is optional - you often know the place before you
 * know exactly where it is.
 */
@Composable
private fun AddCustomPlaceSheet(
    day: Int,
    onAdd: (String, StopCategory, Double?, Double?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val placeSearch = remember { PlaceSearchService(context) }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(StopCategory.FOOD) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var chosenName by remember { mutableStateOf<String?>(null) }
    var chosenLatitude by remember { mutableStateOf<Double?>(null) }
    var chosenLongitude by remember { mutableStateOf<Double?>(null) }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "Add to Day $day",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What is it?") },
                placeholder = { Text("Fabrica Coffee Roasters") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StopCategory.entries.forEach { option ->
                    CuratedFilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = option.label()
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find it (optional)") },
                placeholder = { Text("Costco Hawaii") },
                singleLine = true,
                trailingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            when {
                isSearching -> Text(
                    "Searching…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                chosenName != null -> Text(
                    "Pinned to ${chosenName.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                query.isNotBlank() && results.isEmpty() -> Text(
                    "Nothing found - that's fine, the name alone is enough.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            results.take(5).forEach { suggestion ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val details = placeSearch.details(suggestion)
                                chosenName = details?.address ?: suggestion.subtitle ?: suggestion.title
                                chosenLatitude = details?.latitude
                                chosenLongitude = details?.longitude
                                details?.let { category = it.category }
                                if (name.isBlank()) name = details?.name ?: suggestion.title
                                query = details?.name ?: suggestion.title
                                results = emptyList()
                            }
                        }
                        .padding(vertical = Spacing.xs)
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
                }
            }

            PrimaryButton(
                onClick = { onAdd(name, category, chosenLatitude, chosenLongitude, chosenName) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add to Day $day")
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename plan") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onRename(title) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
