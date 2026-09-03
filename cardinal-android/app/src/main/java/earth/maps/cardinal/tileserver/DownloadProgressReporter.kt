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

/**
 * Interface for reporting download progress to external components
 */
interface DownloadProgressReporter {
    /**
     * Update download progress
     * @param areaId The ID of the area being downloaded
     * @param areaName The name of the area being downloaded
     * @param currentStage The current stage of the download
     * @param stageProgress Current progress within the current stage
     * @param stageTotal Total expected progress for the current stage
     * @param isCompleted Whether the download is fully completed
     * @param hasError Whether an error occurred during download
     */
    fun updateProgress(
        areaId: String,
        areaName: String,
        currentStage: DownloadStage,
        stageProgress: Int,
        stageTotal: Int,
        isCompleted: Boolean,
        hasError: Boolean
    )
}

/**
 * Represents the different stages of the download process
 */
enum class DownloadStage {
    BASEMAP,
    VALHALLA,
    PROCESSING,
    DONE,
    ERROR,
}
