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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string
import earth.maps.cardinal.ui.core.TOOLBAR_HEIGHT_DP

/**
 * Common preference option component with radio buttons
 */
@Composable
fun <T> PreferenceOption(
    selectedValue: T,
    options: List<Pair<T, Int>>,
    onOptionSelected: (T) -> Unit
) {
    Column {
        options.forEach { (value, labelResId) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOptionSelected(value)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedValue == value,
                    onClick = {
                        onOptionSelected(value)
                    }
                )
                Text(
                    text = stringResource(labelResId),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Common settings screen scaffold with top bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenScaffold(
    title: String,
    onDismiss: (() -> Unit)? = null,
    showCloseButton: Boolean = false,
    content: @Composable (paddingValues: PaddingValues) -> Unit
) {
    val snackBarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (showCloseButton) {
                        Spacer(modifier = Modifier.fillMaxWidth())
                        IconButton(onClick = {
                            onDismiss?.invoke()
                        }) {
                            Icon(
                                painter = painterResource(drawable.ic_close),
                                contentDescription = stringResource(string.close)
                            )
                        }
                    }
                }
            })
        },
        content = content
    )
}

/**
 * Common settings item with title, description, and optional icon
 */
@Composable
fun SettingsItem(
    title: String,
    description: String,
    iconResId: Int? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { modifier ->
                if (onClick != null) {
                    modifier.clickable { onClick() }
                } else {
                    modifier
                }
            }
            .padding(
                horizontal = dimensionResource(dimen.padding),
                vertical = dimensionResource(dimen.padding_minor)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (iconResId != null) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null
                )
            }
        }

        content?.invoke()
    }
}

/**
 * Common settings divider
 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = DividerDefaults.Thickness,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Common switch setting component
 */
@Composable
fun SwitchSetting(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabledText: String = stringResource(string.enabled),
    disabledText: String = stringResource(string.disabled)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(dimen.padding),
                vertical = dimensionResource(dimen.padding_minor)
            )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isChecked) enabledText else disabledText,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * Common switch setting with state management
 */
@Composable
fun StatefulSwitchSetting(
    title: String,
    description: String,
    stateFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onStateChanged: (Boolean) -> Unit,
    enabledText: String = stringResource(string.enabled),
    disabledText: String = stringResource(string.disabled)
) {
    val currentState by stateFlow.collectAsState()
    var isChecked by remember { mutableStateOf(currentState) }

    // Update selected state when preference changes from outside
    LaunchedEffect(currentState) {
        isChecked = currentState
    }

    SwitchSetting(
        title = title,
        description = description,
        isChecked = isChecked,
        onCheckedChange = { newValue ->
            isChecked = newValue
            onStateChanged(newValue)
        },
        enabledText = enabledText,
        disabledText = disabledText
    )
}

/**
 * Common scrollable content wrapper for settings screens
 */
@Composable
fun SettingsScrollableContent(
    content: @Composable () -> Unit,
) {
    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            content()

            // Add bottom padding to ensure proper spacing
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOOLBAR_HEIGHT_DP)
            )
        }
    }
}
