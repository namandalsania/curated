package com.curated.app.core.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Country outlines for the visited-countries map, from assets/world_map.json
 * (Natural Earth 1:50m, built by tools/build_world_map.py).
 *
 * Outlines are pre-projected to Equal Earth on a [width] x [height] integer
 * grid, so drawing is just scaling. Antarctica is left out on purpose.
 */
class WorldMapData(
    val width: Int,
    val height: Int,
    /** ISO alpha-2 -> rings, each ring flattened as x0, y0, x1, y1, ... on the grid. */
    val outlines: Map<String, List<IntArray>>,
    /** ISO alpha-2 -> continent code (AF, AS, EU, NA, SA, OC), including countries too small to outline. */
    val continentOf: Map<String, String>,
    private val xMin: Double,
    private val yMax: Double,
    private val gridScale: Double
) {
    val aspectRatio: Float get() = width.toFloat() / height

    /** Each country's bounding box on the grid, so a zoomed view can skip what's off-screen. */
    val countryBounds: Map<String, Rect> by lazy {
        outlines.mapNotNull { (code, rings) -> ringsBounds(rings)?.let { code to it } }.toMap()
    }

    /** Where a real-world point lands on the grid, for countries with no outline (Singapore, Malta...). */
    fun project(latitude: Double, longitude: Double): Offset {
        val (x, y) = equalEarth(longitude, latitude)
        return Offset(((x - xMin) * gridScale).toFloat(), ((yMax - y) * gridScale).toFloat())
    }
}

object WorldMaps {
    private const val ASSET = "world_map.json"
    private val mutex = Mutex()
    private var cached: WorldMapData? = null
    private val json = Json { ignoreUnknownKeys = true }

    /** Parsed once per process (~470 KB of JSON, ~56k points), off the main thread. */
    suspend fun load(context: Context): WorldMapData = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val text = context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
            json.decodeFromString<WorldMapJson>(text).toData()
        }.also { cached = it }
    }
}

/** Approximate count of the world's countries (UN members + observers), for "% of the world". */
const val WORLD_COUNTRY_COUNT = 195

// Equal Earth (Savric, Patterson & Jenny, 2018). Must match tools/build_world_map.py.
private const val A1 = 1.340264
private const val A2 = -0.081106
private const val A3 = 0.000893
private const val A4 = 0.003796
private val M = sqrt(3.0) / 2

private fun equalEarth(longitudeDeg: Double, latitudeDeg: Double): Pair<Double, Double> {
    val lambda = Math.toRadians(longitudeDeg)
    val theta = asin(M * sin(Math.toRadians(latitudeDeg)))
    val t2 = theta * theta
    val t6 = t2 * t2 * t2
    val x = lambda * cos(theta) / (M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)))
    val y = theta * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    return x to y
}

@Serializable
private class WorldMapJson(
    val w: Int,
    val h: Int,
    val bounds: List<Double>,
    val countries: List<CountryJson>,
    val continents: Map<String, String>
) {
    fun toData(): WorldMapData {
        val (xMin, _, xMax, yMax) = bounds
        return WorldMapData(
            width = w,
            height = h,
            outlines = countries.associate { country -> country.c to country.p.map { it.toIntArray() } },
            continentOf = continents,
            xMin = xMin,
            yMax = yMax,
            gridScale = w / (xMax - xMin)
        )
    }
}

@Serializable
private class CountryJson(val c: String, val p: List<List<Int>>)
