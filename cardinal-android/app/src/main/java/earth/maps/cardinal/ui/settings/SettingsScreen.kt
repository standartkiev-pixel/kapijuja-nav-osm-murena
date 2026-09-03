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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string
import earth.maps.cardinal.ui.core.CardinalNavigator
import earth.maps.cardinal.ui.core.CardinalRoute

@Composable
fun SettingsScreen(
    navigator: CardinalNavigator,
    viewModel: SettingsViewModel,
) {
    SettingsScreenScaffold(
        title = stringResource(string.app_name_long),
        onDismiss = { navigator.goBack() },
        showCloseButton = true
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(dimen.padding)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.padding(start = dimensionResource(dimen.padding)),
                    text = viewModel.getVersionName() ?: "v?.?.?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
            }

            SettingsDivider()
            SettingsScrollableContent {

                SettingsItem(
                    title = stringResource(string.routing_profiles),
                    description = stringResource(string.create_and_manage_custom_routing_profiles),
                    iconResId = drawable.commute_icon,
                    onClick = {
                        navigator.navigate(CardinalRoute.RoutingProfiles)
                    }
                )

                SettingsDivider()

                SettingsItem(
                    title = stringResource(string.privacy_settings_title),
                    description = stringResource(string.privacy_settings_summary),
                    iconResId = drawable.ic_offline,
                    onClick = {
                        navigator.navigate(CardinalRoute.OfflineSettings)
                    }
                )

                SettingsDivider()

                SettingsItem(
                    title = stringResource(string.accessibility_settings_title),
                    description = stringResource(string.accessibility_settings_summary),
                    iconResId = drawable.ic_accessiblity_settings,
                    onClick = {
                        navigator.navigate(CardinalRoute.AccessibilitySettings)
                    }
                )

                SettingsDivider()

                SettingsItem(
                    title = stringResource(string.theme_mode_settings_title),
                    description = stringResource(string.theme_mode_settings_help_text),
                    iconResId = drawable.ic_theme_mode,
                    onClick = {
                        navigator.navigate(CardinalRoute.ThemeSettings)
                    }
                )

                SettingsDivider()

                SettingsItem(
                    title = stringResource(string.advanced_settings_title),
                    description = stringResource(string.advanced_settings_summary),
                    iconResId = drawable.ic_settings,
                    onClick = {
                        navigator.navigate(CardinalRoute.AdvancedSettings)
                    }
                )
            }
        }
    }
}
