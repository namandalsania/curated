package com.curated.app.core.cluster

import com.curated.app.core.photo.PhotoExifData
import com.curated.app.core.util.haversineMeters

data class PhotoCluster(
    val photos: List<PhotoExifData>,
    val centerLatitude: Double,
    val centerLongitude: Double
)

/**
 * Groups geo-tagged photos into candidate stops: photos within
 * [maxDistanceMeters] of the cluster's running centroid AND within
 * [maxTimeGapSeconds] of the previous photo belong to the same stop.
 */
object StopClusteringEngine {

    const val DEFAULT_MAX_DISTANCE_METERS = 300.0
    const val DEFAULT_MAX_TIME_GAP_SECONDS = 3 * 60 * 60L

    fun cluster(
        photos: List<PhotoExifData>,
        maxDistanceMeters: Double = DEFAULT_MAX_DISTANCE_METERS,
        maxTimeGapSeconds: Long = DEFAULT_MAX_TIME_GAP_SECONDS
    ): List<PhotoCluster> {
        val geoPhotos = photos.filter { it.hasLocation }
        val sorted = geoPhotos.sortedBy { it.takenAt?.epochSeconds ?: 0L }

        val groups = mutableListOf<MutableList<PhotoExifData>>()
        for (photo in sorted) {
            val current = groups.lastOrNull()
            if (current != null && fitsInCluster(current, photo, maxDistanceMeters, maxTimeGapSeconds)) {
                current.add(photo)
            } else {
                groups.add(mutableListOf(photo))
            }
        }

        return groups.map { group ->
            PhotoCluster(
                photos = group,
                centerLatitude = group.mapNotNull { it.latitude }.average(),
                centerLongitude = group.mapNotNull { it.longitude }.average()
            )
        }
    }

    private fun fitsInCluster(
        cluster: List<PhotoExifData>,
        photo: PhotoExifData,
        maxDistanceMeters: Double,
        maxTimeGapSeconds: Long
    ): Boolean {
        val last = cluster.last()
        val timeOk = if (last.takenAt != null && photo.takenAt != null) {
            kotlin.math.abs(photo.takenAt.epochSeconds - last.takenAt.epochSeconds) <= maxTimeGapSeconds
        } else {
            true
        }

        val centerLat = cluster.mapNotNull { it.latitude }.average()
        val centerLng = cluster.mapNotNull { it.longitude }.average()
        val distanceOk = haversineMeters(centerLat, centerLng, photo.latitude!!, photo.longitude!!) <= maxDistanceMeters

        return timeOk && distanceOk
    }
}
