package com.curated.app.features.explore

/** The part of the map on screen, in degrees. */
data class MapRegion(val south: Double, val west: Double, val north: Double, val east: Double) {
    private val latSpan get() = north - south
    private val lngSpan get() = east - west
    private val centerLat get() = (north + south) / 2
    private val centerLng get() = (east + west) / 2

    /**
     * Whether the map now shows somewhere meaningfully different from [before]:
     * panned by more than a fifth of the view, or zoomed by more than about a
     * third of a level. Small nudges don't count, so "Search this area" doesn't
     * flicker up for a slip of the thumb.
     */
    fun differsFrom(before: MapRegion): Boolean {
        val panned = kotlin.math.abs(centerLat - before.centerLat) > before.latSpan * PAN_FRACTION ||
            kotlin.math.abs(centerLng - before.centerLng) > before.lngSpan * PAN_FRACTION
        val ratio = if (before.lngSpan > 0) lngSpan / before.lngSpan else 1.0
        val zoomed = ratio > ZOOM_RATIO || ratio < 1 / ZOOM_RATIO
        return panned || zoomed
    }

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    private companion object {
        const val PAN_FRACTION = 0.2
        const val ZOOM_RATIO = 1.25
    }
}
