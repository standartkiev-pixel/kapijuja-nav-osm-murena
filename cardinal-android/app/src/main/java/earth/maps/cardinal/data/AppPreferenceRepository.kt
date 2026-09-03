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

package earth.maps.cardinal.data

import android.content.Context
import android.text.format.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class AppPreferenceRepository @Inject constructor(
    context: Context
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appPreferences = AppPreferences(context)

    private val _contrastLevel = MutableStateFlow(AppPreferences.CONTRAST_LEVEL_STANDARD)
    val contrastLevel: StateFlow<Int> = _contrastLevel.asStateFlow()

    private val _animationSpeed = MutableStateFlow(AppPreferences.ANIMATION_SPEED_NORMAL)
    val animationSpeed: StateFlow<Int> = _animationSpeed.asStateFlow()

    val animationSpeedDurationValue: Duration
        get() = when (_animationSpeed.value) {
            AppPreferences.ANIMATION_SPEED_SLOW -> 2000.milliseconds
            AppPreferences.ANIMATION_SPEED_NORMAL -> 1000.milliseconds
            AppPreferences.ANIMATION_SPEED_FAST -> 500.milliseconds
            else -> 1000.milliseconds
        }

    private val _offlineMode = MutableStateFlow(AppPreferences.OFFLINE_MODE_DISABLED)
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    private val _distanceUnit = MutableStateFlow(AppPreferences.DISTANCE_UNIT_METRIC)
    val distanceUnit: StateFlow<Int> = _distanceUnit.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _allowTransitInOfflineMode = MutableStateFlow(true)
    val allowTransitInOfflineMode: StateFlow<Boolean> = _allowTransitInOfflineMode.asStateFlow()

    private val _continuousLocationTracking = MutableStateFlow(true)
    val continuousLocationTracking: StateFlow<Boolean> =
        _continuousLocationTracking.asStateFlow()

    private val _showZoomFabs = MutableStateFlow(true)
    val showZoomFabs: StateFlow<Boolean> = _showZoomFabs.asStateFlow()

    private val _use24HourFormat = MutableStateFlow(DateFormat.is24HourFormat(context))
    val use24HourFormat: StateFlow<Boolean> = _use24HourFormat.asStateFlow()

    private val _lastRoutingMode = MutableStateFlow(appPreferences.loadLastRoutingMode())
    val lastRoutingMode: StateFlow<String> = _lastRoutingMode.asStateFlow()

    private val _hasPromptedLocation = MutableStateFlow(appPreferences.loadHasPromptedLocation())
    val hasPromptedLocation: StateFlow<Boolean> = _hasPromptedLocation.asStateFlow()

    private val _hasPromptedThemeMode = MutableStateFlow(appPreferences.loadHasPromptedThemeMode())
    val hasPromptedThemeMode: StateFlow<Boolean> = _hasPromptedThemeMode.asStateFlow()

    val favoritesSyncMode: FavoritesSyncMode
        get() = appPreferences.loadFavoritesSyncMode()

    val favoritesSyncTreeUri: String?
        get() = appPreferences.loadFavoritesSyncTreeUri()

    // Pelias API configuration
    private val _peliasApiConfig = MutableStateFlow(
        ApiConfiguration(
            baseUrl = appPreferences.loadPeliasBaseUrl(),
            apiKey = appPreferences.loadPeliasApiKey()
        )
    )
    val peliasApiConfig: StateFlow<ApiConfiguration> = _peliasApiConfig.asStateFlow()

    private val _nearbyApiConfig = MutableStateFlow(
        ApiConfiguration(
            baseUrl = appPreferences.loadNearbyBaseUrl()
        )
    )
    val nearbyApiConfig: StateFlow<ApiConfiguration> = _nearbyApiConfig.asStateFlow()

    // Valhalla API configuration
    private val _valhallaApiConfig = MutableStateFlow(
        ApiConfiguration(
            baseUrl = appPreferences.loadValhallaBaseUrl(),
            apiKey = appPreferences.loadValhallaApiKey()
        )
    )
    val valhallaApiConfig: StateFlow<ApiConfiguration> = _valhallaApiConfig.asStateFlow()

    init {
        loadContrastLevel()
        loadAnimationSpeed()
        loadOfflineMode()
        loadDistanceUnit()
        loadThemeMode()
        loadAllowTransitInOfflineMode()
        loadContinuousLocationTracking()
        loadShowZoomFabs()
        loadUse24HourFormat()
        loadLastRoutingMode()
        loadApiConfigurations()
    }

    fun setContrastLevel(level: Int) {
        _contrastLevel.value = level
        viewModelScope.launch {
            appPreferences.saveContrastLevel(level)
        }
    }

    private fun loadContrastLevel() {
        val level = appPreferences.loadContrastLevel()
        _contrastLevel.value = level
    }

    fun setAnimationSpeed(speed: Int) {
        _animationSpeed.value = speed
        viewModelScope.launch {
            appPreferences.saveAnimationSpeed(speed)
        }
    }

    private fun loadAnimationSpeed() {
        val speed = appPreferences.loadAnimationSpeed()
        _animationSpeed.value = speed
    }

    fun setOfflineMode(offlineMode: Boolean) {
        _offlineMode.value = offlineMode
        viewModelScope.launch {
            appPreferences.saveOfflineMode(offlineMode)
        }
    }

    private fun loadOfflineMode() {
        val offlineMode = appPreferences.loadOfflineMode()
        _offlineMode.value = offlineMode
    }

    fun setDistanceUnit(distanceUnit: Int) {
        _distanceUnit.value = distanceUnit
        viewModelScope.launch {
            appPreferences.saveDistanceUnit(distanceUnit)
        }
    }

    private fun loadDistanceUnit() {
        val distanceUnit = appPreferences.loadDistanceUnit()
        _distanceUnit.value = distanceUnit
    }

    fun setThemeMode(themeMode: ThemeMode) {
        _themeMode.value = themeMode
        appPreferences.saveThemeMode(themeMode)
    }

    private fun loadThemeMode() {
        val themeMode = appPreferences.loadThemeMode()
        _themeMode.value = themeMode
    }

    private fun loadAllowTransitInOfflineMode() {
        val allowTransitInOfflineMode = appPreferences.loadAllowTransitInOfflineMode()
        _allowTransitInOfflineMode.value = allowTransitInOfflineMode
    }

    private fun loadContinuousLocationTracking() {
        val enabled = appPreferences.loadContinuousLocationTracking()
        _continuousLocationTracking.value = enabled
    }

    private fun loadShowZoomFabs() {
        val show = appPreferences.loadShowZoomFabs()
        _showZoomFabs.value = show
    }

    private fun loadUse24HourFormat() {
        val use24Hour = appPreferences.loadUse24HourFormat()
        _use24HourFormat.value = use24Hour
    }

    fun setAllowTransitInOfflineMode(allowTransitInOfflineMode: Boolean) {
        _allowTransitInOfflineMode.value = allowTransitInOfflineMode
        viewModelScope.launch {
            appPreferences.saveAllowTransitInOfflineMode(allowTransitInOfflineMode)
        }
    }

    fun setContinuousLocationTracking(enabled: Boolean) {
        _continuousLocationTracking.value = enabled
        viewModelScope.launch {
            appPreferences.saveContinuousLocationTracking(enabled)
        }
    }

    fun setShowZoomFabs(show: Boolean) {
        _showZoomFabs.value = show
        viewModelScope.launch {
            appPreferences.saveShowZoomFabs(show)
        }
    }

    fun setUse24HourFormat(use24Hour: Boolean) {
        _use24HourFormat.value = use24Hour
        viewModelScope.launch {
            appPreferences.saveUse24HourFormat(use24Hour)
        }
    }

    private fun loadLastRoutingMode() {
        val mode = appPreferences.loadLastRoutingMode()
        _lastRoutingMode.value = mode
    }

    fun setLastRoutingMode(mode: String) {
        _lastRoutingMode.value = mode
        viewModelScope.launch {
            appPreferences.saveLastRoutingMode(mode)
        }
    }

    fun setHasPromptedLocation(hasPrompted: Boolean) {
        _hasPromptedLocation.value = hasPrompted
        viewModelScope.launch {
            appPreferences.saveHasPromptedLocation(hasPrompted)
        }
    }

    fun setHasPromptedThemeMode(hasPrompted: Boolean) {
        _hasPromptedThemeMode.value = hasPrompted
        appPreferences.saveHasPromptedThemeMode(hasPrompted)
    }

    fun setFavoritesSyncTreeUri(uri: String?) {
        appPreferences.saveFavoritesSyncTreeUri(uri)
    }

    fun setFavoritesSyncMode(mode: FavoritesSyncMode) {
        appPreferences.saveFavoritesSyncMode(mode)
    }

    private fun loadApiConfigurations() {
        // Load Pelias configuration
        val peliasBaseUrl = appPreferences.loadPeliasBaseUrl()
        val peliasApiKey = appPreferences.loadPeliasApiKey()
        _peliasApiConfig.value = ApiConfiguration(peliasBaseUrl, peliasApiKey)

        // Load nearby configuration
        val nearbyBaseUrl = appPreferences.loadNearbyBaseUrl()
        _nearbyApiConfig.value = ApiConfiguration(nearbyBaseUrl)

        // Load Valhalla configuration
        val valhallaBaseUrl = appPreferences.loadValhallaBaseUrl()
        val valhallaApiKey = appPreferences.loadValhallaApiKey()
        _valhallaApiConfig.value = ApiConfiguration(valhallaBaseUrl, valhallaApiKey)
    }

    // Pelias API configuration methods
    fun setPeliasBaseUrl(baseUrl: String) {
        val currentConfig = _peliasApiConfig.value
        val newConfig = ApiConfiguration(baseUrl, currentConfig.apiKey)
        _peliasApiConfig.value = newConfig
        viewModelScope.launch {
            appPreferences.savePeliasBaseUrl(baseUrl)
        }
    }

    fun setPeliasApiKey(apiKey: String?) {
        val normalizedKey = apiKey?.takeIf { it.isNotBlank() }
        val currentConfig = _peliasApiConfig.value
        val baseUrl = when {
            normalizedKey != null &&
                currentConfig.baseUrl == AppPreferences.DEFAULT_PELIAS_ENDPOINT_WITHOUT_API_KEY ->
                AppPreferences.STADIA_PELIAS_ENDPOINT
            normalizedKey == null &&
                currentConfig.baseUrl == AppPreferences.STADIA_PELIAS_ENDPOINT ->
                AppPreferences.DEFAULT_PELIAS_ENDPOINT_WITHOUT_API_KEY
            else -> currentConfig.baseUrl
        }
        _peliasApiConfig.value = ApiConfiguration(baseUrl, normalizedKey)
        viewModelScope.launch {
            if (baseUrl != currentConfig.baseUrl) {
                appPreferences.savePeliasBaseUrl(baseUrl)
            }
            appPreferences.savePeliasApiKey(normalizedKey)
        }
    }

    fun setNearbyBaseUrl(baseUrl: String) {
        _nearbyApiConfig.value = ApiConfiguration(baseUrl)
        viewModelScope.launch {
            appPreferences.saveNearbyBaseUrl(baseUrl)
        }
    }

    // Valhalla API configuration methods
    fun setValhallaBaseUrl(baseUrl: String) {
        val currentConfig = _valhallaApiConfig.value
        val newConfig = ApiConfiguration(baseUrl, currentConfig.apiKey)
        _valhallaApiConfig.value = newConfig
        viewModelScope.launch {
            appPreferences.saveValhallaBaseUrl(baseUrl)
        }
    }

    fun setValhallaApiKey(apiKey: String?) {
        val normalizedKey = apiKey?.takeIf { it.isNotBlank() }
        val currentConfig = _valhallaApiConfig.value
        val baseUrl = when {
            normalizedKey != null &&
                currentConfig.baseUrl == AppPreferences.DEFAULT_VALHALLA_ENDPOINT_WITHOUT_API_KEY ->
                AppPreferences.STADIA_VALHALLA_ENDPOINT
            normalizedKey == null &&
                currentConfig.baseUrl == AppPreferences.STADIA_VALHALLA_ENDPOINT ->
                AppPreferences.DEFAULT_VALHALLA_ENDPOINT_WITHOUT_API_KEY
            else -> currentConfig.baseUrl
        }
        _valhallaApiConfig.value = ApiConfiguration(baseUrl, normalizedKey)
        viewModelScope.launch {
            if (baseUrl != currentConfig.baseUrl) {
                appPreferences.saveValhallaBaseUrl(baseUrl)
            }
            appPreferences.saveValhallaApiKey(normalizedKey)
        }
    }

    fun resetApiConfigurationsToDefaults() {
        appPreferences.resetApiConfigurationsToDefaults()
        loadApiConfigurations()
    }
}
