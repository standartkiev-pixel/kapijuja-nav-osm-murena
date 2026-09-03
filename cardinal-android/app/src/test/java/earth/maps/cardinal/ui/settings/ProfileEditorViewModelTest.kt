package earth.maps.cardinal.ui.settings

import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.room.RoutingProfile
import earth.maps.cardinal.data.room.RoutingProfileRepository
import earth.maps.cardinal.routing.AutoRoutingOptions
import earth.maps.cardinal.routing.CyclingRoutingOptions
import earth.maps.cardinal.routing.PedestrianRoutingOptions
import earth.maps.cardinal.routing.RoutingOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {

    private lateinit var viewModel: ProfileEditorViewModel
    private lateinit var mockRepository: RoutingProfileRepository

    @Before
    fun setup() {
        mockRepository = mockk(relaxed = true)
        viewModel = ProfileEditorViewModel(repository = mockRepository)
    }

    @Test
    fun `loadProfile with null should initialize as new profile`() = runTest {
        viewModel.loadProfile(null)

        assertEquals("", viewModel.profileName.first())
        assertEquals(RoutingMode.AUTO, viewModel.selectedMode.first())
        assertTrue(viewModel.routingOptions.first() is AutoRoutingOptions)
        assertTrue(viewModel.isNewProfile.first())
        assertFalse(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `loadProfile with valid ID should load existing profile`() = runTest {
        val profileId = "test-profile-id"
        val testProfile = RoutingProfile(
            id = profileId,
            name = "Test Profile",
            routingMode = "bicycle",
            optionsJson = "{}",
            isDefault = false
        )
        val testOptions = CyclingRoutingOptions()

        coEvery { mockRepository.getProfileById(profileId) } returns Result.success(testProfile)
        every { mockRepository.deserializeOptions("bicycle", "{}") } returns testOptions

        viewModel.loadProfile(profileId)

        assertEquals("Test Profile", viewModel.profileName.first())
        assertEquals(RoutingMode.BICYCLE, viewModel.selectedMode.first())
        assertEquals(testOptions, viewModel.routingOptions.first())
        assertFalse(viewModel.isNewProfile.first())
        assertFalse(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `loadProfile with non-existent ID should treat as new profile`() = runTest {
        val profileId = "non-existent-id"
        coEvery { mockRepository.getProfileById(profileId) } returns Result.success(null)

        viewModel.loadProfile(profileId)

        assertEquals("", viewModel.profileName.first())
        assertEquals(RoutingMode.AUTO, viewModel.selectedMode.first())
        assertTrue(viewModel.routingOptions.first() is AutoRoutingOptions)
        assertTrue(viewModel.isNewProfile.first())
        assertFalse(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `loadProfile with error should set error message`() = runTest {
        val profileId = "test-profile-id"
        val errorMessage = "Failed to load"
        coEvery { mockRepository.getProfileById(profileId) } returns Result.failure(Exception(errorMessage))

        viewModel.loadProfile(profileId)

        assertEquals("Failed to load profile: $errorMessage", viewModel.error.first())
    }

    @Test
    fun `updateProfileName should update name and mark as unsaved`() = runTest {
        viewModel.loadProfile(null) // Start with new profile
        val newName = "New Profile Name"

        viewModel.updateProfileName(newName)

        assertEquals(newName, viewModel.profileName.first())
        assertTrue(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `updateRoutingMode should update mode and options`() = runTest {
        viewModel.loadProfile(null) // Start with new profile

        viewModel.updateRoutingMode(RoutingMode.BICYCLE)

        assertEquals(RoutingMode.BICYCLE, viewModel.selectedMode.first())
        assertTrue(viewModel.routingOptions.first() is CyclingRoutingOptions)
        assertTrue(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `updateRoutingMode with same mode should not change anything`() = runTest {
        viewModel.loadProfile(null) // Start with AUTO mode
        val initialOptions = viewModel.routingOptions.first()

        viewModel.updateRoutingMode(RoutingMode.AUTO)

        assertEquals(RoutingMode.AUTO, viewModel.selectedMode.first())
        assertEquals(initialOptions, viewModel.routingOptions.first())
        assertFalse(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `updateRoutingOptions should update options and mark as unsaved`() = runTest {
        viewModel.loadProfile(null) // Start with new profile
        val newOptions = CyclingRoutingOptions()

        viewModel.updateRoutingOptions(newOptions)

        assertEquals(newOptions, viewModel.routingOptions.first())
        assertTrue(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `saveProfile with new profile should call repository createProfile`() = runTest {
        viewModel.loadProfile(null)
        viewModel.updateProfileName("Test Profile")
        
        val profileId = "new-profile-id"
        coEvery { 
            mockRepository.createProfile("Test Profile", RoutingMode.AUTO, any<AutoRoutingOptions>()) 
        } returns Result.success(profileId)

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }

        coVerify { 
            mockRepository.createProfile("Test Profile", RoutingMode.AUTO, any<AutoRoutingOptions>()) 
        }
        assertTrue(onSuccessCalled)
        assertNull(viewModel.error.first())
    }

    @Test
    fun `saveProfile with existing profile should call repository updateProfile`() = runTest {
        val profileId = "existing-profile-id"
        val testProfile = RoutingProfile(
            id = profileId,
            name = "Test Profile",
            routingMode = "auto",
            optionsJson = "{}",
            isDefault = false
        )
        val testOptions = AutoRoutingOptions()

        coEvery { mockRepository.getProfileById(profileId) } returns Result.success(testProfile)
        every { mockRepository.deserializeOptions("auto", "{}") } returns testOptions
        coEvery { mockRepository.updateProfile(profileId, "Updated Profile", testOptions) } returns Result.success(Unit)

        viewModel.loadProfile(profileId)
        viewModel.updateProfileName("Updated Profile")

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }

        coVerify { mockRepository.updateProfile(profileId, "Updated Profile", testOptions) }
        assertTrue(onSuccessCalled)
        assertNull(viewModel.error.first())
    }

    @Test
    fun `saveProfile with empty name should set error`() = runTest {
        viewModel.loadProfile(null)
        viewModel.updateProfileName("")

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }

        assertEquals("Profile name cannot be empty", viewModel.error.first())
        assertFalse(onSuccessCalled)
    }

    @Test
    fun `saveProfile with blank name should set error`() = runTest {
        viewModel.loadProfile(null)
        viewModel.updateProfileName("   ")

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }

        assertEquals("Profile name cannot be empty", viewModel.error.first())
        assertFalse(onSuccessCalled)
    }

    @Test
    fun `saveProfile with repository error should set error message`() = runTest {
        viewModel.loadProfile(null)
        viewModel.updateProfileName("Test Profile")
        
        val errorMessage = "Save failed"
        coEvery { 
            mockRepository.createProfile("Test Profile", RoutingMode.AUTO, any<AutoRoutingOptions>()) 
        } returns Result.failure(Exception(errorMessage))

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }

        assertEquals("Failed to save profile: $errorMessage", viewModel.error.first())
        assertFalse(onSuccessCalled)
    }

    @Test
    fun `clearError should clear error message`() = runTest {
        viewModel.loadProfile(null)
        viewModel.updateProfileName("") // Trigger validation error
        viewModel.saveProfile {} // This will set an error

        assertNotNull(viewModel.error.first())

        viewModel.clearError()

        assertNull(viewModel.error.first())
    }

    @Test
    fun `hasUnsavedChanges should track changes correctly`() = runTest {
        viewModel.loadProfile(null)
        
        // Initially no changes
        assertFalse(viewModel.hasUnsavedChanges.first())
        
        // Change name
        viewModel.updateProfileName("New Name")
        assertTrue(viewModel.hasUnsavedChanges.first())
        
        // Reset and change mode
        viewModel.loadProfile(null)
        viewModel.updateRoutingMode(RoutingMode.BICYCLE)
        assertTrue(viewModel.hasUnsavedChanges.first())
        
        // Reset and change options
        viewModel.loadProfile(null)
        val newOptions = CyclingRoutingOptions()
        viewModel.updateRoutingOptions(newOptions)
        assertTrue(viewModel.hasUnsavedChanges.first())
    }

    @Test
    fun `different routing modes should create correct default options`() = runTest {
        viewModel.loadProfile(null)

        // Test each routing mode
        viewModel.updateRoutingMode(RoutingMode.PEDESTRIAN)
        assertTrue(viewModel.routingOptions.first() is PedestrianRoutingOptions)

        viewModel.updateRoutingMode(RoutingMode.BICYCLE)
        assertTrue(viewModel.routingOptions.first() is CyclingRoutingOptions)

        viewModel.updateRoutingMode(RoutingMode.AUTO)
        assertTrue(viewModel.routingOptions.first() is AutoRoutingOptions)
    }
}