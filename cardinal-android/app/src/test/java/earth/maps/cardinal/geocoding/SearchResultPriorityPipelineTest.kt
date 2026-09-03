package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchResultPriorityPipelineTest {

    private val pipeline = SearchResultPriorityPipeline(
        trainStationPrioritizer = TrainStationSearchResultPrioritizer(
            classifier = TrainStationResultClassifier()
        )
    )

    @Test
    fun `saved and pinned places appear before train stations and other results`() {
        val pinnedCafe = place(id = "pinned-cafe", name = "Café Lyonnais")
        val savedHotel = place(id = "saved-hotel", name = "Hôtel de Lyon")
        val centreVille = geocodeResult(
            id = "centre-ville",
            name = "Centre-ville de Lyon",
            properties = mapOf("place" to "locality")
        )
        val gareDeLyon = geocodeResult(
            id = "gare-de-lyon",
            name = "Gare de Lyon",
            properties = mapOf("railway" to "station")
        )
        val musee = geocodeResult(
            id = "musee-lyon",
            name = "Musée de Lyon",
            properties = mapOf("tourism" to "museum")
        )

        val result = pipeline.prioritize(
            query = "lyon",
            providerResults = listOf(centreVille, gareDeLyon, musee),
            savedAndPinnedPlaces = listOf(pinnedCafe, savedHotel)
        )

        assertEquals(
            listOf("Café Lyonnais", "Hôtel de Lyon", "Gare de Lyon", "Centre-ville de Lyon", "Musée de Lyon"),
            result.displayNames
        )
        assertEquals(listOf(pinnedCafe, savedHotel), result.savedAndPinnedPlaces)
        assertEquals(listOf(gareDeLyon, centreVille, musee), result.providerResults)
        assertSame(gareDeLyon, result.providerResults.first())
    }

    @Test
    fun `user-entered text search surfaces apply saved train station then other priority`() {
        val surfaces = listOf(
            SearchSurface.Home,
            SearchSurface.Directions,
            SearchSurface.NearbyText
        )

        surfaces.forEach { surface ->
            val pinnedParis = place(id = "pinned-paris-$surface", name = "Café de Paris")
            val nonTrain = geocodeResult(
                id = "quartier-paris-$surface",
                name = "Quartier de Paris",
                properties = mapOf("place" to "neighbourhood")
            )
            val trainStation = geocodeResult(
                id = "gare-paris-$surface",
                name = "Gare de Paris",
                properties = mapOf("railway" to "station")
            )

            val result = pipeline.prioritize(
                query = "paris",
                providerResults = listOf(nonTrain, trainStation),
                savedAndPinnedPlaces = listOf(pinnedParis),
                surface = surface
            )

            assertEquals(
                "Unexpected priority order for $surface",
                listOf("Café de Paris", "Gare de Paris", "Quartier de Paris"),
                result.displayNames
            )
            assertEquals(listOf(pinnedParis), result.savedAndPinnedPlaces)
            assertEquals(listOf(trainStation, nonTrain), result.providerResults)
        }
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

    private fun place(id: String, name: String): Place {
        return Place(
            id = id,
            name = name,
            latLng = LatLng(48.8566, 2.3522)
        )
    }
}
