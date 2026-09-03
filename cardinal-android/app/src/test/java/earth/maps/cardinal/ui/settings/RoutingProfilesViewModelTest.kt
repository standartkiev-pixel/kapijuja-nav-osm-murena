package earth.maps.cardinal.ui.settings

import earth.maps.cardinal.data.room.RoutingProfile
import earth.maps.cardinal.data.room.RoutingProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingProfilesViewModelTest {

    private lateinit var viewModel: RoutingProfilesViewModel
    private lateinit var mockRepository: RoutingProfileRepository

    @Before
    fun setup() {
        mockRepository = mockk(relaxed = true)
        
        // Setup default StateFlow behaviors
        every { mockRepository.allProfiles } returns MutableStateFlow(emptyList())
        
        viewModel = RoutingProfilesViewModel(repository = mockRepository)
    }

    @Test
    fun `allProfiles should reflect repository state`() = runTest {
        val testProfiles = listOf(
            RoutingProfile(
                id = "1",
                name = "Test Profile 1",
                routingMode = "auto",
                optionsJson = "{}",
                isDefault = true
            ),
            RoutingProfile(
                id = "2",
                name = "Test Profile 2",
                routingMode = "bicycle",
                optionsJson = "{}",
                isDefault = false
            )
        )
        val expectedFlow = MutableStateFlow(testProfiles)
        every { mockRepository.allProfiles } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = RoutingProfilesViewModel(repository = mockRepository)

        assertEquals(testProfiles, viewModel.allProfiles.first())
    }

    @Test
    fun `deleteProfile should call repository deleteProfile and handle success`() = runTest {
        val profileId = "test-profile-id"
        coEvery { mockRepository.deleteProfile(profileId) } returns Result.success(Unit)

        viewModel.deleteProfile(profileId)

        coVerify { mockRepository.deleteProfile(profileId) }
        // Verify no error is set on success
        assertNull(viewModel.error.first())
    }

    @Test
    fun `deleteProfile should handle failure and set error message`() = runTest {
        val profileId = "test-profile-id"
        val errorMessage = "Failed to delete"
        coEvery { mockRepository.deleteProfile(profileId) } returns Result.failure(Exception(errorMessage))

        viewModel.deleteProfile(profileId)

        coVerify { mockRepository.deleteProfile(profileId) }
        // Verify error message is set
        assertEquals("Failed to delete profile: $errorMessage", viewModel.error.first())
    }

    @Test
    fun `setDefaultProfile should call repository setDefaultProfile and handle success`() = runTest {
        val profileId = "test-profile-id"
        coEvery { mockRepository.setDefaultProfile(profileId) } returns Result.success(Unit)

        viewModel.setDefaultProfile(profileId)

        coVerify { mockRepository.setDefaultProfile(profileId) }
        // Verify no error is set on success
        assertNull(viewModel.error.first())
    }

    @Test
    fun `setDefaultProfile should handle failure and set error message`() = runTest {
        val profileId = "test-profile-id"
        val errorMessage = "Failed to set default"
        coEvery { mockRepository.setDefaultProfile(profileId) } returns Result.failure(Exception(errorMessage))

        viewModel.setDefaultProfile(profileId)

        coVerify { mockRepository.setDefaultProfile(profileId) }
        // Verify error message is set
        assertEquals("Failed to set default profile: $errorMessage", viewModel.error.first())
    }

    @Test
    fun `error should be null initially`() = runTest {
        assertNull(viewModel.error.first())
    }

    @Test
    fun `error should be cleared after successful operation`() = runTest {
        val profileId = "test-profile-id"
        
        // First, simulate an error
        coEvery { mockRepository.deleteProfile(profileId) } returns Result.failure(Exception("Error"))
        viewModel.deleteProfile(profileId)
        assertNotNull(viewModel.error.first())
        
        // Then simulate success
        coEvery { mockRepository.deleteProfile(profileId) } returns Result.success(Unit)
        viewModel.deleteProfile(profileId)
        
        // Error should be cleared
        assertNull(viewModel.error.first())
    }

    @Test
    fun `deleteProfile with different profile IDs should work independently`() = runTest {
        val profileId1 = "profile-1"
        val profileId2 = "profile-2"
        
        coEvery { mockRepository.deleteProfile(profileId1) } returns Result.success(Unit)
        coEvery { mockRepository.deleteProfile(profileId2) } returns Result.failure(Exception("Error"))
        
        // Delete first profile successfully
        viewModel.deleteProfile(profileId1)
        assertNull(viewModel.error.first())
        
        // Delete second profile with error
        viewModel.deleteProfile(profileId2)
        assertEquals("Failed to delete profile: Error", viewModel.error.first())
        
        coVerify { mockRepository.deleteProfile(profileId1) }
        coVerify { mockRepository.deleteProfile(profileId2) }
    }

    @Test
    fun `setDefaultProfile with different profile IDs should work independently`() = runTest {
        val profileId1 = "profile-1"
        val profileId2 = "profile-2"
        
        coEvery { mockRepository.setDefaultProfile(profileId1) } returns Result.success(Unit)
        coEvery { mockRepository.setDefaultProfile(profileId2) } returns Result.failure(Exception("Error"))
        
        // Set first profile as default successfully
        viewModel.setDefaultProfile(profileId1)
        assertNull(viewModel.error.first())
        
        // Set second profile as default with error
        viewModel.setDefaultProfile(profileId2)
        assertEquals("Failed to set default profile: Error", viewModel.error.first())
        
        coVerify { mockRepository.setDefaultProfile(profileId1) }
        coVerify { mockRepository.setDefaultProfile(profileId2) }
    }
}