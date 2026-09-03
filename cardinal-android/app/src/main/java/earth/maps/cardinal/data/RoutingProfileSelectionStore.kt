/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package earth.maps.cardinal.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last explicitly selected custom routing profile for each mode.
 *
 * Kept separate from the profile's `isDefault` flag: a database default describes
 * profile metadata, while this store describes the driver's most recent choice.
 */
@Singleton
class RoutingProfileSelectionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(mode: RoutingMode): String? =
        preferences.getString(keyFor(mode), null)?.takeIf(String::isNotBlank)

    fun save(mode: RoutingMode, profileId: String?) {
        preferences.edit {
            if (profileId.isNullOrBlank()) {
                remove(keyFor(mode))
            } else {
                putString(keyFor(mode), profileId)
            }
        }
    }

    private fun keyFor(mode: RoutingMode): String = "$KEY_PREFIX${mode.value}"

    private companion object {
        const val PREFERENCES_NAME = "app_prefs"
        const val KEY_PREFIX = "last_routing_profile_"
    }
}
