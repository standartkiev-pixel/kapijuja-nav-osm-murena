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

package earth.maps.cardinal.data

import android.content.Context
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppPreferenceRepositoryTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `nearbyApiConfig uses default nearby endpoint`() {
        val repository = AppPreferenceRepository(context)

        assertEquals(
            context.getString(R.string.default_nearby_endpoint),
            repository.nearbyApiConfig.value.baseUrl
        )
        assertNull(repository.nearbyApiConfig.value.apiKey)
    }

    @Test
    fun `nearbyApiConfig uses saved nearby endpoint`() {
        val baseUrl = "https://nearby.example.com/pelias/v1"
        AppPreferences(context).saveNearbyBaseUrl(baseUrl)

        val repository = AppPreferenceRepository(context)

        assertEquals(baseUrl, repository.nearbyApiConfig.value.baseUrl)
        assertNull(repository.nearbyApiConfig.value.apiKey)
    }

    @Test
    fun `setNearbyBaseUrl updates state and persists value`() = runTest {
        val repository = AppPreferenceRepository(context)
        val baseUrl = "https://custom-nearby.example.com/pelias/v1"

        repository.setNearbyBaseUrl(baseUrl)
        advanceUntilIdle()

        assertEquals(baseUrl, repository.nearbyApiConfig.value.baseUrl)
        assertEquals(baseUrl, AppPreferences(context).loadNearbyBaseUrl())
    }
}
