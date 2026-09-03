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

package earth.maps.cardinal.data

import androidx.compose.ui.graphics.Color

/**
 * Desaturates a color by reducing its saturation by the specified amount.
 * 
 * @param amount The amount to desaturate, where 0.0f means no change and 1.0f means completely grayscale.
 *               Values outside 0.0f..1.0f will be clamped to this range.
 * @return A new Color with reduced saturation.
 */
fun Color.desaturate(amount: Float): Color {
    val clampedAmount = amount.coerceIn(0f, 1f)
    
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    
    // If the color is already grayscale, no desaturation needed
    if (max == min) {
        return this
    }
    
    val delta = max - min
    val l = (max + min) / 2f
    
    // Calculate saturation
    val s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
    
    // Calculate hue
    val h = when (max) {
        r -> 60 * (g - b) / delta
        g -> 60 * (2 + (b - r) / delta)
        b -> 60 * (4 + (r - g) / delta)
        else -> 0f
    }
    val hue = if (h < 0) h + 360f else h
    
    // Apply desaturation
    val newSaturation = s * (1f - clampedAmount)
    
    // Convert back to RGB
    return hslToRgb(hue, newSaturation, l, alpha)
}

/**
 * Converts HSL color values to RGB.
 * 
 * @param h Hue in degrees (0-360)
 * @param s Saturation (0-1)
 * @param l Lightness (0-1)
 * @param a Alpha (0-1)
 * @return Color in RGB space
 */
private fun hslToRgb(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    if (s == 0f) {
        // Grayscale
        return Color(l, l, l, a)
    }
    
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hPrime = h / 60f
    val x = c * (1f - kotlin.math.abs(hPrime % 2f - 1f))
    val m = l - c / 2f
    
    val (rPrime, gPrime, bPrime) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    
    return Color(
        red = rPrime + m,
        green = gPrime + m,
        blue = bPrime + m,
        alpha = a
    )
}
