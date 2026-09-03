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

import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ConnectivityRepositoryTest {

    @After
    fun tearDown() {
        ShadowSystemClock.reset()
    }

    @Test
    fun `reportInternetAvailable keeps offline state during no internet hold`() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        val repository = AndroidConnectivityRepository(RuntimeEnvironment.getApplication())

        repository.reportInternetUnavailable()
        repository.reportInternetAvailable()

        assertFalse(repository.isInternetConnected.value)
    }

    @Test
    fun `reportInternetAvailable restores online state after no internet hold expires`() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        val repository = AndroidConnectivityRepository(RuntimeEnvironment.getApplication())

        repository.reportInternetUnavailable()
        ShadowSystemClock.advanceBy(Duration.ofMillis(NO_INTERNET_REPORT_HOLD_MS + 1))
        repository.reportInternetAvailable()

        assertTrue(repository.isInternetConnected.value)
    }

    private companion object {
        const val NO_INTERNET_REPORT_HOLD_MS = 10_000L
    }
}
