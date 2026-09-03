package earth.maps.cardinal.data

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class StringUtilsTest {

    @Test
    fun `levenshteinDistance with identical strings should return 0`() {
        val s1 = "kitten"
        val s2 = "kitten"
        val distance = StringUtils.levenshteinDistance(s1, s2)
        assertThat(distance).isEqualTo(0)
    }

    @Test
    fun `levenshteinDistance with one empty string should return length of the other string`() {
        val s1 = ""
        val s2 = "test"
        val distance1 = StringUtils.levenshteinDistance(s1, s2)
        assertThat(distance1).isEqualTo(4)

        val s3 = "another"
        val s4 = ""
        val distance2 = StringUtils.levenshteinDistance(s3, s4)
        assertThat(distance2).isEqualTo(7)
    }

    @Test
    fun `levenshteinDistance with completely different strings should return length`() {
        val s1 = "abc"
        val s2 = "def"
        val distance = StringUtils.levenshteinDistance(s1, s2)
        assertThat(distance).isEqualTo(3)
    }

    @Test
    fun `levenshteinDistance with one substitution should return 1`() {
        val s1 = "kitten"
        val s2 = "kitcen" // substitution of 't' with 'c'
        val distance = StringUtils.levenshteinDistance(s1, s2)
        assertThat(distance).isEqualTo(1)
    }

    @Test
    fun `levenshteinDistance with known strings should return correct distance`() {
        val s1 = "kitten"
        val s2 = "sitting"
        // Known distance is 3:
        // kitten -> sitten (substitute 'k' with 's')
        // sitten -> sittin (substitute 'e' with 'i')
        // sittin -> sitting (insert 'g' at the end)
        val distance = StringUtils.levenshteinDistance(s1, s2)
        assertThat(distance).isEqualTo(3)
    }

    @Test
    fun `formatDuration should display the largest useful time units`() {
        assertThat(formatDuration(45, Locale.US))
            .isEqualTo("<${formatMeasure(Locale.US, Measure(1, MeasureUnit.MINUTE))}")
        assertThat(formatDuration(45 * 60, Locale.US))
            .isEqualTo(formatMeasures(Locale.US, Measure(45, MeasureUnit.MINUTE)))
        assertThat(formatDuration(126 * 60, Locale.US))
            .isEqualTo(formatMeasures(Locale.US, Measure(2, MeasureUnit.HOUR), Measure(6, MeasureUnit.MINUTE)))
        assertThat(formatDuration((2 * 24 * 60 + 3 * 60 + 15) * 60, Locale.US))
            .isEqualTo(formatMeasures(Locale.US, Measure(2, MeasureUnit.DAY), Measure(3, MeasureUnit.HOUR)))
        assertThat(formatDuration((3 * 7 * 24 * 60 + 2 * 24 * 60 + 30) * 60, Locale.US))
            .isEqualTo(formatMeasures(Locale.US, Measure(3, MeasureUnit.WEEK), Measure(2, MeasureUnit.DAY)))
    }

    @Test
    fun `formatDuration should use the default locale`() {
        val originalLocale = Locale.getDefault()

        try {
            Locale.setDefault(Locale.GERMAN)

            assertThat(formatDuration(126 * 60))
                .isEqualTo(formatMeasures(Locale.GERMAN, Measure(2, MeasureUnit.HOUR), Measure(6, MeasureUnit.MINUTE)))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `formatDuration should localize duration units`() {
        val locales = listOf(Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN, Locale.forLanguageTag("es"))

        locales.forEach { locale ->
            assertThat(formatDuration(45, locale))
                .isEqualTo("<${formatMeasure(locale, Measure(1, MeasureUnit.MINUTE))}")
            assertThat(formatDuration(126 * 60, locale))
                .isEqualTo(formatMeasures(locale, Measure(2, MeasureUnit.HOUR), Measure(6, MeasureUnit.MINUTE)))
            assertThat(formatDuration((3 * 7 * 24 * 60 + 2 * 24 * 60 + 30) * 60, locale))
                .isEqualTo(formatMeasures(locale, Measure(3, MeasureUnit.WEEK), Measure(2, MeasureUnit.DAY)))
        }
    }

    private fun formatMeasure(locale: Locale, measure: Measure): String =
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).format(measure)

    private fun formatMeasures(locale: Locale, vararg measures: Measure): String =
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures)
}
