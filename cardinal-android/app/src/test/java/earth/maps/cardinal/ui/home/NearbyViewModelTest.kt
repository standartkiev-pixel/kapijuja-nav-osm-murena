package earth.maps.cardinal.ui.home

import android.location.Location
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.geocoding.FILTER_HOSPITAL
import earth.maps.cardinal.geocoding.FILTER_TRANSPORTATION
import earth.maps.cardinal.geocoding.GeocodingService
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NearbyViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: NearbyViewModel

    private val mockGeocodingService = mockk<GeocodingService>(relaxed = true)
    private val mockLocationRepository = mockk<LocationRepository>()
    private val locationFlow = MutableStateFlow<Location?>(null)

    @Before
    fun setup() {
        coEvery { mockLocationRepository.locationFlow } returns locationFlow

        viewModel = NearbyViewModel(
            geocodingService = mockGeocodingService,
            locationRepository = mockLocationRepository,
            nearbyFilterPolicy = NearbyFilterPolicy()
        )
    }

    @Test
    fun `initial state should have empty nearby results, not loading, and no error`() {
        assertTrue(viewModel.nearbyResults.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `fetchNearby should update results when successful`() = runTest {
        val testPlaces = listOf(testPlace("1"), testPlace("2"))
        coEvery { mockGeocodingService.nearby(any(), any(), listOf()) } returns testPlaces

        viewModel.fetchNearby(TEST_LATITUDE, TEST_LONGITUDE)
        advanceUntilIdle()

        assertEquals(testPlaces, viewModel.nearbyResults.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `fetchNearby should set error state when geocoding fails`() = runTest {
        val errorMessage = "Failed to fetch nearby places"
        coEvery { mockGeocodingService.nearby(any(), any(), listOf()) } throws Exception(errorMessage)

        viewModel.fetchNearby(TEST_LATITUDE, TEST_LONGITUDE)
        advanceUntilIdle()

        assertTrue(viewModel.nearbyResults.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
        assertEquals(errorMessage, viewModel.error.value)
    }

    @Test
    fun `refreshData should call fetchNearby with last location when available`() = runTest {
        val testLocation = testLocation(TEST_LATITUDE, TEST_LONGITUDE)
        val testPlaces = listOf(testPlace("1"))
        coEvery { mockGeocodingService.nearby(any(), any(), listOf()) } returns testPlaces

        locationFlow.value = testLocation
        advanceUntilIdle()
        clearMocks(mockGeocodingService)

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify {
            mockGeocodingService.nearby(
                testLocation.latitude,
                testLocation.longitude,
                listOf()
            )
        }
    }

    @Test
    fun `refreshData should do nothing when no last location is available`() = runTest {
        viewModel.refreshData()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            mockGeocodingService.nearby(any(), any(), any())
        }
        assertTrue(viewModel.nearbyResults.value.isEmpty())
    }

    @Test
    fun `distinctUntilChanged should ignore location updates closer than 250 meters`() = runTest {
        coEvery { mockGeocodingService.nearby(any(), any(), listOf()) } returns emptyList()
        val loc1 = testLocation(37.7749, -122.4194)
        val loc2 = testLocation(37.7749, -122.4193)
        val loc3 = testLocation(37.77549231327316, -122.42456035029012)

        locationFlow.value = loc1
        advanceUntilIdle()
        locationFlow.value = loc2
        advanceUntilIdle()
        locationFlow.value = loc3
        advanceUntilIdle()

        coVerify(exactly = 2) {
            mockGeocodingService.nearby(
                match { it == loc1.latitude || it == loc3.latitude },
                match { it == loc1.longitude || it == loc3.longitude },
                listOf(),
            )
        }
    }

    @Test
    fun `toggleCategorySelection should update selected categories and refresh with effective categories`() = runTest {
        val testLocation = testLocation(TEST_LATITUDE, TEST_LONGITUDE)
        coEvery { mockGeocodingService.nearby(any(), any(), any()) } returns emptyList()
        locationFlow.value = testLocation
        advanceUntilIdle()
        clearMocks(mockGeocodingService)

        viewModel.toggleCategorySelection("transportation")
        advanceUntilIdle()

        assertEquals(setOf("transportation"), viewModel.getSelectedCategoriesSet())
        assertEquals(listOf(FILTER_TRANSPORTATION), viewModel.getEffectiveCategoriesList())
        coVerify {
            mockGeocodingService.nearby(
                testLocation.latitude,
                testLocation.longitude,
                listOf(FILTER_TRANSPORTATION)
            )
        }
    }

    @Test
    fun `applyCategoryFilters should search typed text when nothing is selected`() = runTest {
        val testLocation = testLocation(TEST_LATITUDE, TEST_LONGITUDE)
        coEvery { mockGeocodingService.nearby(any(), any(), any()) } returns emptyList()
        coEvery { mockGeocodingService.nearbySearch(any(), any(), any()) } returns emptyList()
        locationFlow.value = testLocation
        advanceUntilIdle()
        clearMocks(mockGeocodingService)

        viewModel.beginCategoryFilterEdit()
        viewModel.updateDraftCategorySearchQuery("cafe near me")
        viewModel.applyCategoryFilters()
        advanceUntilIdle()

        assertTrue(viewModel.getSelectedCategoriesSet().isEmpty())
        assertTrue(viewModel.getEffectiveCategoriesList().isEmpty())
        assertTrue(viewModel.isFilterApplied.value)
        coVerify {
            mockGeocodingService.nearbySearch(
                testLocation.latitude,
                testLocation.longitude,
                "cafe near me"
            )
        }
        coVerify(exactly = 0) { mockGeocodingService.nearby(any(), any(), any()) }
    }

    @Test
    fun `applyCategoryFilters should search selected categories instead of typed text`() = runTest {
        val testLocation = testLocation(TEST_LATITUDE, TEST_LONGITUDE)
        coEvery { mockGeocodingService.nearby(any(), any(), any()) } returns emptyList()
        coEvery { mockGeocodingService.nearbySearch(any(), any(), any()) } returns emptyList()
        locationFlow.value = testLocation
        advanceUntilIdle()
        clearMocks(mockGeocodingService)

        viewModel.beginCategoryFilterEdit()
        viewModel.updateDraftCategorySearchQuery("food:pizza")
        viewModel.toggleDraftCategorySelection("health:hospital")
        viewModel.toggleDraftCategorySelection("food:coffee_shop")
        viewModel.applyCategoryFilters()
        advanceUntilIdle()

        assertEquals(setOf("health:hospital", "food:coffee_shop"), viewModel.getSelectedCategoriesSet())
        assertEquals(listOf(FILTER_HOSPITAL, "food:coffee_shop"), viewModel.getEffectiveCategoriesList())
        assertTrue(viewModel.categorySearchQuery.value.isEmpty())
        coVerify {
            mockGeocodingService.nearby(
                testLocation.latitude,
                testLocation.longitude,
                listOf(FILTER_HOSPITAL, "food:coffee_shop")
            )
        }
        coVerify(exactly = 0) { mockGeocodingService.nearbySearch(any(), any(), any()) }
    }

    @Test
    fun `resetDraftCategoryFilters should not clear applied categories`() = runTest {
        viewModel.beginCategoryFilterEdit()
        viewModel.toggleDraftCategorySelection("health")
        viewModel.applyCategoryFilters()

        viewModel.beginCategoryFilterEdit()
        viewModel.toggleDraftCategorySelection("food:coffee_shop")
        viewModel.updateDraftCategorySearchQuery("bakery")
        viewModel.resetDraftCategoryFilters()

        assertEquals(setOf("health"), viewModel.getSelectedCategoriesSet())
        assertEquals(listOf("health"), viewModel.getEffectiveCategoriesList())
        assertTrue(viewModel.draftSelectedCategories.value.isEmpty())
        assertTrue(viewModel.draftCategorySearchQuery.value.isEmpty())
        assertTrue(viewModel.isFilterApplied.value)
    }

    private fun testLocation(latitude: Double, longitude: Double): Location {
        return Location("test").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
    }

    private fun testPlace(id: String): Place {
        return Place(
            id = id,
            name = "Test Place $id",
            latLng = LatLng(0.0, 0.0),
            address = null
        )
    }

    private companion object {
        private const val TEST_LATITUDE = 37.7749
        private const val TEST_LONGITUDE = -122.4194
    }
}
