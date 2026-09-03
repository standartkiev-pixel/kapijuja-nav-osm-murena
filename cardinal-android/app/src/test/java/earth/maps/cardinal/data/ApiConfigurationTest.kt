package earth.maps.cardinal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigurationTest {

    @Test
    fun `maskSensitiveQueryParamsForLogs redacts search text coordinates and api key`() {
        val logLine = "REQUEST: https://maps.example.test/search?text=cafe+near+me&size=40" +
            "&point.lat=19.255988333333335&point.lon=72.983735" +
            "&focus.point.lat=19.255988333333335&focus.point.lon=72.983735" +
            "&boundary.circle.lat=19.255988333333335&boundary.circle.lon=72.983735" +
            "&api_key=secret-key"

        assertEquals(
            "REQUEST: https://maps.example.test/search?text=****&size=40" +
                "&point.lat=****&point.lon=****" +
                "&focus.point.lat=****&focus.point.lon=****" +
                "&boundary.circle.lat=****&boundary.circle.lon=****" +
                "&api_key=****",
            logLine.maskSensitiveQueryParamsForLogs()
        )
    }
}
