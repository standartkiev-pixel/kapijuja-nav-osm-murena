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

package earth.maps.cardinal.ui.directions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string
import earth.maps.cardinal.data.Place

@Composable
internal fun SearchPlaceField(
    label: String,
    place: Place?,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit = {},
    onTextFieldFocusChange: (Boolean) -> Unit = {},
    isFocused: Boolean = false,
) {
    var textFieldValue by remember(place) { mutableStateOf(place.searchTextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchPlaceTextInput(
                label = label,
                place = place,
                value = textFieldValue,
                modifier = Modifier.weight(1.0f),
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onTextChange(newValue.text)
                },
                onFocused = {
                    textFieldValue = place.focusedSearchTextFieldValue(textFieldValue)
                    if (place?.isMyLocation == true) {
                        onTextChange("")
                    }
                },
                onClear = {
                    textFieldValue = TextFieldValue()
                    onTextChange("")
                    onCleared()
                },
                onTextFieldFocusChange = onTextFieldFocusChange,
                focusRequester = focusRequester,
            )
        }
    }

    BackHandler(enabled = isFocused) {
        onTextFieldFocusChange(false)
    }

    RequestSearchPlaceFieldFocus(isFocused, focusRequester)
    SyncSearchPlaceFieldText(place, onTextChange) { textFieldValue = it }
}

@Composable
private fun SearchPlaceTextInput(
    label: String,
    place: Place?,
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit,
    onFocused: () -> Unit,
    onClear: () -> Unit,
    onTextFieldFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
) {
    OutlinedTextField(
        value = value,
        singleLine = true,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
                onTextFieldFocusChange(focusState.isFocused)
            }
            .focusRequester(focusRequester),
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(drawable.ic_location_on),
                contentDescription = null
            )
        },
        trailingIcon = {
            SearchPlaceFieldTrailingIcon(
                showClear = place != null,
                onClear = onClear,
            )
        },
        placeholder = {
            Text(stringResource(string.enter_a_place))
        },
        readOnly = false,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    )
}

@Composable
private fun SearchPlaceFieldTrailingIcon(
    showClear: Boolean,
    onClear: () -> Unit,
) {
    if (showClear) {
        IconButton(onClick = onClear) {
            Icon(
                painter = painterResource(drawable.ic_close),
                contentDescription = stringResource(string.content_description_clear_search)
            )
        }
    }
}

@Composable
private fun RequestSearchPlaceFieldFocus(
    isFocused: Boolean,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun SyncSearchPlaceFieldText(
    place: Place?,
    onTextChange: (String) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
) {
    LaunchedEffect(place) {
        onValueChange(place.searchTextFieldValue())
        if (place?.isMyLocation == true) {
            onTextChange("")
        }
    }
}

private fun Place?.searchTextFieldValue(): TextFieldValue {
    val text = if (this?.isMyLocation == true) "" else this?.name.orEmpty()
    return TextFieldValue(text = text, selection = TextRange(text.length))
}

private fun Place?.focusedSearchTextFieldValue(currentValue: TextFieldValue): TextFieldValue {
    return if (this?.isMyLocation == true) {
        TextFieldValue()
    } else {
        currentValue.copy(selection = TextRange(currentValue.text.length))
    }
}
