package earth.maps.cardinal.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.net.toUri
import earth.maps.cardinal.data.ApiConfiguration
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel
    private lateinit var mockAppPreferenceRepository: AppPreferenceRepository

    @Before
    fun setup() {
        context = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).get()
        mockAppPreferenceRepository = mockk(relaxed = true)

        // Setup default StateFlow behaviors
        every { mockAppPreferenceRepository.offlineMode } returns MutableStateFlow(false)
        every { mockAppPreferenceRepository.allowTransitInOfflineMode } returns MutableStateFlow(false)
        every { mockAppPreferenceRepository.contrastLevel } returns MutableStateFlow(0)
        every { mockAppPreferenceRepository.animationSpeed } returns MutableStateFlow(0)
        every { mockAppPreferenceRepository.peliasApiConfig } returns MutableStateFlow(
            ApiConfiguration("https://api.example.com", "test-key")
        )
        every { mockAppPreferenceRepository.valhallaApiConfig } returns MutableStateFlow(
            ApiConfiguration("https://valhalla.example.com", "valhalla-key")
        )
        every { mockAppPreferenceRepository.continuousLocationTracking } returns MutableStateFlow(false)
        every { mockAppPreferenceRepository.showZoomFabs } returns MutableStateFlow(true)
        every { mockAppPreferenceRepository.use24HourFormat } returns MutableStateFlow(false)
        every { mockAppPreferenceRepository.distanceUnit } returns MutableStateFlow(0)
        every { mockAppPreferenceRepository.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)

        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )
    }

    @Test
    fun `offlineMode should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(true)
        every { mockAppPreferenceRepository.offlineMode } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(true, viewModel.offlineMode.first())

        expectedFlow.value = false
        assertEquals(false, viewModel.offlineMode.first())
    }

    @Test
    fun `setOfflineMode should call repository setOfflineMode`() = runTest {
        viewModel.setOfflineMode(true)
        verify { mockAppPreferenceRepository.setOfflineMode(true) }

        viewModel.setOfflineMode(false)
        verify { mockAppPreferenceRepository.setOfflineMode(false) }
    }

    @Test
    fun `allowTransitInOfflineMode should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(true)
        every { mockAppPreferenceRepository.allowTransitInOfflineMode } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(true, viewModel.allowTransitInOfflineMode.first())

        expectedFlow.value = false
        assertEquals(false, viewModel.allowTransitInOfflineMode.first())
    }

    @Test
    fun `setAllowTransitInOfflineMode should call repository setAllowTransitInOfflineMode`() = runTest {
        viewModel.setAllowTransitInOfflineMode(true)
        verify { mockAppPreferenceRepository.setAllowTransitInOfflineMode(true) }

        viewModel.setAllowTransitInOfflineMode(false)
        verify { mockAppPreferenceRepository.setAllowTransitInOfflineMode(false) }
    }

    @Test
    fun `contrastLevel should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(2)
        every { mockAppPreferenceRepository.contrastLevel } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(2, viewModel.contrastLevel.first())

        expectedFlow.value = 1
        assertEquals(1, viewModel.contrastLevel.first())
    }

    @Test
    fun `setContrastLevel should call repository setContrastLevel`() = runTest {
        viewModel.setContrastLevel(1)
        verify { mockAppPreferenceRepository.setContrastLevel(1) }

        viewModel.setContrastLevel(2)
        verify { mockAppPreferenceRepository.setContrastLevel(2) }
    }

    @Test
    fun `animationSpeed should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(1)
        every { mockAppPreferenceRepository.animationSpeed } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(1, viewModel.animationSpeed.first())

        expectedFlow.value = 2
        assertEquals(2, viewModel.animationSpeed.first())
    }

    @Test
    fun `setAnimationSpeed should call repository setAnimationSpeed`() = runTest {
        viewModel.setAnimationSpeed(1)
        verify { mockAppPreferenceRepository.setAnimationSpeed(1) }

        viewModel.setAnimationSpeed(2)
        verify { mockAppPreferenceRepository.setAnimationSpeed(2) }
    }

    @Test
    fun `peliasApiConfig should reflect repository state`() = runTest {
        val expectedConfig = ApiConfiguration("https://new.pelias.com", "new-key")
        val expectedFlow = MutableStateFlow(expectedConfig)
        every { mockAppPreferenceRepository.peliasApiConfig } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(expectedConfig, viewModel.peliasApiConfig.first())
    }

    @Test
    fun `setPeliasBaseUrl should call repository setPeliasBaseUrl`() = runTest {
        val baseUrl = "https://new.pelias.com"
        viewModel.setPeliasBaseUrl(baseUrl)
        verify { mockAppPreferenceRepository.setPeliasBaseUrl(baseUrl) }
    }

    @Test
    fun `setPeliasApiKey should call repository setPeliasApiKey`() = runTest {
        val apiKey = "new-api-key"
        viewModel.setPeliasApiKey(apiKey)
        verify { mockAppPreferenceRepository.setPeliasApiKey(apiKey) }

        viewModel.setPeliasApiKey(null)
        verify { mockAppPreferenceRepository.setPeliasApiKey(null) }
    }

    @Test
    fun `valhallaApiConfig should reflect repository state`() = runTest {
        val expectedConfig = ApiConfiguration("https://new.valhalla.com", "new-valhalla-key")
        val expectedFlow = MutableStateFlow(expectedConfig)
        every { mockAppPreferenceRepository.valhallaApiConfig } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(expectedConfig, viewModel.valhallaApiConfig.first())
    }

    @Test
    fun `setValhallaBaseUrl should call repository setValhallaBaseUrl`() = runTest {
        val baseUrl = "https://new.valhalla.com"
        viewModel.setValhallaBaseUrl(baseUrl)
        verify { mockAppPreferenceRepository.setValhallaBaseUrl(baseUrl) }
    }

    @Test
    fun `setValhallaApiKey should call repository setValhallaApiKey`() = runTest {
        val apiKey = "new-valhalla-key"
        viewModel.setValhallaApiKey(apiKey)
        verify { mockAppPreferenceRepository.setValhallaApiKey(apiKey) }

        viewModel.setValhallaApiKey(null)
        verify { mockAppPreferenceRepository.setValhallaApiKey(null) }
    }

    @Test
    fun `continuousLocationTracking should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(true)
        every { mockAppPreferenceRepository.continuousLocationTracking } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(true, viewModel.continuousLocationTracking.first())

        expectedFlow.value = false
        assertEquals(false, viewModel.continuousLocationTracking.first())
    }

    @Test
    fun `setContinuousLocationTrackingEnabled should call repository setContinuousLocationTracking`() = runTest {
        viewModel.setContinuousLocationTrackingEnabled(true)
        verify { mockAppPreferenceRepository.setContinuousLocationTracking(true) }

        viewModel.setContinuousLocationTrackingEnabled(false)
        verify { mockAppPreferenceRepository.setContinuousLocationTracking(false) }
    }

    @Test
    fun `showZoomFabs should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(false)
        every { mockAppPreferenceRepository.showZoomFabs } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(false, viewModel.showZoomFabs.first())

        expectedFlow.value = true
        assertEquals(true, viewModel.showZoomFabs.first())
    }

    @Test
    fun `setShowZoomFabsEnabled should call repository setShowZoomFabs`() = runTest {
        viewModel.setShowZoomFabsEnabled(true)
        verify { mockAppPreferenceRepository.setShowZoomFabs(true) }

        viewModel.setShowZoomFabsEnabled(false)
        verify { mockAppPreferenceRepository.setShowZoomFabs(false) }
    }

    @Test
    fun `use24HourFormat should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(true)
        every { mockAppPreferenceRepository.use24HourFormat } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(true, viewModel.use24HourFormat.first())

        expectedFlow.value = false
        assertEquals(false, viewModel.use24HourFormat.first())
    }

    @Test
    fun `setUse24HourFormat should call repository setUse24HourFormat`() = runTest {
        viewModel.setUse24HourFormat(true)
        verify { mockAppPreferenceRepository.setUse24HourFormat(true) }

        viewModel.setUse24HourFormat(false)
        verify { mockAppPreferenceRepository.setUse24HourFormat(false) }
    }

    @Test
    fun `distanceUnit should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(1)
        every { mockAppPreferenceRepository.distanceUnit } returns expectedFlow

        // Re-initialize viewModel to use the new mock
        viewModel = SettingsViewModel(
            context = context,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(1, viewModel.distanceUnit.first())

        expectedFlow.value = 2
        assertEquals(2, viewModel.distanceUnit.first())
    }

    @Test
    fun `setDistanceUnit should call repository setDistanceUnit`() = runTest {
        viewModel.setDistanceUnit(1)
        verify { mockAppPreferenceRepository.setDistanceUnit(1) }

        viewModel.setDistanceUnit(2)
        verify { mockAppPreferenceRepository.setDistanceUnit(2) }
    }

    @Test
    fun `setThemeMode should save theme mode and mark prompt handled`() = runTest {
        viewModel.setThemeMode(ThemeMode.LIGHT)

        verify { mockAppPreferenceRepository.setThemeMode(ThemeMode.LIGHT) }
        verify { mockAppPreferenceRepository.setHasPromptedThemeMode(true) }
    }

    @Test
    fun `getVersionName should execute without crashing`() {
        // Test that the method executes without throwing an exception
        // We can't easily test the exact output due to test environment limitations
        viewModel.getVersionName()
        // If we reach here, the method executed successfully
    }
}
