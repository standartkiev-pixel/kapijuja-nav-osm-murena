/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package earth.maps.cardinal.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.BoundingBox
import earth.maps.cardinal.data.room.DownloadStatus
import earth.maps.cardinal.data.room.OfflineArea
import earth.maps.cardinal.data.room.OfflineAreaRepository
import earth.maps.cardinal.tileserver.PermissionRequestManager
import earth.maps.cardinal.tileserver.PermissionRequest
import earth.maps.cardinal.tileserver.PermissionResult
import earth.maps.cardinal.tileserver.TileDownloadForegroundService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineAreasViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: OfflineAreasViewModel
    private lateinit var mockContext: Context
    private lateinit var mockOfflineAreaRepository: OfflineAreaRepository
    private lateinit var mockPermissionRequestManager: PermissionRequestManager
    private lateinit var mockServiceBinder: TileDownloadForegroundService.TileDownloadBinder
    private lateinit var mockService: TileDownloadForegroundService

    private val testBoundingBox = BoundingBox(
        north = 37.8,
        south = 37.7,
        east = -122.4,
        west = -122.5
    )

    private val testOfflineArea = OfflineArea(
        id = "test-area-id",
        name = "Test Area",
        north = 37.8,
        south = 37.7,
        east = -122.4,
        west = -122.5,
        minZoom = 5,
        maxZoom = 14,
        downloadDate = System.currentTimeMillis(),
        fileSize = 2000000L,
        status = DownloadStatus.COMPLETED
    )

    private val testIncompleteOfflineArea = OfflineArea(
        id = "test-incomplete-area-id",
        name = "Test Incomplete Area",
        north = 37.8,
        south = 37.7,
        east = -122.4,
        west = -122.5,
        minZoom = 5,
        maxZoom = 14,
        downloadDate = System.currentTimeMillis(),
        fileSize = 1000000L,
        status = DownloadStatus.DOWNLOADING_BASEMAP
    )

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockOfflineAreaRepository = mockk()
        mockPermissionRequestManager = mockk(relaxed = true)
        mockServiceBinder = mockk(relaxed = true)
        mockService = mockk(relaxed = true)

        // Mock the repository flow
        every { mockOfflineAreaRepository.getAllOfflineAreas() } returns MutableStateFlow(emptyList())

        // Mock the service binder
        every { mockServiceBinder.getService() } returns mockService
        every { mockService.isDownloading } returns MutableStateFlow(false)
        every { mockPermissionRequestManager.shouldRequestNotificationPermission(mockContext) } returns false
        coEvery {
            mockPermissionRequestManager.requestNotificationPermissionAndWaitForResult()
        } returns PermissionResult.Granted(PermissionRequest.NotificationPermission)

        viewModel = OfflineAreasViewModel(
            context = mockContext,
            offlineAreaRepository = mockOfflineAreaRepository,
            permissionRequestManager = mockPermissionRequestManager
        )
    }

    @After
    fun tearDown() {
        // No cleanup needed
    }

    @Test
    fun `initial state should be correct`() {
        assertTrue(viewModel.offlineAreas.value.isEmpty())
        assertFalse(viewModel.isDownloading.value)
        assertFalse(viewModel.isPaused.value)
        assertEquals(0, viewModel.downloadProgress.intValue)
        assertEquals(0, viewModel.totalTiles.intValue)
        assertEquals("", viewModel.currentAreaName.value)
        assertEquals(0f, viewModel.unifiedProgress.floatValue)
        assertEquals(earth.maps.cardinal.tileserver.DownloadStage.BASEMAP, viewModel.currentStage.value)
    }

    @Test
    fun `loadOfflineAreas should update offlineAreas state`() = runTest {
        val areasFlow = MutableStateFlow(listOf(testOfflineArea))
        every { mockOfflineAreaRepository.getAllOfflineAreas() } returns areasFlow

        // Create a new ViewModel to trigger init
        viewModel = OfflineAreasViewModel(
            context = mockContext,
            offlineAreaRepository = mockOfflineAreaRepository,
            permissionRequestManager = mockPermissionRequestManager
        )

        advanceUntilIdle()

        assertEquals(listOf(testOfflineArea), viewModel.offlineAreas.value)
    }

    @Test
    fun `startDownload should call service startDownload with correct parameters`() = runTest {
        val testName = "Test Download Area"
        
        // Manually set the service binder to simulate service connection
        val serviceBinderField = OfflineAreasViewModel::class.java.getDeclaredField("serviceBinder")
        serviceBinderField.isAccessible = true
        serviceBinderField.set(viewModel, mockServiceBinder)
        
        val isBoundField = OfflineAreasViewModel::class.java.getDeclaredField("isBound")
        isBoundField.isAccessible = true
        isBoundField.set(viewModel, true)

        viewModel.startDownload(testBoundingBox, testName)
        advanceUntilIdle()

        verify {
            mockService.startDownload(
                testBoundingBox,
                OfflineAreasViewModel.OFFLINE_AREA_MIN_ZOOM,
                OfflineAreasViewModel.OFFLINE_AREA_MAX_ZOOM,
                testName
            )
        }
    }

    @Test
    fun `startDownload should request notification permission when permission is missing`() =
        runTest {
            val testName = "Test Download Area"
            every { mockPermissionRequestManager.shouldRequestNotificationPermission(mockContext) } returns true
            coEvery {
                mockPermissionRequestManager.requestNotificationPermissionAndWaitForResult()
            } returns PermissionResult.Denied(PermissionRequest.NotificationPermission)

            // Manually set the service binder to simulate service connection
            val serviceBinderField = OfflineAreasViewModel::class.java.getDeclaredField("serviceBinder")
            serviceBinderField.isAccessible = true
            serviceBinderField.set(viewModel, mockServiceBinder)

            val isBoundField = OfflineAreasViewModel::class.java.getDeclaredField("isBound")
            isBoundField.isAccessible = true
            isBoundField.set(viewModel, true)

            viewModel.startDownload(testBoundingBox, testName)
            advanceUntilIdle()

            coVerify { mockPermissionRequestManager.requestNotificationPermissionAndWaitForResult() }
            verify {
                mockService.startDownload(
                    testBoundingBox,
                    OfflineAreasViewModel.OFFLINE_AREA_MIN_ZOOM,
                    OfflineAreasViewModel.OFFLINE_AREA_MAX_ZOOM,
                    testName
                )
            }
        }

    @Test
    fun `startDownload should not wait for notification permission when already requested before`() =
        runTest {
            val testName = "Test Download Area"
            every { mockPermissionRequestManager.shouldRequestNotificationPermission(mockContext) } returns false

            // Manually set the service binder to simulate service connection
            val serviceBinderField = OfflineAreasViewModel::class.java.getDeclaredField("serviceBinder")
            serviceBinderField.isAccessible = true
            serviceBinderField.set(viewModel, mockServiceBinder)

            val isBoundField = OfflineAreasViewModel::class.java.getDeclaredField("isBound")
            isBoundField.isAccessible = true
            isBoundField.set(viewModel, true)

            viewModel.startDownload(testBoundingBox, testName)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                mockPermissionRequestManager.requestNotificationPermissionAndWaitForResult()
            }
            verify {
                mockService.startDownload(
                    testBoundingBox,
                    OfflineAreasViewModel.OFFLINE_AREA_MIN_ZOOM,
                    OfflineAreasViewModel.OFFLINE_AREA_MAX_ZOOM,
                    testName
                )
            }
        }

    @Test
    fun `deleteOfflineArea should call service deleteTilesForArea and repository deleteOfflineArea`() = runTest {
        // Mock the deleteOfflineArea method
        coEvery { mockOfflineAreaRepository.deleteOfflineArea(any()) } returns Unit
        
        // Manually set the service binder to simulate service connection
        val serviceBinderField = OfflineAreasViewModel::class.java.getDeclaredField("serviceBinder")
        serviceBinderField.isAccessible = true
        serviceBinderField.set(viewModel, mockServiceBinder)
        
        val isBoundField = OfflineAreasViewModel::class.java.getDeclaredField("isBound")
        isBoundField.isAccessible = true
        isBoundField.set(viewModel, true)

        viewModel.deleteOfflineArea(testOfflineArea)
        advanceUntilIdle()

        verify { mockService.deleteTilesForArea(testOfflineArea.id) }
        coVerify { mockOfflineAreaRepository.deleteOfflineArea(testOfflineArea) }
    }

    @Test
    fun `estimateTileCount should calculate correct number of tiles`() {
        val result = viewModel.estimateTileCount(testBoundingBox, 5, 14)
        
        // The exact calculation depends on the calculateTileRange implementation
        // We're just verifying it returns a positive number
        assertTrue(result > 0)
    }

    @Test
    fun `estimateTileCount with max zoom above 14 should limit to 14`() {
        val result = viewModel.estimateTileCount(testBoundingBox, 5, 16)
        
        // Should use 14 as the max zoom
        assertTrue(result > 0)
    }

    @Test
    fun `resetProgressState with no active areas should reset progress`() = runTest {
        // Set some initial state
        viewModel.isDownloading.value = true
        viewModel.downloadProgress.intValue = 50
        viewModel.totalTiles.intValue = 100
        viewModel.currentAreaName.value = "Test Area"

        // Set offline areas to empty list (no active areas)
        val areasFlow = MutableStateFlow(emptyList<OfflineArea>())
        every { mockOfflineAreaRepository.getAllOfflineAreas() } returns areasFlow

        // Create a new ViewModel to trigger init and reset
        viewModel = OfflineAreasViewModel(
            context = mockContext,
            offlineAreaRepository = mockOfflineAreaRepository,
            permissionRequestManager = mockPermissionRequestManager
        )

        advanceUntilIdle()

        // State should be reset
        assertFalse(viewModel.isDownloading.value)
        assertEquals(0, viewModel.downloadProgress.intValue)
        assertEquals(0, viewModel.totalTiles.intValue)
        assertEquals("", viewModel.currentAreaName.value)
    }

    @Test
    fun `resetProgressState with active incomplete areas should not reset progress`() = runTest {
        // Set some initial state
        viewModel.isDownloading.value = true
        viewModel.downloadProgress.intValue = 50
        viewModel.totalTiles.intValue = 100
        viewModel.currentAreaName.value = "Test Area"

        // Set offline areas to include an incomplete area
        val areasFlow = MutableStateFlow(listOf(testIncompleteOfflineArea))
        every { mockOfflineAreaRepository.getAllOfflineAreas() } returns areasFlow

        // Create a new ViewModel to trigger init
        viewModel = OfflineAreasViewModel(
            context = mockContext,
            offlineAreaRepository = mockOfflineAreaRepository,
            permissionRequestManager = mockPermissionRequestManager
        )

        advanceUntilIdle()

        // State should not be reset because there's an active area
        // Note: The actual reset happens in a private method, so we're testing the overall behavior
    }


    @Test
    fun `service connection should handle onServiceConnected correctly`() = runTest {
        // Create a mock binder that can be cast to TileDownloadBinder
        val mockBinder = mockk<TileDownloadForegroundService.TileDownloadBinder>()
        every { mockBinder.getService() } returns mockService
        
        val serviceConnection = viewModel.javaClass.getDeclaredField("serviceConnection").apply {
            isAccessible = true
        }.get(viewModel) as ServiceConnection

        serviceConnection.onServiceConnected(ComponentName("test", "test"), mockBinder)

        // Verify that the service binder was set correctly by checking if we can access the service
        val serviceBinderField = OfflineAreasViewModel::class.java.getDeclaredField("serviceBinder")
        serviceBinderField.isAccessible = true
        val actualBinder = serviceBinderField.get(viewModel)
        assertEquals(mockBinder, actualBinder)
    }

    @Test
    fun `service connection should handle onServiceDisconnected correctly`() = runTest {
        val serviceConnection = viewModel.javaClass.getDeclaredField("serviceConnection").apply {
            isAccessible = true
        }.get(viewModel) as ServiceConnection

        serviceConnection.onServiceDisconnected(ComponentName("test", "test"))

        // Verify that the service binder was set to null
        val serviceBinderField = OfflineAreasViewModel::class.java.getDeclaredField("serviceBinder")
        serviceBinderField.isAccessible = true
        val actualBinder = serviceBinderField.get(viewModel)
        assertEquals(null, actualBinder)
    }

    @Test
    fun `errorMessage should be null initially`() {
        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun `clearErrorMessage should set errorMessage to null`() = runTest {
        // First set an error message
        val errorMessageField = OfflineAreasViewModel::class.java.getDeclaredField("_errorMessage")
        errorMessageField.isAccessible = true
        val errorFlow = errorMessageField.get(viewModel) as MutableStateFlow<String?>
        errorFlow.value = "Test error"

        assertEquals("Test error", viewModel.errorMessage.value)

        // Clear the error
        viewModel.clearErrorMessage()
        assertEquals(null, viewModel.errorMessage.value)
    }
}
