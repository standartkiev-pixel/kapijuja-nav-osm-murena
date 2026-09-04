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

package earth.maps.cardinal.tileserver

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import earth.maps.cardinal.R
import earth.maps.cardinal.data.BoundingBox
import earth.maps.cardinal.data.room.DownloadStatus
import earth.maps.cardinal.data.room.DownloadedTile
import earth.maps.cardinal.data.room.DownloadedTileDao
import earth.maps.cardinal.data.room.OfflineArea
import earth.maps.cardinal.data.room.OfflineAreaDao
import earth.maps.cardinal.data.room.TileType
import earth.maps.cardinal.geocoding.TileProcessor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

class TileDownloadManager(
    private val context: Context,
    private val downloadedTileDao: DownloadedTileDao,
    private val offlineAreaDao: OfflineAreaDao,
    private val tileProcessor: TileProcessor? = null,
    private val progressReporter: DownloadProgressReporter? = null
) {
    private val TAG = "TileDownloadManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation)
    }
    private val countryTileMask by lazy { CountryTileMask(context) }


    companion object {
        private const val MAX_BASEMAP_ZOOM = 14
        private const val OFFLINE_DATABASE_NAME = "offline_areas.mbtiles"
        // Keep network parallelism deliberately moderate. Country downloads contain
        // hundreds of thousands of small basemap requests, so serial I/O is far too slow,
        // but aggressive fan-out can overload the provider or the device.
        private const val MAX_CONCURRENT_DOWNLOADS = 8
        private const val MAX_CONCURRENT_VALHALLA_DOWNLOADS = 8
        private const val GEOCODER_BATCH_SIZE = 200
        private const val MAX_RETRY_COUNT = 3
        private const val COUNTRY_AREA_PREFIX = "country-"

        fun countryCodeFromAreaId(areaId: String?): String? {
            if (areaId == null || !areaId.startsWith(COUNTRY_AREA_PREFIX)) return null
            val code = areaId.removePrefix(COUNTRY_AREA_PREFIX).substringBefore('-').uppercase()
            return code.takeIf { it.length == 2 }
        }
    }

    /**
     * Determines if the basemap download phase is complete for the given area
     */
    suspend fun isBasemapPhaseComplete(areaId: String): Boolean {
        val expectedTileCount =
            downloadedTileDao.getDownloadedTileCountForAreaAndType(areaId, TileType.BASEMAP)
        Log.d(TAG, "Found $expectedTileCount basemap tiles for area $areaId")

        val existingArea = offlineAreaDao.getOfflineAreaById(areaId)
        if (existingArea == null) return false

        // Calculate expected total basemap tiles
        val (totalExpectedBasemapTiles, _) = calculateTotalTiles(
            boundingBox = existingArea.boundingBox(),
            existingArea.minZoom,
            minOf(existingArea.maxZoom, MAX_BASEMAP_ZOOM),
            areaId
        )

        Log.d(
            TAG,
            "Basemap phase for area $areaId: $expectedTileCount/$totalExpectedBasemapTiles tiles downloaded"
        )
        return expectedTileCount >= totalExpectedBasemapTiles
    }

    /**
     * Determines which phase the download should resume from based on current progress
     */
    suspend fun determineResumePhase(areaId: String): DownloadStatus {
        val existingArea =
            offlineAreaDao.getOfflineAreaById(areaId) ?: return DownloadStatus.DOWNLOADING_BASEMAP

        return when (existingArea.status) {
            DownloadStatus.PENDING -> DownloadStatus.DOWNLOADING_BASEMAP
            DownloadStatus.DOWNLOADING_BASEMAP -> {
                if (isBasemapPhaseComplete(areaId)) {
                    DownloadStatus.DOWNLOADING_VALHALLA
                } else {
                    DownloadStatus.DOWNLOADING_BASEMAP
                }
            }

            DownloadStatus.DOWNLOADING_VALHALLA -> DownloadStatus.DOWNLOADING_VALHALLA
            DownloadStatus.PROCESSING_GEOCODER -> DownloadStatus.PROCESSING_GEOCODER
            DownloadStatus.COMPLETED -> DownloadStatus.COMPLETED
            DownloadStatus.FAILED -> DownloadStatus.DOWNLOADING_BASEMAP
        }
    }

    /**
     * Download tiles for a bounding box and zoom range using the foreground service
     */
    fun downloadTiles(
        boundingBox: BoundingBox, minZoom: Int, maxZoom: Int, areaId: String, name: String
    ) {
        downloadJob = coroutineScope.launch {
            try {
                Log.d(TAG, "Starting download for area: $name (ID: $areaId)")

                handleExistingArea(areaId, name, boundingBox, minZoom, maxZoom)

                // Start the foreground service
                val intent = Intent(context, TileDownloadForegroundService::class.java).apply {
                    action = TileDownloadForegroundService.ACTION_START_DOWNLOAD
                }
                context.startForegroundService(intent)
                Log.d(TAG, "Started foreground service for download")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting download for area $areaId", e)
                updateAreaStatus(areaId, DownloadStatus.FAILED)
            }
        }
    }

    /**
     * Handle logic for existing areas: check if exists, determine resume phase, skip if completed, or create new area
     */
    suspend fun handleExistingArea(
        areaId: String, name: String, boundingBox: BoundingBox, minZoom: Int, maxZoom: Int
    ) {
        val existingArea = offlineAreaDao.getOfflineAreaById(areaId)
        if (existingArea != null) {
            Log.d(
                TAG,
                "Area $areaId already exists in database with status: ${existingArea.status}"
            )
            handleResumeLogic(existingArea, areaId)
        } else {
            createNewOfflineArea(areaId, name, boundingBox, minZoom, maxZoom)
        }
    }

    /**
     * Handle resume logic for existing areas
     */
    private suspend fun handleResumeLogic(existingArea: OfflineArea, areaId: String) {
        val resumePhase = determineResumePhase(areaId)
        Log.d(TAG, "Determined resume phase for area $areaId: $resumePhase")

        if (resumePhase == DownloadStatus.COMPLETED) {
            Log.d(TAG, "Area $areaId is already completed, skipping download")
            throw Exception("Download already completed") // Use exception to exit early
        }

        val updatedArea = existingArea.copy(status = resumePhase)
        offlineAreaDao.updateOfflineArea(updatedArea)
        Log.d(TAG, "Updated area $areaId status to $resumePhase for resume")
    }

    /**
     * Create a new offline area
     */
    suspend fun createNewOfflineArea(
        areaId: String, name: String, boundingBox: BoundingBox, minZoom: Int, maxZoom: Int
    ) {
        val offlineArea = OfflineArea(
            id = areaId,
            name = name,
            north = boundingBox.north,
            south = boundingBox.south,
            east = boundingBox.east,
            west = boundingBox.west,
            minZoom = minZoom,
            maxZoom = maxZoom,
            downloadDate = System.currentTimeMillis(),
            fileSize = 0L,
            status = DownloadStatus.DOWNLOADING_BASEMAP,
        )

        offlineAreaDao.insertOfflineArea(offlineArea)
        Log.d(TAG, "Created offline area: $areaId with status ${offlineArea.status}")
    }


    /**
     * Update area status
     */
    suspend fun updateAreaStatus(areaId: String, status: DownloadStatus) {
        val area = offlineAreaDao.getOfflineAreaById(areaId)
        if (area != null) {
            val updatedArea = area.copy(status = status)
            offlineAreaDao.updateOfflineArea(updatedArea)
        }
    }

    internal suspend fun downloadTilesInternal(
        boundingBox: BoundingBox, minZoom: Int, maxZoom: Int, areaId: String, name: String
    ) {
        var db: SQLiteDatabase? = null
        var basemapResult: Pair<Int, Int>
        var valhallaResult: Pair<Int, Int>

        try {
            Log.d(TAG, "Starting tile download for area: $name (ID: $areaId)")
            Log.d(
                TAG,
                "Bounds: N=${boundingBox.north}, S=${boundingBox.south}, E=${boundingBox.east}, W=${boundingBox.west}, Zoom: $minZoom-$maxZoom"
            )

            val resumePhase = determineResumePhase(areaId)
            Log.d(TAG, "Resuming download from phase: $resumePhase")

            db = initializeDatabase()
            val (totalBasemapTiles, totalTilesToProcess, totalValhallaTiles) =
                calculateTotalDownloadCounts(boundingBox, minZoom, maxZoom, areaId)
            val (downloadedBasemapTiles, downloadedValhallaTiles, processedTiles) =
                getCurrentProgressCounts(areaId)

            initializeProgressReporting(
                areaId, name, totalBasemapTiles, totalValhallaTiles,
                downloadedBasemapTiles, downloadedValhallaTiles,
                processedTiles, totalTilesToProcess
            )

            basemapResult = downloadBasemapPhaseIfNeeded(
                resumePhase, boundingBox, minZoom,
                maxZoom, areaId, name, totalValhallaTiles
            )
            valhallaResult = downloadValhallaPhaseIfNeeded(
                resumePhase, boundingBox, areaId,
                db, name
            )

            finalizeAndStoreMetadata(db, areaId, boundingBox, minZoom, maxZoom, name)
            db.close()
            db = null

            val fileSize = calculateAndLogCompletionStats(
                boundingBox, minZoom, maxZoom, areaId,
                basemapResult, valhallaResult,
                downloadedBasemapTiles, downloadedValhallaTiles
            )

            processDownloadedTilesAndComplete(areaId, name, fileSize)

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading tiles for area $name (ID: $areaId)", e)
            handleDownloadError(areaId, name)
            throw e
        } finally {
            closeDatabaseSafely(db)
        }
    }

    /**
     * Initialize or open the MBTiles database
     */
    private fun initializeDatabase(): SQLiteDatabase {
        val outputFile = File(context.filesDir, OFFLINE_DATABASE_NAME)
        val dbExists = outputFile.exists()
        Log.d(TAG, "Using database file: ${outputFile.absolutePath}, exists: $dbExists")

        val db = SQLiteDatabase.openOrCreateDatabase(outputFile, null)

        // Initialize MBTiles schema only if database is new
        if (!dbExists) {
            Log.d(TAG, "Initializing new MBTiles schema")
            initializeMbtilesSchema(db)
        }

        return db
    }

    /**
     * Calculate total counts for basemap and Valhalla tiles
     */
    private fun calculateTotalDownloadCounts(
        boundingBox: BoundingBox, minZoom: Int, maxZoom: Int, areaId: String
    ): Triple<Int, Int, Int> {
        val (totalBasemapTiles, totalTilesToProcess) = calculateTotalTiles(
            boundingBox, minZoom, min(maxZoom, MAX_BASEMAP_ZOOM), areaId
        )
        val totalValhallaTiles = ValhallaTileUtils.tilesForBoundingBox(boundingBox).size

        Log.d(TAG, "Total basemap tiles to download: $totalBasemapTiles")
        Log.d(TAG, "Total basemap tiles to process: $totalTilesToProcess")
        Log.d(TAG, "Total valhalla tiles to download: $totalValhallaTiles")

        return Triple(totalBasemapTiles, totalTilesToProcess, totalValhallaTiles)
    }

    /**
     * Get current download progress counts
     */
    private suspend fun getCurrentProgressCounts(areaId: String): Triple<Int, Int, Int> {
        val downloadedBasemapTiles =
            downloadedTileDao.getDownloadedTileCountForAreaAndType(areaId, TileType.BASEMAP)
        val downloadedValhallaTiles =
            downloadedTileDao.getDownloadedTileCountForAreaAndType(areaId, TileType.VALHALLA)
        val processedTiles = downloadedTileDao.getProcessedTileCountForArea(areaId)

        Log.d(
            TAG,
            "Already downloaded: $downloadedBasemapTiles basemap tiles, $downloadedValhallaTiles valhalla tiles"
        )

        return Triple(downloadedBasemapTiles, downloadedValhallaTiles, processedTiles)
    }

    /**
     * Initialize progress reporting with current state
     */
    private fun initializeProgressReporting(
        areaId: String, name: String, totalBasemapTiles: Int, totalValhallaTiles: Int,
        downloadedBasemapTiles: Int, downloadedValhallaTiles: Int,
        processedTiles: Int, totalTilesToProcess: Int
    ) {
        val currentStage = determineCurrentStage(
            totalBasemapTiles, totalValhallaTiles,
            downloadedBasemapTiles, downloadedValhallaTiles,
            processedTiles, totalTilesToProcess
        )
        val stageProgress =
            getStageProgress(currentStage, downloadedBasemapTiles, downloadedValhallaTiles, processedTiles)
        val stageTotal = getStageTotal(currentStage, totalBasemapTiles, totalValhallaTiles, totalTilesToProcess)

        progressReporter?.updateProgress(
            areaId = areaId,
            areaName = name,
            currentStage = currentStage,
            stageProgress = stageProgress,
            stageTotal = stageTotal,
            isCompleted = false,
            hasError = false
        )

        // Log existing tile count
        val db = SQLiteDatabase.openDatabase(
            File(context.filesDir, OFFLINE_DATABASE_NAME).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        val existingTileCount = getTileCount(db)
        Log.d(TAG, "Existing tiles in database: $existingTileCount")
        db.close()
    }

    /**
     * Determine current download stage based on progress
     */
    private fun determineCurrentStage(
        totalBasemapTiles: Int, totalValhallaTiles: Int,
        downloadedBasemapTiles: Int, downloadedValhallaTiles: Int,
        processedTiles: Int, totalTilesToProcess: Int
    ): DownloadStage {
        return if (downloadedBasemapTiles != totalBasemapTiles) {
            DownloadStage.BASEMAP
        } else if (downloadedValhallaTiles != totalValhallaTiles) {
            DownloadStage.VALHALLA
        } else if (processedTiles != totalTilesToProcess) {
            DownloadStage.PROCESSING
        } else {
            DownloadStage.DONE
        }
    }

    /**
     * Get progress for current stage
     */
    private fun getStageProgress(
        currentStage: DownloadStage,
        downloadedBasemapTiles: Int, downloadedValhallaTiles: Int, processedTiles: Int
    ): Int {
        return when (currentStage) {
            DownloadStage.BASEMAP -> downloadedBasemapTiles
            DownloadStage.VALHALLA -> downloadedValhallaTiles
            DownloadStage.PROCESSING -> processedTiles
            DownloadStage.DONE -> 0
            DownloadStage.ERROR -> 0
        }
    }

    /**
     * Get total for current stage
     */
    private fun getStageTotal(
        currentStage: DownloadStage,
        totalBasemapTiles: Int, totalValhallaTiles: Int, totalTilesToProcess: Int,
    ): Int {
        return when (currentStage) {
            DownloadStage.BASEMAP -> totalBasemapTiles
            DownloadStage.VALHALLA -> totalValhallaTiles
            DownloadStage.PROCESSING -> totalTilesToProcess
            DownloadStage.DONE -> 0
            DownloadStage.ERROR -> 0
        }
    }

    /**
     * Download basemap phase if needed
     */
    private suspend fun downloadBasemapPhaseIfNeeded(
        resumePhase: DownloadStatus, boundingBox: BoundingBox, minZoom: Int, maxZoom: Int,
        areaId: String, name: String, totalValhallaTiles: Int
    ): Pair<Int, Int> {
        if (resumePhase == DownloadStatus.DOWNLOADING_BASEMAP) {
            Log.d(TAG, "Starting/continuing basemap tile download for area: $name (ID: $areaId)")

            updateAreaStatus(areaId, DownloadStatus.DOWNLOADING_BASEMAP)

            val basemapResult = downloadBasemapTiles(
                boundingBox, minZoom, min(maxZoom, MAX_BASEMAP_ZOOM), areaId, name
            )

            Log.d(
                TAG,
                "Basemap download complete: ${basemapResult.first} new tiles downloaded, ${basemapResult.second} failed"
            )

            if (basemapResult.second > 0) {
                throw IllegalStateException(
                    "Basemap download incomplete: ${basemapResult.second} tiles failed"
                )
            }

            updateAreaStatus(areaId, DownloadStatus.DOWNLOADING_VALHALLA)

            // Update progress to show basemap completion
            progressReporter?.updateProgress(
                areaId = areaId,
                areaName = name,
                currentStage = DownloadStage.VALHALLA,
                stageProgress = 0,
                stageTotal = totalValhallaTiles,
                isCompleted = false,
                hasError = false
            )

            return basemapResult
        } else {
            Log.d(TAG, "Skipping basemap download for area $areaId (already completed)")
            return Pair(0, 0)
        }
    }

    /**
     * Download Valhalla phase if needed
     */
    private suspend fun downloadValhallaPhaseIfNeeded(
        resumePhase: DownloadStatus, boundingBox: BoundingBox, areaId: String,
        db: SQLiteDatabase, name: String
    ): Pair<Int, Int> {
        if (resumePhase == DownloadStatus.DOWNLOADING_BASEMAP || resumePhase == DownloadStatus.DOWNLOADING_VALHALLA) {
            Log.d(TAG, "Starting/continuing Valhalla tile download for area: $name (ID: $areaId)")

            ensureValhallaStatus(areaId)

            val valhallaResult = downloadValhallaTiles(boundingBox, areaId, db, name)

            Log.d(
                TAG,
                "Valhalla download complete: ${valhallaResult.first} new tiles downloaded, ${valhallaResult.second} failed"
            )

            if (valhallaResult.second > 0) {
                throw IllegalStateException(
                    "Valhalla download incomplete: ${valhallaResult.second} tiles failed"
                )
            }

            return valhallaResult
        } else {
            Log.d(TAG, "Skipping Valhalla download for area $areaId (already completed)")
            return Pair(0, 0)
        }
    }

    /**
     * Ensure the area status is set to DOWNLOADING_VALHALLA if not already
     */
    private suspend fun ensureValhallaStatus(areaId: String) {
        val currentArea = offlineAreaDao.getOfflineAreaById(areaId)
        if (currentArea != null && currentArea.status != DownloadStatus.DOWNLOADING_VALHALLA) {
            val updatedArea = currentArea.copy(status = DownloadStatus.DOWNLOADING_VALHALLA)
            offlineAreaDao.updateOfflineArea(updatedArea)
        }
    }

    /**
     * Finalize download and store metadata
     */
    private suspend fun finalizeAndStoreMetadata(
        db: SQLiteDatabase, areaId: String, boundingBox: BoundingBox,
        minZoom: Int, maxZoom: Int, name: String
    ) {
        // Log final tile count
        val finalTileCount = getTileCount(db)
        Log.d(TAG, "Final tiles in database after download: $finalTileCount")

        // Store area metadata
        Log.d(TAG, "Storing area metadata for $areaId")
        storeAreaMetadata(db, areaId, boundingBox, minZoom, maxZoom, name)
    }

    /**
     * Calculate completion stats and log results
     */
    private fun calculateAndLogCompletionStats(
        boundingBox: BoundingBox, minZoom: Int, maxZoom: Int, areaId: String,
        basemapResult: Pair<Int, Int>, valhallaResult: Pair<Int, Int>,
        downloadedBasemapTiles: Int, downloadedValhallaTiles: Int
    ): Long {
        val outputFile = File(context.filesDir, OFFLINE_DATABASE_NAME)
        val fileSize = outputFile.length()

        val totalBasemapTiles =
            calculateTotalTiles(boundingBox, minZoom, min(maxZoom, MAX_BASEMAP_ZOOM), areaId).first
        val totalValhallaTiles = ValhallaTileUtils.tilesForBoundingBox(boundingBox).size

        val totalBasemapDownloaded = basemapResult.first + downloadedBasemapTiles
        val totalValhallaDownloaded = valhallaResult.first + downloadedValhallaTiles

        Log.d(
            TAG,
            "Tile download completed. $totalBasemapDownloaded/${totalBasemapTiles} basemap tiles, $totalValhallaDownloaded/${totalValhallaTiles} valhalla tiles downloaded. File size: $fileSize bytes"
        )

        return fileSize
    }

    /**
     * Process downloaded tiles and mark as completed
     */
    private suspend fun processDownloadedTilesAndComplete(
        areaId: String,
        name: String,
        fileSize: Long
    ) {
        // Update offline area status to PROCESSING
        val area = offlineAreaDao.getOfflineAreaById(areaId)
        if (area != null) {
            val processingArea =
                area.copy(status = DownloadStatus.PROCESSING_GEOCODER, fileSize = fileSize)
            offlineAreaDao.updateOfflineArea(processingArea)
        }
        val alreadyProcessed = downloadedTileDao.getProcessedTileCountForArea(areaId)
        val toProcess = downloadedTileDao.getUnprocessedTileCountForArea(areaId)
        val totalProcessingTiles = alreadyProcessed + toProcess
        Log.d(
            TAG,
            "Updating processing progress: $alreadyProcessed already processed, $toProcess remaining"
        )

        // Update service progress - downloads completed, now processing
        progressReporter?.updateProgress(
            areaId = areaId,
            areaName = name,
            currentStage = DownloadStage.PROCESSING,
            stageProgress = alreadyProcessed,
            stageTotal = totalProcessingTiles,
            isCompleted = false,
            hasError = false
        )

        // Start tile processing phase
        Log.d(TAG, "Starting tile processing phase for area $areaId")
        processDownloadedTiles(areaId)

        val remainingAfterProcessing =
            downloadedTileDao.getUnprocessedTileCountForArea(areaId)
        if (remainingAfterProcessing > 0) {
            throw IllegalStateException(
                "Offline geocoder incomplete: $remainingAfterProcessing tiles remain"
            )
        }

        // Update offline area status to COMPLETED
        val completedArea = offlineAreaDao.getOfflineAreaById(areaId)
        if (completedArea != null) {
            val finalArea =
                completedArea.copy(status = DownloadStatus.COMPLETED, fileSize = fileSize)
            offlineAreaDao.updateOfflineArea(finalArea)
        }

        // Update service progress - processing completed
        progressReporter?.updateProgress(
            areaId = areaId,
            areaName = name,
            currentStage = DownloadStage.DONE,
            stageProgress = 0,
            stageTotal = 0,
            isCompleted = true,
            hasError = false
        )
    }

    /**
     * Handle download error
     */
    private suspend fun handleDownloadError(areaId: String, name: String) {
        // Update service progress - download failed
        progressReporter?.updateProgress(
            areaId = areaId,
            areaName = name,
            currentStage = DownloadStage.ERROR,
            stageProgress = 0,
            stageTotal = 0,
            isCompleted = true,
            hasError = true
        )

        // Update offline area status
        val area = offlineAreaDao.getOfflineAreaById(areaId)
        if (area != null) {
            val storedBytes = File(context.filesDir, OFFLINE_DATABASE_NAME).length()
            val updatedArea = area.copy(
                status = DownloadStatus.FAILED,
                fileSize = maxOf(area.fileSize, storedBytes)
            )
            offlineAreaDao.updateOfflineArea(updatedArea)
        }
    }

    /**
     * Close database safely
     */
    private fun closeDatabaseSafely(db: SQLiteDatabase?) {
        try {
            db?.close()
        } catch (closeException: Exception) {
            Log.e(TAG, "Error closing database", closeException)
        }
    }

    /**
     * Download basemap tiles for the given bounds
     */
    suspend fun downloadBasemapTiles(
        boundingBox: BoundingBox, minZoom: Int, maxZoom: Int, areaId: String, areaName: String
    ): Pair<Int, Int> {
        var db: SQLiteDatabase? = null
        val downloadedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)

        try {
            // Calculate total tiles
            val (totalTiles, totalTilesToProcess) =
                calculateTotalTiles(boundingBox, minZoom, maxZoom, areaId)

            // Materialize only the basemap tiles actually needed by this area. Country downloads
            // use a conservative country+20 km mask at z10-z14; custom viewport downloads retain
            // the exact old rectangular behavior.
            val tileCoordinates =
                generateTileCoordinates(boundingBox, minZoom, maxZoom, areaId)

            Log.d(TAG, "Total basemap tiles to process: $totalTilesToProcess")

            // Load resume state once. The old code queried Room once per tile which becomes
            // extremely expensive for country-sized downloads. Failed retry records are kept
            // separate so they are never mistaken for successfully downloaded tiles.
            val successfulTileIds = downloadedTileDao
                .getSuccessfulTileIdsForAreaAndType(areaId, TileType.BASEMAP)
                .toMutableSet()
            val failedTileIds = downloadedTileDao
                .getFailedTileIdsForAreaAndType(areaId, TileType.BASEMAP)
                .toMutableSet()
            val existingTileCount = successfulTileIds.size
            Log.d(
                TAG,
                "Found $existingTileCount successful basemap tiles and ${failedTileIds.size} retry candidates for area $areaId"
            )

            // Calculate remaining tiles to download (not counting already downloaded ones)
            val remainingTiles = maxOf(0, totalTiles - existingTileCount)
            Log.d(
                TAG,
                "Remaining basemap tiles to download: $remainingTiles (total: $totalTiles, existing: $existingTileCount)"
            )

            // Open MBTiles database for tile insertion
            val outputFile = File(context.filesDir, OFFLINE_DATABASE_NAME)
            val dbExists = outputFile.exists()
            Log.d(TAG, "Opening MBTiles database for tile insertion: ${outputFile.absolutePath}")
            db = SQLiteDatabase.openOrCreateDatabase(outputFile, null)

            // Initialize MBTiles schema only if database is new
            if (!dbExists) {
                Log.d(TAG, "Initializing new MBTiles schema for basemap tiles")
                initializeMbtilesSchema(db)
            }

            val globalTileKeys = loadGlobalBasemapTileKeys(db)

            for (chunk in tileCoordinates.chunked(MAX_CONCURRENT_DOWNLOADS)) {
                // If a neighbouring country has already stored the same tile, copy the existing
                // BLOB into this area's row and record it in Room without another HTTP request.
                // Disk duplication is intentional here: it keeps the existing schema/renderer and
                // deletion semantics unchanged while eliminating duplicate network traffic.
                val reused = reuseExistingBasemapTiles(
                    db, chunk, areaId, successfulTileIds, failedTileIds, globalTileKeys
                )
                if (reused > 0) downloadedCount.addAndGet(reused)

                // Process this batch with parallel downloads
                val tileData = processBatch(
                    chunk,
                    areaId,
                    areaName,
                    remainingTiles, // Use remaining tiles instead of total for progress tracking
                    downloadedCount,
                    failedCount,
                    successfulTileIds,
                    failedTileIds
                )

                // Persist actual tile bytes first. Only after MBTiles commit succeeds do we
                // mark the same tiles successful in Room, so an interrupted write cannot create
                // a false "downloaded" marker with missing map data.
                if (tileData.isNotEmpty()) {
                    Log.d(TAG, "Inserting ${tileData.size} tiles into MBTiles database for chunk")
                    batchInsertTiles(db, tileData, areaId)

                    val successRecords = tileData.map { (zoom, coords, _) ->
                        val (x, y) = coords
                        DownloadedTile(
                            id = "basemap_${areaId}_${zoom}_${x}_${y}",
                            areaId = areaId,
                            tileType = TileType.BASEMAP,
                            downloadTimestamp = System.currentTimeMillis(),
                            retryCount = 0,
                            zoom = zoom,
                            tileX = x,
                            tileY = y,
                            processed = false,
                            hierarchyLevel = null,
                            tileIndex = null
                        )
                    }
                    downloadedTileDao.insertDownloadedTiles(successRecords)

                    for (record in successRecords) {
                        successfulTileIds.add(record.id)
                        failedTileIds.remove(record.id)
                    }
                    downloadedCount.addAndGet(successRecords.size)
                }

                progressReporter?.updateProgress(
                    areaId = areaId,
                    areaName = areaName,
                    currentStage = DownloadStage.BASEMAP,
                    stageProgress = successfulTileIds.size,
                    stageTotal = totalTiles,
                    isCompleted = false,
                    hasError = false
                )
            }

            // Retry only unresolved tiles in the same download session. A single transient
            // HTTP failure must not invalidate a country after thousands of successful tiles.
            var retryPass = 1
            while (retryPass < MAX_RETRY_COUNT) {
                val unresolved = tileCoordinates.filter { (zoom, x, y) ->
                    "basemap_${areaId}_${zoom}_${x}_${y}" !in successfulTileIds
                }
                if (unresolved.isEmpty()) break

                retryPass++
                Log.w(
                    TAG,
                    "Retry pass $retryPass/$MAX_RETRY_COUNT for ${unresolved.size} unresolved basemap tiles in $areaId"
                )
                delay(400L * (retryPass - 1))

                for (chunk in unresolved.chunked(MAX_CONCURRENT_DOWNLOADS)) {
                    val retryFailures = AtomicInteger(0)
                    val tileData = processBatch(
                        chunk,
                        areaId,
                        areaName,
                        totalTiles,
                        downloadedCount,
                        retryFailures,
                        successfulTileIds,
                        failedTileIds
                    )

                    if (tileData.isNotEmpty()) {
                        batchInsertTiles(db, tileData, areaId)
                        val successRecords = tileData.map { (zoom, coords, _) ->
                            val (x, y) = coords
                            DownloadedTile(
                                id = "basemap_${areaId}_${zoom}_${x}_${y}",
                                areaId = areaId,
                                tileType = TileType.BASEMAP,
                                downloadTimestamp = System.currentTimeMillis(),
                                retryCount = 0,
                                zoom = zoom,
                                tileX = x,
                                tileY = y,
                                processed = false,
                                hierarchyLevel = null,
                                tileIndex = null
                            )
                        }
                        downloadedTileDao.insertDownloadedTiles(successRecords)
                        for (record in successRecords) {
                            successfulTileIds.add(record.id)
                            failedTileIds.remove(record.id)
                        }
                        downloadedCount.addAndGet(successRecords.size)
                    }

                    progressReporter?.updateProgress(
                        areaId = areaId,
                        areaName = areaName,
                        currentStage = DownloadStage.BASEMAP,
                        stageProgress = successfulTileIds.size,
                        stageTotal = totalTiles,
                        isCompleted = false,
                        hasError = false
                    )
                }
            }

            // Only tiles still missing after all attempts count as failures. Earlier transient
            // failures that later succeeded must not poison the whole country result.
            val finalDownloadedCount = downloadedCount.get()
            val finalExistingTileCount =
                downloadedTileDao.getDownloadedTileCountForAreaAndType(areaId, TileType.BASEMAP)
            val unresolvedCount = maxOf(0, totalTiles - finalExistingTileCount)
            Log.d(
                TAG,
                "Downloaded $finalDownloadedCount new tiles, total for area: $finalExistingTileCount, unresolved: $unresolvedCount"
            )

            return Pair(finalDownloadedCount, unresolvedCount)
        } finally {
            // Close database
            try {
                db?.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "Error closing MBTiles database in downloadBasemapTiles", closeException)
            }
        }
    }

    private fun loadGlobalBasemapTileKeys(db: SQLiteDatabase): MutableSet<Long> {
        val keys = HashSet<Long>()
        db.rawQuery(
            "SELECT DISTINCT zoom_level, tile_column, tile_row FROM tiles",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val zoom = cursor.getInt(0)
                val x = cursor.getInt(1)
                val tmsY = cursor.getInt(2)
                val xyzY = ((1 shl zoom) - 1) - tmsY
                keys.add(packTileKey(zoom, x, xyzY))
            }
        }
        return keys
    }

    private suspend fun reuseExistingBasemapTiles(
        db: SQLiteDatabase,
        chunk: List<Triple<Int, Int, Int>>,
        areaId: String,
        successfulTileIds: MutableSet<String>,
        failedTileIds: MutableSet<String>,
        globalTileKeys: MutableSet<Long>
    ): Int {
        val records = mutableListOf<DownloadedTile>()
        val copyStatement = db.compileStatement(
            """
            INSERT OR REPLACE INTO tiles
                (zoom_level, tile_column, tile_row, tile_data, area_id)
            SELECT zoom_level, tile_column, tile_row, tile_data, ?
            FROM tiles
            WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?
            LIMIT 1
            """.trimIndent()
        )

        db.beginTransaction()
        try {
            for ((zoom, x, y) in chunk) {
                val tileId = "basemap_${areaId}_${zoom}_${x}_${y}"
                if (tileId in successfulTileIds) continue
                if (packTileKey(zoom, x, y) !in globalTileKeys) continue

                val tmsY = ((1 shl zoom) - 1) - y
                copyStatement.bindString(1, areaId)
                copyStatement.bindLong(2, zoom.toLong())
                copyStatement.bindLong(3, x.toLong())
                copyStatement.bindLong(4, tmsY.toLong())
                val rowId = copyStatement.executeInsert()
                copyStatement.clearBindings()
                if (rowId == -1L) continue

                records.add(
                    DownloadedTile(
                        id = tileId,
                        areaId = areaId,
                        tileType = TileType.BASEMAP,
                        downloadTimestamp = System.currentTimeMillis(),
                        retryCount = 0,
                        zoom = zoom,
                        tileX = x,
                        tileY = y,
                        processed = false,
                        hierarchyLevel = null,
                        tileIndex = null
                    )
                )
                successfulTileIds.add(tileId)
                failedTileIds.remove(tileId)
            }
            db.setTransactionSuccessful()
        } finally {
            copyStatement.close()
            db.endTransaction()
        }

        if (records.isNotEmpty()) {
            downloadedTileDao.insertDownloadedTiles(records)
            Log.d(TAG, "Reused ${records.size} basemap tiles from existing offline countries")
        }
        return records.size
    }

    private fun packTileKey(zoom: Int, x: Int, y: Int): Long =
        (zoom.toLong() shl 58) or (x.toLong() shl 29) or y.toLong()

    /**
     * Download Valhalla tiles for the given bounds
     */
    private suspend fun downloadValhallaTiles(
        boundingBox: BoundingBox,
        areaId: String,
        db: SQLiteDatabase,
        areaName: String
    ): Pair<Int, Int> {
        val expectedTiles = ValhallaTileUtils.tilesForBoundingBox(boundingBox)
        val totalValhallaTiles = expectedTiles.size
        var downloadedCount = 0

        Log.d(TAG, "Total Valhalla tiles to download: $totalValhallaTiles")
        ensureValhallaTilesDirectory()

        // MBTiles references are the source of truth for already installed graph files.
        // Mirror them into Room so resume/progress logic remains truthful after app restarts.
        val existingPaths = loadExistingValhallaTilePaths(db, areaId)
        val existingTiles = existingPaths
            .filterValues { path -> File(path).isFile && File(path).length() > 0L }
            .keys
            .toMutableSet()

        if (existingTiles.isNotEmpty()) {
            downloadedTileDao.insertDownloadedTiles(
                existingTiles.map { (level, index) ->
                    DownloadedTile.forValhallaTile(areaId, level, index)
                }
            )
        }

        val completedTiles = existingTiles.toMutableSet()
        val globalTiles = loadGlobalValhallaTilePaths(db)

        // Reuse graph files already downloaded for a neighbouring country. This only creates
        // another reference; the physical .gph file is not downloaded or duplicated.
        val reused = mutableListOf<Triple<Int, Int, String>>()
        for ((level, index) in expectedTiles) {
            val key = Pair(level, index)
            if (key in completedTiles) continue
            val existingPath = globalTiles[key]
            if (existingPath != null && File(existingPath).isFile && File(existingPath).length() > 0L) {
                reused.add(Triple(level, index, existingPath))
                completedTiles.add(key)
            }
        }

        if (reused.isNotEmpty()) {
            batchStoreValhallaTileReferences(db, reused, areaId)
            downloadedTileDao.insertDownloadedTiles(
                reused.map { (level, index, _) ->
                    DownloadedTile.forValhallaTile(areaId, level, index)
                }
            )
            downloadedCount += reused.size
            progressReporter?.updateProgress(
                areaId = areaId,
                areaName = areaName,
                currentStage = DownloadStage.VALHALLA,
                stageProgress = completedTiles.size,
                stageTotal = totalValhallaTiles,
                isCompleted = false,
                hasError = false
            )
            Log.d(TAG, "Reused ${reused.size} Valhalla routing tiles from existing offline areas")
        }

        // Retry only the graph files still missing. Each successful response is first installed
        // atomically on disk, then its references are committed in one SQLite transaction.
        var attempt = 1
        while (attempt <= MAX_RETRY_COUNT) {
            val unresolved = expectedTiles.filterNot { it in completedTiles }
            if (unresolved.isEmpty()) break

            if (attempt > 1) {
                Log.w(
                    TAG,
                    "Valhalla retry pass $attempt/$MAX_RETRY_COUNT for ${unresolved.size} tiles"
                )
                delay(500L * (attempt - 1))
            }

            for (chunk in unresolved.chunked(MAX_CONCURRENT_VALHALLA_DOWNLOADS)) {
                val results = coroutineScope {
                    chunk.map { (level, index) ->
                        async {
                            Triple(level, index, downloadValhallaTile(level, index))
                        }
                    }.awaitAll()
                }

                val successful = results.mapNotNull { (level, index, result) ->
                    val (success, path) = result
                    if (success && path != null) Triple(level, index, path) else null
                }

                if (successful.isNotEmpty()) {
                    batchStoreValhallaTileReferences(db, successful, areaId)
                    downloadedTileDao.insertDownloadedTiles(
                        successful.map { (level, index, _) ->
                            DownloadedTile.forValhallaTile(areaId, level, index)
                        }
                    )
                    for ((level, index, _) in successful) {
                        completedTiles.add(Pair(level, index))
                    }
                    downloadedCount += successful.size
                }

                progressReporter?.updateProgress(
                    areaId = areaId,
                    areaName = areaName,
                    currentStage = DownloadStage.VALHALLA,
                    stageProgress = completedTiles.size,
                    stageTotal = totalValhallaTiles,
                    isCompleted = false,
                    hasError = false
                )
            }

            attempt++
        }

        val unresolvedCount = expectedTiles.count { it !in completedTiles }
        performFinalValhallaConsistencyCheck(db, areaId)
        Log.d(
            TAG,
            "Valhalla routing graph ready: ${completedTiles.size}/$totalValhallaTiles, unresolved: $unresolvedCount"
        )
        return Pair(downloadedCount, unresolvedCount)
    }

    /**
     * Load existing Valhalla references in one query so country downloads do not perform
     * a SQLite existence query for every routing tile.
     */
    private fun loadExistingValhallaTiles(
        db: SQLiteDatabase,
        areaId: String
    ): Set<Pair<Int, Int>> {
        val result = mutableSetOf<Pair<Int, Int>>()
        db.rawQuery(
            "SELECT hierarchy_level, tile_index FROM valhalla_tiles WHERE area_id = ?",
            arrayOf(areaId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Pair(cursor.getInt(0), cursor.getInt(1)))
            }
        }
        return result
    }

    private fun loadGlobalValhallaTilePaths(
        db: SQLiteDatabase
    ): Map<Pair<Int, Int>, String> {
        val result = mutableMapOf<Pair<Int, Int>, String>()
        db.rawQuery(
            "SELECT hierarchy_level, tile_index, file_path FROM valhalla_tiles",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = Pair(cursor.getInt(0), cursor.getInt(1))
                result.putIfAbsent(key, cursor.getString(2))
            }
        }
        return result
    }

    private fun loadExistingValhallaTilePaths(
        db: SQLiteDatabase,
        areaId: String
    ): Map<Pair<Int, Int>, String> {
        val result = mutableMapOf<Pair<Int, Int>, String>()
        db.rawQuery(
            "SELECT hierarchy_level, tile_index, file_path FROM valhalla_tiles WHERE area_id = ?",
            arrayOf(areaId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[Pair(cursor.getInt(0), cursor.getInt(1))] = cursor.getString(2)
            }
        }
        return result
    }

    /**
     * Log the count of existing Valhalla tiles for the area
     */
    private fun logExistingValhallaTileCount(db: SQLiteDatabase, areaId: String) {
        // Validate consistency between expected tiles and existing tiles in database
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM valhalla_tiles WHERE area_id = ?", arrayOf(areaId)
            )
            if (cursor.moveToFirst()) {
                val existingValhallaTileCount = cursor.getInt(0)
                Log.d(
                    TAG, "Found $existingValhallaTileCount existing Valhalla tiles for area $areaId"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking existing Valhalla tiles for area $areaId", e)
        } finally {
            cursor?.close()
        }
    }

    /**
     * Ensure the valhalla tiles directory exists
     */
    private fun ensureValhallaTilesDirectory() {
        // Create valhalla tiles directory
        val valhallaTilesDir = File(context.filesDir, "valhalla_tiles")
        if (!valhallaTilesDir.exists()) {
            valhallaTilesDir.mkdirs()
        }
    }

    /**
     * Process a single Valhalla tile - return true if downloaded successfully, null if failed
     */
    private suspend fun processValhallaTile(
        hierarchyLevel: Int, tileIndex: Int, areaId: String, db: SQLiteDatabase,
        areaName: String, totalValhallaTiles: Int, downloadedCount: Int
    ): Boolean? {
        // Check if this Valhalla tile already exists in the database
        if (valhallaTileExists(db, hierarchyLevel, tileIndex, areaId)) {
            Log.v(
                TAG,
                "Skipping already downloaded Valhalla tile $hierarchyLevel/$tileIndex for area $areaId"
            )
            // Update service progress without incrementing counters
            progressReporter?.updateProgress(
                areaId = areaId,
                areaName = areaName,
                currentStage = DownloadStage.VALHALLA,
                stageProgress = downloadedCount,
                stageTotal = totalValhallaTiles,
                isCompleted = false,
                hasError = false
            )
            return null // Skip tile (not a failure, just already exists)
        }

        val (success, filePath) = downloadValhallaTile(hierarchyLevel, tileIndex)
        return if (success && filePath != null) {
            // Store tile reference in database
            storeValhallaTileReference(db, hierarchyLevel, tileIndex, filePath, areaId)

            // Update service progress
            progressReporter?.updateProgress(
                areaId = areaId,
                areaName = areaName,
                currentStage = DownloadStage.VALHALLA,
                stageProgress = downloadedCount + 1, // Add 1 since we return before increment
                stageTotal = totalValhallaTiles,
                isCompleted = false,
                hasError = false
            )

            true // Successfully downloaded
        } else {
            false // Failed to download
        }
    }

    /**
     * Check if a Valhalla tile already exists in the database
     */
    private fun valhallaTileExists(
        db: SQLiteDatabase, hierarchyLevel: Int, tileIndex: Int, areaId: String
    ): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM valhalla_tiles WHERE hierarchy_level = ? AND tile_index = ? AND area_id = ?",
                arrayOf(hierarchyLevel.toString(), tileIndex.toString(), areaId)
            )
            cursor.moveToFirst() && cursor.getInt(0) > 0
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error checking existing Valhalla tile $hierarchyLevel/$tileIndex for area $areaId",
                e
            )
            false
        } finally {
            cursor?.close()
        }
    }

    /**
     * Perform final consistency check for Valhalla tiles
     */
    private fun performFinalValhallaConsistencyCheck(db: SQLiteDatabase, areaId: String) {
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM valhalla_tiles WHERE area_id = ?", arrayOf(areaId)
            )
            if (cursor.moveToFirst()) {
                val finalValhallaTileCount = cursor.getInt(0)
                Log.d(TAG, "Final Valhalla tiles for area $areaId: $finalValhallaTileCount")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking final Valhalla tiles for area $areaId", e)
        } finally {
            cursor?.close()
        }
    }

    /**
     * Download a single Valhalla tile and save it to disk using streaming to avoid OOM
     */
    private suspend fun downloadValhallaTile(
        hierarchyLevel: Int,
        tileIndex: Int,
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val url = ValhallaTileUtils.getTileUrl(
            "https://tiles.maps.murena.com/valhalla-250825", hierarchyLevel, tileIndex
        )
        val tileFile = ValhallaTileUtils.getLocalTileFilePath(
            File("${context.filesDir}/valhalla_tiles/"), hierarchyLevel, tileIndex
        )
        val partialFile = File(tileFile.absolutePath + ".part")

        try {
            // A pending tile has no trusted database reference. Remove leftovers created by an
            // interrupted older build so the routing engine can never read a partial .gph.
            partialFile.delete()
            if (tileFile.exists()) tileFile.delete()

            Log.v(TAG, "Downloading Valhalla tile $hierarchyLevel/$tileIndex from $url")

            val totalBytes = httpClient.prepareGet(url).execute { response ->
                if (response.status.value != 200) {
                    throw Exception(
                        "HTTP ${response.status.value}: ${response.status.description}"
                    )
                }

                val channel = response.bodyAsChannel()
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var totalBytesRead = 0L
                    while (true) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead == -1) break
                        if (bytesRead == 0) continue
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }
                    output.flush()
                    totalBytesRead
                }
            }

            if (totalBytes <= 0L) {
                throw Exception("Downloaded empty Valhalla tile")
            }

            if (!partialFile.renameTo(tileFile)) {
                partialFile.copyTo(tileFile, overwrite = true)
                partialFile.delete()
            }

            Log.v(
                TAG,
                "Downloaded Valhalla tile $hierarchyLevel/$tileIndex, size: $totalBytes bytes, saved atomically to: ${tileFile.absolutePath}"
            )
            Pair(true, tileFile.absolutePath)
        } catch (e: Exception) {
            partialFile.delete()
            // The final path is deliberately absent on failure.
            tileFile.delete()
            Log.e(TAG, "Error downloading Valhalla tile $hierarchyLevel/$tileIndex via HTTP", e)
            Pair(false, null)
        }
    }

    /**
     * Store Valhalla tile reference in database
     */
    private fun storeValhallaTileReference(
        db: SQLiteDatabase, hierarchyLevel: Int, tileIndex: Int, filePath: String, areaId: String
    ) {
        try {
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO valhalla_tiles (hierarchy_level, tile_index, file_path, area_id) VALUES (?, ?, ?, ?)"
            )
            statement.bindLong(1, hierarchyLevel.toLong())
            statement.bindLong(2, tileIndex.toLong())
            statement.bindString(3, filePath)
            statement.bindString(4, areaId)
            statement.executeInsert()
            statement.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error storing Valhalla tile reference for $hierarchyLevel/$tileIndex", e)
        }
    }

    private fun batchStoreValhallaTileReferences(
        db: SQLiteDatabase,
        tiles: List<Triple<Int, Int, String>>,
        areaId: String
    ) {
        if (tiles.isEmpty()) return

        val statement = db.compileStatement(
            "INSERT OR REPLACE INTO valhalla_tiles (hierarchy_level, tile_index, file_path, area_id) VALUES (?, ?, ?, ?)"
        )
        db.beginTransaction()
        try {
            for ((level, index, path) in tiles) {
                statement.bindLong(1, level.toLong())
                statement.bindLong(2, index.toLong())
                statement.bindString(3, path)
                statement.bindString(4, areaId)
                statement.executeInsert()
                statement.clearBindings()
            }
            db.setTransactionSuccessful()
        } finally {
            statement.close()
            db.endTransaction()
        }
    }

    /**
     * Calculate total number of tiles for all zoom levels
     */
    fun calculateTotalTiles(
        boundingBox: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        areaId: String? = null
    ): Pair<Int, Int> {
        var totalTiles = 0
        var z14Tiles = 0
        val countryCode = countryCodeFromAreaId(areaId)

        for (zoom in minZoom..maxZoom) {
            val (minX, maxX, minY, maxY) = calculateTileRange(boundingBox, zoom)
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    if (countryCode != null &&
                        !countryTileMask.containsBufferedTile(countryCode, zoom, x, y)
                    ) {
                        continue
                    }
                    totalTiles++
                    if (zoom == 14) z14Tiles++
                }
            }
        }
        return Pair(totalTiles, z14Tiles)
    }

    /**
     * Generate all basemap coordinates. Country areas are conservatively masked; normal
     * user-selected viewport areas keep the previous full rectangle.
     */
    private fun generateTileCoordinates(
        boundingBox: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        areaId: String? = null
    ): List<Triple<Int, Int, Int>> {
        val tileCoordinates = mutableListOf<Triple<Int, Int, Int>>()
        val countryCode = countryCodeFromAreaId(areaId)

        for (zoom in minZoom..maxZoom) {
            val (minX, maxX, minY, maxY) = calculateTileRange(boundingBox, zoom)
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    if (countryCode != null &&
                        !countryTileMask.containsBufferedTile(countryCode, zoom, x, y)
                    ) {
                        continue
                    }
                    tileCoordinates.add(Triple(zoom, x, y))
                }
            }
        }
        return tileCoordinates
    }

    /**
     * Process a batch of tiles
     */
    suspend fun processBatch(
        chunk: List<Triple<Int, Int, Int>>,
        areaId: String,
        areaName: String,
        totalTiles: Int,
        downloadedCount: AtomicInteger,
        failedCount: AtomicInteger,
        successfulTileIds: MutableSet<String>? = null,
        failedTileIds: MutableSet<String>? = null
    ): List<Triple<Int, Pair<Int, Int>, ByteArray>> = coroutineScope {
        data class DownloadAttempt(
            val z: Int,
            val x: Int,
            val y: Int,
            val tileId: String,
            val previousRetryCount: Int,
            val success: Boolean,
            val data: ByteArray?
        )

        val attempts = chunk.mapNotNull { (z, xCoord, yCoord) ->
            val tileId = "basemap_${areaId}_${z}_${xCoord}_${yCoord}"
            if (successfulTileIds?.contains(tileId) == true) {
                null
            } else {
                Triple(z, xCoord, yCoord)
            }
        }.map { (z, xCoord, yCoord) ->
            async {
                val tileId = "basemap_${areaId}_${z}_${xCoord}_${yCoord}"

                val existingTile = when {
                    successfulTileIds == null -> downloadedTileDao.getTileById(tileId)
                    failedTileIds?.contains(tileId) == true -> downloadedTileDao.getTileById(tileId)
                    else -> null
                }

                if (existingTile != null && existingTile.retryCount == 0) {
                    return@async DownloadAttempt(
                        z, xCoord, yCoord, tileId, 0, true, null
                    )
                }

                val (success, data) = downloadTile(z, xCoord, yCoord, areaId)
                DownloadAttempt(
                    z = z,
                    x = xCoord,
                    y = yCoord,
                    tileId = tileId,
                    previousRetryCount = existingTile?.retryCount ?: 0,
                    success = success,
                    data = data
                )
            }
        }.awaitAll()

        val results = mutableListOf<Triple<Int, Pair<Int, Int>, ByteArray>>()
        val failedRecords = mutableListOf<DownloadedTile>()

        for (attempt in attempts) {
            if (attempt.success && attempt.data != null) {
                // Success is deliberately not written to Room here. The caller first commits
                // tile bytes to MBTiles and then atomically records successful download state.
                results.add(Triple(attempt.z, Pair(attempt.x, attempt.y), attempt.data))
            } else if (!attempt.success) {
                val retryCount = attempt.previousRetryCount + 1
                if (retryCount < MAX_RETRY_COUNT) {
                    failedRecords.add(
                        DownloadedTile(
                            id = attempt.tileId,
                            areaId = areaId,
                            tileType = TileType.BASEMAP,
                            downloadTimestamp = System.currentTimeMillis(),
                            retryCount = retryCount,
                            zoom = attempt.z,
                            tileX = attempt.x,
                            tileY = attempt.y,
                            processed = false,
                            hierarchyLevel = null,
                            tileIndex = null
                        )
                    )
                    failedTileIds?.add(attempt.tileId)
                    successfulTileIds?.remove(attempt.tileId)
                    Log.w(
                        TAG,
                        "Failed to download tile ${attempt.z}/${attempt.x}/${attempt.y} for area $areaId (attempt $retryCount/$MAX_RETRY_COUNT)"
                    )
                } else {
                    Log.e(
                        TAG,
                        "Giving up on tile ${attempt.z}/${attempt.x}/${attempt.y} for area $areaId after $retryCount attempts"
                    )
                }
                failedCount.incrementAndGet()
            }
        }

        if (failedRecords.isNotEmpty()) {
            downloadedTileDao.insertDownloadedTiles(failedRecords)
        }

        results
    }

    /**
     * Initialize the MBTiles database schema
     */
    private fun initializeMbtilesSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS metadata (
                name TEXT,
                value TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tiles (
                zoom_level INTEGER,
                tile_column INTEGER,
                tile_row INTEGER,
                tile_data BLOB,
                area_id TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS valhalla_tiles (
                hierarchy_level INTEGER,
                tile_index INTEGER,
                file_path TEXT,
                area_id TEXT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row, area_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS valhalla_tile_index ON valhalla_tiles (hierarchy_level, tile_index, area_id)")

        // Insert basic metadata
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('name', 'Cardinal Maps Offline Areas')")
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('type', 'baselayer')")
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('version', '1.0')")
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('description', 'Offline map tiles for Cardinal Maps')")
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('format', 'pbf')")
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('minzoom', '0')")
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('maxzoom', '14')")
        // Specify that we're using TMS coordinate system
        db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('scheme', 'tms')")
    }

    /**
     * Download a single tile and return its data
     */
    suspend fun downloadTile(
        zoom: Int, x: Int, y: Int, layer: String
    ): Pair<Boolean, ByteArray?> = withContext(Dispatchers.IO) {
        try {
            // Build the URL for the tile
            val urlTemplate = context.getString(R.string.tile_url_template)
            val url = urlTemplate.replace("{z}", zoom.toString()).replace("{x}", x.toString())
                .replace("{y}", y.toString())

            Log.v(TAG, "Downloading tile $layer/$zoom/$x/$y from $url")

            // Use ktor to get the tile data
            val response = httpClient.get(url)

            // Check response code
            if (response.status.value != 200) {
                Log.e(TAG, "Error downloading tile $layer/$zoom/$x/$y: HTTP ${response.status}")
                return@withContext Pair(false, null)
            }

            val data = response.body<ByteArray>()

            Log.v(
                TAG,
                "Downloaded tile $layer/$zoom/$x/$y, size: ${data.size} bytes, status: ${response.status}"
            )

            // Tile processing is now postponed until after all downloads complete
            // Processing will happen in batch during the PROCESSING phase

            Pair(true, data)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading tile $layer/$zoom/$x/$y via HTTP", e)
            Pair(false, null)
        }
    }

    /**
     * Batch insert tiles into the database using a transaction
     */
    private fun batchInsertTiles(
        db: SQLiteDatabase, tilesData: List<Triple<Int, Pair<Int, Int>, ByteArray>>, areaId: String
    ) {
        if (tilesData.isEmpty()) {
            Log.d(TAG, "No tiles to insert")
            return
        }

        Log.d(TAG, "Starting batch insert of ${tilesData.size} tiles")

        db.beginTransaction()
        try {
            // Pre-compile the insert statement for better performance
            val insertStatement = db.compileStatement(
                "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data, area_id) VALUES (?, ?, ?, ?, ?)"
            )

            try {
                for ((zoom, coords, data) in tilesData) {
                    val (x, y) = coords
                    val tmsY = (2.0.pow(zoom.toDouble()) - 1 - y).toLong()
                    insertStatement.bindLong(1, zoom.toLong())
                    insertStatement.bindLong(2, x.toLong())
                    insertStatement.bindLong(3, tmsY.toLong())
                    insertStatement.bindBlob(4, data)
                    insertStatement.bindString(5, areaId)
                    insertStatement.executeInsert()
                    insertStatement.clearBindings()
                }

                db.setTransactionSuccessful()
                Log.d(TAG, "Successfully inserted ${tilesData.size} tiles in transaction")
            } finally {
                insertStatement.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during batch insert of tiles", e)
            throw e
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Store metadata for an offline area
     */
    private fun storeAreaMetadata(
        db: SQLiteDatabase,
        areaId: String,
        boundingBox: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        name: String
    ) {
        try {
            // Create areas table if it doesn't exist
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS areas (
                    area_id TEXT PRIMARY KEY,
                    name TEXT,
                    north REAL,
                    south REAL,
                    east REAL,
                    west REAL,
                    min_zoom INTEGER,
                    max_zoom INTEGER,
                    download_date INTEGER
                )
                """.trimIndent()
            )

            // Insert or update area metadata
            val statement = db.compileStatement(
                """
                INSERT OR REPLACE INTO areas 
                (area_id, name, north, south, east, west, min_zoom, max_zoom, download_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            statement.bindString(1, areaId)
            statement.bindString(2, name)
            statement.bindDouble(3, boundingBox.north)
            statement.bindDouble(4, boundingBox.south)
            statement.bindDouble(5, boundingBox.east)
            statement.bindDouble(6, boundingBox.west)
            statement.bindLong(7, minZoom.toLong())
            statement.bindLong(8, maxZoom.toLong())
            statement.bindLong(9, System.currentTimeMillis())
            statement.executeInsert()
            statement.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error storing area metadata for $areaId", e)
        }
    }

    /**
     * Delete tiles for a specific area ID from the database
     * Only deletes tiles that are exclusively used by this area (no shared tiles)
     */
    fun deleteTilesForArea(areaId: String): Boolean {
        var db: SQLiteDatabase? = null
        try {
            Log.d(TAG, "Starting tile deletion for area ID: $areaId")

            val outputFile = File(context.filesDir, OFFLINE_DATABASE_NAME)
            if (!outputFile.exists()) {
                Log.d(TAG, "Offline database file does not exist, nothing to delete")
                return true
            }

            db = SQLiteDatabase.openDatabase(
                outputFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            deleteUnsharedTiles(db, areaId)
            deleteUnsharedValhallaTiles(db, areaId)

            val deletedMetadata = db.delete("areas", "area_id = ?", arrayOf(areaId))
            Log.d(TAG, "Deleted $deletedMetadata area metadata entries for area ID: $areaId")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting tiles for area ID: $areaId", e)
            return false
        } finally {
            closeDatabaseSafely(db)
        }
    }

    /**
     * Delete tiles that are not shared with other areas
     */
    private fun deleteUnsharedTiles(db: SQLiteDatabase, areaId: String): TileDeletionResult {
        val tilesToDelete = getTilesForArea(db, areaId)
        Log.d(TAG, "Found ${tilesToDelete.size} tiles for area ID: $areaId")

        var actuallyDeletedTiles = 0
        var sharedTiles = 0

        for (tile in tilesToDelete) {
            if (!isTileSharedWithOtherAreas(db, tile, areaId)) {
                val deleted = deleteTile(db, tile, areaId)
                actuallyDeletedTiles += deleted
                Log.v(
                    TAG,
                    "Deleted tile ${tile.zoomLevel}/${tile.tileColumn}/${tile.tileRow} for area ID: $areaId"
                )
            } else {
                sharedTiles++
                Log.v(
                    TAG,
                    "Skipping shared tile ${tile.zoomLevel}/${tile.tileColumn}/${tile.tileRow} for area ID: $areaId"
                )
            }
        }

        Log.d(
            TAG,
            "Deleted $actuallyDeletedTiles tiles for area ID: $areaId (shared tiles: $sharedTiles, total: ${tilesToDelete.size})"
        )
        return TileDeletionResult(actuallyDeletedTiles, sharedTiles, tilesToDelete.size)
    }

    /**
     * Delete Valhalla tiles that are not shared with other areas
     */
    private fun deleteUnsharedValhallaTiles(
        db: SQLiteDatabase,
        areaId: String
    ): TileDeletionResult {
        val valhallaTilesToDelete = getValhallaTilesForArea(db, areaId)
        Log.d(TAG, "Found ${valhallaTilesToDelete.size} Valhalla tiles for area ID: $areaId")

        var actuallyDeletedValhallaTiles = 0
        var sharedValhallaTiles = 0

        for (valhallaTile in valhallaTilesToDelete) {
            if (!isValhallaTileSharedWithOtherAreas(db, valhallaTile, areaId)) {
                deleteValhallaPhysicalFile(valhallaTile)
                val deleted = deleteValhallaTile(db, valhallaTile, areaId)
                actuallyDeletedValhallaTiles += deleted
                Log.v(
                    TAG,
                    "Deleted Valhalla tile ${valhallaTile.hierarchyLevel}/${valhallaTile.tileIndex} for area ID: $areaId"
                )
            } else {
                sharedValhallaTiles++
                Log.v(
                    TAG,
                    "Skipping shared Valhalla tile ${valhallaTile.hierarchyLevel}/${valhallaTile.tileIndex} for area ID: $areaId"
                )
            }
        }

        Log.d(
            TAG,
            "Deleted $actuallyDeletedValhallaTiles Valhalla tiles for area ID: $areaId (shared tiles: $sharedValhallaTiles, total: ${valhallaTilesToDelete.size})"
        )
        return TileDeletionResult(
            actuallyDeletedValhallaTiles,
            sharedValhallaTiles,
            valhallaTilesToDelete.size
        )
    }

    /**
     * Delete the physical file for a Valhalla tile
     */
    private fun deleteValhallaPhysicalFile(valhallaTile: ValhallaTileCoordinates) {
        try {
            val file = File(valhallaTile.filePath)
            if (file.exists() && file.delete()) {
                Log.v(TAG, "Deleted Valhalla tile file: ${valhallaTile.filePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting Valhalla tile file: ${valhallaTile.filePath}", e)
        }
    }

    /**
     * Get all tiles for a specific area
     */
    private fun getTilesForArea(db: SQLiteDatabase, areaId: String): List<TileCoordinates> {
        val tiles = mutableListOf<TileCoordinates>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT zoom_level, tile_column, tile_row FROM tiles WHERE area_id = ?",
                arrayOf(areaId)
            )
            while (cursor.moveToNext()) {
                val zoomLevel = cursor.getInt(0)
                val tileColumn = cursor.getInt(1)
                val tileRow = cursor.getInt(2)
                tiles.add(TileCoordinates(zoomLevel, tileColumn, tileRow))
            }
        } finally {
            cursor?.close()
        }
        return tiles
    }

    /**
     * Check if a tile is shared with other areas
     */
    private fun isTileSharedWithOtherAreas(
        db: SQLiteDatabase, tile: TileCoordinates, areaId: String
    ): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? AND area_id != ?",
                arrayOf(
                    tile.zoomLevel.toString(),
                    tile.tileColumn.toString(),
                    tile.tileRow.toString(),
                    areaId
                )
            )
            cursor.moveToFirst() && cursor.getInt(0) > 0
        } finally {
            cursor?.close()
        }
    }

    /**
     * Delete a specific tile for an area
     */
    private fun deleteTile(db: SQLiteDatabase, tile: TileCoordinates, areaId: String): Int {
        return db.delete(
            "tiles", "zoom_level = ? AND tile_column = ? AND tile_row = ? AND area_id = ?", arrayOf(
                tile.zoomLevel.toString(),
                tile.tileColumn.toString(),
                tile.tileRow.toString(),
                areaId
            )
        )
    }

    /**
     * Get the total number of tiles in the database
     */
    private fun getTileCount(db: SQLiteDatabase): Int {
        var cursor: Cursor? = null
        return try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM tiles", null)
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting tile count", e)
            0
        } finally {
            cursor?.close()
        }
    }

    private data class ValhallaTileCoordinates(
        val hierarchyLevel: Int, val tileIndex: Int, val filePath: String
    )

    /**
     * Get all Valhalla tiles for a specific area
     */
    private fun getValhallaTilesForArea(
        db: SQLiteDatabase, areaId: String
    ): List<ValhallaTileCoordinates> {
        val tiles = mutableListOf<ValhallaTileCoordinates>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT hierarchy_level, tile_index, file_path FROM valhalla_tiles WHERE area_id = ?",
                arrayOf(areaId)
            )
            while (cursor.moveToNext()) {
                val hierarchyLevel = cursor.getInt(0)
                val tileIndex = cursor.getInt(1)
                val filePath = cursor.getString(2)
                tiles.add(ValhallaTileCoordinates(hierarchyLevel, tileIndex, filePath))
            }
        } finally {
            cursor?.close()
        }
        return tiles
    }

    /**
     * Check if a Valhalla tile is shared with other areas
     */
    private fun isValhallaTileSharedWithOtherAreas(
        db: SQLiteDatabase, tile: ValhallaTileCoordinates, areaId: String
    ): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM valhalla_tiles WHERE hierarchy_level = ? AND tile_index = ? AND area_id != ?",
                arrayOf(
                    tile.hierarchyLevel.toString(), tile.tileIndex.toString(), areaId
                )
            )
            cursor.moveToFirst() && cursor.getInt(0) > 0
        } finally {
            cursor?.close()
        }
    }

    /**
     * Delete a specific Valhalla tile for an area
     */
    private fun deleteValhallaTile(
        db: SQLiteDatabase, tile: ValhallaTileCoordinates, areaId: String
    ): Int {
        return db.delete(
            "valhalla_tiles", "hierarchy_level = ? AND tile_index = ? AND area_id = ?", arrayOf(
                tile.hierarchyLevel.toString(), tile.tileIndex.toString(), areaId
            )
        )
    }

    /**
     * Process all downloaded tiles in batch after downloads are complete
     * Reads tiles from database to avoid keeping them in memory
     */
    private suspend fun processDownloadedTiles(
        areaId: String
    ) = withContext(Dispatchers.IO) {
        var db: SQLiteDatabase? = null
        try {
            val outputFile = File(context.filesDir, OFFLINE_DATABASE_NAME)
            db = SQLiteDatabase.openDatabase(
                outputFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )

            val alreadyProcessedIds = downloadedTileDao
                .getProcessedBasemapTileIds(areaId)
                .toHashSet()
            val alreadyProcessedCount = alreadyProcessedIds.size
            val remainingCount = downloadedTileDao.getUnprocessedTileCountForArea(areaId)
            val totalTilesToProcess = alreadyProcessedCount + remainingCount

            Log.d(
                TAG,
                "Starting geocoder processing for area $areaId: $alreadyProcessedCount already processed, $remainingCount remaining"
            )

            val cursor = db.rawQuery(
                "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles WHERE area_id = ? AND zoom_level = 14",
                arrayOf(areaId)
            )

            var processedCount = 0
            var failedCount = 0

            try {
                val tileBatch = mutableListOf<Triple<Int, Pair<Int, Int>, ByteArray>>()

                while (cursor.moveToNext()) {
                    val zoom = cursor.getInt(0)
                    val x = cursor.getInt(1)
                    val storedTmsY = cursor.getInt(2)
                    val xyzY = (2.0.pow(zoom.toDouble()) - 1 - storedTmsY).toInt()
                    val tileId = "basemap_${areaId}_${zoom}_${x}_${xyzY}"

                    if (tileId in alreadyProcessedIds) {
                        continue
                    }

                    val data = cursor.getBlob(3)
                    tileBatch.add(Triple(zoom, Pair(x, storedTmsY), data))

                    if (tileBatch.size >= GEOCODER_BATCH_SIZE) {
                        val (batchProcessed, batchFailed) = processTileBatch(tileBatch, areaId)
                        processedCount += batchProcessed
                        failedCount += batchFailed
                        tileBatch.clear()

                        progressReporter?.updateProgress(
                            areaId = areaId,
                            areaName = "",
                            currentStage = DownloadStage.PROCESSING,
                            stageProgress = alreadyProcessedCount + processedCount,
                            stageTotal = totalTilesToProcess,
                            isCompleted = false,
                            hasError = false
                        )

                        delay(5)
                    }
                }

                if (tileBatch.isNotEmpty()) {
                    val (batchProcessed, batchFailed) = processTileBatch(tileBatch, areaId)
                    processedCount += batchProcessed
                    failedCount += batchFailed

                    progressReporter?.updateProgress(
                        areaId = areaId,
                        areaName = "",
                        currentStage = DownloadStage.PROCESSING,
                        stageProgress = alreadyProcessedCount + processedCount,
                        stageTotal = totalTilesToProcess,
                        isCompleted = false,
                        hasError = false
                    )
                }

                Log.d(
                    TAG,
                    "Tile processing completed: $processedCount newly processed, $failedCount failed"
                )
            } finally {
                cursor.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during batch tile processing for area $areaId", e)
        } finally {
            try {
                db?.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "Error closing database during processing", closeException)
            }
        }
    }

    /**
     * Process a batch of tiles
     */
    private suspend fun processTileBatch(
        tileBatch: List<Triple<Int, Pair<Int, Int>, ByteArray>>, areaId: String
    ): Pair<Int, Int> {
        if (tileBatch.isEmpty()) return Pair(0, 0)

        var processedCount = 0
        var failedCount = 0
        val committedTileIds = mutableListOf<String>()

        try {
            // Commit the geocoder in bounded batches. Room's processed flag is written only
            // after the native index commit succeeds, so an interrupted batch is safely retried.
            tileProcessor?.beginTileProcessing()

            for ((zoom, coords, data) in tileBatch) {
                try {
                    val (x, y) = coords
                    val xyzY = (2.0.pow(zoom.toDouble()) - 1 - y).toInt()
                    tileProcessor?.processTile(data, zoom, x, xyzY)
                    committedTileIds.add("basemap_${areaId}_${zoom}_${x}_${xyzY}")
                    processedCount++
                    Log.v(TAG, "Processed tile $zoom/$x/$y for area $areaId")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Error processing tile $zoom/${coords.first}/${coords.second} for area $areaId",
                        e
                    )
                    failedCount++
                }
            }

            tileProcessor?.endTileProcessing()

            // Native commit succeeded. Persist resume markers in one Room statement.
            if (committedTileIds.isNotEmpty()) {
                downloadedTileDao.markTilesProcessed(committedTileIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to commit geocoder batch for area $areaId", e)
            // Nothing from this batch is marked processed; all of it is safe to retry.
            return Pair(0, tileBatch.size)
        }

        return Pair(processedCount, failedCount)
    }
}

/**
 * Convert longitude to tile X coordinate
 */
private fun lonToTileX(lon: Double, zoom: Int): Int {
    return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
}

/**
 * Convert latitude to tile Y coordinate
 */
private fun latToTileY(lat: Double, zoom: Int): Int {
    val latRad = Math.toRadians(lat)
    val n = 2.0.pow(zoom.toDouble())
    return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
}

data class TileRange(
    val minX: Int, val maxX: Int, val minY: Int, val maxY: Int
)

private data class TileCoordinates(
    val zoomLevel: Int, val tileColumn: Int, val tileRow: Int
)

private data class TileDeletionResult(
    val deletedCount: Int,
    val sharedCount: Int,
    val totalCount: Int
)

/**
 * Calculate tile range for a bounding box at a specific zoom level
 */
fun calculateTileRange(
    boundingBox: BoundingBox, zoom: Int
): TileRange {
    // Convert latitude/longitude to tile coordinates using Web Mercator projection
    // Formula:
    // x = (lon + 180) / 360 * 2^zoom
    // y = (1 - ln(tan(lat * π/180) + sec(lat * π/180)) / π) / 2 * 2^zoom

    // Calculate tile coordinates for northwest corner (max latitude, min longitude)
    val nwX = lonToTileX(boundingBox.west, zoom)
    val nwY = latToTileY(boundingBox.north, zoom)

    // Calculate tile coordinates for southeast corner (min latitude, max longitude)
    val seX = lonToTileX(boundingBox.east, zoom)
    val seY = latToTileY(boundingBox.south, zoom)

    // Return the range, ensuring proper min/max values
    // Note: Y coordinates increase downward in tile systems
    return TileRange(
        minX = min(nwX, seX), maxX = max(nwX, seX), minY = min(nwY, seY), maxY = max(nwY, seY)
    )
}
