package com.curated.app.features.explore

/**
 * Where Explore's camera should open so every trip pin is on screen: a box
 * around them, or a single point at city zoom when there's only one place to
 * show (one pin, or several in the same spot) - fitting a box of zero size would
 * zoom all the way in. Null when there's nothing to fit, which keeps the
 * default world view.
 *
 * Google Maps won't zoom out past level 3 on this map (measured on a Pixel-size
 * screen), which shows about 72 degrees of longitude - roughly [MAX_SPAN] once
 * the fit's margins are taken off. Pins spread wider than that (Mexico City to
 * Tokyo) can't all fit, and centering on their middle can land on an ocean or
 * a continent with none of them. So the camera fits the most pins that do fit
 * side by side instead.
 */
sealed interface MapFit {
    data class Point(val latitude: Double, val longitude: Double) : MapFit
    data class Box(val south: Double, val west: Double, val north: Double, val east: Double) : MapFit

    companion object {
        /** Close enough to see a city's neighborhoods. */
        const val CITY_ZOOM = 11f

        /** Pins closer than this (in degrees, ~10 m) count as the same spot. */
        private const val SAME_SPOT = 0.0001

        /** Widest spread of longitudes a phone-sized map can fit, margins included, at its furthest zoom. */
        const val MAX_SPAN = 45.0

        fun of(points: List<Pair<Double, Double>>): MapFit? {
            if (points.isEmpty()) return null
            val fitting = mostThatFit(points)
            if (fitting.size < points.size) return of(fitting)
            val south = points.minOf { it.first }
            val north = points.maxOf { it.first }
            val west = points.minOf { it.second }
            val east = points.maxOf { it.second }
            return if (north - south < SAME_SPOT && east - west < SAME_SPOT) {
                Point(points.first().first, points.first().second)
            } else {
                Box(south, west, north, east)
            }
        }

        /** The largest set of points whose longitudes lie within [MAX_SPAN] of each other. */
        private fun mostThatFit(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
            val byLongitude = points.sortedBy { it.second }
            var best = 0 until 1
            var start = 0
            for (end in byLongitude.indices) {
                while (byLongitude[end].second - byLongitude[start].second > MAX_SPAN) start++
                if (end - start + 1 > best.count()) best = start..end
            }
            return byLongitude.slice(best)
        }
    }
}
