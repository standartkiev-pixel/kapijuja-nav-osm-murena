package earth.maps.cardinal.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaRouteNetworkDiagnosticsTest {

    @Test
    fun `disabled diagnostics do not log or evaluate request summary`() {
        var configEvaluated = false
        var requestBodyEvaluated = false
        val diagnostics = ValhallaRouteNetworkDiagnostics(
            isEnabled = false,
            log = { _, _ -> error("Disabled diagnostics must not log") }
        )

        diagnostics.logRouteRequest(
            endpoint = "https://api.example.test/route",
            config = {
                configEvaluated = true
                "hasApiKey=false"
            },
            requestBody = {
                requestBodyEvaluated = true
                """{"locations":[{"lat":48.74339666666667,"lon":-0.018131666666666667}]}"""
            }
        )

        assertFalse(configEvaluated)
        assertFalse(requestBodyEvaluated)
    }

    @Test
    fun `enabled diagnostics logs route metadata summary`() {
        val messages = mutableListOf<String>()
        val diagnostics = ValhallaRouteNetworkDiagnostics(
            isEnabled = true,
            log = { tag, message -> messages += "$tag: $message" }
        )

        diagnostics.logRouteRequest(
            endpoint = "https://api.example.test/route",
            config = { "baseUrl=https://api.example.test/route, hasApiKey=false" },
            requestBody = {
                """
                    {
                      "alternates": 5,
                      "costing": "auto_traffic_premium",
                      "locations": [
                        {
                          "lat": 48.74339666666667,
                          "lon": -0.018131666666666667
                        }
                      ]
                    }
                """.trimIndent()
            }
        )

        assertTrue(messages.any { message ->
            message.contains("Calling Valhalla route endpoint=https://api.example.test/route")
        })
        assertTrue(messages.any { message ->
            message.contains("REQUEST SUMMARY:") &&
                message.contains("\"profile_class\":\"auto\"") &&
                message.contains("\"uses_traffic_profile\":true")
        })
        assertFalse(messages.any { message -> message.contains("48.74339666666667") })
    }
}
