package moe.antimony.hoshi.features.reader

import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes

/**
 * Entries the app adds to the WebView's native text-selection toolbar.
 *
 * The app replaces the platform's own entries (copy / share / select all / web search) with its own
 * actions, so these ids live in their own range and must never overlap
 * [ReaderHighlightSelectionMenu]. The highlight entry belongs to the app too: it opens the colour
 * picker and has to survive the platform-item filter below.
 */
internal object ReaderSelectionActionMenu {
    const val groupId = 0x4149
    const val aiGrammarItemId = 0x414900
    const val copyItemId = 0x414901

    val actionModeItems: List<ReaderSelectionActionModeItem> =
        listOf(
            ReaderSelectionActionModeItem(
                id = aiGrammarItemId,
                order = Menu.NONE,
                showAsAction = MenuItem.SHOW_AS_ACTION_ALWAYS,
                titleRes = moe.antimony.hoshi.R.string.ai_grammar_action,
            ),
            ReaderSelectionActionModeItem(
                id = copyItemId,
                order = Menu.NONE + 1,
                showAsAction = MenuItem.SHOW_AS_ACTION_ALWAYS,
                titleRes = moe.antimony.hoshi.R.string.action_copy,
            ),
        )

    /** Every toolbar id owned by the app, and therefore never hidden by [platformActionModeItemsToHide]. */
    val ownedItemIds: Set<Int> =
        (actionModeItems.map { it.id } + ReaderHighlightSelectionMenu.actionModeItems.map { it.id }).toSet()

    /**
     * Platform entries to hide so the toolbar only offers app actions.
     *
     * Hiding is preferred over removing: the WebView keeps owning its own menu items, so its internal
     * bookkeeping stays intact and a platform that re-adds them merely degrades to a crowded toolbar
     * instead of breaking selection. Pure so it can be unit tested without an Android [Menu].
     *
     * Entries the user may still see — such as the system's own translate / read-aloud suggestions —
     * are not part of this menu at all and therefore cannot be hidden from here.
     */
    fun platformActionModeItemsToHide(existingItemIds: List<Int>): List<Int> =
        existingItemIds.filterNot { it in ownedItemIds }
}

internal data class ReaderSelectionActionModeItem(
    val id: Int,
    val order: Int,
    val showAsAction: Int,
    @StringRes val titleRes: Int,
)
