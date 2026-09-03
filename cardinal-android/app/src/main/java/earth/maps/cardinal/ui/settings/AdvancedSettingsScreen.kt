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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import earth.maps.cardinal.R.string

@Composable
fun AdvancedSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel<SettingsViewModel>()
) {
    SettingsScreenScaffold(
        title = stringResource(string.advanced_settings_title)
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SettingsDivider()
            SettingsScrollableContent {
                StatefulSwitchSetting(
                    title = stringResource(string.continuous_location_tracking_disabled_title),
                    description = stringResource(string.continuous_location_tracking_disabled_help_text),
                    stateFlow = viewModel.continuousLocationTracking,
                    onStateChanged = viewModel::setContinuousLocationTrackingEnabled
                )

                SettingsDivider()

                StatefulSwitchSetting(
                    title = stringResource(string.show_zoom_fabs_title),
                    description = stringResource(string.show_zoom_fabs_help_text),
                    stateFlow = viewModel.showZoomFabs,
                    onStateChanged = viewModel::setShowZoomFabsEnabled
                )

                SettingsDivider()

                // Time format setting with custom text
                TimeFormatSetting(viewModel)

                SettingsDivider()

                // Distance unit setting with custom text
                DistanceUnitSetting(viewModel)

                SettingsDivider()

                // API Configuration settings
                PeliasBaseUrlSetting(viewModel)

                SettingsDivider()

                PeliasApiKeySetting(viewModel)

                SettingsDivider()

                ValhallaBaseUrlSetting(viewModel)

                SettingsDivider()

                ValhallaApiKeySetting(viewModel)
            }
        }
    }
}

@Composable
private fun TimeFormatSetting(viewModel: SettingsViewModel) {
    val use24HourFormat by viewModel.use24HourFormat.collectAsState()
    var isChecked by remember { mutableStateOf(use24HourFormat) }

    // Update selected state when preference changes from outside
    LaunchedEffect(use24HourFormat) {
        isChecked = use24HourFormat
    }

    SwitchSetting(
        title = stringResource(string.time_format_title),
        description = stringResource(string.time_format_help_text),
        isChecked = isChecked,
        onCheckedChange = { newValue ->
            isChecked = newValue
            viewModel.setUse24HourFormat(newValue)
        },
        enabledText = "24\u2011hour",
        disabledText = "12\u2011hour"
    )
}

@Composable
private fun DistanceUnitSetting(viewModel: SettingsViewModel) {
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val isMetric = distanceUnit == 0
    var isChecked by remember { mutableStateOf(isMetric) }

    // Update selected state when preference changes from outside
    LaunchedEffect(distanceUnit) {
        isChecked = isMetric
    }

    SwitchSetting(
        title = stringResource(string.distance_unit_title),
        description = stringResource(string.distance_unit_help_text),
        isChecked = isChecked,
        onCheckedChange = { newValue ->
            isChecked = newValue
            val newUnit = if (newValue) 0 else 1
            viewModel.setDistanceUnit(newUnit)
        },
        enabledText = stringResource(string.metric),
        disabledText = stringResource(string.imperial)
    )
}

@Composable
private fun PeliasBaseUrlSetting(viewModel: SettingsViewModel) {
    SettingsItem(
        title = stringResource(string.pelias_base_url_title),
        description = ""
    ) {
        val currentPeliasConfig by viewModel.peliasApiConfig.collectAsState()
        var peliasBaseUrl by remember { mutableStateOf(currentPeliasConfig.baseUrl) }

        // Update state when config changes from outside
        LaunchedEffect(currentPeliasConfig) {
            peliasBaseUrl = currentPeliasConfig.baseUrl
        }

        OutlinedTextField(
            value = peliasBaseUrl,
            onValueChange = { newValue ->
                peliasBaseUrl = newValue
                viewModel.setPeliasBaseUrl(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }
}

@Composable
private fun PeliasApiKeySetting(viewModel: SettingsViewModel) {
    SettingsItem(
        title = stringResource(string.pelias_api_key_title),
        description = ""
    ) {
        val currentPeliasConfig by viewModel.peliasApiConfig.collectAsState()
        var peliasApiKey by remember {
            mutableStateOf(
                currentPeliasConfig.apiKey ?: ""
            )
        }

        // Update state when config changes from outside
        LaunchedEffect(currentPeliasConfig) {
            peliasApiKey = currentPeliasConfig.apiKey ?: ""
        }

        OutlinedTextField(
            value = peliasApiKey,
            onValueChange = { newValue ->
                peliasApiKey = newValue
                viewModel.setPeliasApiKey(if (newValue.isNotEmpty()) newValue else null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

@Composable
private fun ValhallaBaseUrlSetting(viewModel: SettingsViewModel) {
    SettingsItem(
        title = stringResource(string.valhalla_base_url_title),
        description = ""
    ) {
        val currentValhallaConfig by viewModel.valhallaApiConfig.collectAsState()
        var valhallaBaseUrl by remember { mutableStateOf(currentValhallaConfig.baseUrl) }

        // Update state when config changes from outside
        LaunchedEffect(currentValhallaConfig) {
            valhallaBaseUrl = currentValhallaConfig.baseUrl
        }

        OutlinedTextField(
            value = valhallaBaseUrl,
            onValueChange = { newValue ->
                valhallaBaseUrl = newValue
                viewModel.setValhallaBaseUrl(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }
}

@Composable
private fun ValhallaApiKeySetting(viewModel: SettingsViewModel) {
    SettingsItem(
        title = stringResource(string.valhalla_api_key_title),
        description = ""
    ) {
        val currentValhallaConfig by viewModel.valhallaApiConfig.collectAsState()
        var valhallaApiKey by remember {
            mutableStateOf(
                currentValhallaConfig.apiKey ?: ""
            )
        }

        // Update state when config changes from outside
        LaunchedEffect(currentValhallaConfig) {
            valhallaApiKey = currentValhallaConfig.apiKey ?: ""
        }

        OutlinedTextField(
            value = valhallaApiKey,
            onValueChange = { newValue ->
                valhallaApiKey = newValue
                viewModel.setValhallaApiKey(if (newValue.isNotEmpty()) newValue else null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}
