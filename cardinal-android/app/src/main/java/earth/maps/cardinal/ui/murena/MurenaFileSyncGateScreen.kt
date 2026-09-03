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

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import earth.maps.cardinal.R
import earth.maps.cardinal.domain.murena.MurenaAccount
import earth.maps.cardinal.ui.theme.AppTheme

@Composable
fun MurenaFileSyncGateRoot(
    onContinueToMap: () -> Unit,
    viewModel: MurenaFileSyncGateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val accountLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onAction(MurenaFileSyncGateAction.OnAccountLoginReturned)
    }
    val folderAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data
        val uri = intent?.data
        if (
            result.resultCode == Activity.RESULT_OK &&
            uri != null &&
            persistFolderAccess(context, intent, uri)
        ) {
            viewModel.onAction(MurenaFileSyncGateAction.OnFolderAccessGranted(uri.toString()))
        } else {
            viewModel.onAction(MurenaFileSyncGateAction.OnFolderAccessCanceled)
        }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                MurenaFileSyncGateEvent.ContinueToMap -> onContinueToMap()
                MurenaFileSyncGateEvent.RequestMurenaAccountLogin -> {
                    accountLoginLauncher.launch(murenaChooseAccountIntent())
                }
                MurenaFileSyncGateEvent.RequestFolderAccess -> {
                    folderAccessLauncher.launch(documentsMapsFolderIntent())
                }
            }
        }
    }

    MurenaFileSyncGateScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun MurenaFileSyncGateScreen(
    state: MurenaFileSyncGateState,
    onAction: (MurenaFileSyncGateAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        MurenaFileSyncGateContent(
            mode = state.mode,
            errorMessage = state.errorMessage,
            onAction = onAction
        )
    }
}

@Composable
private fun MurenaFileSyncGateContent(
    mode: MurenaFileSyncGateMode,
    errorMessage: MurenaFileSyncGateMessage?,
    onAction: (MurenaFileSyncGateAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(152.dp)
        )
        Spacer(Modifier.height(56.dp))
        Text(
            text = stringResource(mode.titleRes),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(mode.messageRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        ErrorMessage(errorMessage)
        Spacer(Modifier.height(72.dp))
        MurenaFileSyncGateActions(
            mode = mode,
            onAction = onAction
        )
    }
}

@Composable
private fun ErrorMessage(message: MurenaFileSyncGateMessage?) {
    message?.let { error ->
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(error.stringRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MurenaFileSyncGateActions(
    mode: MurenaFileSyncGateMode,
    onAction: (MurenaFileSyncGateAction) -> Unit
) {
    if (mode == MurenaFileSyncGateMode.SyncingFile) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        return
    }

    PrimaryActionButton(
        text = stringResource(mode.primaryButtonTextRes).uppercase(),
        onClick = { onAction(mode.primaryAction) }
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.murena_file_sync_or),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        onClick = { onAction(MurenaFileSyncGateAction.OnContinueClick) },
        shape = RoundedCornerShape(percent = 50)
    ) {
        Text(
            text = stringResource(R.string.murena_file_sync_continue).uppercase(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private val MurenaFileSyncGateMode.titleRes: Int
    get() = when (this) {
        MurenaFileSyncGateMode.NeedsMurenaAccount -> R.string.murena_file_sync_welcome_title
        MurenaFileSyncGateMode.SyncingFile,
        MurenaFileSyncGateMode.NeedsFolderAccess,
        MurenaFileSyncGateMode.Failed -> R.string.murena_file_sync_progress_title
    }

private val MurenaFileSyncGateMode.messageRes: Int
    get() = when (this) {
        MurenaFileSyncGateMode.SyncingFile -> R.string.murena_file_sync_syncing_file
        MurenaFileSyncGateMode.NeedsMurenaAccount -> R.string.murena_file_sync_account_message
        MurenaFileSyncGateMode.NeedsFolderAccess -> R.string.murena_file_sync_folder_access_message
        MurenaFileSyncGateMode.Failed -> R.string.murena_file_sync_failed_message
    }

private val MurenaFileSyncGateMode.primaryButtonTextRes: Int
    get() = when (this) {
        MurenaFileSyncGateMode.NeedsMurenaAccount -> R.string.murena_file_sync_sync_with_account
        MurenaFileSyncGateMode.NeedsFolderAccess -> R.string.murena_file_sync_choose_folder
        MurenaFileSyncGateMode.SyncingFile,
        MurenaFileSyncGateMode.Failed -> R.string.murena_file_sync_retry
    }

private val MurenaFileSyncGateMode.primaryAction: MurenaFileSyncGateAction
    get() = when (this) {
        MurenaFileSyncGateMode.NeedsMurenaAccount ->
            MurenaFileSyncGateAction.OnSyncWithAccountClick
        MurenaFileSyncGateMode.NeedsFolderAccess ->
            MurenaFileSyncGateAction.OnChooseFolderClick
        MurenaFileSyncGateMode.SyncingFile,
        MurenaFileSyncGateMode.Failed -> MurenaFileSyncGateAction.OnRetryClick
    }

private val MurenaFileSyncGateMessage.stringRes: Int
    get() = when (this) {
        MurenaFileSyncGateMessage.FolderAccessRequired -> R.string.murena_file_sync_folder_access_required
        MurenaFileSyncGateMessage.FileSyncFailed -> R.string.murena_file_sync_file_sync_failed
    }

private fun murenaChooseAccountIntent(): Intent {
    return AccountManager.newChooseAccountIntent(
        null,
        null,
        arrayOf(MurenaAccount.ACCOUNT_TYPE),
        null,
        null,
        null,
        null
    )
}

private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"
private const val DOCUMENTS_MAPS_DOCUMENT_ID = "primary:Documents/Maps"
private const val TAG = "MurenaFileSync"
private const val FOLDER_ACCESS_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private fun persistFolderAccess(
    context: Context,
    intent: Intent,
    uri: Uri
): Boolean {
    val grantedFlags = intent.flags and FOLDER_ACCESS_FLAGS
    if (grantedFlags != FOLDER_ACCESS_FLAGS) {
        Log.w(TAG, "Folder access result missing required read/write grants")
        return false
    }

    return runCatching {
        context.contentResolver.takePersistableUriPermission(uri, FOLDER_ACCESS_FLAGS)
    }.onFailure { exception ->
        Log.w(TAG, "Could not persist Documents/Maps folder access", exception)
    }.isSuccess
}

private fun documentsMapsFolderIntent(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(FOLDER_ACCESS_FLAGS)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentsMapsInitialUri())
    }
}

private fun documentsMapsInitialUri(): Uri {
    return DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
        DOCUMENTS_MAPS_DOCUMENT_ID
    )
}

@Preview
@Composable
private fun MurenaFileSyncGateScreenPreview() {
    AppTheme {
        MurenaFileSyncGateScreen(
            state = MurenaFileSyncGateState(
                mode = MurenaFileSyncGateMode.Failed,
                errorMessage = MurenaFileSyncGateMessage.FileSyncFailed
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun MurenaFileSyncGateProgressPreview() {
    AppTheme {
        MurenaFileSyncGateScreen(
            state = MurenaFileSyncGateState(mode = MurenaFileSyncGateMode.SyncingFile),
            onAction = {}
        )
    }
}
