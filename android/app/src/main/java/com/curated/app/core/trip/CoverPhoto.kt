package com.curated.app.core.trip

import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.model.StopCategory

/**
 * The one cover rule, for trips and (later) for single days: the first photo of
 * the first stop that isn't where you slept, falling back to the first photo at
 * all. A hotel room is rarely the picture a trip wants to lead with.
 *
 * [stops] must already be in reading order - day, then position in the day.
 */
fun deriveCover(stops: List<StopWithPhotos>): String? =
    stops.firstOrNull { it.stop.category != StopCategory.HOTEL && it.photoUrls.isNotEmpty() }?.photoUrls?.first()
        ?: stops.firstNotNullOfOrNull { it.photoUrls.firstOrNull() }
