package earth.maps.cardinal.tileserver

import android.content.Context
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.BoundingBox
import earth.maps.cardinal.data.room.DownloadStatus
import earth.maps.cardinal.data.room.DownloadedTile
import earth.maps.cardinal.data.room.DownloadedTileDao
import earth.maps.cardinal.data.room.OfflineArea
import earth.maps.cardinal.data.room.OfflineAreaDao
import earth.maps.cardinal.data.room.TileType
import earth.maps.cardinal.geocoding.TileProcessor
import earth.maps.cardinal.routing.BusRoutingOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.atomic.AtomicInteger

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class TileDownloadManagerTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var context: Context
    private lateinit var tileDownloadManager: TileDownloadManager
    private val mockDownloadedTileDao = mockk<DownloadedTileDao>()
    private val mockOfflineAreaDao = mockk<OfflineAreaDao>()
    private val mockTileProcessor = mockk<TileProcessor>()
    private val mockProgressReporter = mockk<DownloadProgressReporter>()

    @Before
    fun setup() {
        context = RuntimeEnvironment.application
        tileDownloadManager = TileDownloadManager(
            context = context,
            downloadedTileDao = mockDownloadedTileDao,
            offlineAreaDao = mockOfflineAreaDao,
            tileProcessor = mockTileProcessor,
            progressReporter = mockProgressReporter
        )
    }

    @Test
    fun `isBasemapPhaseComplete should return true when all expected tiles are downloaded`() =
        runTest {
            // Arrange
            val areaId = "test_area"
            val expectedTileCount = 3824

            coEvery {
                mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                    areaId,
                    TileType.BASEMAP
                )
            } returns expectedTileCount
            coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns OfflineArea(
                id = areaId,
                name = "Test Area",
                north = 40.0,
                south = 39.0,
                east = -74.0,
                west = -75.0,
                minZoom = 10,
                maxZoom = 14,
                downloadDate = System.currentTimeMillis(),
                fileSize = 0L,
                status = DownloadStatus.DOWNLOADING_BASEMAP
            )

            // Act
            val result = tileDownloadManager.isBasemapPhaseComplete(areaId)

            // Assert
            assertTrue(result)
            coVerify {
                mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                    areaId,
                    TileType.BASEMAP
                )
            }
            coVerify { mockOfflineAreaDao.getOfflineAreaById(areaId) }
        }

    @Test
    fun `isBasemapPhaseComplete should return false when not all tiles are downloaded`() = runTest {
        // Arrange
        val areaId = "test_area"
        val expectedTileCount = 50

        coEvery {
            mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                areaId,
                TileType.BASEMAP
            )
        } returns expectedTileCount
        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns OfflineArea(
            id = areaId,
            name = "Test Area",
            north = 40.0,
            south = 39.0,
            east = -74.0,
            west = -75.0,
            minZoom = 10,
            maxZoom = 14,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.DOWNLOADING_BASEMAP
        )

        // Act
        val result = tileDownloadManager.isBasemapPhaseComplete(areaId)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `isBasemapPhaseComplete should return false when area does not exist`() = runTest {
        // Arrange
        val areaId = "test_area"

        coEvery {
            mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                areaId,
                TileType.BASEMAP
            )
        } returns 0
        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns null

        // Act
        val result = tileDownloadManager.isBasemapPhaseComplete(areaId)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `determineResumePhase should return DOWNLOADING_BASEMAP for PENDING area`() = runTest {
        // Arrange
        val areaId = "test_area"
        val area = OfflineArea(
            id = areaId,
            name = "Test Area",
            north = 40.0,
            south = 39.0,
            east = -74.0,
            west = -75.0,
            minZoom = 10,
            maxZoom = 14,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.PENDING
        )

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area

        // Act
        val result = tileDownloadManager.determineResumePhase(areaId)

        // Assert
        assertEquals(DownloadStatus.DOWNLOADING_BASEMAP, result)
    }

    @Test
    fun `determineResumePhase should return DOWNLOADING_VALHALLA when basemap is complete`() =
        runTest {
            // Arrange
            val areaId = "test_area"
            val area = OfflineArea(
                id = areaId,
                name = "Test Area",
                north = 40.0,
                south = 39.0,
                east = -74.0,
                west = -75.0,
                minZoom = 10,
                maxZoom = 14,
                downloadDate = System.currentTimeMillis(),
                fileSize = 0L,
                status = DownloadStatus.DOWNLOADING_BASEMAP
            )

            coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area
            coEvery {
                mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                    areaId,
                    TileType.BASEMAP
                )
            } returns 3824

            // Act
            val result = tileDownloadManager.determineResumePhase(areaId)

            // Assert
            assertEquals(DownloadStatus.DOWNLOADING_VALHALLA, result)
        }

    @Test
    fun `determineResumePhase should return DOWNLOADING_BASEMAP when basemap is not complete`() =
        runTest {
            // Arrange
            val areaId = "test_area"
            val area = OfflineArea(
                id = areaId,
                name = "Test Area",
                north = 40.0,
                south = 39.0,
                east = -74.0,
                west = -75.0,
                minZoom = 10,
                maxZoom = 14,
                downloadDate = System.currentTimeMillis(),
                fileSize = 0L,
                status = DownloadStatus.DOWNLOADING_BASEMAP
            )

            coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area
            coEvery {
                mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                    areaId,
                    TileType.BASEMAP
                )
            } returns 50

            // Act
            val result = tileDownloadManager.determineResumePhase(areaId)

            // Assert
            assertEquals(DownloadStatus.DOWNLOADING_BASEMAP, result)
        }

    @Test
    fun `determineResumePhase should return DOWNLOADING_VALHALLA for DOWNLOADING_VALHALLA area`() =
        runTest {
            // Arrange
            val areaId = "test_area"
            val area = OfflineArea(
                id = areaId,
                name = "Test Area",
                north = 40.0,
                south = 39.0,
                east = -74.0,
                west = -75.0,
                minZoom = 10,
                maxZoom = 14,
                downloadDate = System.currentTimeMillis(),
                fileSize = 0L,
                status = DownloadStatus.DOWNLOADING_VALHALLA
            )

            coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area

            // Act
            val result = tileDownloadManager.determineResumePhase(areaId)

            // Assert
            assertEquals(DownloadStatus.DOWNLOADING_VALHALLA, result)
        }

    @Test
    fun `determineResumePhase should return PROCESSING_GEOCODER for PROCESSING_GEOCODER area`() =
        runTest {
            // Arrange
            val areaId = "test_area"
            val area = OfflineArea(
                id = areaId,
                name = "Test Area",
                north = 40.0,
                south = 39.0,
                east = -74.0,
                west = -75.0,
                minZoom = 10,
                maxZoom = 14,
                downloadDate = System.currentTimeMillis(),
                fileSize = 0L,
                status = DownloadStatus.PROCESSING_GEOCODER
            )

            coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area

            // Act
            val result = tileDownloadManager.determineResumePhase(areaId)

            // Assert
            assertEquals(DownloadStatus.PROCESSING_GEOCODER, result)
        }

    @Test
    fun `determineResumePhase should return COMPLETED for COMPLETED area`() = runTest {
        // Arrange
        val areaId = "test_area"
        val area = OfflineArea(
            id = areaId,
            name = "Test Area",
            north = 40.0,
            south = 39.0,
            east = -74.0,
            west = -75.0,
            minZoom = 10,
            maxZoom = 14,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.COMPLETED
        )

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area

        // Act
        val result = tileDownloadManager.determineResumePhase(areaId)

        // Assert
        assertEquals(DownloadStatus.COMPLETED, result)
    }

    @Test
    fun `determineResumePhase should return DOWNLOADING_BASEMAP for FAILED area`() = runTest {
        // Arrange
        val areaId = "test_area"
        val area = OfflineArea(
            id = areaId,
            name = "Test Area",
            north = 40.0,
            south = 39.0,
            east = -74.0,
            west = -75.0,
            minZoom = 10,
            maxZoom = 14,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.FAILED
        )

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns area

        // Act
        val result = tileDownloadManager.determineResumePhase(areaId)

        // Assert
        assertEquals(DownloadStatus.DOWNLOADING_BASEMAP, result)
    }

    @Test
    fun `determineResumePhase should return DOWNLOADING_BASEMAP when area does not exist`() =
        runTest {
            // Arrange
            val areaId = "test_area"

            coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns null

            // Act
            val result = tileDownloadManager.determineResumePhase(areaId)

            // Assert
            assertEquals(DownloadStatus.DOWNLOADING_BASEMAP, result)
        }

    @Test
    fun `calculateTotalTiles should return correct tile count for zoom range`() {
        // Arrange
        val boundingBox = BoundingBox(40.0, 39.0, -74.0, -75.0)
        val minZoom = 10
        val maxZoom = 12

        // Act
        val (totalTiles, totalTilesToProcess) = tileDownloadManager.calculateTotalTiles(boundingBox, minZoom, maxZoom)

        // Assert
        assertEquals(284, totalTiles)
        assertEquals(0, totalTilesToProcess)
    }

    @Test
    fun `calculateTotalTiles should return correct tile count, including tiles to process, for zoom range up to 14`() {
        // Arrange
        val boundingBox = BoundingBox(40.0, 39.0, -74.0, -75.0)
        val minZoom = 10
        val maxZoom = 14

        // Act
        val (totalTiles, totalTilesToProcess) = tileDownloadManager.calculateTotalTiles(boundingBox, minZoom, maxZoom)

        // Assert
        assertEquals(3824, totalTiles)
        assertEquals(2820, totalTilesToProcess)
    }

    @Test
    fun `handleExistingArea should create new area when it doesn't exist`() = runTest {
        // Arrange
        val areaId = "test_area"
        val name = "Test Area"
        val boundingBox = BoundingBox(40.0, 39.0, -74.0, -75.0)
        val minZoom = 10
        val maxZoom = 14

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns null
        coEvery { mockOfflineAreaDao.insertOfflineArea(any()) } returns Unit

        // Act
        tileDownloadManager.handleExistingArea(areaId, name, boundingBox, minZoom, maxZoom)

        // Assert
        coVerify {
            mockOfflineAreaDao.insertOfflineArea(match {
                it.id == areaId && it.name == name && it.status == DownloadStatus.DOWNLOADING_BASEMAP
            })
        }
    }

    @Test
    fun `handleExistingArea should handle resume logic when area exists`() = runTest {
        // Arrange
        val areaId = "test_area"
        val name = "Test Area"
        val boundingBox = BoundingBox(40.0, 39.0, -74.0, -75.0)
        val minZoom = 10
        val maxZoom = 14
        val existingArea = OfflineArea(
            id = areaId,
            name = name,
            north = 40.0,
            south = 39.0,
            east = -74.0,
            west = -75.0,
            minZoom = minZoom,
            maxZoom = maxZoom,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.DOWNLOADING_BASEMAP
        )

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns existingArea
        coEvery {
            mockDownloadedTileDao.getDownloadedTileCountForAreaAndType(
                areaId,
                TileType.BASEMAP
            )
        } returns 0
        coEvery { mockOfflineAreaDao.updateOfflineArea(existingArea) } returns Unit

        // Act
        tileDownloadManager.handleExistingArea(areaId, name, boundingBox, minZoom, maxZoom)

        // Assert
        coVerify { mockOfflineAreaDao.getOfflineAreaById(areaId) }
    }

    @Test
    fun `updateAreaStatus should update area status`() = runTest {
        // Arrange
        val areaId = "test_area"
        val status = DownloadStatus.DOWNLOADING_VALHALLA
        val existingArea = OfflineArea(
            id = areaId,
            name = "Test Area",
            north = 40.0,
            south = 39.0,
            east = -74.0,
            west = -75.0,
            minZoom = 10,
            maxZoom = 14,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.DOWNLOADING_BASEMAP
        )

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns existingArea
        coEvery { mockOfflineAreaDao.updateOfflineArea(existingArea.copy(status = status)) } returns Unit

        // Act
        tileDownloadManager.updateAreaStatus(areaId, status)

        // Assert
        coVerify {
            mockOfflineAreaDao.updateOfflineArea(match {
                it.id == areaId && it.status == status
            })
        }
    }

    @Test
    fun `updateAreaStatus should not update when area does not exist`() = runTest {
        // Arrange
        val areaId = "test_area"
        val status = DownloadStatus.DOWNLOADING_VALHALLA

        coEvery { mockOfflineAreaDao.getOfflineAreaById(areaId) } returns null

        // Act
        tileDownloadManager.updateAreaStatus(areaId, status)

        // Assert
        coVerify(exactly = 0) { mockOfflineAreaDao.updateOfflineArea(any()) }
    }

    @Test
    fun `createNewOfflineArea should create area with correct parameters`() = runTest {
        // Arrange
        val areaId = "test_area"
        val name = "Test Area"
        val boundingBox = BoundingBox(40.0, 39.0, -74.0, -75.0)
        val minZoom = 10
        val maxZoom = 14

        coEvery { mockOfflineAreaDao.insertOfflineArea(any()) } returns Unit

        // Act
        tileDownloadManager.createNewOfflineArea(areaId, name, boundingBox, minZoom, maxZoom)

        // Assert
        coVerify {
            mockOfflineAreaDao.insertOfflineArea(match {
                it.id == areaId &&
                        it.name == name &&
                        it.north == boundingBox.north &&
                        it.south == boundingBox.south &&
                        it.east == boundingBox.east &&
                        it.west == boundingBox.west &&
                        it.minZoom == minZoom &&
                        it.maxZoom == maxZoom &&
                        it.status == DownloadStatus.DOWNLOADING_BASEMAP
            })
        }
    }

    @Test
    fun `new bus options use native bus semantics by default`() {
        assertTrue(BusRoutingOptions().lineBus)
    }

    @Test
    fun `processBatch should skip already downloaded tiles`() = runTest {
        // Arrange
        val chunk = listOf(Triple(10, 100, 200))
        val areaId = "test_area"
        val areaName = "Test Area"
        val totalTiles = 1
        val downloadedCount = mockk<AtomicInteger>()
        val failedCount = mockk<AtomicInteger>()

        every { downloadedCount.get() } returns 0
        every { downloadedCount.incrementAndGet() } returns 1
        every {
            mockProgressReporter.updateProgress(
                areaId,
                areaName,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit

        val existingTile = DownloadedTile(
            id = "basemap_${areaId}_10_100_200",
            areaId = areaId,
            tileType = TileType.BASEMAP,
            downloadTimestamp = System.currentTimeMillis(),
            retryCount = 0,
            zoom = 10,
            tileX = 100,
            tileY = 200
        )

        coEvery { mockDownloadedTileDao.getTileById("basemap_${areaId}_10_100_200") } returns existingTile

        // Act
        val result = tileDownloadManager.processBatch(
            chunk,
            areaId,
            areaName,
            totalTiles,
            downloadedCount,
            failedCount
        )

        // Assert
        assertTrue(result.isEmpty()) // Should skip already downloaded tiles
    }
}
