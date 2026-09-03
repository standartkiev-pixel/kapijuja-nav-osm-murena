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

package earth.maps.cardinal.ui.navigation

import com.stadiamaps.ferrostar.core.FerrostarCore
import earth.maps.cardinal.data.ConnectivityRepository
import earth.maps.cardinal.routing.FerrostarWrapper
import earth.maps.cardinal.routing.FerrostarWrapperRepository
import earth.maps.cardinal.routing.TrafficEtaCalibration
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import uniffi.ferrostar.Route

class TurnByTurnNavigationViewModelTest {

    private val repository = mockk<FerrostarWrapperRepository>(relaxed = true)
    private val connectivityRepository = mockk<ConnectivityRepository>()
    private val isInternetConnected = MutableStateFlow(true)

    private lateinit var viewModel: TurnByTurnNavigationViewModel

    @Before
    fun setup() {
        every { connectivityRepository.isInternetConnected } returns isInternetConnected
        viewModel = TurnByTurnNavigationViewModel(repository, connectivityRepository)
    }

    @Test
    fun `onEvent OnStartNavigation should call repository start`() {
        viewModel.onEvent(TurnByTurnNavigationUiEvent.OnStartNavigation)

        verify(exactly = 1) {
            repository.onStartNavigation()
        }

        verify(exactly = 0) {
            repository.onStopNavigation()
        }
    }

    @Test
    fun `onEvent OnStopNavigation should call repository stop`() {
        viewModel.onEvent(TurnByTurnNavigationUiEvent.OnStopNavigation)

        verify(exactly = 1) {
            repository.onStopNavigation()
        }

        verify(exactly = 0) {
            repository.onStartNavigation()
        }
    }

    @Test
    fun `should handle multiple events correctly`() {
        viewModel.onEvent(TurnByTurnNavigationUiEvent.OnStartNavigation)
        viewModel.onEvent(TurnByTurnNavigationUiEvent.OnStopNavigation)

        verifyOrder {
            repository.onStartNavigation()
            repository.onStopNavigation()
        }
    }

    @Test
    fun `should not call any repository method without events`() {
        confirmVerified(repository)
    }

    @Test
    fun `should reprocess last location when internet reconnects during navigation`() {
        val wrapper = mockk<FerrostarWrapper>(relaxed = true)
        val core = mockk<FerrostarCore>(relaxed = true)
        val route = mockk<Route>(relaxed = true)
        every { wrapper.core } returns core
        every { wrapper.isUsingTrafficProfile } returns false
        every { wrapper.etaCorrectionFactor } returns TrafficEtaCalibration.NO_CORRECTION_FACTOR

        isInternetConnected.value = false
        viewModel.startNavigation(wrapper, route)
        isInternetConnected.value = true

        verify(timeout = 1_000) {
            wrapper.reprocessLastKnownLocation(core)
        }
    }
}
