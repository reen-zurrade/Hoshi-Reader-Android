package moe.antimony.hoshi.features.reader

import android.view.Menu
import android.view.MenuItem
import moe.antimony.hoshi.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionActionMenuTest {
    @Test
    fun exposesGrammarAndCopyAsLeadingVisibleToolbarActions() {
        val items = ReaderSelectionActionMenu.actionModeItems

        assertEquals(
            listOf(ReaderSelectionActionMenu.aiGrammarItemId, ReaderSelectionActionMenu.copyItemId),
            items.map { it.id },
        )
        assertEquals(listOf(Menu.NONE, Menu.NONE + 1), items.map { it.order })
        assertTrue(items.all { it.showAsAction == MenuItem.SHOW_AS_ACTION_ALWAYS })
    }

    @Test
    fun labelsEachActionWithItsOwnLocalizedString() {
        val titles = ReaderSelectionActionMenu.actionModeItems.associate { it.id to it.titleRes }

        assertEquals(R.string.ai_grammar_action, titles[ReaderSelectionActionMenu.aiGrammarItemId])
        assertEquals(R.string.action_copy, titles[ReaderSelectionActionMenu.copyItemId])
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
