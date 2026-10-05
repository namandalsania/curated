package com.curated.app.features.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curated.app.core.model.BudgetTag
import com.curated.app.core.model.SeasonTag
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.CuratedFilterChip

/** The three filters that open a picker; "Following only" is a plain toggle. */
enum class PickerFilter(val title: String) {
    DURATION("Duration"),
    BUDGET("Budget"),
    SEASON("Season")
}

/** "Duration" when off; "Duration · 1–3 days" when on. */
internal fun chipLabel(filter: PickerFilter, filters: ExploreFilters): String {
    val value = when (filter) {
        PickerFilter.DURATION -> filters.tripLength?.label()
        PickerFilter.BUDGET -> filters.budgetTag?.label()
        PickerFilter.SEASON -> filters.seasonTag?.label()
    }
    return if (value == null) filter.title else "${filter.title} · $value"
}

internal fun isActive(filter: PickerFilter, filters: ExploreFilters): Boolean = when (filter) {
    PickerFilter.DURATION -> filters.tripLength != null
    PickerFilter.BUDGET -> filters.budgetTag != null
    PickerFilter.SEASON -> filters.seasonTag != null
}

/**
 * Every Explore filter in one scrolling row, always in view: Following only
 * (a toggle) first, then the three that open a picker. An active chip is
 * filled and names its value. "Clear all" appears once anything is on.
 */
@Composable
fun ExploreFilterChipRow(
    filters: ExploreFilters,
    onToggleFollowing: () -> Unit,
    onOpenPicker: (PickerFilter) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CuratedFilterChip(
            selected = filters.followScope == FollowScope.FOLLOWING,
            onClick = onToggleFollowing,
            label = "Following only",
            leadingIcon = { Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        PickerFilter.entries.forEach { filter ->
            CuratedFilterChip(
                selected = isActive(filter, filters),
                onClick = { onOpenPicker(filter) },
                label = chipLabel(filter, filters)
            )
        }
        if (filters.activeCount > 0) {
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
    }
}

/** One filter's options, chosen from a sheet - the way pickers open elsewhere in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPickerSheet(
    filter: PickerFilter,
    filters: ExploreFilters,
    onSelectDuration: (TripLength?) -> Unit,
    onSelectBudget: (BudgetTag?) -> Unit,
    onSelectSeason: (SeasonTag?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        when (filter) {
            PickerFilter.DURATION -> PickerOptions(
                title = filter.title,
                options = TripLength.entries,
                selected = filters.tripLength,
                label = { it.label() },
                onSelect = { onSelectDuration(it); onDismiss() }
            )
            PickerFilter.BUDGET -> PickerOptions(
                title = filter.title,
                options = BudgetTag.entries,
                selected = filters.budgetTag,
                label = { it.label() },
                onSelect = { onSelectBudget(it); onDismiss() }
            )
            PickerFilter.SEASON -> PickerOptions(
                title = filter.title,
                options = SeasonTag.entries,
                selected = filters.seasonTag,
                label = { it.label() },
                onSelect = { onSelectSeason(it); onDismiss() }
            )
        }
    }
}

/** The options as radio rows, with "Clear" to turn this one filter off. */
@Composable
private fun <T> PickerOptions(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg).selectableGroup()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSelect(null) }, enabled = selected != null) { Text("Clear") }
        }
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(label(option), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

internal fun TripLength.label() = when (this) {
    TripLength.SHORT -> "1–3 days"
    TripLength.MEDIUM -> "4–7 days"
    TripLength.LONG -> "8+ days"
}

internal fun BudgetTag.label() = when (this) {
    BudgetTag.BUDGET -> "Budget"
    BudgetTag.MID_RANGE -> "Mid-range"
    BudgetTag.LUXURY -> "Luxury"
}

internal fun SeasonTag.label() = when (this) {
    SeasonTag.SPRING -> "Spring"
    SeasonTag.SUMMER -> "Summer"
    SeasonTag.FALL -> "Fall"
    SeasonTag.WINTER -> "Winter"
}

// --- Previews ----------------------------------------------------------------

@Composable
private fun ChipRowPreview(filters: ExploreFilters) {
    CuratedTheme {
        ExploreFilterChipRow(
            filters = filters,
            onToggleFollowing = {},
            onOpenPicker = {},
            onClearAll = {},
            modifier = Modifier.padding(vertical = Spacing.sm)
        )
    }
}

@Preview(showBackground = true, widthDp = 420, name = "Chips · none active")
@Composable
private fun ChipRowNonePreview() = ChipRowPreview(ExploreFilters())

@Preview(showBackground = true, widthDp = 420, name = "Chips · two active")
@Composable
private fun ChipRowTwoPreview() = ChipRowPreview(
    ExploreFilters(tripLength = TripLength.SHORT, budgetTag = BudgetTag.MID_RANGE)
)

@Preview(showBackground = true, widthDp = 900, name = "Chips · all active")
@Composable
private fun ChipRowAllPreview() = ChipRowPreview(
    ExploreFilters(
        tripLength = TripLength.MEDIUM,
        budgetTag = BudgetTag.LUXURY,
        seasonTag = SeasonTag.FALL,
        followScope = FollowScope.FOLLOWING
    )
)
