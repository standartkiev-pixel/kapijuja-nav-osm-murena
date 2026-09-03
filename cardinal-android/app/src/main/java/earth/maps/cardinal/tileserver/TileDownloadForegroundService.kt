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

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import earth.maps.cardinal.MainActivity
import earth.maps.cardinal.R
import earth.maps.cardinal.data.BoundingBox
import earth.maps.cardinal.data.room.DownloadStatus
import earth.maps.cardinal.data.room.DownloadedTileDao
import earth.maps.cardinal.data.room.OfflineAreaDao
import earth.maps.cardinal.data.room.TileType
import earth.maps.cardinal.geocoding.TileProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class TileDownloadForegroundService : Service() {

    @Inject
    lateinit var offlineAreaDao: OfflineAreaDao

    @Inject
    lateinit var downloadedTileDao: DownloadedTileDao

    @Inject
    lateinit var tileProcessor: TileProcessor

    private lateinit var tileDownloadManager: TileDownloadManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    private val _downloadProgress: MutableStateFlow<DownloadProgress?> = MutableStateFlow(null)

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val binder = TileDownloadBinder()

    // Debouncing for notification updates
    private var notificationUpdateJob: Job? = null
    private var lastNotificationUpdate = 0L
    private var isForegroundStarted = false

    companion object {
        private const val TAG = "TileDownloadForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tile_download_channel"
        private const val CHANNEL_NAME = "Map Downloads"

        // Debouncing constants
        private const val NOTIFICATION_DEBOUNCE_DELAY_MS = 500L
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1000L

        const val ACTION_START_DOWNLOAD = "START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "CANCEL_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOADS = "RESUME_DOWNLOADS"
        const val ACTION_PAUSE_DOWNLOAD = "PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "RESUME_DOWNLOAD"
    }

    data class DownloadProgress(
        val areaId: String = "",
        val areaName: String = "",
        val currentStage: DownloadStage,
        val stageProgress: Int = 0,
        val stageTotal: Int = 0,
        val isCompleted: Boolean = false,
        val hasError: Boolean = false
    ) {
        // Unified progress calculation with 3 stages (33.3% each)
        val unifiedProgress: Float get() = calculateUnifiedProgress()

        fun describe(): String {
            if (stageTotal <= 0 && currentStage != DownloadStage.DONE && currentStage != DownloadStage.ERROR) {
                return "Preparing download"
            }

            return when (currentStage) {
                DownloadStage.BASEMAP -> "Downloaded $stageProgress of $stageTotal map tiles"
                DownloadStage.VALHALLA -> "Downloaded $stageProgress of $stageTotal routing tiles"
                DownloadStage.PROCESSING -> "Processed $stageProgress of $stageTotal map tiles"
                DownloadStage.DONE -> "Finished download"
                DownloadStage.ERROR -> "Download finished with error"
            }
        }

        private fun calculateUnifiedProgress(): Float {
            val stageProgressFraction = stageTotal?.let { stageProgress.toFloat() / stageTotal.toFloat() } ?: 0.0f
            return when (currentStage) {
                DownloadStage.BASEMAP -> stageProgressFraction * 0.6f
                DownloadStage.VALHALLA -> 0.6f + stageProgressFraction * 0.2f
                DownloadStage.PROCESSING -> 0.8f + stageProgressFraction * 0.2f
                DownloadStage.DONE -> 1f
                DownloadStage.ERROR -> 0f
            }
        }
    }

    inner class TileDownloadBinder : Binder() {
        fun getService(): TileDownloadForegroundService = this@TileDownloadForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        tileDownloadManager = TileDownloadManager(
            this, downloadedTileDao, offlineAreaDao, tileProcessor,
            ServiceProgressReporter(this)
        )

        Log.d(TAG, "TileDownloadForegroundService created")
        createNotificationChannel()

        // Reset download state on service creation to prevent stale state issues
        _isDownloading.value = false
        _downloadProgress.value = DownloadProgress(currentStage = DownloadStage.BASEMAP)

    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD, null -> {
                val foregroundStarted = startOrUpdateForeground(
                    DownloadProgress(currentStage = DownloadStage.BASEMAP)
                )
                if (foregroundStarted) {
                    serviceScope.launch {
                        processDownloadQueue()
                    }
                } else {
                    stopSelf()
                }
            }

            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload()
            }

            ACTION_RESUME_DOWNLOADS -> {
                serviceScope.launch {
                    processDownloadQueue()
                }
            }

            ACTION_PAUSE_DOWNLOAD -> {
                pauseDownload()
            }

            ACTION_RESUME_DOWNLOAD -> {
                resumeDownload()
            }
        }

        return START_STICKY // Restart service if killed
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows progress of map tile downloads"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created with importance DEFAULT")
    }

    private fun createNotification(progress: DownloadProgress): Notification {
        val areaName = progress.areaName.ifBlank { "map area" }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEEP_LINK_DESTINATION, MainActivity.DEEP_LINK_OFFLINE_AREAS)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, TileDownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, TileDownloadForegroundService::class.java).apply {
            action = ACTION_PAUSE_DOWNLOAD
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, TileDownloadForegroundService::class.java).apply {
            action = ACTION_RESUME_DOWNLOAD
        }
        val resumePendingIntent = PendingIntent.getService(
            this, 3, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = _isPaused.value
        val title = when {
            progress.areaName.isBlank() -> "Preparing map download"
            isPaused -> "Download Paused - $areaName"
            else -> "Downloading $areaName"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(progress.describe()).setSmallIcon(R.drawable.cloud_download_24dp)
            .setContentIntent(pendingIntent).setOngoing(true)

        // Add actions based on current state
        if (isPaused) {
            builder.addAction(
                R.drawable.cloud_download_24dp, "Resume", resumePendingIntent
            )
        } else if (progress.currentStage != DownloadStage.DONE) {
            builder.addAction(
                android.R.drawable.ic_media_pause, "Pause", pausePendingIntent
            )
        }

        if (progress.currentStage != DownloadStage.DONE) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent
            )
        }

        if (progress.unifiedProgress > 0 && progress.currentStage != DownloadStage.DONE) {
            builder.setProgress(1000, (progress.unifiedProgress * 1000f).toInt(), false)
        } else if (progress.currentStage != DownloadStage.DONE) {
            // Initializing state
            builder.setProgress(0, 0, true) // Indeterminate progress
        }

        return builder.build()
    }

    private fun startOrUpdateForeground(progress: DownloadProgress): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(progress),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification(progress))
            }
            isForegroundStarted = true
            Log.d(TAG, "Foreground service started or updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            false
        }
    }

    private fun stopForegroundIfStarted() {
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
    }

    private fun startDownloadJob(
        areaId: String, areaName: String, boundingBox: BoundingBox, minZoom: Int, maxZoom: Int
    ) {
        // Check if we're already downloading the same area
        if (_isDownloading.value && _downloadProgress.value?.areaId == areaId) {
            Log.w(TAG, "Download already in progress for area $areaId, ignoring duplicate request")
            return
        }

        // If we're downloading a different area, abort and, TODO: cascade resumes after each download finishes
        if (_isDownloading.value) {
            Log.d(
                TAG,
                "Deferring download ($areaId)"
            )
            return
        }

        Log.d(TAG, "Starting download for area: $areaName (ID: $areaId)")

        _isDownloading.value = true
        val progress = DownloadProgress(
            areaId = areaId, areaName = areaName, currentStage = DownloadStage.BASEMAP
        )
        _downloadProgress.value = progress

        if (!startOrUpdateForeground(progress)) {
            _isDownloading.value = false
            return
        }

        downloadJob = serviceScope.launch {
            try {
                // Calculate expected tiles for progress tracking
                val expectedBasemapTiles = tileDownloadManager.calculateTotalTiles(
                    boundingBox, minZoom, maxZoom
                )
                val expectedValhallaTiles = ValhallaTileUtils.tilesForBoundingBox(boundingBox).size

                // Check for existing tiles and resume from where we left off
                val existingBasemapTiles =
                    downloadedTileDao.getDownloadedTileCountForAreaAndType(areaId, TileType.BASEMAP)
                val existingValhallaTiles = downloadedTileDao.getDownloadedTileCountForAreaAndType(
                    areaId, TileType.VALHALLA
                )

                Log.d(
                    TAG,
                    "Resuming download: $existingBasemapTiles/$expectedBasemapTiles basemap, $existingValhallaTiles/$expectedValhallaTiles valhalla tiles"
                )

                // Start the actual download
                tileDownloadManager.downloadTilesInternal(
                    boundingBox, minZoom, maxZoom, areaId, areaName
                )
                handleDownloadJobSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Error during download", e)
                // Handle download failure
                handleDownloadCompletion(areaId, false, 0L)
            }
        }
    }

    private suspend fun handleDownloadJobSuccess() {
        Log.d(TAG, "Download job completed successfully")
        downloadJob = null
        _isDownloading.value = false

        val areas = offlineAreaDao.getAllOfflineAreas().first()
        val remainingAreas = areas.filter {
            it.shouldAutomaticallyResume()
        }

        if (remainingAreas.isNotEmpty()) {
            Log.d(TAG, "More downloads in queue, continuing...")
            processDownloadQueue()
        } else {
            Log.d(TAG, "No more downloads in queue, stopping service")
            stopForegroundIfStarted()
            stopSelf()
        }
    }

    private suspend fun handleDownloadCompletion(areaId: String, success: Boolean, fileSize: Long) {
        Log.d(TAG, "Download completed for area $areaId: success=$success, fileSize=$fileSize")

        // Update the offline area status
        val area = offlineAreaDao.getOfflineAreaById(areaId)
        if (area != null) {
            val updatedArea = area.copy(
                status = if (success) DownloadStatus.PROCESSING_GEOCODER else DownloadStatus.FAILED,
                fileSize = fileSize
            )
            offlineAreaDao.updateOfflineArea(updatedArea)
        }

        _downloadProgress.value = _downloadProgress.value?.copy(
            isCompleted = true, hasError = !success
        )

        _isDownloading.value = false
        downloadJob = null

        // Check if there are more downloads in the queue
        val areas = offlineAreaDao.getAllOfflineAreas().first()
        val remainingAreas = areas.filter {
            it.shouldAutomaticallyResume()
        }

        if (remainingAreas.isNotEmpty()) {
            Log.d(TAG, "More downloads in queue, continuing...")
            // Continue processing the queue
            processDownloadQueue()
        } else {
            Log.d(TAG, "No more downloads in queue, stopping service")
            stopForegroundIfStarted()
            stopSelf()
        }
    }

    private fun updateNotification() {
        try {
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(
                        TAG, "POST_NOTIFICATIONS permission not granted, cannot update notification"
                    )
                    return
                }
            }

            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notification = _downloadProgress.value?.let { createNotification(it) }
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated successfully: ${notification?.extras}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    /**
     * Update notification with debouncing to prevent excessive updates during rapid progress changes.
     * @param immediate If true, bypasses debouncing for critical state changes
     */
    private fun updateNotificationDebounced(immediate: Boolean = false) {
        if (immediate) {
            // For critical state changes (pause/resume/error/completion), update immediately
            updateNotification()
            lastNotificationUpdate = System.currentTimeMillis()
            return
        }

        // Cancel any pending notification update
        notificationUpdateJob?.cancel()

        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastNotificationUpdate

        if (timeSinceLastUpdate >= NOTIFICATION_MIN_INTERVAL_MS) {
            // Enough time has passed since last update, update immediately
            updateNotification()
            lastNotificationUpdate = currentTime
        } else {
            // Too soon since last update, schedule a debounced update
            notificationUpdateJob = serviceScope.launch {
                delay(NOTIFICATION_DEBOUNCE_DELAY_MS)
                if (_isDownloading.value) {
                    updateNotification()
                }
                lastNotificationUpdate = System.currentTimeMillis()
            }
        }
    }

    private fun stopDownloadJob() {
        try {
            downloadJob?.cancel()
        } finally {
            downloadJob = null
        }
    }

    fun cancelDownload() {
        Log.d(TAG, "Cancelling download")
        // Capture the value for the closure before we start changing state.
        val areaId = _downloadProgress.value?.areaId
        if (areaId == null) {
            Log.d(TAG, "Download progress was null")
            return
        }
        stopDownloadJob()
        _isDownloading.value = false
        serviceScope.launch {
            val area = offlineAreaDao.getOfflineAreaById(areaId)?.copy(
                status = DownloadStatus.FAILED
            )
            area?.let { offlineAreaDao.updateOfflineArea(it) }
            stopForegroundIfStarted()
            stopSelf()
        }
        stopForegroundIfStarted()
    }

    fun startDownload(boundingBox: BoundingBox, minZoom: Int, maxZoom: Int, areaName: String) {
        val areaId = UUID.randomUUID().toString()
        tileDownloadManager.downloadTiles(boundingBox, minZoom, maxZoom, areaId, areaName)
    }

    /**
     * Update download progress (unified version with processing)
     */
    fun updateProgress(
        areaId: String,
        areaName: String,
        currentStage: DownloadStage,
        stageProgress: Int,
        stageTotal: Int,
        isCompleted: Boolean,
        hasError: Boolean
    ) {
        _downloadProgress.value = DownloadProgress(
            areaId = areaId,
            areaName = areaName,
            currentStage = currentStage,
            stageProgress = stageProgress,
            stageTotal = stageTotal,
            isCompleted = isCompleted,
            hasError = hasError
        )

        val isImmediate = isCompleted || hasError

        // Update notification if service is running or if this is the final state.
        if (_isDownloading.value || isImmediate) {
            // Use immediate update for completion/error states, debounced for progress updates
            updateNotificationDebounced(immediate = isImmediate)
        }

        if (isImmediate) {
            _isDownloading.value = false
        }
    }


    private suspend fun processDownloadQueue() {
        Log.d(TAG, "Processing download queue")

        try {
            val areas = offlineAreaDao.getAllOfflineAreas().first()
            val pendingAreas = areas.filter {
                it.shouldAutomaticallyResume()
            }

            Log.d(TAG, "Found ${pendingAreas.size} areas to process")

            if (pendingAreas.isEmpty()) {
                Log.d(TAG, "No pending downloads, stopping service")
                stopForegroundIfStarted()
                stopSelf()
                return
            }

            // Process areas one by one
            for (area in pendingAreas) {
                Log.d(TAG, "Processing download for area: ${area.name}")

                // Start the download
                startDownloadJob(
                    area.id, area.name, area.boundingBox(), area.minZoom, area.maxZoom
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing download queue", e)
            stopForegroundIfStarted()
            stopSelf()
        }
    }

    fun pauseDownload() {
        Log.d(TAG, "Pausing download")

        stopDownloadJob()
        _isDownloading.value = false
        _isPaused.value = true

        // Update the current offline area to mark it as paused
        serviceScope.launch {
            val currentProgress = _downloadProgress.value
            if (currentProgress == null) {
                Log.d(TAG, "Progress was null while attempting to pause download")
                return@launch
            }
            if (currentProgress.areaId.isNotEmpty()) {
                val area = offlineAreaDao.getOfflineAreaById(currentProgress.areaId)
                if (area != null) {
                    val pausedArea = area.copy(paused = true)
                    offlineAreaDao.updateOfflineArea(pausedArea)
                    Log.d(TAG, "Marked area ${currentProgress.areaId} as paused in database")
                }
            }
        }

        // Update notification to show paused state
        updateNotificationDebounced(immediate = true)
    }

    fun deleteTilesForArea(areaId: String): Boolean {
        return tileDownloadManager.deleteTilesForArea(areaId)
    }

    fun resumeDownload() {
        Log.d(TAG, "Resuming download")
        _isPaused.value = false

        // Update the current offline area to mark it as not paused
        serviceScope.launch {
            val currentProgress = _downloadProgress.value
            if (currentProgress == null) {
                Log.d(TAG, "Progress was null while attempting to resume download")
                return@launch
            }
            if (currentProgress.areaId.isNotEmpty()) {
                val area = offlineAreaDao.getOfflineAreaById(currentProgress.areaId)
                if (area != null) {
                    val resumedArea = area.copy(paused = false)
                    offlineAreaDao.updateOfflineArea(resumedArea)
                    Log.d(TAG, "Marked area ${currentProgress.areaId} as resumed in database")
                }
                processDownloadQueue()
            }
        }

        // Update notification to show resumed state
        updateNotificationDebounced(immediate = true)

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TileDownloadForegroundService destroyed")
        stopDownloadJob()

        // Cancel any pending notification updates
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
    }
}
