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

import earth.maps.cardinal.R
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.FIRST_EXIT
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.FOURTH_EXIT
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.FULL_CIRCLE_DEGREES
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.HALF_CIRCLE_DEGREES
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.NORMAL_TURN_THRESHOLD
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.SECOND_EXIT
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.SLIGHT_TURN_THRESHOLD
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.STRAIGHT_ANGLE_THRESHOLD
import earth.maps.cardinal.ui.navigation.NavigationUiConstants.THIRD_EXIT
import kotlin.math.abs
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.VisualInstructionContent

private enum class RoundaboutCirculation {
    CLOCKWISE,
    COUNTERCLOCKWISE
}

private data class ManeuverIconOverride(
    val maneuverType: ManeuverType?,
    val maneuverModifier: ManeuverModifier?,
)

internal fun VisualInstruction.patchRoundaboutInstructionIcons(): VisualInstruction =
    patchRoundaboutInstructionIcons(step = null)

internal fun VisualInstruction.patchRoundaboutInstructionIcons(step: RouteStep?): VisualInstruction =
    copy(
        primaryContent = primaryContent.patchRoundaboutInstructionIcon(step),
        secondaryContent = secondaryContent?.patchRoundaboutInstructionIcon(step),
        subContent = subContent?.patchRoundaboutInstructionIcon(step),
    )

internal fun RouteStep.patchRoundaboutInstructionIcons(): RouteStep =
    copy(
        visualInstructions = visualInstructions.map { it.patchRoundaboutInstructionIcons(this) }
    )

internal fun VisualInstructionContent.patchRoundaboutInstructionIcon(
    step: RouteStep? = null
): VisualInstructionContent {
    if (!maneuverType.isRoundaboutLike()) {
        return this
    }

    val exitNumber = exitNumbers.firstExitNumber() ?: step?.firstRoundaboutExitNumber()
    val resolvedExitNumbers = exitNumbers.ifEmpty { exitNumber?.let { listOf(it.toString()) } ?: emptyList() }
    val resolvedIcon =
        resolveRoundaboutIconFromExitNumber(
            maneuverType = maneuverType,
            modifier = maneuverModifier,
            exitNumber = exitNumber
        ) ?: roundaboutExitDegrees?.toInt()?.let { exitDegrees ->
            resolveRoundaboutModifier(maneuverModifier, exitDegrees)?.let { resolvedModifier ->
                ManeuverIconOverride(
                    maneuverType = maneuverType,
                    maneuverModifier = resolvedModifier,
                )
            }
        }
            ?: return this

    if (resolvedIcon.maneuverType == maneuverType &&
        resolvedIcon.maneuverModifier == maneuverModifier &&
        resolvedExitNumbers == exitNumbers) {
        return this
    }

    return copy(
        text = text,
        maneuverType = resolvedIcon.maneuverType,
        maneuverModifier = resolvedIcon.maneuverModifier,
        roundaboutExitDegrees = roundaboutExitDegrees,
        laneInfo = laneInfo,
        exitNumbers = resolvedExitNumbers,
    )
}

internal fun resolveRoundaboutModifier(
    modifier: ManeuverModifier?,
    roundaboutExitDegrees: Int
): ManeuverModifier? {
    val circulation = modifier.roundaboutCirculation() ?: return null
    val signedTurnAngle = circulation.toSignedTurnAngle(roundaboutExitDegrees)

    return when {
        abs(signedTurnAngle) < STRAIGHT_ANGLE_THRESHOLD -> ManeuverModifier.STRAIGHT
        abs(signedTurnAngle) < SLIGHT_TURN_THRESHOLD ->
            if (signedTurnAngle > 0) ManeuverModifier.SLIGHT_RIGHT else ManeuverModifier.SLIGHT_LEFT
        abs(signedTurnAngle) < NORMAL_TURN_THRESHOLD ->
            if (signedTurnAngle > 0) ManeuverModifier.RIGHT else ManeuverModifier.LEFT
        else ->
            if (signedTurnAngle > 0) ManeuverModifier.SHARP_RIGHT else ManeuverModifier.SHARP_LEFT
    }
}

internal fun resolveRoundaboutModifierFromExitNumber(
    modifier: ManeuverModifier?,
    exitNumber: Int?
): ManeuverModifier? {
    return resolveRoundaboutIconFromExitNumber(
        maneuverType = ManeuverType.ROTARY,
        modifier = modifier,
        exitNumber = exitNumber,
    )?.maneuverModifier
}

private fun resolveRoundaboutIconFromExitNumber(
    maneuverType: ManeuverType?,
    modifier: ManeuverModifier?,
    exitNumber: Int?
): ManeuverIconOverride? {
    val circulation = modifier.roundaboutCirculation() ?: return null
    return when (exitNumber) {
        FIRST_EXIT ->
            when (circulation) {
                RoundaboutCirculation.CLOCKWISE ->
                    ManeuverIconOverride(
                        maneuverType = maneuverType ?: ManeuverType.ROTARY,
                        maneuverModifier = ManeuverModifier.SLIGHT_LEFT,
                    )
                RoundaboutCirculation.COUNTERCLOCKWISE ->
                    ManeuverIconOverride(
                        maneuverType = maneuverType ?: ManeuverType.ROTARY,
                        maneuverModifier = ManeuverModifier.SLIGHT_RIGHT,
                    )
            }
        SECOND_EXIT ->
            ManeuverIconOverride(
                maneuverType = maneuverType ?: ManeuverType.ROTARY,
                maneuverModifier = modifier,
            )
        THIRD_EXIT ->
            ManeuverIconOverride(
                maneuverType = maneuverType ?: ManeuverType.ROTARY,
                maneuverModifier = modifier,
            )
        FOURTH_EXIT ->
            ManeuverIconOverride(
                maneuverType = maneuverType ?: ManeuverType.ROTARY,
                maneuverModifier = modifier,
            )
        else -> null
    }
}

internal fun VisualInstructionContent.cardinalManeuverIcon(): Int? {
    val exitNumber = exitNumbers.firstExitNumber()
    if (!maneuverType.isRoundaboutLike()) {
        return null
    }

    return when (exitNumber) {
        FIRST_EXIT ->
            when (maneuverModifier.roundaboutCirculation()) {
                RoundaboutCirculation.CLOCKWISE -> R.drawable.ic_maneuver_roundabout_first_exit_left
                RoundaboutCirculation.COUNTERCLOCKWISE -> R.drawable.ic_maneuver_roundabout_first_exit_right
                null -> null
            }
        SECOND_EXIT ->
            when (maneuverModifier.roundaboutCirculation()) {
                RoundaboutCirculation.CLOCKWISE -> R.drawable.ic_maneuver_roundabout_second_exit_left
                RoundaboutCirculation.COUNTERCLOCKWISE -> R.drawable.ic_maneuver_roundabout_second_exit_right
                null -> null
            }
        THIRD_EXIT ->
            when (maneuverModifier.roundaboutCirculation()) {
                RoundaboutCirculation.CLOCKWISE -> R.drawable.ic_maneuver_roundabout_third_exit_right
                RoundaboutCirculation.COUNTERCLOCKWISE -> R.drawable.ic_maneuver_roundabout_third_exit_left
                null -> null
            }
        FOURTH_EXIT ->
            when (maneuverModifier.roundaboutCirculation()) {
                RoundaboutCirculation.CLOCKWISE -> R.drawable.ic_maneuver_roundabout_fourth_exit_right
                RoundaboutCirculation.COUNTERCLOCKWISE -> R.drawable.ic_maneuver_roundabout_fourth_exit_left
                null -> null
            }
        else -> null
    }
}

private fun ManeuverType?.isRoundaboutLike(): Boolean =
    this == ManeuverType.ROUNDABOUT ||
        this == ManeuverType.ROTARY ||
        this == ManeuverType.ROUNDABOUT_TURN ||
        this == ManeuverType.EXIT_ROUNDABOUT ||
        this == ManeuverType.EXIT_ROTARY

private fun ManeuverModifier?.roundaboutCirculation(): RoundaboutCirculation? =
    when (this) {
        ManeuverModifier.SLIGHT_LEFT,
        ManeuverModifier.LEFT,
        ManeuverModifier.SHARP_LEFT -> RoundaboutCirculation.CLOCKWISE
        ManeuverModifier.SLIGHT_RIGHT,
        ManeuverModifier.RIGHT,
        ManeuverModifier.SHARP_RIGHT -> RoundaboutCirculation.COUNTERCLOCKWISE
        else -> null
    }

private fun RoundaboutCirculation.toSignedTurnAngle(roundaboutExitDegrees: Int): Int {
    val normalizedDegrees = ((roundaboutExitDegrees % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    return when (this) {
        RoundaboutCirculation.CLOCKWISE -> normalizedDegrees - HALF_CIRCLE_DEGREES
        RoundaboutCirculation.COUNTERCLOCKWISE -> HALF_CIRCLE_DEGREES - normalizedDegrees
    }
}

private fun RouteStep.firstRoundaboutExitNumber(): Int? =
    exits.firstExitNumber()
        ?: spokenInstructions.asSequence().mapNotNull { it.text.firstExitNumber() }.firstOrNull()
        ?: instruction.firstExitNumber()

internal fun List<RouteStep>.stepForInstruction(instruction: VisualInstruction?): RouteStep? {
    if (instruction == null) {
        return firstOrNull()
    }

    return firstOrNull { step ->
        step.visualInstructions.any { candidate ->
            candidate.primaryContent.matchesInstruction(instruction.primaryContent)
        }
    } ?: firstOrNull()
}

private fun VisualInstructionContent.matchesInstruction(other: VisualInstructionContent): Boolean =
    text == other.text &&
        maneuverType == other.maneuverType &&
        maneuverModifier == other.maneuverModifier

private fun List<String>.firstExitNumber(): Int? = firstOrNull()?.firstExitNumber()

private fun String.firstExitNumber(): Int? =
    trim().toIntOrNull()
        ?: FIRST_EXIT_NUMBER_REGEX.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

private val FIRST_EXIT_NUMBER_REGEX = Regex("""\b(\d+)(?:st|nd|rd|th)\s+exit\b""", RegexOption.IGNORE_CASE)

private object NavigationUiConstants {
    const val FULL_CIRCLE_DEGREES = 360
    const val HALF_CIRCLE_DEGREES = 180

    const val STRAIGHT_ANGLE_THRESHOLD = 30
    const val SLIGHT_TURN_THRESHOLD = 60
    const val NORMAL_TURN_THRESHOLD = 135

    const val FIRST_EXIT = 1
    const val SECOND_EXIT = 2
    const val THIRD_EXIT = 3
    const val FOURTH_EXIT = 4
}
