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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface ConnectivityRepository {
    val isInternetConnected: StateFlow<Boolean>

    fun reportInternetAvailable()

    fun reportInternetUnavailable()
}

@Singleton
class AndroidConnectivityRepository @Inject constructor(
    @ApplicationContext context: Context
) : ConnectivityRepository {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastNoInternetReportElapsedRealtime = 0L

    private val _isInternetConnected = MutableStateFlow(connectivityManager.hasInternet())
    override val isInternetConnected: StateFlow<Boolean> = _isInternetConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateInternetStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateInternetStatus()
        }

        override fun onLost(network: Network) {
            updateInternetStatus()
        }

        override fun onUnavailable() {
            updateInternetStatus()
        }
    }

    init {
        updateInternetStatus()
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }
        scope.launch {
            while (isActive) {
                delay(CONNECTIVITY_POLL_INTERVAL_MS)
                updateInternetStatus()
            }
        }
    }

    override fun reportInternetAvailable() {
        if (recentlyReportedNoInternet()) {
            return
        }
        _isInternetConnected.value = true
    }

    override fun reportInternetUnavailable() {
        lastNoInternetReportElapsedRealtime = SystemClock.elapsedRealtime()
        _isInternetConnected.value = false
    }

    private fun updateInternetStatus() {
        val hasInternet = connectivityManager.hasInternet()
        if (hasInternet && recentlyReportedNoInternet()) {
            return
        }
        _isInternetConnected.value = hasInternet
    }

    private fun recentlyReportedNoInternet(): Boolean {
        val lastReport = lastNoInternetReportElapsedRealtime
        return lastReport != 0L &&
            SystemClock.elapsedRealtime() - lastReport < NO_INTERNET_REPORT_HOLD_MS
    }

    private fun ConnectivityManager.hasInternet(): Boolean {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object {
        const val CONNECTIVITY_POLL_INTERVAL_MS = 2_000L
        const val NO_INTERNET_REPORT_HOLD_MS = 10_000L
    }
}
