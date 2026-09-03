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

package earth.maps.cardinal.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `light theme never uses dark theme`() {
        assertFalse(ThemeMode.LIGHT.shouldUseDarkTheme(systemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.shouldUseDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun `dark theme always uses dark theme`() {
        assertTrue(ThemeMode.DARK.shouldUseDarkTheme(systemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.shouldUseDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun `system theme follows system dark theme setting`() {
        assertFalse(ThemeMode.SYSTEM.shouldUseDarkTheme(systemInDarkTheme = false))
        assertTrue(ThemeMode.SYSTEM.shouldUseDarkTheme(systemInDarkTheme = true))
    }
}
