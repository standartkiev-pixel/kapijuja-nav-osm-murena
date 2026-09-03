package earth.maps.cardinal.ui.place

import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.room.SavedPlace
import earth.maps.cardinal.data.room.SavedPlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceCardViewModelTest {

    private lateinit var viewModel: PlaceCardViewModel

    private val mockSavedPlaceRepository = mockk<SavedPlaceRepository>()

    @Before
    fun setup() {
        viewModel = PlaceCardViewModel(
            savedPlaceRepository = mockSavedPlaceRepository
        )
    }

    @Test
    fun `setPlace should update place and check if place is saved`() = runTest {
        val testPlace = Place(
            id = "1",
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )
        val savedPlace = SavedPlace.fromPlace(testPlace)

        coEvery { mockSavedPlaceRepository.getPlaceById("1") } returns Result.success(savedPlace)

        viewModel.setPlace(testPlace)

        assertThat(viewModel.place.value).isEqualTo(testPlace)
        assertThat(viewModel.isPlaceSaved.value).isTrue()
        coVerify { mockSavedPlaceRepository.getPlaceById("1") }
    }

    @Test
    fun `checkIfPlaceIsSaved should set isPlaceSaved to true when place exists`() = runTest {
        val testPlace = Place(
            id = "1",
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )
        val savedPlace = SavedPlace.fromPlace(testPlace)

        coEvery { mockSavedPlaceRepository.getPlaceById("1") } returns Result.success(savedPlace)

        viewModel.checkIfPlaceIsSaved(testPlace)

        assertThat(viewModel.isPlaceSaved.value).isTrue()
    }

    @Test
    fun `checkIfPlaceIsSaved should set isPlaceSaved to false when place does not exist`() = runTest {
        val testPlace = Place(
            id = "1",
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )

        coEvery { mockSavedPlaceRepository.getPlaceById("1") } returns Result.success(null)

        viewModel.checkIfPlaceIsSaved(testPlace)

        assertThat(viewModel.isPlaceSaved.value).isFalse()
    }

    @Test
    fun `checkIfPlaceIsSaved should not check when place id is null`() = runTest {
        val testPlace = Place(
            id = null,
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )

        viewModel.checkIfPlaceIsSaved(testPlace)

        assertThat(viewModel.isPlaceSaved.value).isFalse()
        coVerify(exactly = 0) { mockSavedPlaceRepository.getPlaceById(any()) }
    }

    @Test
    fun `savePlace should save place and set isPlaceSaved to true`() = runTest {
        val testPlace = Place(
            id = "1",
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )

        coEvery { mockSavedPlaceRepository.savePlace(testPlace) } returns Result.success("1")

        viewModel.savePlace(testPlace)

        coVerify { mockSavedPlaceRepository.savePlace(testPlace) }
        assertThat(viewModel.isPlaceSaved.value).isTrue()
    }

    @Test
    fun `unsavePlace should delete place and set isPlaceSaved to false`() = runTest {
        val testPlace = Place(
            id = "1",
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )

        coEvery { mockSavedPlaceRepository.deletePlace("1") } returns Result.success(Unit)

        viewModel.unsavePlace(testPlace)

        coVerify { mockSavedPlaceRepository.deletePlace(placeId = "1") }
        assertThat(viewModel.isPlaceSaved.value).isFalse()
    }

    @Test
    fun `unsavePlace should not delete when place id is null`() = runTest {
        val testPlace = Place(
            id = null,
            name = "Test Place",
            latLng = earth.maps.cardinal.data.LatLng(0.0, 0.0),
            address = null
        )

        viewModel.unsavePlace(testPlace)

        coVerify(exactly = 0) { mockSavedPlaceRepository.deletePlace(any()) }
        assertThat(viewModel.isPlaceSaved.value).isFalse()
    }
}
