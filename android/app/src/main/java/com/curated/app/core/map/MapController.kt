package com.curated.app.core.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

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
 * Tapping a cluster zooms in until its pins split apart; no route line is
 * drawn here since these are collections of places, not one trip's path.
 */
@Composable
fun ClusteredMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier.fillMaxSize(),
    initialCamera: CameraPosition = DEFAULT_WORLD_CAMERA,
    /** Pass one to move or read the camera from outside; otherwise the map keeps its own. */
    cameraPositionState: CameraPositionState = rememberCameraPositionState { position = initialCamera },
    /** Space covered by other UI (a bottom sheet); fitting and the visible region stay clear of it. */
    contentPadding: PaddingValues = PaddingValues(),
    onMapLoaded: () -> Unit = {},
    onPinClick: (MapPin) -> Unit = {},
    /**
     * Tapped a cluster whose pins all sit in one spot, which zooming can never
     * split. Null keeps the old behavior: step the zoom in.
     */
    onStackClick: ((List<MapPin>) -> Unit)? = null,
    /** Drawn as ordinary UI, then turned into the marker's bitmap by the clustering library. */
    pinContent: (@Composable @UiComposable (MapPin) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        onMapLoaded = onMapLoaded,
        properties = rememberCuratedMapProperties(),
        uiSettings = rememberCuratedMapUiSettings()
    ) {
        Clustering(
            items = pins.map { PinClusterItem(it) },
            onClusterClick = { cluster ->
                val points = cluster.items.map { it.position }
                val sameSpot = allAtSameSpot(points.map { it.latitude to it.longitude })
                if (sameSpot && onStackClick != null) {
                    onStackClick(cluster.items.map { it.pin })
                    return@Clustering true
                }
                val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
                val update = if (sameSpot) {
                    // Nothing to fit: step in instead.
                    CameraUpdateFactory.newLatLngZoom(cluster.position, cameraPositionState.position.zoom + 3f)
                } else {
                    CameraUpdateFactory.newLatLngBounds(bounds, with(density) { ClusterZoomPadding.roundToPx() })
                }
                scope.launch { runCatching { cameraPositionState.animate(update) } }
                true
            },
            onClusterItemClick = { item ->
                onPinClick(item.pin)
                true
            },
            // Total weight, not pin count: a cluster of two countries with 3 and
            // 4 trips reads "7", consistent with what each pin shows on its own.
            clusterContent = { cluster -> CuratedClusterBubble(cluster.items.sumOf { it.pin.weight }) },
            clusterItemContent = pinContent?.let { render ->
                { item: PinClusterItem -> render(item.pin) }
            },
            // The library only groups 4 or more pins by default, so 2-3 trips
            // in one city drew as stacked pins: one visible, the others
            // unreachable. Grouping from 2 gives them a count to tap instead.
            onClusterManager = { manager ->
                (manager.renderer as? DefaultClusterRenderer<PinClusterItem>)?.minClusterSize = MIN_CLUSTER_SIZE
            }
        )
    }
}

private const val MIN_CLUSTER_SIZE = 2

/** Room around a cluster's pins once it's zoomed into. */
private val ClusterZoomPadding = 64.dp

private class PinClusterItem(val pin: MapPin) : ClusterItem {
    override fun getPosition(): LatLng = pin.position
    override fun getTitle(): String = pin.title
    override fun getSnippet(): String? = pin.snippet
    override fun getZIndex(): Float = 0f
}
