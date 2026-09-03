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

package earth.maps.cardinal.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.geocoding.MultiplexedGeocodingService
import earth.maps.cardinal.geocoding.NearbyCategorySearchCoordinator
import earth.maps.cardinal.geocoding.OfflineGeocodingService
import earth.maps.cardinal.geocoding.PeliasGeocodingService
import earth.maps.cardinal.geocoding.TileProcessor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeocodingModule {

    @Provides
    @Singleton
    fun provideGeocodingService(
        appPreferenceRepository: AppPreferenceRepository,
        locationRepository: LocationRepository,
        nearbyCategorySearchCoordinator: NearbyCategorySearchCoordinator,
        @ApplicationContext context: Context
    ): GeocodingService {
        val onlineService =
            providePeliasGeocodingService(
                appPreferenceRepository,
                locationRepository,
                nearbyCategorySearchCoordinator
            )
        val offlineService = provideOfflineGeocodingService(context, locationRepository)
        return MultiplexedGeocodingService(
            appPreferenceRepository = appPreferenceRepository,
            onlineGeocodingService = onlineService,
            offlineGeocodingService = offlineService,
            locationRepository = locationRepository
        )
    }

    @Provides
    @Singleton
    fun providePeliasGeocodingService(
        appPreferenceRepository: AppPreferenceRepository,
        locationRepository: LocationRepository,
        nearbyCategorySearchCoordinator: NearbyCategorySearchCoordinator
    ): PeliasGeocodingService {
        return PeliasGeocodingService(
            appPreferenceRepository,
            locationRepository,
            nearbyCategorySearchCoordinator
        )
    }

    @Provides
    @Singleton
    fun provideOfflineGeocodingService(
        @ApplicationContext context: Context,
        locationRepository: LocationRepository
    ): OfflineGeocodingService {
        return OfflineGeocodingService(context, locationRepository)
    }

    @Provides
    @Singleton
    fun provideTileProcessor(
        @ApplicationContext context: Context,
        locationRepository: LocationRepository
    ): TileProcessor {
        return provideOfflineGeocodingService(context, locationRepository)
    }
}
