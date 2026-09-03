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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.string

@Composable
fun ThemeSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel<SettingsViewModel>(),
    onDismiss: () -> Unit
) {
    SettingsScreenScaffold(
        title = stringResource(string.theme_mode_settings_title),
        onDismiss = onDismiss
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SettingsDivider()
            SettingsScrollableContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(dimen.padding),
                            vertical = dimensionResource(dimen.padding_minor)
                        )
                ) {
                    Text(
                        text = stringResource(string.theme_mode_settings_help_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val currentThemeMode by viewModel.themeMode.collectAsState()
                    var selectedThemeMode by remember {
                        mutableStateOf(currentThemeMode)
                    }

                    LaunchedEffect(currentThemeMode) {
                        selectedThemeMode = currentThemeMode
                    }

                    PreferenceOption(
                        selectedValue = selectedThemeMode,
                        options = themeModeOptions
                    ) { newValue ->
                        selectedThemeMode = newValue
                        viewModel.setThemeMode(newValue)
                    }
                }
            }
        }
    }
}
