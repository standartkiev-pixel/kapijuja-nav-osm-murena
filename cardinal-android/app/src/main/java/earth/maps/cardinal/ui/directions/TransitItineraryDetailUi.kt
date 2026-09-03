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

import earth.maps.cardinal.data.GeoUtils
import earth.maps.cardinal.data.formatDuration
import earth.maps.cardinal.data.formatTime
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Leg
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.transit.TransitPlace
import java.time.Duration
import java.time.Instant

internal class TransitItineraryDetailPresenter(
    private val text: TransitItineraryDetailTextProvider
) {
    fun present(
        itinerary: Itinerary,
        use24HourFormat: Boolean,
        distanceUnit: Int
    ): TransitItineraryDetailUi {
        val timelineItems = itinerary.legs.mapIndexed { index, leg ->
            leg.toTimelineUi(
                index = index,
                nextLeg = itinerary.legs.getOrNull(index + 1),
                isLast = index == itinerary.legs.lastIndex,
                use24HourFormat = use24HourFormat,
                distanceUnit = distanceUnit
            )
        }
        return TransitItineraryDetailUi(
            overview = presentOverview(
                itinerary = itinerary,
                use24HourFormat = use24HourFormat,
                distanceUnit = distanceUnit,
                currentSpeedText = null
            ),
            summaryItems = listOf(TransitSummaryItemUi.Overview) + timelineItems.map { item -> item.summaryItem },
            timelineItems = timelineItems
        )
    }

    fun presentOverview(
        itinerary: Itinerary,
        use24HourFormat: Boolean,
        distanceUnit: Int,
        currentSpeedText: String?
    ): TransitTripOverviewUi {
        val speedSuffix = currentSpeedText?.let { " · $it" }.orEmpty()
        return TransitTripOverviewUi(
            durationText = formatDuration(itinerary.duration),
            timeAndTransfersText = "${itinerary.startTime.formatTime(use24HourFormat)}-${
                itinerary.endTime.formatTime(use24HourFormat)
            } · ${
                text.transferCount(itinerary.transfers)
            }",
            walkingText = text.walkOverview(
                walkingDistanceText = itinerary.walkingDistanceText(distanceUnit),
                speedSuffix = speedSuffix
            )
        )
    }

    private fun Itinerary.walkingDistanceText(distanceUnit: Int): String {
        val walkingMeters = legs
            .filter { leg -> leg.mode == Mode.WALK }
            .sumOf { leg -> leg.distance ?: 0.0 }
        return if (walkingMeters > 0.0) {
            GeoUtils.formatDistance(walkingMeters, distanceUnit)
        } else {
            text.distanceNotAvailable()
        }
    }

    private fun Leg.toTimelineUi(
        index: Int,
        nextLeg: Leg?,
        isLast: Boolean,
        use24HourFormat: Boolean,
        distanceUnit: Int
    ): TransitTimelineLegUi {
        val durationText = formatDuration(duration)
        return TransitTimelineLegUi(
            key = "leg-$index",
            legIndex = index,
            mode = mode,
            routeColor = routeColor,
            routeTextColor = routeTextColor,
            isLast = isLast,
            summaryItem = TransitSummaryItemUi.Leg(
                legIndex = index,
                mode = mode,
                routeColor = routeColor,
                labelText = routeShortName ?: durationText,
                durationText = durationText
            ),
            fromStop = fromTransitPlace.toStopUi(
                time = startTime,
                use24HourFormat = use24HourFormat,
                isDestination = false
            ),
            toStop = toTransitPlace.toStopUi(
                time = endTime,
                use24HourFormat = use24HourFormat,
                isDestination = isLast
            ),
            walkingSegment = walkingSegmentUi(nextLeg, distanceUnit),
            transitSegment = transitSegmentUi(use24HourFormat, distanceUnit),
            alerts = alerts?.mapNotNull { alert -> alert.headerText }.orEmpty()
        )
    }

    private fun TransitPlace.toStopUi(
        time: String,
        use24HourFormat: Boolean,
        isDestination: Boolean
    ): TransitStopUi {
        return TransitStopUi(
            nameText = name.ifBlank {
                if (isDestination) {
                    text.destination()
                } else {
                    text.stopFallback()
                }
            },
            timeText = time.formatTime(use24HourFormat),
            descriptionText = description?.takeIf { description -> description.isNotBlank() },
            platformText = platformText()
        )
    }

    private fun TransitPlace.platformText(): String? {
        val platform = track?.takeIf { it.isNotBlank() }
            ?: scheduledTrack?.takeIf { it.isNotBlank() }
            ?: return null
        return text.platform(platform)
    }

    private fun Leg.walkingSegmentUi(nextLeg: Leg?, distanceUnit: Int): TransitWalkingSegmentUi? {
        if (mode != Mode.WALK) return null
        val distance = distance?.let { meters -> " (${GeoUtils.formatDistance(meters, distanceUnit)})" }.orEmpty()
        return TransitWalkingSegmentUi(
            text = text.walkSegment(
                durationText = formatDuration(duration),
                distanceSuffix = distance,
                waitSuffix = waitDurationText(nextLeg).orEmpty()
            ),
            streetName = steps?.firstOrNull()?.streetName
        )
    }

    private fun Leg.transitSegmentUi(
        use24HourFormat: Boolean,
        distanceUnit: Int
    ): TransitSegmentUi? {
        if (mode == Mode.WALK) return null
        return TransitSegmentUi(
            badgeText = routeShortName ?: text.modeName(mode),
            headsignText = headsign?.let(text::headsign),
            startTimeText = startTime.formatTime(use24HourFormat),
            agencyText = agencyName ?: text.modeName(mode),
            rideSummaryText = rideSummary(distanceUnit),
            status = statusUi()
        )
    }

    private fun Leg.rideSummary(distanceUnit: Int): String {
        val stops = intermediateStops?.size ?: 0
        val distance = distance?.let { meters -> " · ${GeoUtils.formatDistance(meters, distanceUnit)}" }.orEmpty()
        return text.rideSummary(
            stopCount = stops,
            durationText = formatDuration(duration),
            distanceSuffix = distance
        )
    }

    private fun Leg.waitDurationText(nextLeg: Leg?): String? {
        val nextStart = nextLeg?.startTime ?: return null
        val waitSeconds = runCatching {
            Duration.between(Instant.parse(endTime), Instant.parse(nextStart)).seconds
        }.getOrDefault(0L)
        return if (waitSeconds > 0L) {
            text.waitUpTo(formatDuration(waitSeconds.toInt()))
        } else {
            null
        }
    }

    private fun Leg.statusUi(): TransitStatusUi? {
        return when {
            cancelled == true -> TransitStatusUi(
                kind = TransitStatusKind.CANCELLED,
                text = text.cancelled()
            )
            realTime -> realTimeStatusUi()
            else -> null
        }
    }

    private fun Leg.realTimeStatusUi(): TransitStatusUi? {
        val delaySeconds = delaySeconds() ?: return null
        return when {
            delaySeconds == 0L -> TransitStatusUi(
                kind = TransitStatusKind.ON_TIME,
                text = text.onTime()
            )
            delaySeconds > 0L -> TransitStatusUi(
                kind = TransitStatusKind.DELAYED,
                text = text.delayed(formatDuration(delaySeconds.toInt()))
            )
            else -> null
        }
    }

    private fun Leg.delaySeconds(): Long? {
        return runCatching {
            Duration.between(Instant.parse(scheduledStartTime), Instant.parse(startTime)).seconds
        }.getOrNull()
    }
}

internal data class TransitItineraryDetailUi(
    val overview: TransitTripOverviewUi,
    val summaryItems: List<TransitSummaryItemUi>,
    val timelineItems: List<TransitTimelineLegUi>
)

internal data class TransitTripOverviewUi(
    val durationText: String,
    val timeAndTransfersText: String,
    val walkingText: String
)

internal sealed interface TransitSummaryItemUi {
    val key: String

    object Overview : TransitSummaryItemUi {
        override val key = "overview"
    }

    data class Leg(
        val legIndex: Int,
        val mode: Mode,
        val routeColor: String?,
        val labelText: String,
        val durationText: String
    ) : TransitSummaryItemUi {
        override val key = "leg-$legIndex"
    }
}

internal data class TransitTimelineLegUi(
    val key: String,
    val legIndex: Int,
    val mode: Mode,
    val routeColor: String?,
    val routeTextColor: String?,
    val isLast: Boolean,
    val summaryItem: TransitSummaryItemUi.Leg,
    val fromStop: TransitStopUi,
    val toStop: TransitStopUi,
    val walkingSegment: TransitWalkingSegmentUi?,
    val transitSegment: TransitSegmentUi?,
    val alerts: List<String>
)

internal data class TransitStopUi(
    val nameText: String,
    val timeText: String,
    val descriptionText: String?,
    val platformText: String?
)

internal data class TransitWalkingSegmentUi(
    val text: String,
    val streetName: String?
)

internal data class TransitSegmentUi(
    val badgeText: String,
    val headsignText: String?,
    val startTimeText: String,
    val agencyText: String,
    val rideSummaryText: String,
    val status: TransitStatusUi?
)

internal data class TransitStatusUi(
    val kind: TransitStatusKind,
    val text: String
)

internal enum class TransitStatusKind {
    CANCELLED,
    ON_TIME,
    DELAYED
}

internal interface TransitItineraryDetailTextProvider {
    fun distanceNotAvailable(): String
    fun transferCount(count: Int): String
    fun walkOverview(walkingDistanceText: String, speedSuffix: String): String
    fun destination(): String
    fun stopFallback(): String
    fun platform(platform: String): String
    fun walkSegment(durationText: String, distanceSuffix: String, waitSuffix: String): String
    fun headsign(headsign: String): String
    fun rideSummary(stopCount: Int, durationText: String, distanceSuffix: String): String
    fun waitUpTo(durationText: String): String
    fun cancelled(): String
    fun onTime(): String
    fun delayed(durationText: String): String
    fun modeName(mode: Mode): String
}

internal fun List<TransitSummaryItemUi>.indexOfLeg(legIndex: Int): Int =
    indexOfFirst { item -> item is TransitSummaryItemUi.Leg && item.legIndex == legIndex }

internal fun List<TransitTimelineLegUi>.indexOfTimelineLeg(legIndex: Int): Int =
    indexOfFirst { item -> item.legIndex == legIndex }
