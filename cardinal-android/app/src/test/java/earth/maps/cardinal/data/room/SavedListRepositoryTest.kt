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

package earth.maps.cardinal.data.room

import android.content.Context
import earth.maps.cardinal.domain.sync.FavoritesFileSyncRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SavedListRepositoryTest {

    private lateinit var repository: SavedListRepository
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDatabase = mockk<AppDatabase>(relaxed = false)
    private val mockListDao = mockk<SavedListDao>(relaxed = false)
    private val mockListItemDao = mockk<ListItemDao>(relaxed = false)
    private val mockPlaceDao = mockk<SavedPlaceDao>(relaxed = false)
    private val mockFavoritesFileSyncRepository = mockk<FavoritesFileSyncRepository>(relaxed = true)

    // Test data
    private val rootList = SavedList(
        id = "root-list-id",
        name = "Saved Places",
        description = null,
        isRoot = true,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val parentList = SavedList(
        id = "parent-list-id",
        name = "Parent List",
        description = "Parent Description",
        isRoot = false,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val childList = SavedList(
        id = "child-list-id",
        name = "Child List",
        description = "Child Description",
        isRoot = false,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val grandchildList = SavedList(
        id = "grandchild-list-id",
        name = "Grandchild List",
        description = "Grandchild Description",
        isRoot = false,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val unrelatedList = SavedList(
        id = "unrelated-list-id",
        name = "Unrelated List",
        description = "Unrelated Description",
        isRoot = false,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        every { mockDatabase.savedListDao() } returns mockListDao
        every { mockDatabase.listItemDao() } returns mockListItemDao
        every { mockDatabase.savedPlaceDao() } returns mockPlaceDao
        coEvery {
            mockFavoritesFileSyncRepository.exportLocalDatabaseToFile()
        } returns Result.success(Unit)
        
        repository = SavedListRepository(
            mockContext,
            mockDatabase,
            mockFavoritesFileSyncRepository
        )
    }

    @Test
    fun `wouldCreateCycle should return false when no lists are being pasted`() = runTest {
        // Given
        val targetListId = "target-list-id"
        val listIdsToPaste = emptySet<String>()
        
        // When
        val result = repository.wouldCreateCycle(targetListId, listIdsToPaste)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `wouldCreateCycle should return true when pasting list into itself`() = runTest {
        // Given
        val targetListId = parentList.id
        val listIdsToPaste = setOf(parentList.id)
        
        // When
        val result = repository.wouldCreateCycle(targetListId, listIdsToPaste)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `wouldCreateCycle should return true when pasting parent into child`() = runTest {
        // Given - set up hierarchy: parent -> child
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns emptyList()
        
        // When
        val result = repository.wouldCreateCycle(childList.id, setOf(parentList.id))
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `wouldCreateCycle should return true when pasting grandparent into grandchild`() = runTest {
        // Given - set up hierarchy: parent -> child -> grandchild
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        val childToGrandchildListItem = ListItem(
            listId = childList.id,
            itemId = grandchildList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns listOf(childToGrandchildListItem)
        coEvery { mockListItemDao.getItemsInList(grandchildList.id) } returns emptyList()
        
        // When
        val result = repository.wouldCreateCycle(grandchildList.id, setOf(parentList.id))
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `wouldCreateCycle should return false when pasting child into parent`() = runTest {
        // Given - set up hierarchy: parent -> child
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns emptyList()
        
        // When
        val result = repository.wouldCreateCycle(parentList.id, setOf(childList.id))
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `wouldCreateCycle should return false when pasting unrelated list`() = runTest {
        // Given - set up hierarchy: parent -> child
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns emptyList()
        coEvery { mockListItemDao.getItemsInList(unrelatedList.id) } returns emptyList()
        
        // When
        val result = repository.wouldCreateCycle(parentList.id, setOf(unrelatedList.id))
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `wouldCreateCycle should return true when any list in set would create cycle`() = runTest {
        // Given - set up hierarchy: parent -> child
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns emptyList()
        coEvery { mockListItemDao.getItemsInList(unrelatedList.id) } returns emptyList()
        
        // When - trying to paste both child (would create cycle) and unrelated (wouldn't create cycle)
        val result = repository.wouldCreateCycle(childList.id, setOf(parentList.id, unrelatedList.id))
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `wouldCreateCycle should return false when no lists in set would create cycle`() = runTest {
        // Given - set up hierarchy: parent -> child
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns emptyList()
        coEvery { mockListItemDao.getItemsInList(unrelatedList.id) } returns emptyList()
        
        // When - trying to paste unrelated lists only
        val result = repository.wouldCreateCycle(parentList.id, setOf(unrelatedList.id))
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `wouldCreateCycle should handle complex hierarchy correctly`() = runTest {
        // Given - set up complex hierarchy: parent -> child1, child2 -> grandchild1, grandchild2
        val parentToChild1ListItem = ListItem(
            listId = parentList.id,
            itemId = "child1-list-id",
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        val parentToChild2ListItem = ListItem(
            listId = parentList.id,
            itemId = "child2-list-id",
            itemType = ItemType.LIST,
            position = 1,
            addedAt = System.currentTimeMillis()
        )
        
        val child1ToGrandchild1ListItem = ListItem(
            listId = "child1-list-id",
            itemId = "grandchild1-list-id",
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        val child2ToGrandchild2ListItem = ListItem(
            listId = "child2-list-id",
            itemId = "grandchild2-list-id",
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChild1ListItem, parentToChild2ListItem)
        coEvery { mockListItemDao.getItemsInList("child1-list-id") } returns listOf(child1ToGrandchild1ListItem)
        coEvery { mockListItemDao.getItemsInList("child2-list-id") } returns listOf(child2ToGrandchild2ListItem)
        coEvery { mockListItemDao.getItemsInList("grandchild1-list-id") } returns emptyList()
        coEvery { mockListItemDao.getItemsInList("grandchild2-list-id") } returns emptyList()
        
        // When - trying to paste parent into grandchild (should create cycle)
        val result1 = repository.wouldCreateCycle("grandchild1-list-id", setOf(parentList.id))
        
        // When - trying to paste child1 into child2 (should not create cycle)
        val result2 = repository.wouldCreateCycle("child2-list-id", setOf("child1-list-id"))
        
        // When - trying to paste grandchild1 into parent (should not create cycle)
        val result3 = repository.wouldCreateCycle(parentList.id, setOf("grandchild1-list-id"))
        
        // Then
        assertTrue(result1) // parent -> child1 -> grandchild1, so pasting parent into grandchild1 creates cycle
        assertFalse(result2) // child1 and child2 are siblings, no cycle
        assertFalse(result3) // grandchild1 is descendant of parent, but pasting it into parent doesn't create cycle
    }

    @Test
    fun `wouldCreateCycle should handle empty lists correctly`() = runTest {
        // Given
        val targetListId = "target-list-id"
        val listIdsToPaste = setOf("list1-id", "list2-id")
        
        // Mock all lists as empty
        coEvery { mockListItemDao.getItemsInList(any()) } returns emptyList()
        
        // When
        val result = repository.wouldCreateCycle(targetListId, listIdsToPaste)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `wouldCreateCycle should handle database errors gracefully`() = runTest {
        // Given
        val targetListId = parentList.id
        val listIdsToPaste = setOf(childList.id)
        
        // Mock database to throw exception
        coEvery { mockListItemDao.getItemsInList(any()) } throws RuntimeException("Database error")
        
        // When
        val result = repository.wouldCreateCycle(targetListId, listIdsToPaste)
        
        // Then - should return false (allow paste) when there's an error
        assertFalse(result)
    }

    @Test
    fun `wouldCreateCycle should handle mixed item types correctly`() = runTest {
        // Given - list contains both lists and places
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        
        val parentToPlaceListItem = ListItem(
            listId = parentList.id,
            itemId = "place-id",
            itemType = ItemType.PLACE,
            position = 1,
            addedAt = System.currentTimeMillis()
        )
        
        coEvery { mockListItemDao.getItemsInList(parentList.id) } returns listOf(parentToChildListItem, parentToPlaceListItem)
        coEvery { mockListItemDao.getItemsInList(childList.id) } returns emptyList()
        
        // When
        val result = repository.wouldCreateCycle(childList.id, setOf(parentList.id))
        
        // Then - should still detect cycle even with mixed item types
        assertTrue(result)
    }
}
