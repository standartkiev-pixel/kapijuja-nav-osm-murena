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

package earth.maps.cardinal.data.murena

import android.accounts.AccountManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import earth.maps.cardinal.domain.murena.MurenaAccount
import earth.maps.cardinal.domain.murena.MurenaAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMurenaAccountRepository @Inject constructor(
    @ApplicationContext context: Context
) : MurenaAccountRepository {

    private val appContext = context.applicationContext
    private val accountManager = AccountManager.get(appContext)

    override suspend fun hasMurenaAccount(): Boolean = withContext(Dispatchers.IO) {
        if (queryCurrentPackageAccounts()) {
            return@withContext true
        }

        queryReleasePackageAccounts()
    }

    private fun queryCurrentPackageAccounts(): Boolean {
        return try {
            accountManager
                .getAccountsByType(MurenaAccount.ACCOUNT_TYPE)
                .hasAnyMurenaAccount(source = "current-package")
        } catch (exception: SecurityException) {
            Log.w(TAG, "Murena account query is not visible to Maps", exception)
            false
        }
    }

    private fun queryReleasePackageAccounts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val packageName = appContext.packageName
        if (packageName == RELEASE_PACKAGE_NAME) {
            return false
        }

        return try {
            accountManager
                .getAccountsByTypeForPackage(MurenaAccount.ACCOUNT_TYPE, RELEASE_PACKAGE_NAME)
                .hasAnyMurenaAccount(source = "release-package-visibility")
        } catch (exception: SecurityException) {
            Log.w(TAG, "Murena release package account query is not visible to Maps", exception)
            false
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Murena release package account query is not available", exception)
            false
        }
    }

    private fun Array<android.accounts.Account>.hasAnyMurenaAccount(source: String): Boolean {
        Log.d(TAG, "Murena account query source=$source count=$size")
        return isNotEmpty()
    }

    private companion object {
        private const val TAG = "MurenaFileSync"
        private const val RELEASE_PACKAGE_NAME = "foundation.e.maps"
    }
}
