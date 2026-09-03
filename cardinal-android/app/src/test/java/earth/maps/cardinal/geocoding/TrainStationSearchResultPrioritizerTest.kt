package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainStationSearchResultPrioritizerTest {

    private val prioritizer = TrainStationSearchResultPrioritizer(
        classifier = TrainStationResultClassifier()
    )

    @Test
    fun `train stations are moved before non-train provider results`() {
        val quartierMontparnasse = geocodeResult(
            id = "quartier-montparnasse",
            name = "Quartier Montparnasse",
            properties = mapOf("place" to "neighbourhood")
        )
        val gareMontparnasse = geocodeResult(
            id = "gare-montparnasse",
            name = "Gare Montparnasse",
            properties = mapOf("railway" to "station")
        )
        val arretMontparnasse = geocodeResult(
            id = "arret-montparnasse",
            name = "Arrêt Montparnasse",
            properties = mapOf("highway" to "bus_stop")
        )

        val result = prioritizer.prioritize(
            listOf(quartierMontparnasse, gareMontparnasse, arretMontparnasse)
        )

        assertEquals(
            listOf(gareMontparnasse, quartierMontparnasse, arretMontparnasse),
            result
        )
    }

    @Test
    fun `train station prioritisation is stable`() {
        val quartierSaintLazare = geocodeResult(
            id = "quartier-saint-lazare",
            name = "Quartier Saint-Lazare",
            properties = mapOf("place" to "suburb")
        )
        val gareSaintLazare = geocodeResult(
            id = "gare-saint-lazare",
            name = "Gare Saint-Lazare",
            properties = mapOf("railway" to "station")
        )
        val gareParisSaintLazare = geocodeResult(
            id = "gare-paris-saint-lazare",
            name = "Gare Paris Saint-Lazare",
            properties = mapOf("station" to "train")
        )
        val galerieSaintLazare = geocodeResult(
            id = "galerie-saint-lazare",
            name = "Galerie Saint-Lazare",
            properties = mapOf("shop" to "mall")
        )

        val result = prioritizer.prioritize(
            listOf(
                quartierSaintLazare,
                gareSaintLazare,
                gareParisSaintLazare,
                galerieSaintLazare
            )
        )

        assertEquals(
            listOf(
                gareSaintLazare,
                gareParisSaintLazare,
                quartierSaintLazare,
                galerieSaintLazare
            ),
            result
        )
    }

    private fun geocodeResult(
        id: String,
        name: String,
        properties: Map<String, String>
    ): GeocodeResult {
        return GeocodeResult(
            geocodeId = id,
            latitude = 48.8443,
            longitude = 2.3744,
            displayName = name,
            properties = properties
        )
    }
}
