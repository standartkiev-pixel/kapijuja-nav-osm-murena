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

package earth.maps.cardinal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string

@Composable
fun PrivacySettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel<SettingsViewModel>(),
    onDismiss: () -> Unit,
    onNavigateToOfflineAreas: () -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(string.privacy_settings_title),
        onDismiss = onDismiss
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SettingsDivider()
            SettingsScrollableContent {
                // Offline Areas Settings Item
                SettingsItem(
                    title = stringResource(string.offline_areas_title),
                    description = stringResource(string.offline_areas_help_text),
                    iconResId = drawable.cloud_download_24dp,
                    onClick = onNavigateToOfflineAreas
                )

                SettingsDivider()

                StatefulSwitchSetting(
                    title = stringResource(string.offline_mode_title),
                    description = stringResource(string.offline_mode_help_text),
                    stateFlow = viewModel.offlineMode,
                    onStateChanged = viewModel::setOfflineMode
                )

                SettingsDivider()

                StatefulSwitchSetting(
                    title = stringResource(string.allow_transit_in_offline_mode_title),
                    description = stringResource(string.allow_transit_in_offline_mode_help_text),
                    stateFlow = viewModel.allowTransitInOfflineMode,
                    onStateChanged = viewModel::setAllowTransitInOfflineMode
                )
            }
        }
    }
}
