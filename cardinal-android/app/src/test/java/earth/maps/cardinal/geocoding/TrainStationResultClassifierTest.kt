package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainStationResultClassifierTest {

    private val classifier = TrainStationResultClassifier()

    @Test
    fun `language-neutral metadata identifies train stations in France regardless of display language`() {
        val trainStations = listOf(
            geocodeResult(name = "Gare du Nord", properties = mapOf("railway" to "station")),
            geocodeResult(name = "Gare de l'Est", properties = mapOf("railway" to "halt")),
            geocodeResult(
                name = "Gare de Strasbourg",
                properties = mapOf(
                    "public_transport" to "station",
                    "station" to "train"
                )
            ),
            geocodeResult(name = "Gare de Bordeaux", properties = mapOf("station" to "train")),
            geocodeResult(
                name = "Gare de Lille Europe",
                properties = mapOf("category" to "transportation:train_station")
            ),
            geocodeResult(name = "Lyon Part-Dieu Railway Station", properties = mapOf("railway" to "station")),
            geocodeResult(name = "Bahnhof Lyon-Part-Dieu", properties = mapOf("station" to "train")),
            geocodeResult(
                name = "Gare de Lyon",
                properties = mapOf(
                    "category" to "transportation",
                    "railway" to "station"
                )
            )
        )

        trainStations.forEach { result ->
            assertTrue(
                "${result.displayName} should be classified as a train station",
                classifier.isTrainStation(result)
            )
        }
    }

    @Test
    fun `display names alone do not identify train stations when metadata is missing`() {
        val results = listOf(
            geocodeResult(name = "Gare du Nord"),
            geocodeResult(name = "Lyon Part-Dieu Railway Station"),
            geocodeResult(name = "Bahnhof Lyon-Part-Dieu"),
            geocodeResult(name = "Station ferroviaire de Nice"),
            geocodeResult(name = "Gare de Beziers"),
            geocodeResult(name = "Gare de Béziers")
        )

        results.forEach { result ->
            assertFalse(
                "${result.displayName} should not be classified as a train station without metadata",
                classifier.isTrainStation(result)
            )
        }
    }

    @Test
    fun `explicit non-train metadata vetoes ambiguous station names`() {
        val nonTrainResults = listOf(
            geocodeResult(
                name = "Gare Routière de Lyon",
                properties = mapOf("amenity" to "bus_station")
            ),
            geocodeResult(
                name = "Station de Métro Nation",
                properties = mapOf("station" to "metro")
            )
        )

        nonTrainResults.forEach { result ->
            assertFalse(
                "${result.displayName} should not be classified as a train station",
                classifier.isTrainStation(result)
            )
        }
    }

    @Test
    fun `broad transport signals do not identify train stations by themselves`() {
        val nonTrainResults = listOf(
            geocodeResult(
                name = "Gare Routière de Lyon",
                properties = mapOf("amenity" to "bus_station")
            ),
            geocodeResult(
                name = "Station de Métro Nation",
                properties = mapOf("railway" to "subway")
            ),
            geocodeResult(
                name = "Station de Taxi Opéra",
                properties = mapOf("amenity" to "taxi")
            ),
            geocodeResult(
                name = "Station-service Total",
                properties = mapOf("amenity" to "fuel")
            ),
            geocodeResult(
                name = "Maison des Mobilités",
                properties = mapOf("category" to "transportation")
            )
        )

        nonTrainResults.forEach { result ->
            assertFalse(
                "${result.displayName} should not be classified as a train station",
                classifier.isTrainStation(result)
            )
        }
    }

    private fun geocodeResult(
        name: String,
        properties: Map<String, String> = emptyMap()
    ): GeocodeResult {
        return GeocodeResult(
            geocodeId = name,
            latitude = 48.8443,
            longitude = 2.3744,
            displayName = name,
            properties = properties
        )
    }
}
