package com.curated.app.core.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * Appends [rings] (grid coordinates) to [path], mapped to screen as
 * `grid * scale + offset`. Points closer than [minStepPx] to the last one
 * drawn are skipped: at world scale most of the 1:50m points land within a
 * pixel of each other, and dropping them keeps the path cheap to draw.
 */
fun Path.addRings(rings: List<IntArray>, scale: Float, offset: Offset = Offset.Zero, minStepPx: Float = 0.75f) {
    val minStepSquared = minStepPx * minStepPx
    for (ring in rings) {
        var lastX = ring[0] * scale + offset.x
        var lastY = ring[1] * scale + offset.y
        moveTo(lastX, lastY)
        var drawn = 1
        var i = 2
        while (i < ring.size) {
            val x = ring[i] * scale + offset.x
            val y = ring[i + 1] * scale + offset.y
            val dx = x - lastX
            val dy = y - lastY
            if (dx * dx + dy * dy >= minStepSquared) {
                lineTo(x, y)
                lastX = x
                lastY = y
                drawn++
            }
            i += 2
        }
        // Keep sub-pixel islands as a speck rather than dropping them.
        if (drawn < 3) lineTo(lastX + minStepPx, lastY)
        close()
    }
}

/** Bounding box of [rings] in grid units, or null for no points. */
fun ringsBounds(rings: List<IntArray>): Rect? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    for (ring in rings) {
        var i = 0
        while (i < ring.size) {
            minX = minOf(minX, ring[i])
            maxX = maxOf(maxX, ring[i])
            minY = minOf(minY, ring[i + 1])
            maxY = maxOf(maxY, ring[i + 1])
            i += 2
        }
    }
    return if (minX > maxX) null else Rect(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
}
