/*
 * Cardinal Maps / Kapijuja country download traffic mask
 * GPL-3.0-or-later
 */
package earth.maps.cardinal.tileserver

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Conservative basemap-only country mask.
 *
 * This class deliberately does NOT participate in Valhalla routing, Ferrostar, MapLibre rendering,
 * or the geocoder format. Country downloads still keep the original rectangular OfflineArea and
 * the original rectangular Valhalla tile coverage. Only the list of basemap HTTP requests is
 * reduced for z10-z14.
 *
 * If a boundary is unavailable or cannot be parsed, [containsBufferedTile] returns true so the
 * downloader falls back to the old full rectangle rather than risking a coverage hole.
 */
class CountryTileMask(context: Context) {
    private data class Point(val lon: Double, val lat: Double)
    private data class Ring(
        val points: List<Point>,
        val minLon: Double,
        val maxLon: Double,
        val minLat: Double,
        val maxLat: Double
    )

    private val ringsByCountry: Map<String, List<Ring>> = try {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            parse(JSONObject(reader.readText()))
        }
    } catch (error: Exception) {
        Log.e(TAG, "Failed to load country boundary mask; using full rectangles", error)
        emptyMap()
    }

    fun containsBufferedTile(
        countryCode: String,
        zoom: Int,
        x: Int,
        y: Int,
        bufferKm: Double = DEFAULT_BUFFER_KM
    ): Boolean {
        // Low zooms are tiny in number and their tiles are geographically large. Keeping the
        // complete original rectangle avoids low-zoom edge artefacts for negligible traffic.
        if (zoom < MASK_MIN_ZOOM) return true

        val rings = ringsByCountry[countryCode.uppercase()] ?: return true
        if (rings.isEmpty()) return true

        val center = tileCenter(zoom, x, y)
        val tileMarginKm = tileHalfDiagonalKm(zoom, center.lat)
        val effectiveBufferKm = bufferKm + tileMarginKm

        for (ring in rings) {
            if (!withinExpandedBounds(center, ring, effectiveBufferKm)) continue
            if (pointInRing(center, ring.points)) return true
            if (distanceToRingKm(center, ring.points) <= effectiveBufferKm) return true
        }
        return false
    }

    private fun parse(root: JSONObject): Map<String, List<Ring>> {
        val geometries = root.getJSONObject("geometries")
        val result = mutableMapOf<String, List<Ring>>()

        for (countryCode in geometries.keys()) {
            val geometry = geometries.getJSONObject(countryCode)
            val type = geometry.getString("type")
            val coordinates = geometry.getJSONArray("coordinates")
            val rings = when (type) {
                "Polygon" -> polygonOuterRings(coordinates)
                "MultiPolygon" -> multiPolygonOuterRings(coordinates)
                else -> emptyList()
            }.mapNotNull(::toRing)
            if (rings.isNotEmpty()) result[countryCode] = rings
        }
        return result
    }

    private fun polygonOuterRings(coordinates: JSONArray): List<JSONArray> =
        if (coordinates.length() > 0) listOf(coordinates.getJSONArray(0)) else emptyList()

    private fun multiPolygonOuterRings(coordinates: JSONArray): List<JSONArray> {
        val rings = mutableListOf<JSONArray>()
        for (i in 0 until coordinates.length()) {
            val polygon = coordinates.getJSONArray(i)
            if (polygon.length() > 0) rings.add(polygon.getJSONArray(0))
        }
        return rings
    }

    private fun toRing(json: JSONArray): Ring? {
        if (json.length() < 3) return null
        val points = ArrayList<Point>(json.length())
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (i in 0 until json.length()) {
            val pair = json.getJSONArray(i)
            val point = Point(pair.getDouble(0), pair.getDouble(1))
            points.add(point)
            minLon = min(minLon, point.lon)
            maxLon = max(maxLon, point.lon)
            minLat = min(minLat, point.lat)
            maxLat = max(maxLat, point.lat)
        }
        return Ring(points, minLon, maxLon, minLat, maxLat)
    }

    private fun tileCenter(zoom: Int, x: Int, y: Int): Point {
        val n = 2.0.pow(zoom.toDouble())
        val lon = (x + 0.5) / n * 360.0 - 180.0
        val mercator = PI * (1.0 - 2.0 * (y + 0.5) / n)
        val lat = Math.toDegrees(atan(sinh(mercator)))
        return Point(lon, lat)
    }

    private fun tileHalfDiagonalKm(zoom: Int, latitude: Double): Double {
        val n = 2.0.pow(zoom.toDouble())
        val widthKm = 40_075.016686 * cos(Math.toRadians(latitude)) / n
        val heightKm = 40_007.863 * cos(Math.toRadians(latitude)) / n
        return 0.5 * sqrt(widthKm * widthKm + heightKm * heightKm)
    }

    private fun withinExpandedBounds(point: Point, ring: Ring, bufferKm: Double): Boolean {
        val latPad = bufferKm / KM_PER_LAT_DEGREE
        val cosLat = max(0.15, cos(Math.toRadians(point.lat)))
        val lonPad = bufferKm / (KM_PER_LON_DEGREE_AT_EQUATOR * cosLat)
        return point.lat >= ring.minLat - latPad &&
            point.lat <= ring.maxLat + latPad &&
            point.lon >= ring.minLon - lonPad &&
            point.lon <= ring.maxLon + lonPad
    }

    private fun pointInRing(point: Point, ring: List<Point>): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            val crosses = ((a.lat > point.lat) != (b.lat > point.lat)) &&
                (point.lon < (b.lon - a.lon) * (point.lat - a.lat) /
                    ((b.lat - a.lat).takeIf { kotlin.math.abs(it) > 1e-12 } ?: 1e-12) + a.lon)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun distanceToRingKm(point: Point, ring: List<Point>): Double {
        var bestSquared = Double.POSITIVE_INFINITY
        var previous = ring.last()
        for (current in ring) {
            bestSquared = min(
                bestSquared,
                pointToSegmentSquaredKm(point, previous, current)
            )
            previous = current
        }
        return sqrt(bestSquared)
    }

    private fun pointToSegmentSquaredKm(point: Point, a: Point, b: Point): Double {
        val cosLat = cos(Math.toRadians(point.lat))
        val ax = (a.lon - point.lon) * KM_PER_LON_DEGREE_AT_EQUATOR * cosLat
        val ay = (a.lat - point.lat) * KM_PER_LAT_DEGREE
        val bx = (b.lon - point.lon) * KM_PER_LON_DEGREE_AT_EQUATOR * cosLat
        val by = (b.lat - point.lat) * KM_PER_LAT_DEGREE
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-12) return ax * ax + ay * ay
        val t = ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return cx * cx + cy * cy
    }

    companion object {
        private const val TAG = "CountryTileMask"
        private const val ASSET_NAME = "europe_country_boundaries.json"
        private const val MASK_MIN_ZOOM = 10
        const val DEFAULT_BUFFER_KM = 50.0
        private const val KM_PER_LAT_DEGREE = 110.574
        private const val KM_PER_LON_DEGREE_AT_EQUATOR = 111.320
    }
}
