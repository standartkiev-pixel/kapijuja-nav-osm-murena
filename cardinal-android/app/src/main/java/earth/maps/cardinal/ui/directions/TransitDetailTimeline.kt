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

package earth.maps.cardinal.ui.directions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.data.parseRouteColor
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.ui.theme.AppTheme

@Composable
internal fun TransitDetailTimeline(
    timelineItems: List<TransitTimelineLegUi>,
    activeLegIndex: Int,
    listState: LazyListState,
    onLegClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = timelineItems,
            key = { item -> item.key }
        ) { item ->
            JourneyTimelineItem(
                item = item,
                isActive = item.legIndex == activeLegIndex,
                onClick = { onLegClick(item.legIndex) }
            )
        }
    }
}

@Composable
private fun JourneyTimelineItem(
    item: TransitTimelineLegUi,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val routeColor = item.routeTimelineColor()
    val indicatorWidth by animateDpAsState(
        targetValue = if (isActive) 6.dp else 0.dp,
        label = "Transit active item indicator"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isActive) routeColor else Color.Transparent,
        label = "Transit active item indicator color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TimelineStop(
                stop = item.fromStop,
                mode = item.mode,
                routeColor = routeColor,
                showTopConnector = item.legIndex > 0
            )

            TimelineSegment(
                item = item,
                routeColor = routeColor,
                onClick = onClick
            )

            TimelineStop(
                stop = item.toStop,
                mode = item.mode,
                routeColor = routeColor,
                showTopConnector = true,
                isDestination = item.isLast
            )

            if (!item.isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, bottom = dimensionResource(dimen.padding_minor)),
                    thickness = DividerDefaults.Thickness,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .fillMaxHeight()
                .background(indicatorColor, RoundedCornerShape(50))
        )
    }
}

@Composable
private fun TimelineStop(
    stop: TransitStopUi,
    mode: Mode,
    routeColor: Color,
    showTopConnector: Boolean,
    isDestination: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(dimen.padding_minor)),
        verticalAlignment = Alignment.Top
    ) {
        StopMarkerColumn(
            color = routeColor,
            icon = mode.transitIcon(),
            showTopConnector = showTopConnector,
            isDestination = isDestination,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensionResource(dimen.padding_minor))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = stop.nameText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stop.timeText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = dimensionResource(dimen.padding_minor))
                )
            }

            stop.descriptionText?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            stop.platformText?.let { platform ->
                Text(
                    text = platform,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StopMarkerColumn(
    color: Color,
    icon: Int,
    showTopConnector: Boolean,
    isDestination: Boolean
) {
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showTopConnector) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(12.dp)
                    .background(color)
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .size(if (isDestination) 20.dp else 34.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(3.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!isDestination) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TimelineSegment(
    item: TransitTimelineLegUi,
    routeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
    ) {
        SegmentRail(
            color = routeColor,
            dotted = item.mode == Mode.WALK,
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensionResource(dimen.padding_minor), bottom = dimensionResource(dimen.padding_minor)),
            verticalArrangement = Arrangement.Center
        ) {
            item.walkingSegment?.let { segment ->
                WalkingSegmentText(segment = segment)
            }
            item.transitSegment?.let { segment ->
                TransitSegmentText(
                    item = item,
                    segment = segment,
                    color = routeColor
                )
            }
        }
    }
}

@Composable
private fun SegmentRail(color: Color, dotted: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            if (dotted) {
                val radius = 4.dp.toPx()
                val spacing = 16.dp.toPx()
                var y = radius
                while (y < size.height - radius) {
                    drawCircle(color = color, radius = radius, center = Offset(centerX, y))
                    y += spacing
                }
            } else {
                drawLine(
                    color = color,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun WalkingSegmentText(segment: TransitWalkingSegmentUi) {
    Text(
        text = segment.text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
    )

    segment.streetName?.let { streetName ->
        Text(
            text = streetName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransitSegmentText(
    item: TransitTimelineLegUi,
    segment: TransitSegmentUi,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RouteBadge(item = item, segment = segment, color = color)

        segment.headsignText?.let { headsign ->
            Text(
                text = headsign,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = segment.startTimeText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }

    Text(
        text = segment.agencyText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    Text(
        text = segment.rideSummaryText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TransitStatus(status = segment.status)

    LegAlerts(alerts = item.alerts)
}

@Composable
private fun RouteBadge(item: TransitTimelineLegUi, segment: TransitSegmentUi, color: Color) {
    val textColor = parseRouteColor(item.routeTextColor) ?: MaterialTheme.colorScheme.onPrimary
    Surface(
        color = color,
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(item.mode.transitIcon()),
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = segment.badgeText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TransitStatus(status: TransitStatusUi?) {
    status ?: return
    when (status.kind) {
        TransitStatusKind.CANCELLED -> Text(
            text = status.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )

        TransitStatusKind.ON_TIME -> Text(
            text = status.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Medium
        )

        TransitStatusKind.DELAYED -> Text(
            text = status.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LegAlerts(alerts: List<String>) {
    alerts.forEach { alert ->
        Text(
            text = alert,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(
    name = "Transit timeline large font with alerts",
    widthDp = 360,
    heightDp = 640,
    fontScale = 1.6f,
    showBackground = true
)
@Composable
private fun TransitDetailTimelineLargeFontPreview() {
    AppTheme {
        Surface {
            TransitDetailTimeline(
                timelineItems = previewTransitTimelineItems(),
                activeLegIndex = 1,
                listState = rememberLazyListState(),
                onLegClick = {}
            )
        }
    }
}

private fun previewTransitTimelineItems(): List<TransitTimelineLegUi> =
    listOf(
        previewWalkingTimelineItem(),
        previewTransitTimelineItem()
    )

private fun previewWalkingTimelineItem(): TransitTimelineLegUi =
    TransitTimelineLegUi(
        key = "preview-walk",
        legIndex = 0,
        mode = Mode.WALK,
        routeColor = null,
        routeTextColor = null,
        isLast = false,
        summaryItem = TransitSummaryItemUi.Leg(
            legIndex = 0,
            mode = Mode.WALK,
            routeColor = null,
            labelText = "Walk",
            durationText = "8 min"
        ),
        fromStop = TransitStopUi(
            nameText = "Current location",
            timeText = "8:05 AM",
            descriptionText = null,
            platformText = null
        ),
        toStop = TransitStopUi(
            nameText = "Central Station Entrance",
            timeText = "8:13 AM",
            descriptionText = "Enter via north concourse",
            platformText = null
        ),
        walkingSegment = TransitWalkingSegmentUi(
            text = "Walk 8 min (0.4 mi)",
            streetName = "Market Street and a very long pedestrian arcade name"
        ),
        transitSegment = null,
        alerts = emptyList()
    )

private fun previewTransitTimelineItem(): TransitTimelineLegUi =
    TransitTimelineLegUi(
        key = "preview-bus",
        legIndex = 1,
        mode = Mode.BUS,
        routeColor = "1A73E8",
        routeTextColor = "FFFFFF",
        isLast = true,
        summaryItem = TransitSummaryItemUi.Leg(
            legIndex = 1,
            mode = Mode.BUS,
            routeColor = "1A73E8",
            labelText = "Bus 38",
            durationText = "24 min"
        ),
        fromStop = TransitStopUi(
            nameText = "Central Station Bay 12",
            timeText = "8:18 AM",
            descriptionText = "Board toward Waterfront via Downtown",
            platformText = "Platform 12"
        ),
        toStop = TransitStopUi(
            nameText = "Waterfront Transit Center",
            timeText = "8:42 AM",
            descriptionText = "Exit near Ferry Building",
            platformText = "Stop C"
        ),
        walkingSegment = null,
        transitSegment = TransitSegmentUi(
            badgeText = "38",
            headsignText = "Waterfront via Downtown",
            startTimeText = "8:18 AM",
            agencyText = "Cardinal Transit Authority",
            rideSummaryText = "Ride 12 stops (24 min)",
            status = TransitStatusUi(
                kind = TransitStatusKind.ON_TIME,
                text = "On time"
            )
        ),
        alerts = listOf(
            "Service alert: expect crowding near Central Station during the morning commute.",
            "Stop change: use Bay 12 today because construction is blocking the regular bay.",
            "Accessibility notice: elevator access may require extra time at Waterfront Transit Center."
        )
    )
