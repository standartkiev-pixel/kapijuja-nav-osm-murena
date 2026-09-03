package earth.maps.cardinal.geocoding

import org.junit.Assert.assertEquals
import org.junit.Test

class PeliasGeocodingServiceTest {

    @Test
    fun `splitNearbyCategoriesForProviderRequests separates native and synthetic categories`() {
        val split = splitNearbyCategoriesForProviderRequests(
            listOf(
                "food:coffee_shop",
                FILTER_BAR,
                FILTER_HOSPITAL
            )
        )

        assertEquals(listOf("food:coffee_shop"), split.nativeCategories)
        assertEquals(listOf(FILTER_BAR, FILTER_HOSPITAL), split.syntheticCategories)
    }
}
