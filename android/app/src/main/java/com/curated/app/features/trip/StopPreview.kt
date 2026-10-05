package com.curated.app.features.trip

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

// The one-line "A → B → C +N" summary of a trip's or a day's stops: the feed
// card and the live trip's day cards both show it.

private const val STOP_PREVIEW_LIMIT = 3

/** The names line and the count of stops it leaves out. */
data class StopPreview(val names: String, val remaining: Int)

/**
 * "Sintra → Belem → Alfama" plus however many stops that leaves out.
 *
 * Consecutive repeats collapse into one name - an itinerary that returns to the
 * same station twice in a row reads as a stutter otherwise. A collapsed name
 * still stands for every stop it absorbed, so those stops count as shown and
 * aren't counted again in the remainder.
 */
fun stopPreview(names: List<String>, stopCount: Int): StopPreview {
    val runs = mutableListOf<Pair<String, Int>>()
    names.forEach { name ->
        val last = runs.lastOrNull()
        if (last != null && last.first == name) {
            runs[runs.lastIndex] = last.first to last.second + 1
        } else {
            runs.add(name to 1)
        }
    }
    val shown = runs.take(STOP_PREVIEW_LIMIT)
    val namedStops = shown.sumOf { it.second }
    val total = maxOf(stopCount, names.size)
    return StopPreview(
        names = shown.joinToString(" → ") { it.first },
        remaining = (total - namedStops).coerceAtLeast(0)
    )
}

/**
 * Stop names and their "+N" as separate Texts.
 *
 * As one string the suffix was the first thing to disappear: long names ate the
 * line and the ellipsis swallowed the count, so a 17-stop trip could look like a
 * 3-stop one. Only the names give up space now.
 */
@Composable
fun StopPreviewRow(preview: StopPreview) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            preview.names,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (preview.remaining > 0) {
            Text(
                " +${preview.remaining}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
