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

package earth.maps.cardinal.data.navigation

import android.content.Context
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import earth.maps.cardinal.data.ConnectivityRepository
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.OrientationRepository
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.room.RoutingProfileRepository
import earth.maps.cardinal.routing.FerrostarWrapper
import earth.maps.cardinal.routing.RoutingOptions
import okhttp3.OkHttpClient
import javax.inject.Inject

class CardinalFerrostarWrapperFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val orientationRepository: OrientationRepository,
    private val androidTtsObserver: SpokenInstructionObserver,
    private val routingProfileRepository: RoutingProfileRepository,
    private val okHttpClient: OkHttpClient,
    private val connectivityRepository: ConnectivityRepository
) : FerrostarWrapperFactory {

    override fun create(
        mode: RoutingMode,
        endpoint: String,
        routingOptions: RoutingOptions?
    ): FerrostarWrapper {

        return FerrostarWrapper(
            context = context,
            locationRepository = locationRepository,
            orientationRepository = orientationRepository,
            mode = mode,
            localValhallaEndpoint = endpoint,
            androidTtsObserver = androidTtsObserver,
            routingProfileRepository = routingProfileRepository,
            routingOptions = routingOptions,
            okHttpClient = okHttpClient,
            connectivityRepository = connectivityRepository
        )
    }
}
