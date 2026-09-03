/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
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
import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(RobolectricTestRunner::class)
class PermissionRequestManagerTest {

    private lateinit var application: Application
    private lateinit var permissionRequestManager: PermissionRequestManager

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences(
            PERMISSION_REQUEST_PREFERENCES,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        permissionRequestManager = PermissionRequestManager(application)
    }

    @Test
    fun `shouldRequestNotificationPermission returns true before first prompt`() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(permissionRequestManager.shouldRequestNotificationPermission(application))
    }

    @Test
    fun `shouldRequestNotificationPermission returns false after prompt was requested`() =
        runTest {
            shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

            permissionRequestManager.requestNotificationPermission()

            assertFalse(permissionRequestManager.shouldRequestNotificationPermission(application))
        }

    @Test
    fun `requestNotificationPermissionAndWaitForResult waits for permission answer`() =
        runTest {
            val result = async {
                permissionRequestManager.requestNotificationPermissionAndWaitForResult()
            }
            runCurrent()

            permissionRequestManager.onPermissionDenied(PermissionRequest.NotificationPermission)

            assertTrue(result.await() is PermissionResult.Denied)
        }

    companion object {
        private const val PERMISSION_REQUEST_PREFERENCES = "permission_request_preferences"
    }
}
