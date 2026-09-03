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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import earth.maps.cardinal.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FasterRoutePromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun promptAcceptAndDismissActionsInvokeCallbacks() {
        var acceptCount = 0
        var dismissCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val switchRouteText = context.getString(R.string.switch_route)
        val dismissText = context.getString(R.string.dismiss_button)

        composeRule.setContent {
            MaterialTheme {
                FasterRoutePrompt(
                    timeSavingsSeconds = 300,
                    onAccept = { acceptCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeRule.onNodeWithText(switchRouteText)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, acceptCount)
            assertEquals(0, dismissCount)
        }

        composeRule.onNodeWithText(dismissText)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, acceptCount)
            assertEquals(1, dismissCount)
        }
    }
}
