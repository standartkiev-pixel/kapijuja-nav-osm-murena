package earth.maps.cardinal.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaRequestLogRedactorTest {

    @Test
    fun `request log summary only includes safe routing metadata`() {
        val request = """
            {
              "alternates": 5,
              "costing": "auto_traffic_premium",
              "costing_options": {
                "auto": {
                  "use_tolls": 0.0
                }
              },
              "date_time": {
                "type": 0
              },
              "locations": [
                {
                  "lat": 48.74339666666667,
                  "lon": -0.018131666666666667
                },
                {
                  "lat": 48.242356,
                  "lon": -4.492532,
                  "type": "break"
                }
              ],
              "filters": {
                "action": "include",
                "attributes": ["shape_attributes.speed", "shape_attributes.time"]
              }
            }
        """.trimIndent()

        val summary = ValhallaRequestLogRedactor.summarize(request)

        assertTrue(summary.contains("\"alternates\":5"))
        assertTrue(summary.contains("\"profile_class\":\"auto\""))
        assertTrue(summary.contains("\"uses_traffic_profile\":true"))
        assertFalse(summary.contains("auto_traffic_premium"))
        assertFalse(summary.contains("use_tolls"))
        assertFalse(summary.contains("48.74339666666667"))
        assertFalse(summary.contains("-4.492532"))
        assertFalse(summary.contains("date_time"))
        assertFalse(summary.contains("shape_attributes"))
    }

    @Test
    fun `malformed request body returns safe placeholder`() {
        val summary = ValhallaRequestLogRedactor.summarize("{")

        assertEquals("<malformed request body>", summary)
    }

    @Test
    fun `non object request body returns safe placeholder`() {
        val summary = ValhallaRequestLogRedactor.summarize("[]")

        assertEquals("<non-object request body>", summary)
    }
}
