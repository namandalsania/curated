package com.curated.app.core.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState

/** A single point to show on any of the app's maps, decoupled from Trip/Stop models. */
data class MapPin(
    val id: String,
    val position: LatLng,
    val title: String,
    val snippet: String? = null,
    val weight: Int = 1
)

val DEFAULT_WORLD_CAMERA: CameraPosition = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)

/**
 * Shared map rendering used by both Explore (individual trip pins) and the
 * profile's aggregate visited-places map (country pins sized by trip count).
 * Tapping a cluster zooms in via the library's default behavior; no route
 * line is drawn here since these are collections of places, not one trip's path.
 */
@Composable
fun ClusteredMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier.fillMaxSize(),
    initialCamera: CameraPosition = DEFAULT_WORLD_CAMERA,
    onPinClick: (MapPin) -> Unit = {},
    pinContent: (@Composable (MapPin) -> Unit)? = null
) {
    val cameraPositionState = rememberCameraPositionState { position = initialCamera }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = rememberCuratedMapProperties(),
        uiSettings = rememberCuratedMapUiSettings()
    ) {
        Clustering(
            items = pins.map { PinClusterItem(it) },
            onClusterItemClick = { item ->
                onPinClick(item.pin)
                true
            },
            // Total weight, not pin count: a cluster of two countries with 3 and
            // 4 trips reads "7", consistent with what each pin shows on its own.
            clusterContent = { cluster -> CuratedClusterBubble(cluster.items.sumOf { it.pin.weight }) },
            clusterItemContent = pinContent?.let { render ->
                { item: PinClusterItem -> render(item.pin) }
            }
        )
    }
}

private class PinClusterItem(val pin: MapPin) : ClusterItem {
    override fun getPosition(): LatLng = pin.position
    override fun getTitle(): String = pin.title
    override fun getSnippet(): String? = pin.snippet
    override fun getZIndex(): Float = 0f
}
