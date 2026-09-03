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

package earth.maps.cardinal.data.sync

import android.content.Context
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.FavoritesSyncMode
import earth.maps.cardinal.data.room.AppDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentFavoritesFileSyncRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val appPreferenceRepository = mockk<AppPreferenceRepository>(relaxed = true)

    @Test
    fun `sync and export are skipped in local only mode`() = runTest {
        every { appPreferenceRepository.favoritesSyncMode } returns FavoritesSyncMode.LOCAL_ONLY
        val repository = DocumentFavoritesFileSyncRepository(
            context = context,
            database = database,
            appPreferenceRepository = appPreferenceRepository
        )

        val syncResult = repository.syncFileToLocalDatabase()
        val exportResult = repository.exportLocalDatabaseToFile()

        assertTrue(syncResult.isSuccess)
        assertTrue(exportResult.isSuccess)
        verify(exactly = 0) { database.savedListDao() }
        verify(exactly = 0) { database.savedPlaceDao() }
        verify(exactly = 0) { database.listItemDao() }
    }
}
