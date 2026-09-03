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

package earth.maps.cardinal.ui.directions

import earth.maps.cardinal.data.ConnectivityRepository
import earth.maps.cardinal.routing.FerrostarWrapperRepository
import earth.maps.cardinal.ui.navigation.TurnByTurnNavigationViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TurnByTurnNavigationViewModelTest {

    private lateinit var viewModel: TurnByTurnNavigationViewModel
    private lateinit var mockFerrostarWrapperRepository: FerrostarWrapperRepository
    private lateinit var mockConnectivityRepository: ConnectivityRepository

    @Before
    fun setup() {
        mockFerrostarWrapperRepository = mockk(relaxed = true)
        mockConnectivityRepository = mockk()
        every { mockConnectivityRepository.isInternetConnected } returns MutableStateFlow(true)
        viewModel = TurnByTurnNavigationViewModel(
            ferrostarWrapperRepository = mockFerrostarWrapperRepository,
            connectivityRepository = mockConnectivityRepository
        )
    }

    @Test
    fun `viewModel should have correct ferrostarWrapperRepository`() {
        assertSame(mockFerrostarWrapperRepository, viewModel.ferrostarWrapperRepository)
    }
}
