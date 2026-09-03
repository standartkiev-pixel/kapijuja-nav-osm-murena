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
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import earth.maps.cardinal.data.GeocodeResult
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.room.AppDatabase
import earth.maps.cardinal.data.room.DownloadedTileDao
import earth.maps.cardinal.data.room.ListItemDao
import earth.maps.cardinal.data.room.OfflineAreaDao
import earth.maps.cardinal.data.room.RecentSearchDao
import earth.maps.cardinal.data.room.SavedListDao
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.di.DatabaseModule
import earth.maps.cardinal.di.GeocodingModule
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.geocoding.OfflineGeocodingService
import earth.maps.cardinal.geocoding.TileProcessor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Singleton

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(RobolectricTestRunner::class)
class TileDownloadForegroundServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun `start download starts foreground even when notification permission is denied`() {
        hiltRule.inject()
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val service = Robolectric.buildService(TileDownloadForegroundService::class.java)
            .create()
            .get()

        service.onStartCommand(
            Intent(application, TileDownloadForegroundService::class.java).apply {
                action = TileDownloadForegroundService.ACTION_START_DOWNLOAD
            },
            0,
            1
        )

        val shadowService = shadowOf(service)
        assertEquals(FOREGROUND_NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)
        assertEquals(
            "Preparing map download",
            shadowService.lastForegroundNotification.extras.getString(Notification.EXTRA_TITLE)
        )
    }

    @Test
    fun `sticky restart starts foreground before processing queue`() {
        hiltRule.inject()
        val service = Robolectric.buildService(TileDownloadForegroundService::class.java)
            .create()
            .get()

        service.onStartCommand(null, 0, 1)

        val shadowService = shadowOf(service)
        assertEquals(FOREGROUND_NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)
    }

    @Test
    fun `completed progress clears downloading state`() {
        hiltRule.inject()
        val service = Robolectric.buildService(TileDownloadForegroundService::class.java)
            .create()
            .get()

        service.setIsDownloadingForTest(true)

        service.updateProgress(
            areaId = "finished-area",
            areaName = "Finished Area",
            currentStage = DownloadStage.DONE,
            stageProgress = 0,
            stageTotal = 0,
            isCompleted = true,
            hasError = false
        )

        assertFalse(service.isDownloading.value)
    }

    @Test
    fun `start download uses preparing notification instead of stale completed progress`() {
        hiltRule.inject()
        val application = RuntimeEnvironment.getApplication()
        val service = Robolectric.buildService(TileDownloadForegroundService::class.java)
            .create()
            .get()
        service.updateProgress(
            areaId = "finished-area",
            areaName = "Finished Area",
            currentStage = DownloadStage.DONE,
            stageProgress = 0,
            stageTotal = 0,
            isCompleted = true,
            hasError = false
        )

        service.onStartCommand(
            Intent(application, TileDownloadForegroundService::class.java).apply {
                action = TileDownloadForegroundService.ACTION_START_DOWNLOAD
            },
            0,
            1
        )

        assertEquals(
            "Preparing map download",
            shadowOf(service).lastForegroundNotification.extras.getString(Notification.EXTRA_TITLE)
        )
    }

    class FakeGeocodingService : GeocodingService(mockk<LocationRepository>(relaxed = true)) {
        override fun hasSeparateAutocomplete(): Boolean = false

        override suspend fun geocodeRaw(
            query: String,
            focusPoint: LatLng?,
            autocomplete: Boolean
        ): List<GeocodeResult> = emptyList()

        override suspend fun reverseGeocodeRaw(
            latitude: Double,
            longitude: Double
        ): List<GeocodeResult> = emptyList()

        override suspend fun nearbyRaw(
            latitude: Double,
            longitude: Double,
            selectedCategories: List<String>
        ): List<GeocodeResult> = emptyList()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }
}

@Suppress("UNCHECKED_CAST")
private fun TileDownloadForegroundService.setIsDownloadingForTest(isDownloading: Boolean) {
    val isDownloadingField =
        TileDownloadForegroundService::class.java.getDeclaredField("_isDownloading")
    isDownloadingField.isAccessible = true
    val isDownloadingState = isDownloadingField.get(this) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
    isDownloadingState.value = isDownloading
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TileDownloadForegroundServiceTestDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideOfflineAreaDao(appDatabase: AppDatabase): OfflineAreaDao {
        return appDatabase.offlineAreaDao()
    }

    @Provides
    fun provideDownloadedTileDao(appDatabase: AppDatabase): DownloadedTileDao {
        return appDatabase.downloadedTileDao()
    }

    @Provides
    fun provideSavedListDao(appDatabase: AppDatabase): SavedListDao {
        return appDatabase.savedListDao()
    }

    @Provides
    fun provideSavedPlaceDao(appDatabase: AppDatabase): SavedPlaceDao {
        return appDatabase.savedPlaceDao()
    }

    @Provides
    fun provideListItemDao(appDatabase: AppDatabase): ListItemDao {
        return appDatabase.listItemDao()
    }

    @Provides
    fun provideRecentSearchDao(appDatabase: AppDatabase): RecentSearchDao {
        return appDatabase.recentSearchDao()
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [GeocodingModule::class]
)
object TileDownloadForegroundServiceTestGeocodingModule {

    @Provides
    @Singleton
    fun provideGeocodingService(): GeocodingService {
        return TileDownloadForegroundServiceTest.FakeGeocodingService()
    }

    @Provides
    @Singleton
    fun provideOfflineGeocodingService(): OfflineGeocodingService {
        return mockk(relaxed = true)
    }

    @Provides
    @Singleton
    fun provideTileProcessor(): TileProcessor {
        return mockk(relaxed = true)
    }
}
