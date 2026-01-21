package com.nendo.argosy.ui.screens.settings.menu

import com.nendo.argosy.ui.components.ListSection

enum class DisabledBehavior {
    HIDDEN,
    LOCKED
}

class SettingsLayout<Item, State>(
    private val allItems: List<Item>,
    private val isFocusable: (Item) -> Boolean,
    private val visibleWhen: (Item, State) -> Boolean,
    private val disabledBehavior: (Item) -> DisabledBehavior = { DisabledBehavior.HIDDEN },
    private val sectionOf: (Item) -> String? = { null }
) {
    fun visibleItems(state: State): List<Item> =
        allItems.filter { visibleWhen(it, state) || disabledBehavior(it) == DisabledBehavior.LOCKED }

    fun focusableItems(state: State): List<Item> =
        visibleItems(state).filter { isFocusable(it) }

    fun focusIndexOf(item: Item, state: State): Int =
        focusableItems(state).indexOf(item)

    fun itemAtFocusIndex(index: Int, state: State): Item? =
        focusableItems(state).getOrNull(index)

    fun maxFocusIndex(state: State): Int =
        (focusableItems(state).size - 1).coerceAtLeast(0)

    fun focusToListIndex(focusIndex: Int, state: State): Int {
        val item = focusableItems(state).getOrNull(focusIndex) ?: return focusIndex
        return visibleItems(state).indexOf(item)
    }

    fun buildSections(state: State): List<ListSection> {
        val visible = visibleItems(state)
        val focusable = focusableItems(state)
        val sectionNames = visible.mapNotNull { sectionOf(it) }.distinct()

        return sectionNames.mapNotNull { sectionName ->
            val sectionItems = visible.filter { sectionOf(it) == sectionName }
            val sectionFocusable = focusable.filter { sectionOf(it) == sectionName }
            if (sectionItems.isEmpty() || sectionFocusable.isEmpty()) return@mapNotNull null

            ListSection(
                listStartIndex = visible.indexOf(sectionItems.first()),
                listEndIndex = visible.indexOf(sectionItems.last()),
                focusStartIndex = focusable.indexOf(sectionFocusable.first()),
                focusEndIndex = focusable.indexOf(sectionFocusable.last())
            )
        }
    }
}
