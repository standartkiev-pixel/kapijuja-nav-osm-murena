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

package earth.maps.cardinal.ui.murena

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.FavoritesSyncMode
import earth.maps.cardinal.domain.murena.MurenaAccountRepository
import earth.maps.cardinal.domain.sync.FavoritesFileSyncRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MurenaFileSyncGateViewModel @Inject constructor(
    private val favoritesFileSyncRepository: FavoritesFileSyncRepository,
    private val murenaAccountRepository: MurenaAccountRepository,
    private val appPreferenceRepository: AppPreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MurenaFileSyncGateState())
    val state = _state.asStateFlow()

    private val _events = Channel<MurenaFileSyncGateEvent>()
    val events = _events.receiveAsFlow()

    init {
        checkAccountAndSyncFile()
    }

    fun onAction(action: MurenaFileSyncGateAction) {
        when (action) {
            MurenaFileSyncGateAction.OnRetryClick -> checkAccountAndSyncFile()
            MurenaFileSyncGateAction.OnContinueClick -> continueToMap()
            MurenaFileSyncGateAction.OnSyncWithAccountClick -> requestMurenaAccountLogin()
            MurenaFileSyncGateAction.OnAccountLoginReturned -> checkAccountAndSyncFile(waitForAccount = true)
            MurenaFileSyncGateAction.OnChooseFolderClick -> requestFolderAccess()
            MurenaFileSyncGateAction.OnFolderAccessCanceled -> continueToMap()
            is MurenaFileSyncGateAction.OnFolderAccessGranted -> saveFolderAccessAndRetry(action.uri)
        }
    }

    private fun checkAccountAndSyncFile(waitForAccount: Boolean = false) {
        viewModelScope.launch {
            if (appPreferenceRepository.favoritesSyncMode == FavoritesSyncMode.LOCAL_ONLY) {
                Log.d(TAG, "Favorites sync is local-only; skipping Murena file sync gate")
                _events.send(MurenaFileSyncGateEvent.ContinueToMap)
                return@launch
            }

            Log.d(TAG, "Favorites file sync gate started")
            _state.update {
                it.copy(
                    mode = MurenaFileSyncGateMode.SyncingFile,
                    errorMessage = null
                )
            }

            if (favoritesFileSyncRepository.hasReadableSyncFile()) {
                Log.d(TAG, "Documents/Maps favorites file is readable; syncing without account check")
                syncFileAndContinue()
                return@launch
            }

            if (appPreferenceRepository.favoritesSyncTreeUri == null) {
                Log.d(TAG, "Documents/Maps is not readable yet; requesting folder access before account check")
                requestFolderAccess()
                return@launch
            }

            if (waitForFileIfNeeded(waitForAccount)) {
                syncFileAndContinue()
                return@launch
            }

            if (!waitForMurenaAccount()) {
                Log.d(TAG, "No Murena account on device; showing account choice")
                showAccountChoice()
                return@launch
            }

            syncFileAndContinue()
        }
    }

    private suspend fun waitForMurenaAccount(): Boolean {
        repeat(ACCOUNT_VISIBILITY_RETRY_COUNT) { attempt ->
            if (murenaAccountRepository.hasMurenaAccount()) {
                Log.d(TAG, "Murena account found on attempt=${attempt + 1}")
                return true
            }
            if (attempt < ACCOUNT_VISIBILITY_RETRY_COUNT - 1) {
                Log.d(TAG, "Murena account not visible yet; retrying account query")
                delay(ACCOUNT_VISIBILITY_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private fun showAccountChoice() {
        _state.update {
            it.copy(
                mode = MurenaFileSyncGateMode.NeedsMurenaAccount,
                errorMessage = null
            )
        }
    }

    private suspend fun waitForFileIfNeeded(waitForFile: Boolean): Boolean {
        val maxAttempts = if (waitForFile) SYNC_FILE_AVAILABILITY_RETRY_COUNT else 1
        repeat(maxAttempts) { attempt ->
            if (favoritesFileSyncRepository.hasReadableSyncFile()) {
                Log.d(TAG, "Documents/Maps favorites file is readable on attempt=${attempt + 1}")
                return true
            }
            if (attempt < maxAttempts - 1) {
                Log.d(TAG, "Documents/Maps favorites file not readable yet; waiting for eDrive")
                delay(SYNC_FILE_AVAILABILITY_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private suspend fun syncFileAndContinue() {
        Log.d(TAG, "Syncing favorites file")
        _state.update {
            it.copy(
                mode = MurenaFileSyncGateMode.SyncingFile,
                errorMessage = null
            )
        }

        favoritesFileSyncRepository.syncFileToLocalDatabase()
            .onSuccess {
                Log.d(TAG, "Favorites file sync completed")
                _events.send(MurenaFileSyncGateEvent.ContinueToMap)
            }
            .onFailure { exception ->
                Log.w(TAG, "Favorites file sync failed", exception)
                requestFolderAccess()
            }
    }

    private fun requestMurenaAccountLogin() {
        appPreferenceRepository.setFavoritesSyncMode(FavoritesSyncMode.SYNC_ENABLED)
        viewModelScope.launch {
            _events.send(MurenaFileSyncGateEvent.RequestMurenaAccountLogin)
        }
    }

    private fun requestFolderAccess() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    mode = MurenaFileSyncGateMode.SyncingFile,
                    errorMessage = null
                )
            }
            favoritesFileSyncRepository.prepareSyncFolderForAccess()
                .onSuccess {
                    Log.d(TAG, "Documents/Maps folder prepared for access request")
                }
                .onFailure { exception ->
                    Log.w(TAG, "Could not prepare Documents/Maps before access request", exception)
                }
            _events.send(MurenaFileSyncGateEvent.RequestFolderAccess)
        }
    }

    private fun saveFolderAccessAndRetry(uri: String) {
        appPreferenceRepository.setFavoritesSyncMode(FavoritesSyncMode.SYNC_ENABLED)
        appPreferenceRepository.setFavoritesSyncTreeUri(uri)
        checkAccountAndSyncFile()
    }

    private fun continueToMap() {
        appPreferenceRepository.setFavoritesSyncMode(FavoritesSyncMode.LOCAL_ONLY)
        viewModelScope.launch {
            Log.d(TAG, "Continuing to map after favorites file sync failure")
            _events.send(MurenaFileSyncGateEvent.ContinueToMap)
        }
    }

    private companion object {
        private const val TAG = "MurenaFileSync"
        private const val ACCOUNT_VISIBILITY_RETRY_COUNT = 10
        private const val ACCOUNT_VISIBILITY_RETRY_DELAY_MS = 500L
        private const val SYNC_FILE_AVAILABILITY_RETRY_COUNT = 12
        private const val SYNC_FILE_AVAILABILITY_RETRY_DELAY_MS = 1_000L
    }
}
