package earth.maps.cardinal.ui.core

import android.content.Context
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.data.Address
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.OrientationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.ViewportPreferences
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.SavedPlace
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.geocoding.OfflineGeocodingService
import earth.maps.cardinal.ui.util.AnnotationPlacer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: MapViewModel

    private val mockViewportPreferences = mockk<ViewportPreferences>(relaxed = true)
    private val mockViewportRepository = mockk<ViewportRepository>(relaxed = true)
    private val mockLocationRepository = mockk<LocationRepository>()
    private val mockOrientationRepository = mockk<OrientationRepository>()
    private val mockGeocodingService = mockk<GeocodingService>()
    private val mockOfflineGeocodingService = mockk<OfflineGeocodingService>()
    private val mockPlaceDao = mockk<earth.maps.cardinal.data.room.SavedPlaceDao>()

    @Before
    fun setup() {
        context = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).get()

        // Setup default repository behaviors
        every { mockLocationRepository.isLocating } returns MutableStateFlow(false)
        every { mockLocationRepository.locationFlow } returns MutableStateFlow(null)
        coEvery { mockLocationRepository.getCurrentLocation(any()) } returns null
        every { mockLocationRepository.createSearchResultPlace(any()) } returns Place(
            name = "Test Place", latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0), address = null
        )

        every { mockOrientationRepository.azimuth } returns MutableStateFlow<Float>(0f)
        coEvery { mockGeocodingService.reverseGeocode(any(), any()) } returns emptyList()

        every { mockPlaceDao.getAllPlacesAsFlow() } returns flowOf(emptyList())

        viewModel = MapViewModel(
            context = context,
            viewportPreferences = mockViewportPreferences,
            viewportRepository = mockViewportRepository,
            locationRepository = mockLocationRepository,
            orientationRepository = mockOrientationRepository,
            geocodingService = mockGeocodingService,
            offlineGeocodingService = mockOfflineGeocodingService,
            placeDao = mockPlaceDao,
            annotationPlacer = AnnotationPlacer()
        )

        // Initialize screen dimensions
        viewModel.peekHeight = 100.dp
        viewModel.screenHeight = 800.dp
        viewModel.screenWidth = 600.dp
    }

    @Test
    fun `saveViewport should call viewportPreferences saveViewport`() = runTest {
        val cameraPosition = CameraPosition(
            target = Position(0.0, 0.0), zoom = 10.0
        )

        viewModel.saveViewport(cameraPosition)

        verify { mockViewportPreferences.saveViewport(cameraPosition) }
        verify { mockViewportRepository.updateViewportCenter(cameraPosition) }
    }

    @Test
    fun `loadViewport should return saved viewport from preferences`() = runTest {
        val expectedCameraPosition = CameraPosition(
            target = Position(1.0, 1.0), zoom = 15.0
        )

        every { mockViewportPreferences.loadViewport() } returns expectedCameraPosition

        val result = viewModel.loadViewport()

        assertThat(result).isEqualTo(expectedCameraPosition)
    }

    @Test
    fun `loadViewport should return null when no viewport is saved`() = runTest {
        every { mockViewportPreferences.loadViewport() } returns null

        val result = viewModel.loadViewport()

        assertThat(result).isNull()
    }

    @Test
    fun `updateViewportCenter should call viewportRepository updateViewportCenter`() = runTest {
        val cameraPosition = CameraPosition(
            target = Position(2.0, 2.0), zoom = 12.0
        )

        viewModel.updateViewportCenter(cameraPosition)

        verify { mockViewportRepository.updateViewportCenter(cameraPosition) }
    }

    @Test
    fun `markLocationRequestPending should update hasPendingLocationRequest to true`() = runTest {
        viewModel.markLocationRequestPending()

        assertThat(viewModel.hasPendingLocationRequest.first()).isTrue()
    }

    @Test
    fun `isLocating should reflect LocationRepository's isLocating state`() = runTest {
        val expectedLocatingState = MutableStateFlow(false)
        every { mockLocationRepository.isLocating } returns expectedLocatingState

        // Re-initialize viewModel to use the new mock
        viewModel = MapViewModel(
            context = context,
            viewportPreferences = mockViewportPreferences,
            viewportRepository = mockViewportRepository,
            locationRepository = mockLocationRepository,
            orientationRepository = mockOrientationRepository,
            geocodingService = mockGeocodingService,
            offlineGeocodingService = mockOfflineGeocodingService,
            placeDao = mockPlaceDao,
            annotationPlacer = AnnotationPlacer()
        )

        assertThat(viewModel.isLocating.first()).isFalse()

        expectedLocatingState.value = true
        assertThat(viewModel.isLocating.first()).isTrue()

        expectedLocatingState.value = false
        assertThat(viewModel.isLocating.first()).isFalse()
    }

    @Test
    fun `locationFlow should reflect LocationRepository's locationFlow state`() = runTest {
        val expectedLocation = android.location.Location("test").apply {
            latitude = 37.7749
            longitude = -122.4194
        }
        val expectedLocationFlow = MutableStateFlow(expectedLocation)
        every { mockLocationRepository.locationFlow } returns expectedLocationFlow

        // Re-initialize viewModel to use the new mock
        viewModel = MapViewModel(
            context = context,
            viewportPreferences = mockViewportPreferences,
            viewportRepository = mockViewportRepository,
            locationRepository = mockLocationRepository,
            orientationRepository = mockOrientationRepository,
            geocodingService = mockGeocodingService,
            offlineGeocodingService = mockOfflineGeocodingService,
            placeDao = mockPlaceDao,
            annotationPlacer = AnnotationPlacer()

        )

        assertThat(viewModel.locationFlow.first()).isEqualTo(expectedLocation)

        val newLocation = android.location.Location("test").apply {
            latitude = 34.0522
            longitude = -118.2437
        }
        expectedLocationFlow.value = newLocation
        assertThat(viewModel.locationFlow.first()).isEqualTo(newLocation)
    }

    @Test
    fun `heading should reflect OrientationRepository's azimuth state`() = runTest {
        val expectedHeading = 45.0f
        val expectedHeadingFlow = MutableStateFlow(expectedHeading)
        every { mockOrientationRepository.azimuth } returns expectedHeadingFlow

        // Re-initialize viewModel to use the new mock
        viewModel = MapViewModel(
            context = context,
            viewportPreferences = mockViewportPreferences,
            viewportRepository = mockViewportRepository,
            locationRepository = mockLocationRepository,
            orientationRepository = mockOrientationRepository,
            geocodingService = mockGeocodingService,
            offlineGeocodingService = mockOfflineGeocodingService,
            placeDao = mockPlaceDao,
            annotationPlacer = AnnotationPlacer()
        )

        assertThat(viewModel.heading.first()).isEqualTo(expectedHeading)

        val newHeading = 90.0f
        expectedHeadingFlow.value = newHeading
        assertThat(viewModel.heading.first()).isEqualTo(newHeading)
    }

    @Test
    fun `savedPlacesFlow should reflect PlaceDao's data`() = runTest {
        val savedPlace = SavedPlace(
            id = "1",
            placeId = 0,
            name = "Test Place",
            type = "Point of Interest",
            icon = "default_icon",
            latitude = 37.7749,
            longitude = -122.4194,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val expectedPlacesFlow = flowOf(listOf(savedPlace))
        every { mockPlaceDao.getAllPlacesAsFlow() } returns expectedPlacesFlow

        // Re-initialize viewModel to use the new mock
        viewModel = MapViewModel(
            context = context,
            viewportPreferences = mockViewportPreferences,
            viewportRepository = mockViewportRepository,
            locationRepository = mockLocationRepository,
            orientationRepository = mockOrientationRepository,
            geocodingService = mockGeocodingService,
            offlineGeocodingService = mockOfflineGeocodingService,
            placeDao = mockPlaceDao,
            annotationPlacer = AnnotationPlacer()
        )

        val featureCollection = viewModel.savedPlacesFlow.first()
        assertThat(featureCollection.features).hasSize(1)
        assertThat(featureCollection.features.first().properties["name"].toString()).isEqualTo("\"Test Place\"")
    }

    @Test
    fun `enrichPlaceWithReverseGeocodedCountry should add missing country without replacing POI details`() = runTest {
        val poi = Place(
            name = "Spice Up",
            description = "Restaurant",
            latLng = LatLng(19.118, 72.911),
            address = Address(road = "Forest Avenue")
        )
        val reverseGeocodedPlace = Place(
            name = "Nearby Address",
            description = "Address",
            latLng = poi.latLng,
            address = Address(country = "India", countryCode = "IN")
        )
        coEvery {
            mockGeocodingService.reverseGeocode(19.118, 72.911)
        } returns listOf(reverseGeocodedPlace)

        val enrichedPlace = viewModel.enrichPlaceWithReverseGeocodedCountry(poi)

        assertThat(enrichedPlace.name).isEqualTo("Spice Up")
        assertThat(enrichedPlace.description).isEqualTo("Restaurant")
        assertThat(enrichedPlace.address?.road).isEqualTo("Forest Avenue")
        assertThat(enrichedPlace.address?.country).isEqualTo("India")
        assertThat(enrichedPlace.address?.countryCode).isEqualTo("IN")
    }

    @Test
    fun `enrichPlaceWithReverseGeocodedCountry should use coordinate country fallback when reverse geocode is empty`() =
        runTest {
            val poi = Place(
                name = "Changan burger",
                description = "Restaurant",
                latLng = LatLng(-36.8509, 174.7645),
                address = Address(road = "18 Kitchener St", city = "Auckland")
            )
            coEvery { mockGeocodingService.reverseGeocode(any(), any()) } returns emptyList()

            val enrichedPlace = viewModel.enrichPlaceWithReverseGeocodedCountry(poi)

            assertThat(enrichedPlace.address?.road).isEqualTo("18 Kitchener St")
            assertThat(enrichedPlace.address?.city).isEqualTo("Auckland")
            assertThat(enrichedPlace.address?.country).isEqualTo("New Zealand")
            assertThat(enrichedPlace.address?.countryCode).isEqualTo("NZ")
        }

}
