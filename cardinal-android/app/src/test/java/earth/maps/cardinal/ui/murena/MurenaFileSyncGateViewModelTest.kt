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

package earth.maps.cardinal.ui.murena

import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.FavoritesSyncMode
import earth.maps.cardinal.domain.murena.MurenaAccountRepository
import earth.maps.cardinal.domain.sync.FavoritesFileSyncRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MurenaFileSyncGateViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val appPreferenceRepository = mockk<AppPreferenceRepository>(relaxed = true)

    @Test
    fun `imports favorites file and continues to map on launch`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val event = async {
            createViewModel(favoritesFileSyncRepository = favoritesFileSyncRepository).events.first()
        }

        advanceUntilIdle()

        assertEquals(1, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `local only mode skips account and file sync gate on launch`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val event = async {
            createViewModel(
                favoritesFileSyncRepository = favoritesFileSyncRepository,
                murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false),
                favoritesSyncMode = FavoritesSyncMode.LOCAL_ONLY
            ).events.first()
        }

        advanceUntilIdle()

        assertEquals(0, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `imports readable favorites file without querying Murena account`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository(
            syncFileReadableAfterChecks = 1
        )
        val murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        val event = async {
            createViewModel(
                favoritesFileSyncRepository = favoritesFileSyncRepository,
                murenaAccountRepository = murenaAccountRepository
            ).events.first()
        }

        advanceUntilIdle()

        assertEquals(0, murenaAccountRepository.checkCount)
        assertEquals(1, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `requests folder access before querying account when folder access is missing`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        val viewModel = createViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = murenaAccountRepository,
            hasFolderAccess = false
        )
        val event = async { viewModel.events.first() }

        advanceUntilIdle()

        assertEquals(0, murenaAccountRepository.checkCount)
        assertEquals(0, favoritesFileSyncRepository.importCount)
        assertEquals(1, favoritesFileSyncRepository.prepareFolderCount)
        assertEquals(MurenaFileSyncGateEvent.RequestFolderAccess, event.await())
    }

    @Test
    fun `waits for readable sync file after account login before asking for folder access`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository(
            syncFileReadableAfterChecks = 3
        )
        val murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        val viewModel = createViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = murenaAccountRepository
        )

        advanceUntilIdle()
        murenaAccountRepository.hasAccount = true
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnAccountLoginReturned)
        advanceUntilIdle()

        assertEquals(3, favoritesFileSyncRepository.readableCheckCount)
        assertEquals(1, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `shows account choice and does not sync when Murena account is missing`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val viewModel = createViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()

        assertEquals(0, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateMode.NeedsMurenaAccount, viewModel.state.value.mode)
    }

    @Test
    fun `waits for Murena account on initial launch and skips account choice`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val murenaAccountRepository = FakeMurenaAccountRepository(
            hasAccount = false,
            accountAvailableAfterChecks = 3
        )
        val event = async {
            createViewModel(
                favoritesFileSyncRepository = favoritesFileSyncRepository,
                murenaAccountRepository = murenaAccountRepository
            ).events.first()
        }

        advanceUntilIdle()

        assertEquals(3, murenaAccountRepository.checkCount)
        assertEquals(1, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `does not request system account flow when account is not visible on launch`() = runTest {
        val viewModel = createViewModel(
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()

        assertEquals(MurenaFileSyncGateMode.NeedsMurenaAccount, viewModel.state.value.mode)
    }

    @Test
    fun `sync with account action requests account login event`() = runTest {
        val viewModel = createViewModel(
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnSyncWithAccountClick)
        advanceUntilIdle()

        verify { appPreferenceRepository.setFavoritesSyncMode(FavoritesSyncMode.SYNC_ENABLED) }
        assertEquals(MurenaFileSyncGateEvent.RequestMurenaAccountLogin, event.await())
    }

    @Test
    fun `returning from account login retries account check and syncs file`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        val viewModel = createViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = murenaAccountRepository
        )

        advanceUntilIdle()
        murenaAccountRepository.hasAccount = true
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnAccountLoginReturned)
        advanceUntilIdle()

        assertEquals(1, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `returning from account login waits until account becomes visible`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val murenaAccountRepository = FakeMurenaAccountRepository(
            hasAccount = false,
            accountAvailableAfterChecks = 13
        )
        val viewModel = createViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = murenaAccountRepository
        )

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnAccountLoginReturned)
        advanceUntilIdle()

        assertEquals(1, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `requests folder access directly when favorites file import fails`() = runTest {
        val viewModel = createViewModel(
            favoritesFileSyncRepository = FakeFavoritesFileSyncRepository(
                importResult = Result.failure(IllegalStateException("boom"))
            )
        )
        val event = async { viewModel.events.first() }

        advanceUntilIdle()

        assertEquals(MurenaFileSyncGateEvent.RequestFolderAccess, event.await())
    }

    @Test
    fun `choose folder action requests folder access event`() = runTest {
        val viewModel = createViewModel(
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnChooseFolderClick)
        advanceUntilIdle()

        assertEquals(MurenaFileSyncGateEvent.RequestFolderAccess, event.await())
    }

    @Test
    fun `choose folder action prepares sync folder before requesting folder access`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository()
        val viewModel = createViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnChooseFolderClick)
        advanceUntilIdle()

        assertEquals(1, favoritesFileSyncRepository.prepareFolderCount)
        assertEquals(MurenaFileSyncGateEvent.RequestFolderAccess, event.await())
    }

    @Test
    fun `folder grant saves uri and retries sync`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository(
            importResult = Result.failure(IllegalStateException("boom"))
        )
        val viewModel = createViewModel(favoritesFileSyncRepository = favoritesFileSyncRepository)
        val folderAccessEvent = async { viewModel.events.first() }

        advanceUntilIdle()
        assertEquals(MurenaFileSyncGateEvent.RequestFolderAccess, folderAccessEvent.await())
        favoritesFileSyncRepository.importResult = Result.success(Unit)
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnFolderAccessGranted("content://tree"))
        advanceUntilIdle()

        verify { appPreferenceRepository.setFavoritesSyncMode(FavoritesSyncMode.SYNC_ENABLED) }
        verify { appPreferenceRepository.setFavoritesSyncTreeUri("content://tree") }
        assertEquals(2, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `retry imports favorites file again`() = runTest {
        val favoritesFileSyncRepository = FakeFavoritesFileSyncRepository(
            importResult = Result.failure(IllegalStateException("boom"))
        )
        val viewModel = createViewModel(favoritesFileSyncRepository = favoritesFileSyncRepository)
        val folderAccessEvent = async { viewModel.events.first() }

        advanceUntilIdle()
        assertEquals(MurenaFileSyncGateEvent.RequestFolderAccess, folderAccessEvent.await())
        favoritesFileSyncRepository.importResult = Result.success(Unit)
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnRetryClick)
        advanceUntilIdle()

        assertEquals(2, favoritesFileSyncRepository.importCount)
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `continue action enters map after sync failure`() = runTest {
        val viewModel = createViewModel(
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnContinueClick)
        advanceUntilIdle()

        verify { appPreferenceRepository.setFavoritesSyncMode(FavoritesSyncMode.LOCAL_ONLY) }
        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    @Test
    fun `folder access cancel continues to map`() = runTest {
        val viewModel = createViewModel(
            murenaAccountRepository = FakeMurenaAccountRepository(hasAccount = false)
        )

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onAction(MurenaFileSyncGateAction.OnFolderAccessCanceled)
        advanceUntilIdle()

        assertEquals(MurenaFileSyncGateEvent.ContinueToMap, event.await())
    }

    private fun createViewModel(
        favoritesFileSyncRepository: FavoritesFileSyncRepository = FakeFavoritesFileSyncRepository(),
        murenaAccountRepository: MurenaAccountRepository = FakeMurenaAccountRepository(),
        hasFolderAccess: Boolean = true,
        favoritesSyncMode: FavoritesSyncMode = FavoritesSyncMode.SYNC_ENABLED
    ): MurenaFileSyncGateViewModel {
        every { appPreferenceRepository.favoritesSyncMode } returns favoritesSyncMode
        every { appPreferenceRepository.favoritesSyncTreeUri } returns if (hasFolderAccess) {
            "content://tree"
        } else {
            null
        }
        return MurenaFileSyncGateViewModel(
            favoritesFileSyncRepository = favoritesFileSyncRepository,
            murenaAccountRepository = murenaAccountRepository,
            appPreferenceRepository = appPreferenceRepository
        )
    }

    private class FakeMurenaAccountRepository(
        var hasAccount: Boolean = true,
        private val accountAvailableAfterChecks: Int? = null
    ) : MurenaAccountRepository {
        var checkCount = 0
            private set

        override suspend fun hasMurenaAccount(): Boolean {
            checkCount++
            if (accountAvailableAfterChecks != null && checkCount >= accountAvailableAfterChecks) {
                hasAccount = true
            }
            return hasAccount
        }
    }

    private class FakeFavoritesFileSyncRepository(
        var importResult: Result<Unit> = Result.success(Unit),
        private val syncFileReadableAfterChecks: Int? = null
    ) : FavoritesFileSyncRepository {
        var importCount = 0
            private set
        var readableCheckCount = 0
            private set
        var prepareFolderCount = 0
            private set

        override suspend fun hasReadableSyncFile(): Boolean {
            readableCheckCount++
            return syncFileReadableAfterChecks != null &&
                readableCheckCount >= syncFileReadableAfterChecks
        }

        override suspend fun prepareSyncFolderForAccess(): Result<Unit> {
            prepareFolderCount++
            return Result.success(Unit)
        }

        override suspend fun syncFileToLocalDatabase(): Result<Unit> {
            importCount++
            return importResult
        }

        override suspend fun exportLocalDatabaseToFile(): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
