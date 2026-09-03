package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCategorySearchCoordinatorTest {

    private val matcher = SyntheticCategoryMatcher()

    @Test
    fun `native-only selection queries provider categories without synthetic fallback`() = runTest {
        val provider = mockk<NearbyProviderClient>()
        coEvery {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf("food:coffee_shop"),
                NearbyProviderOperation.NEARBY
            )
        } returns listOf(cafeResult())
        val coordinator = newCoordinator()

        val results = coordinator.searchNearbyCategories(
            nearbyProviderClient = provider,
            latitude = LATITUDE,
            longitude = LONGITUDE,
            selectedCategories = listOf("food:coffee_shop")
        )

        assertEquals(listOf(cafeResult()), results)
        coVerify(exactly = 1) {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf("food:coffee_shop"),
                NearbyProviderOperation.NEARBY
            )
        }
        coVerify(exactly = 0) { provider.searchSyntheticFallback(any(), any(), any()) }
    }

    @Test
    fun `synthetic-only selection filters broad provider bucket and uses fallback when under minimum`() = runTest {
        val provider = mockk<NearbyProviderClient>()
        coEvery {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf(FILTER_HOSPITAL),
                NearbyProviderOperation.NEARBY
            )
        } returns listOf(pharmacyResult())
        coEvery {
            provider.searchSyntheticFallback(match { it.syntheticFilter == FILTER_HOSPITAL }, LATITUDE, LONGITUDE)
        } returns listOf(hospitalResult())
        val coordinator = newCoordinator()

        val results = coordinator.searchNearbyCategories(
            nearbyProviderClient = provider,
            latitude = LATITUDE,
            longitude = LONGITUDE,
            selectedCategories = listOf(FILTER_HOSPITAL)
        )

        assertEquals(listOf(hospitalResult()), results)
        coVerify(exactly = 1) {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf(FILTER_HOSPITAL),
                NearbyProviderOperation.NEARBY
            )
        }
        coVerify(exactly = 1) {
            provider.searchSyntheticFallback(match { it.syntheticFilter == FILTER_HOSPITAL }, LATITUDE, LONGITUDE)
        }
        assertMinimumMatches(results, FILTER_HOSPITAL, expectedMinimum = 1)
        assertFalse(results.any { result -> matcher.matches(result, FILTER_PHARMACY) })
    }

    @Test
    fun `mixed selection keeps native results and recovers synthetic matches through fallback`() = runTest {
        val provider = mockk<NearbyProviderClient>()
        coEvery {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf("food:coffee_shop"),
                NearbyProviderOperation.NEARBY_NATIVE
            )
        } returns listOf(cafeResult())
        coEvery {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf(FILTER_HOSPITAL),
                NearbyProviderOperation.NEARBY_SYNTHETIC
            )
        } returns listOf(pharmacyResult())
        coEvery {
            provider.searchSyntheticFallback(match { it.syntheticFilter == FILTER_HOSPITAL }, LATITUDE, LONGITUDE)
        } returns listOf(hospitalResult())
        val coordinator = newCoordinator()

        val results = coordinator.searchNearbyCategories(
            nearbyProviderClient = provider,
            latitude = LATITUDE,
            longitude = LONGITUDE,
            selectedCategories = listOf("food:coffee_shop", FILTER_HOSPITAL)
        )

        assertEquals(listOf(cafeResult(), hospitalResult()), results)
        coVerify(exactly = 1) {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf("food:coffee_shop"),
                NearbyProviderOperation.NEARBY_NATIVE
            )
        }
        coVerify(exactly = 1) {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf(FILTER_HOSPITAL),
                NearbyProviderOperation.NEARBY_SYNTHETIC
            )
        }
        coVerify(exactly = 1) {
            provider.searchSyntheticFallback(match { it.syntheticFilter == FILTER_HOSPITAL }, LATITUDE, LONGITUDE)
        }
        assertMinimumMatches(results, FILTER_HOSPITAL, expectedMinimum = 1)
    }

    @Test
    fun `synthetic category taxonomy has provider and fallback path for every filter`() {
        SYNTHETIC_CATEGORY_FILTERS.forEach { syntheticFilter ->
            val providerCategories = listOf(syntheticFilter).toProviderNearbyCategories()
            val fallbackSearch = syntheticFallbackSearchFor(syntheticFilter)

            assertTrue(
                "Expected provider category remapping for $syntheticFilter",
                providerCategories.isNotEmpty() && syntheticFilter !in providerCategories
            )
            assertTrue(
                "Expected fallback search terms for $syntheticFilter",
                fallbackSearch?.searchTerms?.isNotEmpty() == true
            )
        }
    }

    @Test
    fun `category-only nearby search does not apply train station priority`() = runTest {
        val provider = mockk<NearbyProviderClient>()
        coEvery {
            provider.requestNearbyProviderResults(
                LATITUDE,
                LONGITUDE,
                listOf(FILTER_HOSPITAL),
                NearbyProviderOperation.NEARBY
            )
        } returns emptyList()
        coEvery {
            provider.searchSyntheticFallback(match { it.syntheticFilter == FILTER_HOSPITAL }, LATITUDE, LONGITUDE)
        } returns listOf(gareAusterlitzResult(), hopitalSaintLouisResult())
        val coordinator = newCoordinator()

        val results = coordinator.searchNearbyCategories(
            nearbyProviderClient = provider,
            latitude = LATITUDE,
            longitude = LONGITUDE,
            selectedCategories = listOf(FILTER_HOSPITAL)
        )

        assertEquals(listOf(hopitalSaintLouisResult()), results)
        assertFalse(
            "Train station priority must not reintroduce or promote internal fallback results for category-only searches",
            results.any { result -> result.geocodeId == "venue-gare-austerlitz" }
        )
    }

    private fun newCoordinator(): NearbyCategorySearchCoordinator {
        return NearbyCategorySearchCoordinator(
            syntheticCategoryMatcher = matcher,
            fallbackSearchStrategy = SyntheticFallbackSearchStrategy(
                syntheticCategoryMatcher = matcher
            )
        )
    }

    private fun assertMinimumMatches(
        results: List<GeocodeResult>,
        selectedCategory: String,
        expectedMinimum: Int
    ) {
        val matchingResults = results.count { result ->
            matcher.matches(result, selectedCategory)
        }
        assertTrue(
            "Expected at least $expectedMinimum results matching $selectedCategory, but found $matchingResults",
            matchingResults >= expectedMinimum
        )
    }

    private companion object {
        private const val LATITUDE = 19.255988333333335
        private const val LONGITUDE = 72.983735

        private fun cafeResult(): GeocodeResult {
            return fixtureResult(
                id = "venue-cafe",
                name = "Blue Tokai Coffee Roasters",
                properties = mapOf("category" to "food:coffee_shop")
            )
        }

        private fun hospitalResult(): GeocodeResult {
            return fixtureResult(
                id = "venue-hospital",
                name = "City Care Hospital",
                properties = mapOf("category" to "health:hospital")
            )
        }

        private fun pharmacyResult(): GeocodeResult {
            return fixtureResult(
                id = "venue-pharmacy",
                name = "Metro Chemist",
                properties = mapOf("category" to "health:pharmacy")
            )
        }

        private fun gareAusterlitzResult(): GeocodeResult {
            return fixtureResult(
                id = "venue-gare-austerlitz",
                name = "Gare d'Austerlitz",
                properties = mapOf("railway" to "station")
            )
        }

        private fun hopitalSaintLouisResult(): GeocodeResult {
            return fixtureResult(
                id = "venue-hopital-saint-louis",
                name = "Hôpital Saint-Louis",
                properties = mapOf("category" to "health:hospital")
            )
        }

        private fun fixtureResult(
            id: String,
            name: String,
            properties: Map<String, String>
        ): GeocodeResult {
            return GeocodeResult(
                geocodeId = id,
                latitude = LATITUDE,
                longitude = LONGITUDE,
                displayName = name,
                properties = properties
            )
        }
    }
}
