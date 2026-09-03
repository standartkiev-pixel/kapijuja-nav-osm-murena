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

data class MurenaFileSyncGateState(
    val mode: MurenaFileSyncGateMode = MurenaFileSyncGateMode.SyncingFile,
    val errorMessage: MurenaFileSyncGateMessage? = null
)

enum class MurenaFileSyncGateMode {
    SyncingFile,
    NeedsMurenaAccount,
    NeedsFolderAccess,
    Failed
}

sealed interface MurenaFileSyncGateAction {
    data object OnRetryClick : MurenaFileSyncGateAction
    data object OnContinueClick : MurenaFileSyncGateAction
    data object OnSyncWithAccountClick : MurenaFileSyncGateAction
    data object OnAccountLoginReturned : MurenaFileSyncGateAction
    data object OnChooseFolderClick : MurenaFileSyncGateAction
    data object OnFolderAccessCanceled : MurenaFileSyncGateAction
    data class OnFolderAccessGranted(val uri: String) : MurenaFileSyncGateAction
}

sealed interface MurenaFileSyncGateEvent {
    data object ContinueToMap : MurenaFileSyncGateEvent
    data object RequestMurenaAccountLogin : MurenaFileSyncGateEvent
    data object RequestFolderAccess : MurenaFileSyncGateEvent
}

enum class MurenaFileSyncGateMessage {
    FolderAccessRequired,
    FileSyncFailed
}
