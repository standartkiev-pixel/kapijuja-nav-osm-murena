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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import earth.maps.cardinal.data.BoundingBox
import earth.maps.cardinal.data.EuropeanCountryDownloadRegion
import earth.maps.cardinal.data.room.OfflineArea
import earth.maps.cardinal.data.room.OfflineAreaRepository
import earth.maps.cardinal.tileserver.CountryTileMask
import earth.maps.cardinal.tileserver.DownloadStage
import earth.maps.cardinal.tileserver.PermissionRequestManager
import earth.maps.cardinal.tileserver.TileDownloadForegroundService
import earth.maps.cardinal.tileserver.calculateTileRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
class OfflineAreasViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val offlineAreaRepository: OfflineAreaRepository,
    private val permissionRequestManager: PermissionRequestManager,
) : ViewModel() {

    val offlineAreas = mutableStateOf<List<OfflineArea>>(emptyList())
    val isDownloading = mutableStateOf(false)
    val isPaused = mutableStateOf(false)
    val downloadProgress = mutableIntStateOf(0)
    val totalTiles = mutableIntStateOf(0)
    val currentAreaName = mutableStateOf("")

    // New unified progress properties
    val unifiedProgress = mutableFloatStateOf(0f) // 0.0 to 1.0
    val currentStage = mutableStateOf(DownloadStage.BASEMAP)
    private val countryTileMask by lazy { CountryTileMask(context) }

    // Error handling
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Service binding infrastructure
    private var serviceBinder: TileDownloadForegroundService.TileDownloadBinder? = null
    private var progressJob: Job? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as TileDownloadForegroundService.TileDownloadBinder
            isBound = true
            syncWithOngoingDownloads()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            isBound = false
            progressJob?.cancel()
            progressJob = null
            // Reset progress state when service disconnects
            resetProgressState()
        }

        override fun onBindingDied(name: ComponentName?) {
            serviceBinder = null
            isBound = false
            progressJob?.cancel()
            progressJob = null
            resetProgressState()
        }

        override fun onNullBinding(name: ComponentName?) {
            serviceBinder = null
            isBound = false
            resetProgressState()
        }
    }

    init {
        loadOfflineAreas()
        bindToService()
    }

    /**
     * Called when ViewModel is created to sync with any ongoing downloads
     */
    private fun syncWithOngoingDownloads() {
        viewModelScope.launch {
            // Give service binding a moment to establish
            delay(500)

            if (isBound && serviceBinder != null) {
                val service = serviceBinder!!.getService()
                // Check if service is currently downloading
                val isServiceDownloading = service.isDownloading.value
                if (isServiceDownloading) {
                    // The StateFlow observations should handle the rest
                }
            }
        }
    }

    private fun loadOfflineAreas() {
        viewModelScope.launch {
            offlineAreaRepository.getAllOfflineAreas().collect { areas ->
                offlineAreas.value = areas
                // Reset progress state based on current offline areas status
                resetProgressState()
            }
        }
    }

    fun startDownload(
        boundingBox: BoundingBox, name: String
    ) {
        viewModelScope.launch {
            if (permissionRequestManager.shouldRequestNotificationPermission(context)) {
                permissionRequestManager.requestNotificationPermissionAndWaitForResult()
            }

            serviceBinder?.getService()?.startDownload(
                boundingBox,
                OFFLINE_AREA_MIN_ZOOM,
                OFFLINE_AREA_MAX_ZOOM,
                name
            )
        }
    }

    fun startCountryDownload(country: EuropeanCountryDownloadRegion) {
        viewModelScope.launch {
            if (permissionRequestManager.shouldRequestNotificationPermission(context)) {
                permissionRequestManager.requestNotificationPermissionAndWaitForResult()
            }

            serviceBinder?.getService()?.startCountryDownload(
                country.boundingBox,
                OFFLINE_AREA_MIN_ZOOM,
                OFFLINE_AREA_MAX_ZOOM,
                country.name,
                country.countryCode
            )
        }
    }

    fun estimateCountryTileCount(country: EuropeanCountryDownloadRegion): Int {
        var totalTiles = 0
        for (zoom in OFFLINE_AREA_MIN_ZOOM..OFFLINE_AREA_MAX_ZOOM) {
            val (minX, maxX, minY, maxY) = calculateTileRange(country.boundingBox, zoom)
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    if (countryTileMask.containsBufferedTile(country.countryCode, zoom, x, y)) {
                        totalTiles++
                    }
                }
            }
        }
        return totalTiles
    }

    fun deleteOfflineArea(offlineArea: OfflineArea) {
        viewModelScope.launch {
            // Delete tiles from the single database
            serviceBinder?.getService()?.deleteTilesForArea(offlineArea.id)

            // Delete the offline area entry from Room database
            offlineAreaRepository.deleteOfflineArea(offlineArea)
        }
    }

    /**
     * Calculate the estimated number of tiles for a bounding box and zoom range
     */
    fun estimateTileCount(
        boundingBox: BoundingBox, minZoom: Int, maxZoom: Int
    ): Int {
        // Use the same logic as in tileDownloadManager
        var totalTiles = 0

        for (zoom in minZoom..min(maxZoom, 14)) {
            val (minX, maxX, minY, maxY) = calculateTileRange(
                boundingBox, zoom
            )
            totalTiles += (maxX - minX + 1) * (maxY - minY + 1)
        }

        return totalTiles
    }

   /**
     * Bind to the TileDownloadForegroundService
     */
   private fun bindToService() {
       if (!isBound) {
           val intent = Intent(context, TileDownloadForegroundService::class.java)
           try {
               context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
           } catch (e: Exception) {
               _errorMessage.value = "Failed to bind to download service: ${e.message}"
           }
       }
   }

   /**
     * Unbind from the TileDownloadForegroundService
     */
   private fun unbindFromService() {
       if (isBound) {
           try {
               context.unbindService(serviceConnection)
           } catch (e: Exception) {
               _errorMessage.value = "Failed to unbind from download service: ${e.message}"
           }
           serviceBinder = null
           isBound = false
       }
       progressJob?.cancel()
       progressJob = null
   }

    /**
     * Reset progress state when service is not available
     */
    private fun resetProgressState() {
        // Only reset if we're sure there's no ongoing download or processing
        // Check both offline areas status and service state (if available)
        val hasActiveAreas = offlineAreas.value.any {
            it.isIncomplete()
        }

        if (!hasActiveAreas) {
           // No downloading or processing areas in database
           isDownloading.value = false
           downloadProgress.intValue = 0
           totalTiles.intValue = 0
           currentAreaName.value = ""
       }
    }

    /**
     * Clean up when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }

    companion object {
        const val OFFLINE_AREA_MIN_ZOOM = 5
        const val OFFLINE_AREA_MAX_ZOOM = 14
    }

    /**
     * Clear any error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
