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

package earth.maps.cardinal.data.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import earth.maps.cardinal.R
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.FavoritesSyncMode
import earth.maps.cardinal.data.room.AppDatabase
import earth.maps.cardinal.data.room.ItemType
import earth.maps.cardinal.data.room.ListItem
import earth.maps.cardinal.data.room.SavedList
import earth.maps.cardinal.data.room.SavedListDao
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.domain.sync.FavoritesFileSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentFavoritesFileSyncRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val appPreferenceRepository: AppPreferenceRepository
) : FavoritesFileSyncRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun hasReadableSyncFile(): Boolean = withContext(Dispatchers.IO) {
        if (isLocalOnly()) {
            return@withContext false
        }

        readableSyncFileExists()
    }

    override suspend fun prepareSyncFolderForAccess(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isLocalOnly()) {
                    Log.d(TAG, "Favorites sync is local-only; skipping folder preparation")
                    return@runCatching
                }

                ensureSyncFolderExistsForAccess()
            }.onFailure { exception ->
                Log.w(TAG, "Unable to prepare $SYNC_FILE_DIRECTORY_LOG_PATH for folder access", exception)
            }
        }

    override suspend fun syncFileToLocalDatabase(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isLocalOnly()) {
                Log.d(TAG, "Favorites sync is local-only; skipping file import")
                return@runCatching
            }

            if (!readableSyncFileExists()) {
                if (hasLocalFavoritesSnapshotData()) {
                    Log.d(TAG, "No readable favorites sync file found; exporting local favorites to $SYNC_FILE_LOG_PATH")
                    exportLocalSnapshot()
                } else {
                    Log.d(
                        TAG,
                        "No readable favorites sync file found and local favorites are empty; " +
                            "skipping startup import"
                    )
                    return@runCatching
                }
                return@runCatching
            }

            val syncFileDto = readSyncFile()
            importSnapshot(syncFileDto)
            exportLocalSnapshot()
            Log.d(
                TAG,
                "Merged favorites sync file lists=${syncFileDto.lists.size} " +
                    "places=${syncFileDto.places.size} items=${syncFileDto.listItems.size}"
            )
        }.onFailure { exception ->
            Log.w(TAG, "Unable to sync favorites file to local database", exception)
        }
    }

    override suspend fun exportLocalDatabaseToFile(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isLocalOnly()) {
                    Log.d(TAG, "Favorites sync is local-only; skipping file export")
                    return@runCatching
                }

                exportLocalSnapshot()
            }.onFailure { exception ->
                Log.w(TAG, "Unable to export favorites file", exception)
            }
        }

    private fun isLocalOnly(): Boolean {
        return appPreferenceRepository.favoritesSyncMode == FavoritesSyncMode.LOCAL_ONLY
    }

    private suspend fun exportLocalSnapshot() {
        val syncFileDto = createSyncFileDto()
        writeSyncFile(syncFileDto)
        Log.d(
            TAG,
            "Exported favorites sync file lists=${syncFileDto.lists.size} " +
                "places=${syncFileDto.places.size} items=${syncFileDto.listItems.size}"
        )
    }

    private suspend fun createSyncFileDto(): FavoritesSyncFileDto {
        ensureRootList()

        val listDao = database.savedListDao()
        val placeDao = database.savedPlaceDao()
        val listItemDao = database.listItemDao()

        return FavoritesSyncFileDto(
            updatedAt = System.currentTimeMillis(),
            lists = listDao.getAllLists().map { it.toFavoritesSyncDto() },
            places = placeDao.getAllPlaces().map { it.toFavoritesSyncDto() },
            listItems = listItemDao.getAllItems().map { it.toFavoritesSyncDto() }
        )
    }

    private suspend fun importSnapshot(syncFileDto: FavoritesSyncFileDto) {
        val places = syncFileDto.places.map { it.toSavedPlace() }

        database.withTransaction {
            val listDao = database.savedListDao()
            val placeDao = database.savedPlaceDao()
            val listItemDao = database.listItemDao()

            val existingLists = listDao.getAllLists()
            val existingRootList = existingLists.firstOrNull { it.isRoot }
            val importedLists = syncFileDto.lists.map { it.toSavedList() }
            val importedRootList = importedLists.firstOrNull { it.isRoot }
            val rootList = existingRootList ?: importedRootList ?: createRootList()
            val rootIdRemap = if (
                existingRootList != null &&
                importedRootList != null &&
                existingRootList.id != importedRootList.id
            ) {
                mapOf(importedRootList.id to existingRootList.id)
            } else {
                emptyMap()
            }
            val lists = importedLists
                .mapNotNull { list ->
                    if (rootIdRemap.containsKey(list.id)) {
                        null
                    } else {
                        list
                    }
                }
                .ensureRootList(rootList)
            val importedListIds = lists.mapTo(mutableSetOf()) { it.id }
            val importedPlaceIds = places.mapTo(mutableSetOf()) { it.id }
            val listItems = syncFileDto.listItems
                .map { it.toListItem().remapListIds(rootIdRemap) }
                .filter { item ->
                    item.listId in importedListIds &&
                        item.referencesImportedItem(importedListIds, importedPlaceIds)
                }

            listItemDao.clearAllItems()
            placeDao.deletePlacesAbsentFrom(importedPlaceIds)
            listDao.deleteListsAbsentFrom(importedListIds)
            listDao.upsertLists(lists)
            placeDao.upsertPlaces(places)
            listItemDao.upsertItems(listItems)
        }
    }

    private fun ListItem.referencesImportedItem(
        listIds: Set<String>,
        placeIds: Set<String>
    ): Boolean {
        return when (itemType) {
            ItemType.LIST -> itemId in listIds
            ItemType.PLACE -> itemId in placeIds
        }
    }

    private suspend fun SavedPlaceDao.deletePlacesAbsentFrom(
        placeIds: Set<String>
    ) {
        if (placeIds.isEmpty()) {
            deleteAllPlaces()
        } else {
            deletePlacesNotIn(placeIds.toList())
        }
    }

    private suspend fun SavedListDao.deleteListsAbsentFrom(
        listIds: Set<String>
    ) {
        if (listIds.isEmpty()) {
            deleteAllLists()
        } else {
            deleteListsNotIn(listIds.toList())
        }
    }

    private suspend fun ensureRootList() {
        val listDao = database.savedListDao()
        if (listDao.getRootList() != null) {
            return
        }

        listDao.insertList(createRootList())
    }

    private fun List<SavedList>.ensureRootList(rootList: SavedList = createRootList()): List<SavedList> {
        if (any { it.isRoot }) {
            return this
        }

        return this + rootList
    }

    private fun ListItem.remapListIds(
        idRemap: Map<String, String>
    ): ListItem {
        return copy(
            listId = idRemap[listId] ?: listId,
            itemId = idRemap[itemId] ?: itemId
        )
    }

    private fun createRootList(): SavedList {
        return SavedList.createList(
            name = context.getString(R.string.saved_places_title_case),
            isRoot = true
        )
    }

    private fun readSyncFile(): FavoritesSyncFileDto {
        return try {
            json.decodeFromString<FavoritesSyncFileDto>(readSyncFileText())
        } catch (exception: SerializationException) {
            throw IOException("Favorites sync file is not valid JSON", exception)
        } catch (exception: IllegalArgumentException) {
            throw IOException("Favorites sync file has invalid content", exception)
        }
    }

    private fun readSyncFileText(): String {
        val safFile = findSafSyncFile()
        if (safFile != null) {
            return context.contentResolver.openInputStream(safFile.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IOException("Could not open SAF $SYNC_FILE_LOG_PATH for reading")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findReadableSyncFileUri()
                ?: throw FileNotFoundException("$SYNC_FILE_LOG_PATH was not found in MediaStore")
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IOException("Could not open $SYNC_FILE_LOG_PATH for reading")
        } else {
            findDirectReadableSyncFile()?.readText()
                ?: throw FileNotFoundException("$SYNC_FILE_LOG_PATH was not found")
        }
    }

    private fun writeSyncFile(syncFileDto: FavoritesSyncFileDto) {
        if (writeSafSyncFile(syncFileDto)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeSyncFileToMediaStore(syncFileDto)
        } else {
            writeDirectSyncFile(syncFileDto)
        }
    }

    private fun writeSyncFileToMediaStore(syncFileDto: FavoritesSyncFileDto) {
        val contentResolver = context.contentResolver
        val existingUri = findWritableSyncFileUri()
        val uri = existingUri ?: createSyncFileUri()
        contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(json.encodeToString(syncFileDto)) }
            ?: throw IOException("Could not open $SYNC_FILE_LOG_PATH for writing")
    }

    private fun writeDirectSyncFile(syncFileDto: FavoritesSyncFileDto) {
        val file = directSyncFile()
        ensureParentDirectory(file)
        val tempFile = File(file.parentFile, "$FAVORITES_SYNC_FILE_NAME.tmp")
        tempFile.writeText(json.encodeToString(syncFileDto))
        if (file.exists() && !file.delete()) {
            throw IOException("Could not replace existing favorites sync file")
        }
        if (!tempFile.renameTo(file)) {
            throw IOException("Could not move favorites sync file into place")
        }
    }

    private fun readableSyncFileExists(): Boolean {
        if (findSafSyncFile() != null) {
            return true
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findReadableSyncFileUri() != null
        } else {
            findDirectReadableSyncFile() != null
        }
    }

    private suspend fun hasLocalFavoritesSnapshotData(): Boolean {
        val listDao = database.savedListDao()
        val placeDao = database.savedPlaceDao()
        val listItemDao = database.listItemDao()
        return placeDao.getAllPlaces().isNotEmpty() ||
            listItemDao.getAllItems().isNotEmpty() ||
            listDao.getAllLists().any { !it.isRoot }
    }

    private fun findReadableSyncFileUri(): Uri? {
        return findSyncFileCandidate(requireNonEmpty = true)?.uri
    }

    private fun findWritableSyncFileUri(): Uri? {
        return findSyncFileCandidate(requireNonEmpty = false)?.uri
    }

    private fun findSyncFileCandidate(requireNonEmpty: Boolean): SyncFileCandidate? {
        val collection = syncFileCollectionUri()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(FAVORITES_SYNC_FILE_NAME_LIKE)

        return context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val candidates = mutableListOf<SyncFileCandidate>()

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameColumn).orEmpty()
                val relativePath = cursor.getString(pathColumn).orEmpty()
                val size = cursor.getLong(sizeColumn)
                if (!displayName.isFavoritesSyncFileName() || !relativePath.isMapsDocumentsPath()) {
                    continue
                }
                if (requireNonEmpty && size <= 0L) {
                    continue
                }

                val id = cursor.getLong(idColumn)
                candidates += SyncFileCandidate(
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = displayName,
                    size = size,
                    dateModified = cursor.getLong(modifiedColumn)
                )
            }
            candidates
                .sortedWith(
                    compareByDescending<SyncFileCandidate> { it.size > 0L }
                        .thenByDescending { it.dateModified }
                )
                .firstOrNull()
        }
    }

    private fun createSyncFileUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FAVORITES_SYNC_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, FAVORITES_SYNC_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, syncFileRelativePath())
        }

        return context.contentResolver.insert(syncFileCollectionUri(), values)
            ?: throw IOException("Could not create $SYNC_FILE_LOG_PATH")
    }

    private fun ensureSyncFolderExistsForAccess() {
        if (directSyncDirectory().exists()) {
            return
        }
        if (directSyncDirectory().mkdirs()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ensureMediaStoreSyncFolderExists()
        } else {
            throw IOException("Could not create $SYNC_FILE_DIRECTORY_LOG_PATH")
        }
    }

    private fun ensureMediaStoreSyncFolderExists() {
        if (syncFolderHasMediaStoreEntry()) {
            return
        }

        val uri = createSyncFolderMarkerUri()
        context.contentResolver.openOutputStream(uri, "wt")
            ?.use { it.write(ByteArray(0)) }
            ?: throw IOException("Could not create $SYNC_FILE_DIRECTORY_LOG_PATH marker")
    }

    private fun syncFolderHasMediaStoreEntry(): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(syncFileRelativePath())

        return context.contentResolver.query(
            syncFileCollectionUri(),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    private fun createSyncFolderMarkerUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, SYNC_FOLDER_MARKER_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, SYNC_FOLDER_MARKER_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, syncFileRelativePath())
        }

        return context.contentResolver.insert(syncFileCollectionUri(), values)
            ?: throw IOException("Could not create $SYNC_FILE_DIRECTORY_LOG_PATH marker")
    }

    private fun syncFileCollectionUri(): Uri {
        return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    private fun syncFileRelativePath(): String {
        return "${Environment.DIRECTORY_DOCUMENTS}/$FAVORITES_SYNC_DIRECTORY/"
    }

    private fun String.isFavoritesSyncFileName(): Boolean {
        return FAVORITES_SYNC_FILE_NAME_REGEX.matches(this)
    }

    private fun String.isMapsDocumentsPath(): Boolean {
        return trimEnd('/').endsWith(SYNC_FILE_DIRECTORY_LOG_PATH)
    }

    private fun ensureParentDirectory(file: File) {
        val parent = file.parentFile ?: throw IOException("Favorites sync file has no parent")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create favorites sync directory")
        }
    }

    @Suppress("DEPRECATION")
    private fun directSyncDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FAVORITES_SYNC_DIRECTORY
        )
    }

    @Suppress("DEPRECATION")
    private fun directSyncFile(): File {
        return File(directSyncDirectory(), FAVORITES_SYNC_FILE_NAME)
    }

    @Suppress("DEPRECATION")
    private fun findDirectReadableSyncFile(): File? {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FAVORITES_SYNC_DIRECTORY
        )
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.isFavoritesSyncFileName() && it.length() > 0L }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun findSafSyncFile(): DocumentFile? {
        return favoritesSyncTree()?.findFile(FAVORITES_SYNC_FILE_NAME)
            ?.takeIf { it.isFile && it.canRead() && it.length() > 0L }
    }

    private fun writeSafSyncFile(syncFileDto: FavoritesSyncFileDto): Boolean {
        val tree = favoritesSyncTree() ?: return false
        val file = tree.findFile(FAVORITES_SYNC_FILE_NAME)
            ?: tree.createFile(FAVORITES_SYNC_MIME_TYPE, FAVORITES_SYNC_FILE_NAME)
            ?: throw IOException("Could not create SAF $SYNC_FILE_LOG_PATH")

        context.contentResolver.openOutputStream(file.uri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(json.encodeToString(syncFileDto)) }
            ?: throw IOException("Could not open SAF $SYNC_FILE_LOG_PATH for writing")
        return true
    }

    private fun favoritesSyncTree(): DocumentFile? {
        val treeUri = appPreferenceRepository.favoritesSyncTreeUri
            ?.let(Uri::parse)
            ?: return null
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: return null

        if (!tree.canRead() || !tree.canWrite()) {
            return null
        }

        return if (tree.name == FAVORITES_SYNC_DIRECTORY) {
            tree
        } else {
            tree.findFile(FAVORITES_SYNC_DIRECTORY)
                ?: tree.createDirectory(FAVORITES_SYNC_DIRECTORY)
        }
    }

    private data class SyncFileCandidate(
        val uri: Uri,
        val displayName: String,
        val size: Long,
        val dateModified: Long
    )

    private companion object {
        private const val TAG = "MurenaFileSync"
        private const val FAVORITES_SYNC_DIRECTORY = "Maps"
        private const val FAVORITES_SYNC_FILE_NAME = "favorites.json"
        private const val FAVORITES_SYNC_FILE_NAME_LIKE = "favorites%.json"
        private const val FAVORITES_SYNC_MIME_TYPE = "application/json"
        private const val SYNC_FOLDER_MARKER_FILE_NAME = ".maps-folder"
        private const val SYNC_FOLDER_MARKER_MIME_TYPE = "application/octet-stream"
        private const val SYNC_FILE_DIRECTORY_LOG_PATH = "Documents/$FAVORITES_SYNC_DIRECTORY"
        private const val SYNC_FILE_LOG_PATH = "$SYNC_FILE_DIRECTORY_LOG_PATH/$FAVORITES_SYNC_FILE_NAME"
        private val FAVORITES_SYNC_FILE_NAME_REGEX = Regex("""favorites( \([0-9]+\))?\.json""")
    }
}
