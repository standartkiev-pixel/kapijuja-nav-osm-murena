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
import earth.maps.cardinal.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(context)
    }

    @Test
    fun `loadNearbyBaseUrl returns default nearby endpoint when unset`() {
        assertEquals(
            context.getString(R.string.default_nearby_endpoint),
            appPreferences.loadNearbyBaseUrl()
        )
    }

    @Test
    fun `loadNearbyBaseUrl returns saved nearby endpoint`() {
        val baseUrl = "https://nearby.example.com/pelias/v1"

        appPreferences.saveNearbyBaseUrl(baseUrl)

        assertEquals(baseUrl, appPreferences.loadNearbyBaseUrl())
    }

    @Test
    fun `loadNearbyBaseUrl falls back to default nearby endpoint when saved value is blank`() {
        appPreferences.saveNearbyBaseUrl(" ")

        assertEquals(
            context.getString(R.string.default_nearby_endpoint),
            appPreferences.loadNearbyBaseUrl()
        )
    }

    @Test
    fun `loadPeliasBaseUrl uses maps earth endpoint when default Stadia key is unavailable`() {
        assertEquals("https://maps.earth/pelias/v1", appPreferences.loadPeliasBaseUrl())
        assertNull(appPreferences.loadPeliasApiKey())
    }

    @Test
    fun `loadValhallaBaseUrl uses maps earth endpoint when default Stadia key is unavailable`() {
        assertEquals("https://maps.earth/valhalla/route", appPreferences.loadValhallaBaseUrl())
        assertNull(appPreferences.loadValhallaApiKey())
    }
}
