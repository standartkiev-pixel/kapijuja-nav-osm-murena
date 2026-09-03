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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.data.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CurrentSpeedNavigationOverlayUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offlineWarning_isDisplayed_whenOfflineNavigationActive() {
        setOverlayContent(showOfflineWarning = true)

        composeRule
            .onNodeWithTag(NAVIGATION_OFFLINE_WARNING_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun offlineWarning_isHidden_whenInternetConnected() {
        setOverlayContent(showOfflineWarning = false)

        composeRule
            .onAllNodesWithTag(NAVIGATION_OFFLINE_WARNING_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun offlineWarning_isAboveRoadNamePill() {
        setOverlayContent(showOfflineWarning = true)

        val warningBounds = composeRule
            .onNodeWithTag(NAVIGATION_OFFLINE_WARNING_TEST_TAG)
            .getUnclippedBoundsInRoot()
        val roadNameBounds = composeRule
            .onNodeWithTag(ROAD_NAME_ANCHOR_TAG)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Offline warning should sit above the road-name pill.",
            warningBounds.bottom <= roadNameBounds.top
        )
    }

    @Test
    fun offlineWarning_doesNotOverlapRightNavigationControls() {
        setOverlayContent(showOfflineWarning = true)

        val warningBounds = composeRule
            .onNodeWithTag(NAVIGATION_OFFLINE_WARNING_TEST_TAG)
            .getUnclippedBoundsInRoot()
        val topRightControlBounds = composeRule
            .onNodeWithTag(TOP_RIGHT_CONTROL_ANCHOR_TAG)
            .getUnclippedBoundsInRoot()

        assertFalse(
            "Offline warning should not overlap the right-side navigation controls.",
            warningBounds.overlaps(topRightControlBounds)
        )
    }

    private fun setOverlayContent(showOfflineWarning: Boolean) {
        val portraitConfiguration = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides portraitConfiguration) {
                MaterialTheme {
                    Box(
                        modifier = Modifier.size(
                            width = PHONE_WIDTH,
                            height = PHONE_HEIGHT
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = INSTRUCTION_HEIGHT + GRID_SPACING,
                                    end = GRID_SPACING
                                )
                                .size(RIGHT_CONTROL_SIZE)
                                .testTag(TOP_RIGHT_CONTROL_ANCHOR_TAG)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = PROGRESS_HEIGHT + GRID_SPACING)
                                .size(
                                    width = ROAD_NAME_WIDTH,
                                    height = ROAD_NAME_HEIGHT
                                )
                                .testTag(ROAD_NAME_ANCHOR_TAG)
                        )
                        CurrentSpeedNavigationOverlay(
                            modifier = Modifier.fillMaxSize(),
                            currentSpeed = NavigationSpeedUi(
                                label = NavigationSpeedLabel.SPEED,
                                valueText = "25",
                                unitText = "mph"
                            ),
                            speedLimit = null,
                            distanceUnit = AppPreferences.DISTANCE_UNIT_IMPERIAL,
                            instructionHeight = INSTRUCTION_HEIGHT,
                            progressHeight = PROGRESS_HEIGHT,
                            showOfflineWarning = showOfflineWarning
                        )
                    }
                }
            }
        }
    }

    private fun DpRect.overlaps(other: DpRect): Boolean {
        return left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
    }

    private companion object {
        private val PHONE_WIDTH = 411.dp
        private val PHONE_HEIGHT = 891.dp
        private val INSTRUCTION_HEIGHT = 220.dp
        private val PROGRESS_HEIGHT = 96.dp
        private val GRID_SPACING = 16.dp
        private val RIGHT_CONTROL_SIZE = 64.dp
        private val ROAD_NAME_WIDTH = 220.dp
        private val ROAD_NAME_HEIGHT = 56.dp
        private const val ROAD_NAME_ANCHOR_TAG = "navigation_road_name_anchor"
        private const val TOP_RIGHT_CONTROL_ANCHOR_TAG = "navigation_top_right_control_anchor"
    }
}
