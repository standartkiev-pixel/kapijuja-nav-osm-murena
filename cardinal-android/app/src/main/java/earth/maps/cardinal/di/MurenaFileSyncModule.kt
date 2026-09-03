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

package earth.maps.cardinal.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import earth.maps.cardinal.data.murena.AndroidMurenaAccountRepository
import earth.maps.cardinal.data.sync.DocumentFavoritesFileSyncRepository
import earth.maps.cardinal.domain.murena.MurenaAccountRepository
import earth.maps.cardinal.domain.sync.FavoritesFileSyncRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MurenaFileSyncModule {

    @Binds
    @Singleton
    abstract fun bindFavoritesFileSyncRepository(
        repository: DocumentFavoritesFileSyncRepository
    ): FavoritesFileSyncRepository

    @Binds
    @Singleton
    abstract fun bindMurenaAccountRepository(
        repository: AndroidMurenaAccountRepository
    ): MurenaAccountRepository
}
