package com.curated.app.features.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.core.format.label
import com.curated.app.core.model.PlanItem
import com.curated.app.core.model.SavedPlace
import com.curated.app.core.model.StopCategory
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing

/** What a row shows, from either a saved place or a place in a plan. */
data class PlaceDisplay(
    val name: String,
    val category: StopCategory,
    val city: String?,
    val photoUrl: String?,
    val sourceTripTitle: String?,
    val sourceAuthorName: String?
)

fun SavedPlace.toDisplay() = PlaceDisplay(name, category, city, photoUrl, sourceTripTitle, sourceAuthorName)

fun PlanItem.toDisplay() = PlaceDisplay(name, category, city, photoUrl, sourceTripTitle, sourceAuthorName)

/**
 * One place: photo, name, "Food · Naples", and who it came from.
 * [note] adds a line of its own ("added by Leo"), and [trailing] holds the
 * row's controls (remove, reorder, a checkbox...).
 */
@Composable
fun PlaceRow(
    place: PlaceDisplay,
    modifier: Modifier = Modifier,
    showCredit: Boolean = true,
    note: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(CuratedCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            place.photoUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                place.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(place.category.label(), place.city).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showCredit) creditLine(place)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

/** "from Maya's Naples in 3 days" - credit to the creator the place came from. */
private fun creditLine(place: PlaceDisplay): String? {
    val title = place.sourceTripTitle ?: return null
    val author = place.sourceAuthorName
    return if (author != null) "from $author's $title" else "from $title"
}
