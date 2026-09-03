package earth.maps.cardinal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

class GeoUtilsTest {

    @Test
    fun formatDistance_metric_short() {
        val meters = 50.0
        val unitPreference = AppPreferences.DISTANCE_UNIT_METRIC
        val expected = "50 m"
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatDistance_metric_long_km_int() {
        val meters = 15000.0 // 15 km
        val unitPreference = AppPreferences.DISTANCE_UNIT_METRIC
        val expected = "15 km"
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatDistance_metric_long_km_float() {
        val meters = 1234.5 // 1.2345 km
        val unitPreference = AppPreferences.DISTANCE_UNIT_METRIC
        val expected = "1.2 km" // Rounded to one decimal place
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatDistance_imperial_short() {
        val meters = 30.0 // Approx 98.4 feet
        val unitPreference = AppPreferences.DISTANCE_UNIT_IMPERIAL
        val expected = "98 ft" // Rounded to nearest foot
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatDistance_imperial_long_mi_int() {
        val meters = 16093.4 // 10 miles
        val unitPreference = AppPreferences.DISTANCE_UNIT_IMPERIAL
        val expected = "10 mi"
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatDistance_imperial_long_mi_float() {
        val meters = 8046.72 // 5.00045 miles
        val unitPreference = AppPreferences.DISTANCE_UNIT_IMPERIAL
        val expected = "5.0 mi" // Rounded to one decimal place
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatDistance_defaultToMetric() {
        val meters = 500.0
        val unitPreference = -1 // Invalid unit preference
        val expected = "500 m" // Should default to metric
        val actual = GeoUtils.formatDistance(meters, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatSpeed_metric() {
        val metersPerSecond = 10.0
        val unitPreference = AppPreferences.DISTANCE_UNIT_METRIC
        val expected = "36 km/h"
        val actual = GeoUtils.formatSpeed(metersPerSecond, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatSpeed_imperial() {
        val metersPerSecond = 10.0
        val unitPreference = AppPreferences.DISTANCE_UNIT_IMPERIAL
        val expected = "22 mph"
        val actual = GeoUtils.formatSpeed(metersPerSecond, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun formatSpeed_defaultToMetric() {
        val metersPerSecond = 5.0
        val unitPreference = -1
        val expected = "18 km/h"
        val actual = GeoUtils.formatSpeed(metersPerSecond, unitPreference)
        assertEquals(expected, actual)
    }

    @Test
    fun haversineDistance_samePoint() {
        val latLng1 = LatLng(37.7749, -122.4194) // San Francisco
        val latLng2 = LatLng(37.7749, -122.4194) // Same point
        val expectedDistance = 0.0
        val actualDistance = GeoUtils.haversineDistance(latLng1, latLng2)
        assertEquals(expectedDistance, actualDistance, 0.01) // Allowing a small delta for floating point precision
    }

    @Test
    fun haversineDistance_knownDistance() {
        // Distance between New York City and Los Angeles (approx 3935 km or 3935000 meters)
        val nyc = LatLng(40.7128, -74.0060)
        val la = LatLng(34.0522, -118.2437)
        val expectedDistance = 3935746.254609722
        val actualDistance = GeoUtils.haversineDistance(nyc, la)
        assertEquals(expectedDistance, actualDistance, 1.0) // Allowing a delta of 1m
    }

    @Test
    fun createBoundingBoxAroundPoint_equator() {
        val center = LatLng(0.0, 0.0) // Equator and Prime Meridian
        val radiusMeters = 100000.0 // 100 km
        val boundingBox = GeoUtils.createBoundingBoxAroundPoint(center, radiusMeters)

        // Approximate calculations for 100km radius at the equator
        // 1 degree of latitude is approx 111,320 meters
        // 1 degree of longitude at the equator is also approx 111,320 meters
        val latDeltaDegrees = 100000.0 / 111320.0 // approx 0.898 degrees
        val lonDeltaDegrees = 100000.0 / 111320.0 // approx 0.898 degrees

        assertEquals(center.latitude + latDeltaDegrees, boundingBox.north, 0.01)
        assertEquals(center.latitude - latDeltaDegrees, boundingBox.south, 0.01)
        assertEquals(center.longitude + lonDeltaDegrees, boundingBox.east, 0.01)
        assertEquals(center.longitude - lonDeltaDegrees, boundingBox.west, 0.01)
    }

    @Test
    fun createBoundingBoxAroundPoint_pole() {
        val center = LatLng(85.0, 0.0) // Near the North Pole
        val radiusMeters = 50000.0 // 50 km
        val boundingBox = GeoUtils.createBoundingBoxAroundPoint(center, radiusMeters)

        // At high latitudes, longitude delta shrinks significantly
        val earthRadius = 6371000.0
        val latDelta = Math.toDegrees(radiusMeters / earthRadius)
        // The radius of the circle of latitude at 85 degrees North
        val radiusAtLatitude = earthRadius * cos(Math.toRadians(center.latitude))
        val lonDelta = Math.toDegrees(radiusMeters / radiusAtLatitude)

        assertEquals(center.latitude + latDelta, boundingBox.north, 0.01)
        assertEquals(center.latitude - latDelta, boundingBox.south, 0.01)
        assertEquals(center.longitude + lonDelta, boundingBox.east, 0.01)
        assertEquals(center.longitude - lonDelta, boundingBox.west, 0.01)
    }

    @Test
    fun fastDistance_samePoint() {
        val latLng1 = LatLng(37.7749, -122.4194) // San Francisco
        val latLng2 = LatLng(37.7749, -122.4194) // Same point
        val expectedDistance = 0.0
        val actualDistance = GeoUtils.fastDistance(latLng1, latLng2)
        assertEquals(expectedDistance, actualDistance, 0.01)
    }

    @Test
    fun fastDistance_shortDistance_equals_haversine() {
        // Short distance between two nearby points in San Francisco
        val point1 = LatLng(37.7749, -122.4194)
        val point2 = LatLng(37.7750, -122.4195) // About 14m apart
        val expectedDistance = GeoUtils.haversineDistance(point1, point2)
        val actualDistance = GeoUtils.fastDistance(point1, point2)
        assertEquals(expectedDistance, actualDistance, 0.01)
    }


    @Test
    fun fastDistance_shortDistance() {
        // Short distance between two nearby points in San Francisco
        val point1 = LatLng(37.7749, -122.4194)
        val point2 = LatLng(37.7750, -122.4195) // About 14m apart
        val expectedDistance = 14.173617226379271
        val actualDistance = GeoUtils.fastDistance(point1, point2)
        assertEquals(expectedDistance, actualDistance, 0.01)
    }

    @Test
    fun fastDistance_mediumDistance() {
        // Distance between San Francisco and Oakland (across the bay)
        val sf = LatLng(37.7749, -122.4194)
        val oakland = LatLng(37.8044, -122.2711)
        val expectedDistance = 13438.14841287118
        val actualDistance = GeoUtils.fastDistance(sf, oakland)
        assertEquals(expectedDistance, actualDistance, 0.01)
    }

    @Test
    fun fastDistance_longDistance() {
        // Distance between New York City and Los Angeles
        val nyc = LatLng(40.7128, -74.0060)
        val la = LatLng(34.0522, -118.2437)
        val expectedDistance = 3978193.533035539
        val actualDistance = GeoUtils.fastDistance(nyc, la)
        assertEquals(expectedDistance, actualDistance, 0.01)
    }

    @Test
    fun fastDistance_highLatitude() {
        // Test at high latitude (near Arctic Circle)
        val point1 = LatLng(66.5, 0.0)
        val point2 = LatLng(66.6, 0.1)
        val expectedDistance = 11967.60736579976
        val actualDistance = GeoUtils.fastDistance(point1, point2)
        assertEquals(expectedDistance, actualDistance, 0.01)
    }
}
