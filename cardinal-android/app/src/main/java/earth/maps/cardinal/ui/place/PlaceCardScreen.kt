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

package earth.maps.cardinal.ui.place

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ch.poole.openinghoursparser.OpeningHoursParseException
import ch.poole.openinghoursparser.OpeningHoursParser
import ch.poole.openinghoursparser.Rule
import ch.poole.openinghoursparser.WeekDayRange
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string
import earth.maps.cardinal.data.AddressFormatter
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.format
import earth.maps.cardinal.data.formatTime
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

// Data class to hold opening hours information for each day
data class DayOpeningHours(
    val dayOfWeek: Int,
    val dayName: String,
    val timeRanges: List<String>,
    val isToday: Boolean
)

@Composable
fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY.ordinal -> stringResource(string.day_monday)
        DayOfWeek.TUESDAY.ordinal -> stringResource(string.day_tuesday)
        DayOfWeek.WEDNESDAY.ordinal -> stringResource(string.day_wednesday)
        DayOfWeek.THURSDAY.ordinal -> stringResource(string.day_thursday)
        DayOfWeek.FRIDAY.ordinal -> stringResource(string.day_friday)
        DayOfWeek.SATURDAY.ordinal -> stringResource(string.day_saturday)
        DayOfWeek.SUNDAY.ordinal -> stringResource(string.day_sunday)
        else -> "Unknown day"
    }
}

// Helper function to format minutes to time string
fun formatMinutesToTime(minutes: Int, use24HourFormat: Boolean): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (use24HourFormat) {
        String.format(Locale.getDefault(), "%02d:%02d", hours, mins)
    } else {
        val displayHour = if (hours == 0) 12 else if (hours > 12) hours - 12 else hours
        val amPm = if (hours >= 12) "PM" else "AM"
        String.format(Locale.getDefault(), "%d:%02d %s", displayHour, mins, amPm)
    }
}

// Helper function to get opening hours for a specific day
fun getOpeningHoursForDay(
    rules: List<ch.poole.openinghoursparser.Rule>,
    dayOfWeek: Int,
    use24HourFormat: Boolean
): List<String> {
    val timeRanges = mutableListOf<String>()

    for (rule in rules) {
        val days = rule.days
        val times = rule.times
        if (days == null || times == null) {
            continue
        }

        for (dayRule in days) {
            if (weekdayRangeIncludesDay(dayRule, dayOfWeek)) {
                // Collect all time ranges for this day
                for (timeRule in times) {
                    val startTime = formatMinutesToTime(timeRule.start, use24HourFormat)
                    val endTime = formatMinutesToTime(timeRule.end, use24HourFormat)
                    timeRanges.add("$startTime - $endTime")
                }
            }
        }
    }

    return timeRanges.distinct()
}

// Helper function to get opening hours for the next 7 days
@Composable
fun getOpeningHoursForNext7Days(
    openingHours: String,
    now: LocalDateTime,
    use24HourFormat: Boolean
): List<DayOpeningHours> {
    val parser =
        OpeningHoursParser(ByteArrayInputStream(openingHours.toByteArray(charset = Charsets.UTF_8)))
    val rules = try {
        parser.rules(false, false)
    } catch (e: OpeningHoursParseException) {
        Log.e("PlaceCardScreen", "Failed to parse opening hours", e)
        return emptyList()
    }

    val dayOpeningHours = mutableListOf<DayOpeningHours>()
    val today = now.dayOfWeek

    // Get opening hours for today and next 6 days
    for (i in 0..6) {
        val targetDay = (today.ordinal + i) % 7
        val dayName = if (i == 0) stringResource(string.day_today) else getDayName(targetDay)
        val timeRanges = getOpeningHoursForDay(rules, targetDay, use24HourFormat)

        dayOpeningHours.add(
            DayOpeningHours(
                dayOfWeek = targetDay,
                dayName = dayName,
                timeRanges = timeRanges,
                isToday = i == 0
            )
        )
    }

    return dayOpeningHours
}

fun ordinalInRange(ord: Int, start: Int, end: Int): Boolean {
    return ord >= start && ord <= end
}

fun weekdayRangeIncludesDay(range: WeekDayRange, day: Int): Boolean {
    if (range.startDay != null && range.startDay.ordinal == day) {
        return true
    } else if (range.startDay == null || range.endDay == null) {
        return false
    }
    return if (range.endDay < range.startDay) {
        ordinalInRange(day, 0, range.endDay.ordinal) || ordinalInRange(day, range.startDay.ordinal, 6)
    } else {
        ordinalInRange(day, range.startDay.ordinal, range.endDay.ordinal)
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun ExpandableOpeningHours(
    place: Place,
    now: LocalDateTime,
    timeZone: TimeZone,
    use24HourFormat: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val openingHoursData = place.openingHours?.let { openingHours ->
        getOpeningHoursForNext7Days(openingHours, now, use24HourFormat)
    } ?: return

    // Get current status for collapsed view
    val currentStatus = getCurrentOpeningStatus(place, now, timeZone, use24HourFormat)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with current status and expand/collapse icon
            OpeningHoursHeader(expanded, currentStatus) {
                expanded = it
            }

            // Expanded content with table
            if (expanded && openingHoursData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                // Table header
                OpeningHoursTableHeader()

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Table rows for each day
                openingHoursData.forEach { dayHours ->
                    OpeningHoursTableRow(dayHours)

                    if (dayHours != openingHoursData.last()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpeningHoursTableRow(dayHours: DayOpeningHours) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayHours.dayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (dayHours.isToday) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (dayHours.timeRanges.isEmpty()) {
                stringResource(string.opening_hours_closed_all_day)
            } else {
                dayHours.timeRanges.joinToString(", ")
            },
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OpeningHoursTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.opening_hours_day),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(string.opening_hours_hours),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OpeningHoursHeader(
    expanded: Boolean,
    currentStatus: OpeningStatusDisplay?,
    onExpandChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandChanged(!expanded) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(string.opening_hours_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            currentStatus?.let { status ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status.statusText,
                        color = status.statusColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    status.nextTimeText?.let { nextTime ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = nextTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Icon(
            painter = painterResource(
                drawable.ic_arrow_down
            ),
            contentDescription = stringResource(
                if (expanded) string.content_description_collapse_opening_hours
                else string.content_description_expand_opening_hours
            ),
            modifier = Modifier.size(24.dp)
        )
    }
}

data class OpeningStatusDisplay(
    val statusText: String,
    val statusColor: Color,
    val nextTimeText: String?
)

@OptIn(ExperimentalTime::class)
@Composable
fun getCurrentOpeningStatus(
    place: Place,
    now: LocalDateTime,
    timeZone: TimeZone,
    use24HourFormat: Boolean
): OpeningStatusDisplay? {
    place.openingHours?.let { openingHours ->
        val today = now.dayOfWeek
        val parser =
            OpeningHoursParser(ByteArrayInputStream(openingHours.toByteArray(charset = Charsets.UTF_8)))
        val rules = try {
            parser.rules(false, false)
        } catch (e: OpeningHoursParseException) {
            Log.e("PlaceCardScreen", "Failed to parse opening hours", e)
            return null
        }

        val timeOfDay = now.time
        val minutesFromMidnight = timeOfDay.minute + timeOfDay.hour * 60

        val openingStatus = processOpeningHoursRules(rules, today, minutesFromMidnight)

        val openingInstantToday = openingStatus.openingTimeToday?.let { openingTime ->
            now.date.atTime(0, 0).toInstant(timeZone = timeZone).plus(openingTime.minutes)
        }
        val closingInstantToday = openingStatus.closingTimeToday?.let { closingTime ->
            now.date.atTime(0, 0).toInstant(timeZone = timeZone).plus(closingTime.minutes)
        }

        return if (openingStatus.isOpen) {
            OpeningStatusDisplay(
                statusText = stringResource(string.opening_hours_open),
                statusColor = Color.Green,
                nextTimeText = closingInstantToday?.let { closingInstant ->
                    stringResource(
                        string.opening_hours_closes_at,
                        closingInstant.toString().formatTime(use24HourFormat)
                    )
                }
            )
        } else {
            OpeningStatusDisplay(
                statusText = stringResource(string.opening_hours_closed),
                statusColor = Color.Red,
                nextTimeText = openingInstantToday?.let { openingInstant ->
                    stringResource(
                        string.opening_hours_opens_at,
                        openingInstant.toString().formatTime(use24HourFormat)
                    )
                }
            )
        }
    }
    return null
}

data class OpeningStatus(
    val isOpen: Boolean,
    val closingTimeToday: Int?,
    val openingTimeToday: Int?,

)

@Suppress("CognitiveComplexMethod")
private fun processOpeningHoursRules(
    rules: List<Rule>,
    today: DayOfWeek,
    minutesFromMidnight: Int,
): OpeningStatus {
    var isOpen = false
    var closingTimeToday: Int? = null
    var openingTimeToday: Int? = null
    for (rule in rules) {
        val days = rule.days
        val times = rule.times
        if (days == null || times == null) {
            continue
        }
        for (dayRule in days) {
            if (weekdayRangeIncludesDay(dayRule, today.ordinal)) {
                for (timeRule in times) {
                    if (timeRule.start < minutesFromMidnight && timeRule.end > minutesFromMidnight) {
                        isOpen = true
                        if (closingTimeToday == null || timeRule.end < closingTimeToday) {
                            closingTimeToday = timeRule.end
                        }
                    } else if (openingTimeToday == null || timeRule.start < openingTimeToday) {
                        openingTimeToday = timeRule.start
                    }
                }
            }
        }
    }
    return OpeningStatus(
        isOpen,
        closingTimeToday,
        openingTimeToday,
    )
}

@OptIn(ExperimentalTime::class)
@Composable
fun PlaceCardScreen(
    place: Place,
    onBack: () -> Unit,
    onGetDirections: (Place) -> Unit,
    viewModel: PlaceCardViewModel,
    appPreferences: AppPreferenceRepository,
    onPeekHeightChange: (dp: Dp) -> Unit
) {
    val density = LocalDensity.current
    val addressFormatter = remember { AddressFormatter() }

    val use24HourFormat by appPreferences.use24HourFormat.collectAsState()

    // Check if place is saved when screen is opened
    LaunchedEffect(place) {
        viewModel.checkIfPlaceIsSaved(place)
    }

    BackHandler {
        onBack()
    }

    // Use the loaded place from viewModel if available
    val displayedPlace = viewModel.place.value ?: place

    // State for unsave confirmation dialog
    var showUnsaveConfirmationDialog by remember { mutableStateOf(false) }

    // Place details content
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimensionResource(dimen.padding),
                    end = dimensionResource(dimen.padding),
                )
                .verticalScroll(rememberScrollState())
                .onGloballyPositioned { coordinates ->
                    val heightInDp = with(density) { coordinates.size.height.toDp() }
                    onPeekHeightChange(heightInDp)
                },
        ) {
            PlaceHeader(displayedPlace)
            PlaceAddress(displayedPlace, addressFormatter)
            ExpandableOpeningHours(
                displayedPlace,
                Clock.System.now().toLocalDateTime(timeZone = TimeZone.currentSystemDefault()),
                TimeZone.currentSystemDefault(),
                use24HourFormat
            )
            PlaceActions(
                displayedPlace,
                viewModel,
                place,
                onGetDirections
            ) { showUnsaveConfirmationDialog = true }
            // Inset horizontal divider
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(dimen.padding) / 2),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        if (place.isTransitStop) {
            TransitStopInformation(viewModel = hiltViewModel<TransitStopCardViewModel>().also {
                it.setStop(place)
            }, onRouteClicked = {})
        }

        UnsaveConfirmationDialog(
            displayedPlace,
            viewModel,
            showUnsaveConfirmationDialog
        ) { showUnsaveConfirmationDialog = false }
    }
}

@Composable
private fun PlaceHeader(displayedPlace: Place) {
    Text(
        text = displayedPlace.name,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Text(
        text = displayedPlace.description,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun PlaceAddress(displayedPlace: Place, addressFormatter: AddressFormatter) {
    displayedPlace.address?.let { address ->
        val formattedAddress = address.format(addressFormatter, includeCountry = false)
            ?.normalizedAddressLines()
            ?.takeIf { it.isNotBlank() }
        val countryText = addressFormatter.formatCountry(address)
        val addressText = formattedAddress ?: stringResource(string.address_unavailable)
            .takeIf { countryText == null }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(drawable.ic_location_on),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(dimensionResource(dimen.icon_size))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = dimensionResource(dimen.padding))
            ) {
                addressText?.let {
                    Text(text = it)
                }
                countryText?.let { country ->
                    Text(
                        text = country,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun String.normalizedAddressLines(): String {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

@Composable
private fun PlaceActions(
    displayedPlace: Place,
    viewModel: PlaceCardViewModel,
    place: Place,
    onGetDirections: (Place) -> Unit,
    onShowUnsaveDialog: () -> Unit
) {
    val context = LocalContext.current
    val addressFormatter = remember { AddressFormatter() }
    val shareChooserTitle = stringResource(string.share_place_chooser_title)
    val favoriteIconScale = remember { Animatable(1f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Get directions button
        PlaceActionButton(
            text = stringResource(string.get_directions),
            icon = drawable.ic_directions,
            isPrimary = true,
            onClick = { onGetDirections(displayedPlace) }
        )

        val coroutineScope = rememberCoroutineScope()

        // Save/Unsave button
        PlaceActionButton(
            text = if (viewModel.isPlaceSaved.value) {
                stringResource(string.unsave_place)
            } else {
                stringResource(string.save_place)
            },
            icon = if (viewModel.isPlaceSaved.value) {
                drawable.ic_heart_minus
            } else {
                drawable.ic_heart
            },
            iconModifier = Modifier.graphicsLayer {
                scaleX = favoriteIconScale.value
                scaleY = favoriteIconScale.value
            },
            onClick = {
                coroutineScope.launch {
                    favoriteIconScale.snapTo(0.82f)
                    favoriteIconScale.animateTo(
                        targetValue = 1.24f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    favoriteIconScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                if (viewModel.isPlaceSaved.value) {
                    // Show confirmation dialog for unsaving
                    onShowUnsaveDialog()
                } else {
                    coroutineScope.launch {
                        viewModel.savePlace(place)
                    }
                }
            }
        )

        PlaceActionButton(
            text = stringResource(string.share_place),
            icon = drawable.ic_share,
            onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                displayedPlace.toShareText(addressFormatter)
                            )
                        },
                        shareChooserTitle
                    )
                )
            }
        )
    }
}

@Composable
private fun PlaceActionButton(
    text: String,
    icon: Int,
    iconModifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(percent = 50)
    val contentPadding = PaddingValues(horizontal = 12.dp)
    val modifier = Modifier.height(48.dp)

    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            PlaceActionButtonContent(text = text, icon = icon, iconModifier = iconModifier)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            contentPadding = contentPadding,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            PlaceActionButtonContent(text = text, icon = icon, iconModifier = iconModifier)
        }
    }
}

@Composable
private fun PlaceActionButtonContent(
    text: String,
    icon: Int,
    iconModifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = iconModifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun Place.toShareText(addressFormatter: AddressFormatter): String {
    val addressText = address?.format(addressFormatter)

    return listOfNotNull(
        name,
        description.takeIf { it.isNotBlank() },
        addressText,
        toMurenaMapsUrl()
    ).joinToString(separator = "\n")
}

private fun Place.toMurenaMapsUrl(): String {
    return "https://share.maps.murena.com/shareplace?lat=${latLng.latitude}" +
        "&lng=${latLng.longitude}" +
        "&name=${Uri.encode(name)}" +
        "#map=16/${latLng.latitude}/${latLng.longitude}"
}

@Composable
private fun UnsaveConfirmationDialog(
    displayedPlace: Place,
    viewModel: PlaceCardViewModel,
    show: Boolean,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(string.unsave_place)) },
            text = {
                Text(
                    stringResource(
                        string.are_you_sure_you_want_to_delete, displayedPlace.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.unsavePlace(displayedPlace)
                            onDismiss()
                        }
                    }) {
                    Text(stringResource(string.unsave_place))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(string.cancel_button))
                }
            }
        )
    }
}
