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

package earth.maps.cardinal.routing

import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.navigation.FerrostarWrapperFactory
import earth.maps.cardinal.data.room.RoutingProfileRepository
import earth.maps.cardinal.data.tts.MapsTtsObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FerrostarWrapperRepository @Inject constructor(
    private val routingProfileRepository: RoutingProfileRepository,
    private val androidTtsObserver: SpokenInstructionObserver,
    private val factory: FerrostarWrapperFactory
) {
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private var _walking: FerrostarWrapper? = null
    private var _cycling: FerrostarWrapper? = null
    private var _driving: FerrostarWrapper? = null
    private var _truck: FerrostarWrapper? = null
    private var _motorScooter: FerrostarWrapper? = null
    private var _motorcycle: FerrostarWrapper? = null

    val walking: FerrostarWrapper get() = _walking ?: throw IllegalStateException("Walking wrapper not initialized")
    val cycling: FerrostarWrapper get() = _cycling ?: throw IllegalStateException("Cycling wrapper not initialized")
    val driving: FerrostarWrapper get() = _driving ?: throw IllegalStateException("Driving wrapper not initialized")
    val truck: FerrostarWrapper get() = _truck ?: throw IllegalStateException("Truck wrapper not initialized")
    val motorScooter: FerrostarWrapper get() = _motorScooter ?: throw IllegalStateException("MotorScooter wrapper not initialized")
    val motorcycle: FerrostarWrapper get() = _motorcycle ?: throw IllegalStateException("Motorcycle wrapper not initialized")

    private val pendingOptions = mutableMapOf<RoutingMode, RoutingOptions>()

    /**
     * Suspends the caller until the repository has been initialized with a Valhalla endpoint.
     *
     * Once [_isInitialized] becomes true, this function resumes. If the repository
     * is already initialized, it returns immediately.
     */
    suspend fun awaitInitialization() {
        _isInitialized.filter { it }.first()
    }

    fun onStartNavigation() {
        (androidTtsObserver as? MapsTtsObserver)?.start()
    }

    fun onStopNavigation() {
        (androidTtsObserver as? MapsTtsObserver)?.shutdown()
    }

    fun setValhallaEndpoint(endpoint: String) {
        _walking = factory.create(
            mode = RoutingMode.PEDESTRIAN,
            endpoint = endpoint
        )
        _cycling = factory.create(
            mode = RoutingMode.BICYCLE,
            endpoint = endpoint
        )
        _driving = factory.create(
            mode = RoutingMode.AUTO,
            endpoint = endpoint
        )
        _truck = factory.create(
            mode = RoutingMode.TRUCK,
            endpoint = endpoint
        )
        _motorScooter = factory.create(
            mode = RoutingMode.MOTOR_SCOOTER,
            endpoint = endpoint
        )
        _motorcycle = factory.create(
            mode = RoutingMode.MOTORCYCLE,
            endpoint = endpoint
        )

        _isInitialized.value = true

        // Apply pending options
        synchronized(pendingOptions) {
            pendingOptions.forEach { (mode, options) ->
                setOptionsForMode(mode, options)
            }
            pendingOptions.clear()
        }
    }

    /**
     * Updates the [RoutingOptions] for the specified [mode] by modifying the existing wrapper.
     *
     * If the wrapper for the given [mode] is not yet initialized (i.e., [setValhallaEndpoint]
     * hasn't been called), the options are stored in [pendingOptions] and applied
     * automatically once initialization completes.
     *
     * @param mode The [RoutingMode] to update (e.g., PEDESTRIAN, AUTO).
     * @param routingOptions The new configuration options to apply.
     */
    fun setOptionsForMode(mode: RoutingMode, routingOptions: RoutingOptions) {
        val wrapper = getWrapperForMode(mode)
        if (wrapper != null) {
            wrapper.setOptions(routingOptions)
        } else {
            synchronized(pendingOptions) {
                pendingOptions[mode] = routingOptions
            }
        }
    }

    /**
     * Resets the [RoutingOptions] for the specified [mode] to their default values.
     *
     * This retrieves the defaults from [routingProfileRepository]. If the [FerrostarWrapper]
     * for this mode is already initialized, the options are applied immediately.
     *
     * If the wrapper is not yet initialized (i.e., [setValhallaEndpoint] hasn't been called),
     * the default options are stored in [pendingOptions] and applied automatically
     * during initialization.
     *
     * @param mode The [RoutingMode] to reset (e.g., PEDESTRIAN, BICYCLE).
     */
    fun resetOptionsToDefaultsForMode(mode: RoutingMode) {
        val defaultOptions = routingProfileRepository.createDefaultOptionsForMode(mode)
        val wrapper = getWrapperForMode(mode)
        if (wrapper != null) {
            wrapper.setOptions(defaultOptions)
        } else {
            defaultOptions?.let {
                synchronized(pendingOptions) {
                    pendingOptions[mode] = it
                }
            }
        }
    }

    /**
     * Resolves the internal [FerrostarWrapper] instance associated with a specific [RoutingMode].*
     * @param mode The [RoutingMode] for which to retrieve the wrapper.
     * @return The corresponding [FerrostarWrapper] (e.g., [_walking], [_cycling]),
     * or `null` if the mode is unrecognized or the wrapper hasn't been initialized via [setValhallaEndpoint].
     */
    private fun getWrapperForMode(mode: RoutingMode): FerrostarWrapper? = when (mode) {
        RoutingMode.PEDESTRIAN -> _walking
        RoutingMode.BICYCLE -> _cycling
        RoutingMode.AUTO -> _driving
        RoutingMode.TRUCK -> _truck
        RoutingMode.MOTOR_SCOOTER -> _motorScooter
        RoutingMode.MOTORCYCLE -> _motorcycle
        else -> null
    }
}
