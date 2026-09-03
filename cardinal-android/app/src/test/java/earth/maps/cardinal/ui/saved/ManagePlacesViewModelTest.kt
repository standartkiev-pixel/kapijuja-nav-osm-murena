package earth.maps.cardinal.ui.saved

import android.content.Context
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.R.string
import earth.maps.cardinal.data.ClipboardItem
import earth.maps.cardinal.data.CutPasteRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.room.ItemType
import earth.maps.cardinal.data.room.ListItem
import earth.maps.cardinal.data.room.ListItemDao
import earth.maps.cardinal.data.room.SavedList
import earth.maps.cardinal.data.room.SavedListRepository
import earth.maps.cardinal.data.room.SavedPlace
import earth.maps.cardinal.data.room.SavedPlaceRepository
import earth.maps.cardinal.domain.sync.FavoritesFileSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagePlacesViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    // Mock dependencies
    private val mockContext = mockk<Context>()
    private val mockSavedListRepository = mockk<SavedListRepository>(relaxed = false)
    private val mockSavedPlaceRepository = mockk<SavedPlaceRepository>(relaxed = false)
    private val mockListItemDao = mockk<ListItemDao>(relaxed = false)
    private val mockCutPasteRepository = mockk<CutPasteRepository>(relaxed = false)
    private val mockFavoritesFileSyncRepository = mockk<FavoritesFileSyncRepository>(relaxed = false)

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

    private val testList = SavedList(
        id = "test-list-id",
        name = "Test List",
        description = "Test Description",
        isRoot = false,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val nestedList = SavedList(
        id = "nested-list-id",
        name = "Nested List",
        description = "Nested Description",
        isRoot = false,
        isCollapsed = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val testPlace = SavedPlace(
        id = "test-place-id",
        placeId = null,
        customName = null,
        customDescription = null,
        isPinned = false,
        name = "Test Place",
        type = "place",
        icon = "icon",
        latitude = 40.7128,
        longitude = -74.0060,
        houseNumber = null,
        road = null,
        city = null,
        state = null,
        postcode = null,
        country = null,
        countryCode = null,
        isTransitStop = false,
        transitStopId = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private val listItem1 = ListItem(
        listId = testList.id,
        itemId = testPlace.id,
        itemType = ItemType.PLACE,
        position = 0,
        addedAt = System.currentTimeMillis()
    )

    private lateinit var viewModel: ManagePlacesViewModel

    @Before
    fun setup() {
        // Mock context methods
        every { mockContext.getString(string.cannot_paste_a_list_into_itself_or_one_of_its_sublists) } returns "error"
        // Mock repository methods
        coEvery { mockSavedListRepository.getRootList() } returns Result.success(rootList)
        coEvery { mockSavedListRepository.getListById(any()) } returns Result.success(null)
        coEvery { mockSavedListRepository.getListById(testList.id) } returns Result.success(testList)
        coEvery { mockSavedListRepository.getListById(nestedList.id) } returns Result.success(
            nestedList
        )
        coEvery { mockSavedListRepository.getItemsInList(any()) } returns Result.success(emptyList())
        coEvery { mockSavedListRepository.getItemsInList(testList.id) } returns Result.success(
            listOf(listItem1)
        )
        coEvery { mockSavedListRepository.getItemIdsInListAsFlow(any()) } returns flowOf(emptySet())
        coEvery { mockSavedListRepository.getItemIdsInListAsFlow(testList.id) } returns flowOf(
            setOf(
                testPlace.id
            )
        )
        coEvery { mockSavedListRepository.getListContent(any()) } returns flowOf(emptyList())

        // Mock additional repository methods needed for tests
        coEvery { mockSavedListRepository.deleteList(any()) } returns Result.success(Unit)
        coEvery { mockSavedListRepository.updateList(any(), any(), any()) } returns Result.success(
            Unit
        )
        coEvery {
            mockSavedListRepository.createList(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Result.success("new-list-id")

        // Mock DAO methods
        coEvery { mockListItemDao.getItemsInList(any()) } returns emptyList()
        coEvery { mockListItemDao.getItemsInList(testList.id) } returns listOf(listItem1)
        coEvery { mockListItemDao.moveItem(any(), any(), any()) } just runs

        // Mock place repository
        coEvery { mockSavedPlaceRepository.getPlaceById(any()) } returns Result.success(null)
        coEvery { mockSavedPlaceRepository.getPlaceById(testPlace.id) } returns Result.success(
            testPlace
        )
        coEvery { mockSavedPlaceRepository.toPlace(any()) } returns Place(
            id = testPlace.id,
            name = testPlace.name,
            description = testPlace.type,
            icon = testPlace.icon,
            latLng = earth.maps.cardinal.data.LatLng(testPlace.latitude, testPlace.longitude),
            address = null,
            isTransitStop = testPlace.isTransitStop,
            transitStopId = testPlace.transitStopId
        )
        coEvery { mockSavedPlaceRepository.deletePlace(any()) } returns Result.success(Unit)
        coEvery {
            mockSavedPlaceRepository.updatePlace(
                any(),
                any(),
                any(),
                any()
            )
        } returns Result.success(Unit)

        // Mock cut/paste repository
        every { mockCutPasteRepository.clipboard } returns MutableStateFlow(emptySet())

        // Mock wouldCreateCycle method
        coEvery { mockSavedListRepository.wouldCreateCycle(any(), any()) } returns false
        coEvery { mockFavoritesFileSyncRepository.syncFileToLocalDatabase() } returns Result.success(Unit)

        viewModel = ManagePlacesViewModel(
            context = mockContext,
            savedPlaceRepository = mockSavedPlaceRepository,
            savedListRepository = mockSavedListRepository,
            listItemDao = mockListItemDao,
            cutPasteRepository = mockCutPasteRepository,
            favoritesFileSyncRepository = mockFavoritesFileSyncRepository
        )
    }

    @Test
    fun `setInitialList with null should navigate to root list`() = runTest {
        // When - use the existing viewModel instance
        viewModel.setInitialList(null)

        // Then - wait for the async operation to complete
        advanceUntilIdle()
        // Verify that the current list name is set to the root list name
        val listName = viewModel.currentListName.first()
        assertEquals("Saved Places", listName)
    }

    @Test
    fun `setInitialList with valid listId should navigate to that list`() = runTest {
        // When
        viewModel.setInitialList(testList.id)

        // Then - wait for the async operation to complete
        advanceUntilIdle()
        assertEquals(testList, viewModel.currentList.value)
    }

    @Test
    fun `setInitialList with invalid listId should not navigate`() = runTest {
        // When
        viewModel.setInitialList("invalid-id")

        // Then
        assertEquals(null, viewModel.currentList.value)
    }

    @Test
    fun `currentListName should return current list name`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)

        // When
        val listName = viewModel.currentListName.first()

        // Then
        assertEquals("Test List", listName)
    }

    @Test
    fun `currentListName should return root list name when no current list`() = runTest {
        // Given - ViewModel initialized with no current list
        val freshViewModel = ManagePlacesViewModel(
            context = mockContext,
            savedPlaceRepository = mockSavedPlaceRepository,
            savedListRepository = mockSavedListRepository,
            listItemDao = mockListItemDao,
            cutPasteRepository = mockCutPasteRepository,
            favoritesFileSyncRepository = mockFavoritesFileSyncRepository
        )

        // When
        val listName = freshViewModel.currentListName.first()

        // Then
        assertEquals("Saved Places", listName)
    }

    @Test
    fun `toggleSelection should add item to selection when not selected`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)

        // When
        viewModel.toggleSelection(testPlace.id)

        // Then
        assertTrue(viewModel.selectedItems.value.contains(testPlace.id))
    }

    @Test
    fun `toggleSelection should remove item from selection when already selected`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.toggleSelection(testPlace.id)

        // Then
        assertFalse(viewModel.selectedItems.value.contains(testPlace.id))
    }

    @Test
    fun `selectAll should select all items in current list`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.selectAll()
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.selectedItems.value.contains(testPlace.id))
    }

    @Test
    fun `clearSelection should clear all selected items`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.clearSelection()

        // Then
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `isAllSelected should be true when all items are selected`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)  // Manually select the item
        advanceUntilIdle()

        // When
        val result = viewModel.isAllSelected.first()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isAllSelected should be false when not all items are selected`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        val result = viewModel.isAllSelected.first()

        // Then
        assertFalse(result)
    }

    @Test
    fun `deleteSelected should delete selected place`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.deleteSelected()
        advanceUntilIdle()

        // Then
        coVerify { mockSavedPlaceRepository.deletePlace(testPlace.id) }
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `deleteSelected should delete selected list`() = runTest {
        // Given - We need to mock a list that contains the nested list
        val listItemWithNestedList = ListItem(
            listId = testList.id,
            itemId = nestedList.id,
            itemType = ItemType.LIST,
            position = 1,
            addedAt = System.currentTimeMillis()
        )

        coEvery { mockSavedListRepository.getItemsInList(testList.id) } returns Result.success(
            listOf(listItem1, listItemWithNestedList)
        )

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(nestedList.id)

        // When
        viewModel.deleteSelected()
        advanceUntilIdle()

        // Then
        coVerify { mockSavedListRepository.deleteList(nestedList.id) }
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `createNewListWithSelected should create new list with selected items`() = runTest {
        // Given
        val newListId = "new-list-id"
        coEvery {
            mockSavedListRepository.createList(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Result.success(newListId)
        coEvery { mockListItemDao.getItemsInList(newListId) } returns emptyList()

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.createNewListWithSelected("New List")
        advanceUntilIdle()

        // Then
        coVerify { mockSavedListRepository.createList("New List", testList.id, null, false, false) }
        coVerify { mockListItemDao.moveItem(testPlace.id, newListId, 0) }
        assertTrue(viewModel.selectedItems.value.isEmpty()) // Selection should be cleared
    }

    @Test
    fun `cutSelected should update clipboard with selected items`() = runTest {
        // Given
        val clipboardFlow = MutableStateFlow<Set<ClipboardItem>>(emptySet())
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.cutSelected()
        advanceUntilIdle()

        // Then
        assertEquals(setOf(ClipboardItem(testPlace.id, ItemType.PLACE)), clipboardFlow.value)
        assertTrue(viewModel.selectedItems.value.isEmpty()) // Selection should be cleared
    }

    @Test
    fun `pasteSelected should move items from clipboard to current list`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testPlace.id, ItemType.PLACE))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then
        coVerify { mockListItemDao.moveItem(testPlace.id, testList.id, 1) }
        assertEquals(emptySet<ClipboardItem>(), clipboardFlow.value)
    }

    @Test
    fun `clipboard should reflect CutPasteRepository clipboard`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testPlace.id, ItemType.PLACE))
        every { mockCutPasteRepository.clipboard } returns MutableStateFlow(clipboardItems)

        // When
        val freshViewModel = ManagePlacesViewModel(
            context = mockContext,
            savedPlaceRepository = mockSavedPlaceRepository,
            savedListRepository = mockSavedListRepository,
            listItemDao = mockListItemDao,
            cutPasteRepository = mockCutPasteRepository,
            favoritesFileSyncRepository = mockFavoritesFileSyncRepository
        )

        // Then
        assertEquals(clipboardItems, freshViewModel.clipboard.first())
    }

    @Test
    fun `updatePlace should call repository with correct parameters`() = runTest {
        // Given
        val customName = "Custom Name"
        val customDescription = "Custom Description"
        val isPinned = true

        // When
        viewModel.updatePlace(testPlace.id, customName, customDescription, isPinned)
        advanceUntilIdle()

        // Then
        coVerify {
            mockSavedPlaceRepository.updatePlace(
                placeId = testPlace.id,
                customName = customName,
                customDescription = customDescription,
                isPinned = isPinned
            )
        }
    }

    @Test
    fun `updateList should call repository with correct parameters`() = runTest {
        // Given
        val newName = "New List Name"
        val newDescription = "New Description"

        // When
        viewModel.updateList(testList.id, newName, newDescription)
        advanceUntilIdle()

        // Then
        coVerify {
            mockSavedListRepository.updateList(
                listId = testList.id,
                name = newName,
                description = newDescription
            )
        }
    }

    @Test
    fun `getSavedPlace should return place when found`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)

        // When
        val result = viewModel.getSavedPlace(testPlace.id)

        // Then
        assertEquals(testPlace.id, result?.id)
    }

    @Test
    fun `getSavedPlace should return null when not found`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)

        // When
        val result = viewModel.getSavedPlace("non-existent-id")

        // Then
        assertEquals(null, result)
    }

    @Test
    fun `cutSelected should clear selection after cutting`() = runTest {
        // Given
        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.cutSelected()
        advanceUntilIdle() // Wait for the coroutine to complete

        // Then
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `createNewListWithSelected should clear selection after creating list`() = runTest {
        // Given
        val newListId = "new-list-id"
        coEvery {
            mockSavedListRepository.createList(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Result.success(newListId)
        coEvery { mockListItemDao.getItemsInList(newListId) } returns emptyList()

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.createNewListWithSelected("New List")
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `pasteSelected should handle multiple items correctly`() = runTest {
        // Given
        val testPlace2 = SavedPlace(
            id = "test-place-id-2",
            placeId = null,
            customName = null,
            customDescription = null,
            isPinned = false,
            name = "Test Place 2",
            type = "place",
            icon = "icon",
            latitude = 40.7128,
            longitude = -74.0060,
            houseNumber = null,
            road = null,
            city = null,
            state = null,
            postcode = null,
            country = null,
            countryCode = null,
            isTransitStop = false,
            transitStopId = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val clipboardItems = setOf(
            ClipboardItem(testPlace.id, ItemType.PLACE),
            ClipboardItem(testPlace2.id, ItemType.PLACE)
        )
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then
        coVerify { mockListItemDao.moveItem(testPlace.id, testList.id, 1) }
        coVerify { mockListItemDao.moveItem(testPlace2.id, testList.id, 2) }
        assertEquals(emptySet<ClipboardItem>(), clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should handle empty target list`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testPlace.id, ItemType.PLACE))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        // Mock empty target list
        coEvery { mockListItemDao.getItemsInList(testList.id) } returns emptyList()

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then
        coVerify { mockListItemDao.moveItem(testPlace.id, testList.id, 0) }
        assertEquals(emptySet<ClipboardItem>(), clipboardFlow.value)
    }

    @Test
    fun `deleteSelected should handle repository failures gracefully`() = runTest {
        // Given
        coEvery { mockSavedPlaceRepository.deletePlace(any()) } returns Result.failure(Exception("Delete failed"))

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)

        // When
        viewModel.deleteSelected()
        advanceUntilIdle()

        // Then - should still clear selection even if delete fails
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `createNewListWithSelected should handle creation failures`() = runTest {
        // Given
        coEvery {
            mockSavedListRepository.createList(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Result.failure(Exception("Creation failed"))

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.toggleSelection(testPlace.id)
        val initialSelection = viewModel.selectedItems.value

        // When
        viewModel.createNewListWithSelected("New List")
        advanceUntilIdle()

        // Then - selection should not be cleared if creation fails
        assertEquals(initialSelection, viewModel.selectedItems.value)
    }

    @Test
    fun `selectAll should handle empty list gracefully`() = runTest {
        // Given - empty list
        coEvery { mockSavedListRepository.getItemsInList(testList.id) } returns Result.success(
            emptyList()
        )

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.selectAll()
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun `pasteSelected should prevent pasting list into itself`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testList.id, ItemType.LIST))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        // Mock wouldCreateCycle to return true when trying to paste list into itself
        coEvery {
            mockSavedListRepository.wouldCreateCycle(testList.id, setOf(testList.id))
        } returns true

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should not move any items and clipboard should remain unchanged
        coVerify(exactly = 0) { mockListItemDao.moveItem(any(), any(), any()) }
        assertEquals(clipboardItems, clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should prevent pasting parent list into child list`() = runTest {
        // Given - set up a parent-child relationship
        val parentList = SavedList(
            id = "parent-list-id",
            name = "Parent List",
            description = "Parent Description",
            isRoot = false,
            isCollapsed = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val childList = SavedList(
            id = "child-list-id",
            name = "Child List",
            description = "Child Description",
            isRoot = false,
            isCollapsed = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Mock parent list contains child list
        val parentToChildListItem = ListItem(
            listId = parentList.id,
            itemId = childList.id,
            itemType = ItemType.LIST,
            position = 0,
            addedAt = System.currentTimeMillis()
        )

        coEvery { mockSavedListRepository.getListById(parentList.id) } returns Result.success(
            parentList
        )
        coEvery { mockSavedListRepository.getListById(childList.id) } returns Result.success(
            childList
        )
        coEvery { mockSavedListRepository.getItemsInList(parentList.id) } returns Result.success(
            listOf(parentToChildListItem)
        )

        // Mock wouldCreateCycle to return true when trying to paste parent into child
        coEvery {
            mockSavedListRepository.wouldCreateCycle(childList.id, setOf(parentList.id))
        } returns true

        val clipboardItems = setOf(ClipboardItem(parentList.id, ItemType.LIST))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        viewModel.setInitialList(childList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should not move any items and clipboard should remain unchanged
        coVerify(exactly = 0) { mockListItemDao.moveItem(any(), any(), any()) }
        assertEquals(clipboardItems, clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should allow pasting place into list`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testPlace.id, ItemType.PLACE))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        // Places don't create cycles, so wouldCreateCycle should return false
        coEvery {
            mockSavedListRepository.wouldCreateCycle(testList.id, emptySet())
        } returns false

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should move the place and clear clipboard
        coVerify { mockListItemDao.moveItem(testPlace.id, testList.id, 1) }
        assertEquals(emptySet<ClipboardItem>(), clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should allow pasting list into different list without cycle`() = runTest {
        // Given
        val targetList = SavedList(
            id = "target-list-id",
            name = "Target List",
            description = "Target Description",
            isRoot = false,
            isCollapsed = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        coEvery { mockSavedListRepository.getListById(targetList.id) } returns Result.success(
            targetList
        )
        coEvery { mockSavedListRepository.getItemsInList(targetList.id) } returns Result.success(
            emptyList()
        )

        // Mock wouldCreateCycle to return false when no cycle would be created
        coEvery {
            mockSavedListRepository.wouldCreateCycle(targetList.id, setOf(testList.id))
        } returns false

        val clipboardItems = setOf(ClipboardItem(testList.id, ItemType.LIST))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        viewModel.setInitialList(targetList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should move the list and clear clipboard
        coVerify { mockListItemDao.moveItem(testList.id, targetList.id, 0) }
        assertEquals(emptySet<ClipboardItem>(), clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should allow pasting mixed items when no cycle`() = runTest {
        // Given
        val clipboardItems = setOf(
            ClipboardItem(testPlace.id, ItemType.PLACE),
            ClipboardItem(nestedList.id, ItemType.LIST)
        )
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        // Mock wouldCreateCycle to return false when no cycle would be created
        coEvery {
            mockSavedListRepository.wouldCreateCycle(testList.id, setOf(nestedList.id))
        } returns false

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should move both items and clear clipboard
        coVerify { mockListItemDao.moveItem(testPlace.id, testList.id, 1) }
        coVerify { mockListItemDao.moveItem(nestedList.id, testList.id, 2) }
        assertEquals(emptySet<ClipboardItem>(), clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should prevent pasting when any list would create cycle`() = runTest {
        // Given
        val anotherList = SavedList(
            id = "another-list-id",
            name = "Another List",
            description = "Another Description",
            isRoot = false,
            isCollapsed = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val clipboardItems = setOf(
            ClipboardItem(testPlace.id, ItemType.PLACE),
            ClipboardItem(testList.id, ItemType.LIST),
            ClipboardItem(anotherList.id, ItemType.LIST)
        )
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        // Mock wouldCreateCycle to return true because testList would create a cycle
        coEvery {
            mockSavedListRepository.wouldCreateCycle(
                testList.id,
                setOf(testList.id, anotherList.id)
            )
        } returns true

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should not move any items and clipboard should remain unchanged
        coVerify(exactly = 0) { mockListItemDao.moveItem(any(), any(), any()) }
        assertEquals(clipboardItems, clipboardFlow.value)
    }

    @Test
    fun `pasteSelected should set error message when cycle detected`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testList.id, ItemType.LIST))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        // Mock wouldCreateCycle to return true
        coEvery {
            mockSavedListRepository.wouldCreateCycle(testList.id, setOf(testList.id))
        } returns true

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should set error message
        val errorMessage = viewModel.errorMessage.value
        assertEquals("error", errorMessage)
    }

    @Test
    fun `pasteSelected should not set error message when no cycle detected`() = runTest {
        // Given
        val clipboardItems = setOf(ClipboardItem(testPlace.id, ItemType.PLACE))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow
        every { mockContext.getString(string.cannot_paste_a_list_into_itself_or_one_of_its_sublists) } returns "error"

        // Mock wouldCreateCycle to return false
        coEvery {
            mockSavedListRepository.wouldCreateCycle(testList.id, emptySet())
        } returns false

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()

        // When
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Then - should not set error message
        val errorMessage = viewModel.errorMessage.value
        assertEquals(null, errorMessage)
    }

    @Test
    fun `clearErrorMessage should clear the error message`() = runTest {
        // Given - set an error message first
        val clipboardItems = setOf(ClipboardItem(testList.id, ItemType.LIST))
        val clipboardFlow = MutableStateFlow(clipboardItems)
        every { mockCutPasteRepository.clipboard } returns clipboardFlow

        coEvery {
            mockSavedListRepository.wouldCreateCycle(testList.id, setOf(testList.id))
        } returns true

        viewModel.setInitialList(testList.id)
        advanceUntilIdle()
        viewModel.pasteSelected()
        advanceUntilIdle()

        // Verify error message is set
        assertEquals("error", viewModel.errorMessage.value)

        // When
        viewModel.clearErrorMessage()

        // Then - error message should be cleared
        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun `errorMessage should be null initially`() = runTest {
        // Given - fresh ViewModel
        val freshViewModel = ManagePlacesViewModel(
            context = mockContext,
            savedPlaceRepository = mockSavedPlaceRepository,
            savedListRepository = mockSavedListRepository,
            listItemDao = mockListItemDao,
            cutPasteRepository = mockCutPasteRepository,
            favoritesFileSyncRepository = mockFavoritesFileSyncRepository
        )

        // When
        val errorMessage = freshViewModel.errorMessage.value

        // Then - should be null initially
        assertEquals(null, errorMessage)
    }
}
