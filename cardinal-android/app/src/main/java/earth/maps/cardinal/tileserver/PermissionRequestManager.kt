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
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRequestManager @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context
) {

    private val _permissionRequests = MutableSharedFlow<PermissionRequest>()
    val permissionRequests: SharedFlow<PermissionRequest> = _permissionRequests.asSharedFlow()

    private val _permissionResults = MutableSharedFlow<PermissionResult>()
    val permissionResults: SharedFlow<PermissionResult> = _permissionResults.asSharedFlow()

    private val preferences = applicationContext.getSharedPreferences(
        PERMISSION_REQUEST_PREFERENCES,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "PermissionRequestManager"
        private const val PERMISSION_REQUEST_PREFERENCES = "permission_request_preferences"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED =
            "notification_permission_requested"
    }

    /**
     * Request notification permission for tile download service
     */
    suspend fun requestNotificationPermission() {
        Log.d(TAG, "Requesting notification permission for tile downloads")
        markNotificationPermissionRequested()
        _permissionRequests.emit(PermissionRequest.NotificationPermission)
    }

    /**
     * Request notification permission and suspend until the user answers the prompt.
     */
    suspend fun requestNotificationPermissionAndWaitForResult(): PermissionResult = coroutineScope {
        val result = async {
            permissionResults.first { result ->
                result.request == PermissionRequest.NotificationPermission
            }
        }
        requestNotificationPermission()
        result.await()
    }

    /**
     * Notify that permission was granted
     */
    suspend fun onPermissionGranted(request: PermissionRequest) {
        Log.d(TAG, "Permission granted for request: $request")
        if (request == PermissionRequest.NotificationPermission) {
            markNotificationPermissionRequested()
        }
        _permissionResults.emit(PermissionResult.Granted(request))
    }

    /**
     * Notify that permission was denied
     */
    suspend fun onPermissionDenied(request: PermissionRequest) {
        Log.d(TAG, "Permission denied for request: $request")
        if (request == PermissionRequest.NotificationPermission) {
            markNotificationPermissionRequested()
        }
        _permissionResults.emit(PermissionResult.Denied(request))
    }

    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older Android versions
        }
    }

    /**
     * Returns true only for the first notification-permission prompt opportunity.
     */
    fun shouldRequestNotificationPermission(context: Context): Boolean {
        return !hasNotificationPermission(context) && !hasRequestedNotificationPermission()
    }

    private fun hasRequestedNotificationPermission(): Boolean {
        return preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
    }

    private fun markNotificationPermissionRequested() {
        preferences.edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
            .apply()
    }
}

/**
 * Represents a permission request
 */
sealed class PermissionRequest {
    data object NotificationPermission : PermissionRequest()
}

/**
 * Represents the result of a permission request
 */
sealed class PermissionResult {
    abstract val request: PermissionRequest

    data class Granted(override val request: PermissionRequest) : PermissionResult()
    data class Denied(override val request: PermissionRequest) : PermissionResult()
}
