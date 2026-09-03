package earth.maps.cardinal.ui.util

import android.util.Log
import earth.maps.cardinal.data.LatLng
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import javax.inject.Inject
import kotlin.math.abs

/**
 * Utility class for determining optimal placement of route duration annotations on a map.
 * 
 * This class solves the problem of placing annotations (such as route duration text) on polylines
 * in a way that users can clearly identify which annotation applies to which route. The primary
 * challenge is avoiding placement on route segments where multiple routes overlap, as this would
 * create visual ambiguity.
 * 
 * The algorithm works by:
 * 1. Analyzing all routes to identify overlapping segments
 * 2. Finding divergent segments (where only one route exists) for each route
 * 3. Selecting the longest divergent segment for optimal visibility
 * 4. Placing the annotation at the midpoint of that segment
 * 
 * @constructor Creates an instance of AnnotationPlacer using dependency injection
 */
class AnnotationPlacer @Inject constructor() {

    /**
     * Determines optimal annotation placement points for a collection of routes.
     * 
     * This method analyzes the geometry of all provided routes to find non-overlapping
     * segments where annotations can be placed without visual ambiguity. For each route,
     * it identifies the longest segment that doesn't overlap with any other route and
     * places the annotation at the midpoint of that segment.
     * 
     * The algorithm follows these steps:
     * 1. Count overlapping points across all routes
     * 2. For each route, identify contiguous segments where overlap count == 1 (diverged)
     * 3. Track cumulative distance along each divergent segment
     * 4. Select the longest divergent segment for each route
     * 5. Place annotation at the midpoint of the selected segment
     * 
     * @param routes A list of Route objects containing geographic coordinate data
     * @return A Map where each Route is mapped to its optimal annotation placement LatLng coordinate.
     *         Routes with no divergent segments will be excluded from the result.
     * 
     * @see Route for the route data structure
     * @see LatLng for the coordinate representation
     * 
     * Example usage:
     * ```kotlin
     * val annotationPlacer = AnnotationPlacer()
     * val routes = listOf(route1, route2, route3)
     * val placements = annotationPlacer.placeAnnotations(routes)
     * 
     * placements.forEach { (route, position) ->
     *     // Place annotation at 'position' for 'route'
     * }
     * ```
     */
    fun placeAnnotations(routes: List<Route>): Map<Route, LatLng> {
        Log.d(TAG, "calculating annotations for ${routes.size} routes")
        
        if (routes.isEmpty()) {
            return emptyMap()
        }
        
        val routesLatLng = convertRoutesToLatLng(routes)
        val routeOverlapCount = countOverlappingPoints(routesLatLng)
        
        return routesLatLng.indices.mapNotNull { index ->
            findOptimalPlacement(routes[index], routesLatLng[index], routeOverlapCount)
        }.toMap()
    }
    
    /**
     * Converts Route objects to lists of LatLng coordinates.
     */
    private fun convertRoutesToLatLng(routes: List<Route>): List<List<LatLng>> {
        return routes.map { route ->
            route.geometry.map { LatLng(it.lat, it.lng) }
        }
    }
    
    /**
     * Counts how many routes pass through each geographic point.
     */
    private fun countOverlappingPoints(routesLatLng: List<List<LatLng>>): Map<LatLng, Int> {
        val routeOverlapCount: MutableMap<LatLng, Int> = mutableMapOf()
        
        for (route in routesLatLng) {
            for (point in route) {
                routeOverlapCount.merge(point, 1, Int::plus)
            }
        }
        
        return routeOverlapCount
    }
    
    /**
     * Finds the optimal annotation placement for a single route.
     * Returns null if no suitable placement is found.
     */
    private fun findOptimalPlacement(
        route: Route,
        routeLatLng: List<LatLng>,
        routeOverlapCount: Map<LatLng, Int>
    ): Pair<Route, LatLng>? {
        val divergentSegments = identifyDivergentSegments(routeLatLng, routeOverlapCount)
        
        if (divergentSegments.isEmpty()) {
            Log.w(TAG, "Route with zero divergent segments found, skipping")
            return null
        }
        
        val bestSegment = selectLongestSegment(divergentSegments)
        val midpoint = findSegmentMidpoint(bestSegment)
        
        return Pair(route, midpoint)
    }
    
    /**
     * Identifies all contiguous divergent segments in a route.
     * A segment is divergent when only one route passes through its points.
     */
    private fun identifyDivergentSegments(
        route: List<LatLng>,
        routeOverlapCount: Map<LatLng, Int>
    ): List<List<Pair<LatLng, Double>>> {
        val segments: MutableList<List<Pair<LatLng, Double>>> = mutableListOf()
        var currentSegment: MutableList<Pair<LatLng, Double>>? = null
        var currentSegmentLengthMeters: Double? = null
        
        for (point in route) {
            val isDiverged = routeOverlapCount[point] == 1
            
            if (isDiverged) {
                if (currentSegment != null) {
                    // Continue current segment
                    currentSegmentLengthMeters = currentSegmentLengthMeters?.plus(
                        currentSegment.lastOrNull()?.first?.fastDistanceTo(point) ?: 0.0
                    )
                    currentSegment.add(Pair(point, currentSegmentLengthMeters ?: 0.0))
                } else {
                    // Start new segment
                    currentSegment = mutableListOf(Pair(point, 0.0))
                    currentSegmentLengthMeters = 0.0
                }
            } else {
                // End current segment if we hit an overlapping point
                if (currentSegment != null) {
                    segments.add(currentSegment)
                    currentSegment = null
                    currentSegmentLengthMeters = null
                }
            }
        }
        
        // Add the final segment if the route ends while diverged
        if (currentSegment != null) {
            segments.add(currentSegment)
        }
        
        return segments
    }
    
    /**
     * Selects the longest divergent segment from all available segments.
     */
    private fun selectLongestSegment(segments: List<List<Pair<LatLng, Double>>>): List<Pair<LatLng, Double>> {
        return segments
            .maxByOrNull { segment -> segment.lastOrNull()?.second ?: 0.0 }
            ?: throw IllegalStateException("No segments available when selecting longest segment")
    }
    
    /**
     * Finds the midpoint of a segment based on cumulative distance.
     */
    private fun findSegmentMidpoint(segment: List<Pair<LatLng, Double>>): LatLng {
        val segmentLength = segment.lastOrNull()?.second
            ?: throw IllegalStateException("Empty segment found when finding midpoint")
        
        val midpoint = segment.minByOrNull { abs(it.second - segmentLength / 2.0) }
            ?: throw IllegalStateException("Empty segment found after sorting when finding midpoint")
        
        return midpoint.first
    }

    companion object {
        /** Tag used for logging debug and warning messages */
        const val TAG = "AnnotationPlacer"
    }
}
