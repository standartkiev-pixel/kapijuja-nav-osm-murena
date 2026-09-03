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

package earth.maps.cardinal.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.composeui.views.components.speedlimit.SignageStyle
import com.stadiamaps.ferrostar.composeui.views.components.speedlimit.SpeedLimitView
import com.stadiamaps.ferrostar.core.measurement.MeasurementSpeed
import earth.maps.cardinal.R

@Composable
fun CurrentSpeedDisplay(
    currentSpeed: NavigationSpeedUi,
    modifier: Modifier = Modifier,
    showBackground: Boolean = false,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
    unitStyle: TextStyle = MaterialTheme.typography.labelSmall,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val shape = RoundedCornerShape(50)
    val backgroundModifier = if (showBackground) {
        Modifier
            .background(backgroundColor, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    } else {
        Modifier
    }
    val contentDescription = stringResource(
        R.string.current_speed_content_description,
        currentSpeed.displayText
    )
    val labelText = currentSpeed.label.displayText()

    Column(
        modifier = modifier
            .then(backgroundModifier)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = labelText,
            style = labelStyle.copy(color = contentColor, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Text(
            text = currentSpeed.valueText,
            style = valueStyle.copy(color = contentColor),
            maxLines = 1
        )
        Text(
            text = currentSpeed.unitText,
            style = unitStyle.copy(color = contentColor),
            maxLines = 1
        )
    }
}

@Composable
fun CurrentSpeedSign(
    speed: NavigationSpeedUi,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val labelText = speed.label.displayText()

    Box(
        modifier = modifier
            .height(76.dp)
            .width(64.dp)
            .shadow(4.dp, shape)
            .background(Color.White, shape)
            .border(width = 2.dp, color = Color.Black, shape = shape)
            .padding(6.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = labelText,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = speed.valueText,
                fontSize = when {
                    speed.valueText.length > 3 -> 18.sp
                    speed.valueText.length > 2 -> 22.sp
                    else -> 30.sp
                },
                lineHeight = when {
                    speed.valueText.length > 3 -> 20.sp
                    speed.valueText.length > 2 -> 24.sp
                    else -> 32.sp
                },
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = speed.unitText,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NavigationSpeedLabel.displayText(): String {
    return when (this) {
        NavigationSpeedLabel.SPEED -> stringResource(R.string.current_speed_label)
        NavigationSpeedLabel.LIMIT -> stringResource(R.string.speed_limit_label)
    }
}

@Composable
fun SpeedLimitSign(
    speedLimit: MeasurementSpeed,
    signageStyle: SignageStyle,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.TopCenter
    ) {
        SpeedLimitView(
            speedLimit = speedLimit,
            signageStyle = signageStyle
        )
        if (signageStyle == SignageStyle.ViennaConvention) {
            Text(
                text = stringResource(R.string.speed_limit_label),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1
            )
        }
    }
}
