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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import earth.maps.cardinal.R.string
import earth.maps.cardinal.data.AppPreferences

@Composable
fun AccessibilitySettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel<SettingsViewModel>(),
    onDismiss: () -> Unit
) {
    SettingsScreenScaffold(
        title = stringResource(string.accessibility_settings_title),
        onDismiss = onDismiss
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SettingsDivider()
            SettingsScrollableContent() {
                // Contrast Settings Item
                SettingsItem(
                    title = stringResource(string.contrast_settings_title),
                    description = stringResource(string.contrast_settings_help_text)
                ) {
                    // Contrast level selection
                    val currentContrastLevel by viewModel.contrastLevel.collectAsState()
                    var selectedContrastLevel by remember { mutableIntStateOf(currentContrastLevel) }

                    // Update selected state when preference changes from outside
                    LaunchedEffect(currentContrastLevel) {
                        selectedContrastLevel = currentContrastLevel
                    }

                    PreferenceOption(
                        selectedValue = selectedContrastLevel, options = listOf(
                            AppPreferences.CONTRAST_LEVEL_STANDARD to string.contrast_standard,
                            AppPreferences.CONTRAST_LEVEL_MEDIUM to string.contrast_medium,
                            AppPreferences.CONTRAST_LEVEL_HIGH to string.contrast_high
                        )
                    ) { newValue ->
                        selectedContrastLevel = newValue
                        viewModel.setContrastLevel(newValue)
                    }
                }

                SettingsDivider()

                // Animation Speed Settings Item
                SettingsItem(
                    title = stringResource(string.animation_speed_title),
                    description = stringResource(string.animation_speed_help_text)
                ) {
                    // Animation speed selection
                    val currentAnimationSpeed by viewModel.animationSpeed.collectAsState()
                    var selectedAnimationSpeed by remember {
                        mutableIntStateOf(
                            currentAnimationSpeed
                        )
                    }

                    // Update selected state when preference changes from outside
                    LaunchedEffect(currentAnimationSpeed) {
                        selectedAnimationSpeed = currentAnimationSpeed
                    }

                    PreferenceOption(
                        selectedValue = selectedAnimationSpeed, options = listOf(
                            AppPreferences.ANIMATION_SPEED_SLOW to string.animation_speed_slow,
                            AppPreferences.ANIMATION_SPEED_NORMAL to string.animation_speed_normal,
                            AppPreferences.ANIMATION_SPEED_FAST to string.animation_speed_fast
                        )
                    ) { newValue ->
                        selectedAnimationSpeed = newValue
                        viewModel.setAnimationSpeed(newValue)
                    }
                }
            }
        }
    }
}
