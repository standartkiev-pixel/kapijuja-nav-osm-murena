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

package earth.maps.cardinal

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import foundation.e.lib.telemetry.Telemetry.init

@HiltAndroidApp
class CardinalMapsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            try {
                init(BuildConfig.SENTRY_DSN, this, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Sentry SDK", e)
            }
        }
    }

    companion object {
        private const val TAG = "CardinalMapsApplication"
    }

}
