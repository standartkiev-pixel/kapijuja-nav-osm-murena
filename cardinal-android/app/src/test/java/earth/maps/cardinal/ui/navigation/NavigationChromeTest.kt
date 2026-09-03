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

import com.stadiamaps.ferrostar.core.annotation.AnnotationWrapper
import com.stadiamaps.ferrostar.core.annotation.valhalla.ValhallaOSRMExtendedAnnotation
import com.stadiamaps.ferrostar.core.measurement.MeasurementSpeed
import com.stadiamaps.ferrostar.core.measurement.MeasurementSpeedUnit
import earth.maps.cardinal.R
import earth.maps.cardinal.data.AppPreferences
import earth.maps.cardinal.data.UserSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.SpokenInstruction
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.VisualInstructionContent
import java.util.UUID

class NavigationChromeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Valhalla maxspeed annotation maps to displayable speed limit`() {
        val annotation = json.decodeFromString(
            ValhallaOSRMExtendedAnnotation.serializer(),
            """
                {
                    "maxspeed": {
                        "speed": 55.0,
                        "unit": "mph"
                    },
                    "speed": 20.0,
                    "distance": 100.0,
                    "duration": 5.0
                }
            """.trimIndent()
        )

        val speedLimit = AnnotationWrapper(annotation, annotation.speedLimit).speedLimit

        assertNotNull(speedLimit)
        assertEquals(55.0, speedLimit!!.value, 0.0)
        assertEquals(MeasurementSpeedUnit.MilesPerHour, speedLimit.unit)
    }

    @Test
    fun `unknown Valhalla maxspeed annotation does not display a speed limit`() {
        val annotation = json.decodeFromString(
            ValhallaOSRMExtendedAnnotation.serializer(),
            """
                {
                    "maxspeed": {
                        "unknown": true
                    },
                    "speed": null,
                    "distance": null,
                    "duration": null
                }
            """.trimIndent()
        )

        val speedLimit = AnnotationWrapper(annotation, annotation.speedLimit).speedLimit

        assertNull(speedLimit)
    }

    @Test
    fun `speed limit ui formats metric speed limit`() {
        val speedLimit = MeasurementSpeed(50.0, MeasurementSpeedUnit.KilometersPerHour)

        val speedLimitUi = speedLimit.toSpeedLimitUi(AppPreferences.DISTANCE_UNIT_METRIC)

        assertEquals(
            NavigationSpeedUi(label = NavigationSpeedLabel.LIMIT, valueText = "50", unitText = "km/h"),
            speedLimitUi
        )
    }

    @Test
    fun `speed limit ui formats imperial speed limit`() {
        val speedLimit = MeasurementSpeed(55.0, MeasurementSpeedUnit.MilesPerHour)

        val speedLimitUi = speedLimit.toSpeedLimitUi(AppPreferences.DISTANCE_UNIT_IMPERIAL)

        assertEquals(
            NavigationSpeedUi(label = NavigationSpeedLabel.LIMIT, valueText = "55", unitText = "mph"),
            speedLimitUi
        )
    }

    @Test
    fun `current speed ui formats metric speed`() {
        val speed = UserSpeed(metersPerSecond = 10.0, timestampMillis = 1_000L)

        val speedUi = speed.toCurrentSpeedUi(
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC,
            nowMillis = 1_000L
        )

        assertEquals(
            NavigationSpeedUi(label = NavigationSpeedLabel.SPEED, valueText = "36", unitText = "km/h"),
            speedUi
        )
    }

    @Test
    fun `current speed ui formats imperial speed`() {
        val speed = UserSpeed(metersPerSecond = 10.0, timestampMillis = 1_000L)

        val speedUi = speed.toCurrentSpeedUi(
            distanceUnit = AppPreferences.DISTANCE_UNIT_IMPERIAL,
            nowMillis = 1_000L
        )

        assertEquals(
            NavigationSpeedUi(label = NavigationSpeedLabel.SPEED, valueText = "22", unitText = "mph"),
            speedUi
        )
    }

    @Test
    fun `current speed ui hides stale speed`() {
        val speed = UserSpeed(metersPerSecond = 10.0, timestampMillis = 1_000L)

        val speedUi = speed.toCurrentSpeedUi(
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC,
            nowMillis = 1_000L + CURRENT_SPEED_STALE_MILLIS
        )

        assertNull(speedUi)
    }

    @Test
    fun `left-driving first exit roundabout resolves to left icon`() {
        val modifier = resolveRoundaboutModifier(
            modifier = ManeuverModifier.SLIGHT_LEFT,
            roundaboutExitDegrees = 91,
        )

        assertEquals(ManeuverModifier.LEFT, modifier)
    }

    @Test
    fun `left-driving second exit roundabout resolves to straight icon`() {
        val modifier = resolveRoundaboutModifier(
            modifier = ManeuverModifier.SLIGHT_LEFT,
            roundaboutExitDegrees = 180,
        )

        assertEquals(ManeuverModifier.STRAIGHT, modifier)
    }

    @Test
    fun `left-driving third exit roundabout resolves to right icon`() {
        val modifier = resolveRoundaboutModifier(
            modifier = ManeuverModifier.SLIGHT_LEFT,
            roundaboutExitDegrees = 271,
        )

        assertEquals(ManeuverModifier.RIGHT, modifier)
    }

    @Test
    fun `right-driving first exit roundabout resolves to right icon`() {
        val modifier = resolveRoundaboutModifier(
            modifier = ManeuverModifier.SLIGHT_RIGHT,
            roundaboutExitDegrees = 91,
        )

        assertEquals(ManeuverModifier.RIGHT, modifier)
    }

    @Test
    fun `first exit fallback resolves to slight left icon when degrees are missing`() {
        val roundaboutInstruction =
            VisualInstruction(
                primaryContent =
                    VisualInstructionContent(
                        text = "Forest Avenue",
                        maneuverType = uniffi.ferrostar.ManeuverType.ROTARY,
                        maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                        roundaboutExitDegrees = null,
                        laneInfo = null,
                        exitNumbers = emptyList(),
                    ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 0.0,
            )
        val departInstruction =
            VisualInstruction(
                primaryContent =
                    VisualInstructionContent(
                        text = "Central Avenue",
                        maneuverType = uniffi.ferrostar.ManeuverType.DEPART,
                        maneuverModifier = null,
                        roundaboutExitDegrees = null,
                        laneInfo = null,
                        exitNumbers = emptyList(),
                    ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 0.0,
            )
        val departStep =
            RouteStep(
                geometry = listOf(GeographicCoordinate(0.0, 0.0)),
                distance = 1.0,
                duration = 1.0,
                roadName = "Central Avenue",
                exits = emptyList(),
                instruction = "Drive southwest on Central Avenue.",
                visualInstructions = listOf(departInstruction),
                spokenInstructions = emptyList(),
                annotations = emptyList(),
                incidents = emptyList(),
            )
        val step =
            RouteStep(
                geometry = listOf(GeographicCoordinate(0.0, 0.0)),
                distance = 1.0,
                duration = 1.0,
                roadName = "Central Avenue",
                exits = emptyList(),
                instruction = "Drive southwest on Central Avenue.",
                visualInstructions = listOf(roundaboutInstruction),
                spokenInstructions =
                    listOf(
                        SpokenInstruction(
                            text = "Enter Hiranandani Circle and take the 1st exit onto Forest Avenue.",
                            ssml = "",
                            triggerDistanceBeforeManeuver = 0.0,
                            utteranceId = UUID.randomUUID(),
                        )
                    ),
                annotations = emptyList(),
                incidents = emptyList(),
            )

        val matchedStep = listOf(departStep, step).stepForInstruction(roundaboutInstruction)
        val patched = roundaboutInstruction.patchRoundaboutInstructionIcons(matchedStep).primaryContent

        assertEquals(step, matchedStep)
        assertEquals(ManeuverType.ROTARY, patched.maneuverType)
        assertEquals(ManeuverModifier.SLIGHT_LEFT, patched.maneuverModifier)
        assertEquals(listOf("1"), patched.exitNumbers)
        assertEquals(R.drawable.ic_maneuver_roundabout_first_exit_left, patched.cardinalManeuverIcon())
    }

    @Test
    fun `first exit number fallback resolves to slight right icon for counterclockwise roundabout`() {
        val modifier = resolveRoundaboutModifierFromExitNumber(
            modifier = ManeuverModifier.SLIGHT_RIGHT,
            exitNumber = 1,
        )

        assertEquals(ManeuverModifier.SLIGHT_RIGHT, modifier)
    }

    @Test
    fun `first exit roundabout uses Cardinal right-side banner icon`() {
        val instruction =
            VisualInstructionContent(
                text = "Main Street",
                maneuverType = ManeuverType.ROTARY,
                maneuverModifier = ManeuverModifier.SLIGHT_RIGHT,
                roundaboutExitDegrees = null,
                laneInfo = null,
                exitNumbers = listOf("1"),
            )

        assertEquals(R.drawable.ic_maneuver_roundabout_first_exit_right, instruction.cardinalManeuverIcon())
    }

    @Test
    fun `second exit roundabout uses Cardinal left-side banner icon`() {
        val instruction =
            VisualInstructionContent(
                text = "Main Street",
                maneuverType = ManeuverType.ROTARY,
                maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                roundaboutExitDegrees = 180.toUShort(),
                laneInfo = null,
                exitNumbers = listOf("2"),
            )

        assertEquals(R.drawable.ic_maneuver_roundabout_second_exit_left, instruction.cardinalManeuverIcon())
    }

    @Test
    fun `second exit fallback keeps circulation and uses Cardinal icon`() {
        val patched =
            VisualInstruction(
                primaryContent =
                    VisualInstructionContent(
                        text = "Main Street",
                        maneuverType = ManeuverType.ROTARY,
                        maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                        roundaboutExitDegrees = null,
                        laneInfo = null,
                        exitNumbers = emptyList(),
                    ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 0.0,
            )
                .patchRoundaboutInstructionIcons(
                    RouteStep(
                        geometry = listOf(GeographicCoordinate(0.0, 0.0)),
                        distance = 1.0,
                        duration = 1.0,
                        roadName = "Circle Road",
                        exits = emptyList(),
                        instruction = "Take the 2nd exit.",
                        visualInstructions = emptyList(),
                        spokenInstructions =
                            listOf(
                                SpokenInstruction(
                                    text = "At the roundabout, take the 2nd exit onto Main Street.",
                                    ssml = "",
                                    triggerDistanceBeforeManeuver = 0.0,
                                    utteranceId = UUID.randomUUID(),
                                )
                            ),
                        annotations = emptyList(),
                        incidents = emptyList(),
                    )
                )
                .primaryContent

        assertEquals(ManeuverModifier.SLIGHT_LEFT, patched.maneuverModifier)
        assertEquals(listOf("2"), patched.exitNumbers)
        assertEquals(R.drawable.ic_maneuver_roundabout_second_exit_left, patched.cardinalManeuverIcon())
    }

    @Test
    fun `third exit roundabout uses Cardinal right-side banner icon`() {
        val instruction =
            VisualInstructionContent(
                text = "Main Street",
                maneuverType = ManeuverType.ROTARY,
                maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                roundaboutExitDegrees = 271.toUShort(),
                laneInfo = null,
                exitNumbers = listOf("3"),
            )

        assertEquals(R.drawable.ic_maneuver_roundabout_third_exit_right, instruction.cardinalManeuverIcon())
    }

    @Test
    fun `third exit fallback keeps circulation and uses Cardinal icon`() {
        val patched =
            VisualInstruction(
                primaryContent =
                    VisualInstructionContent(
                        text = "Main Street",
                        maneuverType = ManeuverType.ROTARY,
                        maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                        roundaboutExitDegrees = null,
                        laneInfo = null,
                        exitNumbers = emptyList(),
                    ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 0.0,
            )
                .patchRoundaboutInstructionIcons(
                    RouteStep(
                        geometry = listOf(GeographicCoordinate(0.0, 0.0)),
                        distance = 1.0,
                        duration = 1.0,
                        roadName = "Circle Road",
                        exits = emptyList(),
                        instruction = "Take the 3rd exit.",
                        visualInstructions = emptyList(),
                        spokenInstructions =
                            listOf(
                                SpokenInstruction(
                                    text = "At the roundabout, take the 3rd exit onto Main Street.",
                                    ssml = "",
                                    triggerDistanceBeforeManeuver = 0.0,
                                    utteranceId = UUID.randomUUID(),
                                )
                            ),
                        annotations = emptyList(),
                        incidents = emptyList(),
                    )
                )
                .primaryContent

        assertEquals(ManeuverModifier.SLIGHT_LEFT, patched.maneuverModifier)
        assertEquals(listOf("3"), patched.exitNumbers)
        assertEquals(R.drawable.ic_maneuver_roundabout_third_exit_right, patched.cardinalManeuverIcon())
    }

    @Test
    fun `fourth exit roundabout uses Cardinal right-side banner icon`() {
        val instruction =
            VisualInstructionContent(
                text = "Main Street",
                maneuverType = ManeuverType.ROTARY,
                maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                roundaboutExitDegrees = 359.toUShort(),
                laneInfo = null,
                exitNumbers = listOf("4"),
            )

        assertEquals(R.drawable.ic_maneuver_roundabout_fourth_exit_right, instruction.cardinalManeuverIcon())
    }

    @Test
    fun `fourth exit fallback keeps circulation and uses Cardinal icon`() {
        val patched =
            VisualInstruction(
                primaryContent =
                    VisualInstructionContent(
                        text = "Main Street",
                        maneuverType = ManeuverType.ROTARY,
                        maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                        roundaboutExitDegrees = null,
                        laneInfo = null,
                        exitNumbers = emptyList(),
                    ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 0.0,
            )
                .patchRoundaboutInstructionIcons(
                    RouteStep(
                        geometry = listOf(GeographicCoordinate(0.0, 0.0)),
                        distance = 1.0,
                        duration = 1.0,
                        roadName = "Circle Road",
                        exits = emptyList(),
                        instruction = "Take the 4th exit.",
                        visualInstructions = emptyList(),
                        spokenInstructions =
                            listOf(
                                SpokenInstruction(
                                    text = "At the roundabout, take the 4th exit onto Main Street.",
                                    ssml = "",
                                    triggerDistanceBeforeManeuver = 0.0,
                                    utteranceId = UUID.randomUUID(),
                                )
                            ),
                        annotations = emptyList(),
                        incidents = emptyList(),
                    )
                )
                .primaryContent

        assertEquals(ManeuverModifier.SLIGHT_LEFT, patched.maneuverModifier)
        assertEquals(listOf("4"), patched.exitNumbers)
        assertEquals(R.drawable.ic_maneuver_roundabout_fourth_exit_right, patched.cardinalManeuverIcon())
    }
}
