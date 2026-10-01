package com.curated.app.core.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.curated.app.R
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

/**
 * Every Google map in the app goes through these, so they all wear the
 * Curated palette (res/raw/curated_map_style.json): cream land, muted
 * grey-teal water, off-white roads. Travel detail stays - landmarks,
 * places of worship, parks, rail/metro, neighborhoods - with icons tinted
 * toward terracotta; shops, clinics, schools and bus stops are hidden so
 * street-level views don't bury our own pins.
 *
 * JSON styling is ignored if a cloud Map ID is ever set - keep it unset,
 * or move this style into the Cloud console alongside it.
 */
@Composable
fun rememberCuratedMapProperties(): MapProperties {
    val context = LocalContext.current
    return remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.curated_map_style))
    }
}

/** No Google toolbar or +/- buttons: pinch-zoom is enough and they clash with the flat chrome. */
@Composable
fun rememberCuratedMapUiSettings(): MapUiSettings = remember {
    MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false)
}

/** A terracotta place pin, for dropping a single location. Anchor it at bottom-center. */
@Composable
fun CuratedPlacePin() {
    Icon(
        Icons.Filled.Place,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp)
    )
}

/** Terracotta bubble for a cluster of pins, replacing maps-utils' default blue circle. */
@Composable
fun CuratedClusterBubble(count: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
