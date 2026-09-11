package moe.antimony.hoshi.features.reader

import android.view.Menu
import android.view.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionActionMenuTest {
    @Test
    fun exposesCopyAsLeadingVisibleToolbarAction() {
        val item = ReaderSelectionActionMenu.actionModeItems.single()

        assertEquals(ReaderSelectionActionMenu.copyItemId, item.id)
        assertEquals(Menu.NONE, item.order)
        assertEquals(MenuItem.SHOW_AS_ACTION_ALWAYS, item.showAsAction)
    }

    @Test
    fun keepsSelectionItemIdsClearOfTheHighlightRange() {
        val selectionIds = ReaderSelectionActionMenu.actionModeItems.map { it.id }.toSet()
        val highlightIds =
            (ReaderHighlightSelectionMenu.actionModeItems.map { it.id } +
                ReaderHighlightSelectionMenu.colorItems.map { it.id }).toSet()

        assertTrue(selectionIds.intersect(highlightIds).isEmpty())
        assertTrue(ReaderSelectionActionMenu.groupId !in selectionIds)
    }

    @Test
    fun hidesPlatformItemsWhileKeepingAppOwnedOnes() {
        val owned = ReaderSelectionActionMenu.ownedItemIds.toList()
        val platform = listOf(1, 2, 3)

        assertEquals(
            platform,
            ReaderSelectionActionMenu.platformActionModeItemsToHide(platform + owned),
        )
        assertTrue(ReaderSelectionActionMenu.platformActionModeItemsToHide(owned).isEmpty())
    }

    @Test
    fun treatsTheHighlightEntryAsAppOwned() {
        assertTrue(
            ReaderHighlightSelectionMenu.parentItemId in ReaderSelectionActionMenu.ownedItemIds,
        )
    }
}
