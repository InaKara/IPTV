package com.example.iptv.ui.browser

import com.example.iptv.data.model.M3UChannel
import org.junit.Assert.*
import org.junit.Test

class BrowserStateTest {
    private fun channel(name: String, group: String = "Group", stream: String = name) =
        M3UChannel(name, group, null, "https://provider.example/$stream", null, "duplicate-tvg-id", null, false)

    @Test fun hierarchyAndNestedAuxiliaryBackPreserveSeparateBookmarks() {
        val index = BrowserCatalog.create((0..60).map { channel("Channel$it") })
        val browser = BrowserController().apply { load(index, emptyList()) }
        val category = browser.state.value.categories.last()
        browser.focusGrid(category.id)
        browser.scroll(1, 0)
        browser.openCategory(category.id)
        browser.focusGrid(index.channels[40].id)
        browser.scroll(30, 7)
        browser.open(BrowserDestination.FAVORITES)
        browser.open(BrowserDestination.FAVORITES)
        browser.open(BrowserDestination.SEARCH)
        browser.query("Channel4")
        browser.searchResults("Channel4", browser.search("Channel4"))
        browser.open(BrowserDestination.SETTINGS)
        assertTrue(browser.back())
        assertEquals(BrowserDestination.SEARCH, browser.state.value.location.destination)
        assertEquals("Channel4", browser.state.value.query)
        assertTrue(browser.back())
        assertEquals(BrowserDestination.FAVORITES, browser.state.value.location.destination)
        assertTrue(browser.back())
        assertEquals(category.id, browser.state.value.location.categoryId)
        assertEquals(index.channels[40].id, browser.state.value.bookmark.focusedId)
        assertEquals(30, browser.state.value.bookmark.firstVisibleIndex)
        assertEquals(7, browser.state.value.bookmark.scrollOffset)
        assertTrue(browser.back())
        assertEquals(category.id, browser.state.value.bookmark.focusedId)
        assertEquals(1, browser.state.value.bookmark.firstVisibleIndex)
        assertFalse(browser.back())
    }

    @Test fun identitiesKeepDuplicatesSeparateWhileMetadataAndOrderingChange() {
        val a = channel("One", stream = "same")
        val source = listOf(a, a.copy(), channel("Two"), channel("Third", group = "Other", stream = "same"))
        val index = BrowserCatalog.create(source)
        assertEquals(4, index.channels.map { it.id }.distinct().size)
        assertTrue(index.channels.none { "https" in it.id || "provider" in it.id })
        val updated = BrowserCatalog.create(listOf(source[2], a.copy(name = "Renamed"), a.copy(), source[3]))
        assertEquals(index.channels[0].id, updated.channels[1].id)
        assertEquals(index.channels[1].id, updated.channels[2].id)
        val browser = BrowserController().apply { load(index, emptyList()); openCategory(index.categories.first { it.name == "Group" }.id) }
        browser.focusGrid(index.channels[1].id)
        browser.load(updated, emptyList())
        assertEquals(2, restoredIndex(browser.state.value.items, browser.state.value.bookmark.focusedId, 0))
        browser.load(BrowserCatalog.create(listOf(source[2])), emptyList())
        assertEquals(0, restoredIndex(browser.state.value.items, browser.state.value.bookmark.focusedId, 4))
    }

    @Test fun remoteTargetsStopAtBoundariesAndScrollOnlyNecessaryRows() {
        for (density in GridDensity.values()) {
            val columns = density.columns
            assertNull(gridTarget(0, 22, columns, horizontal = -1))
            assertNull(gridTarget(columns - 1, 22, columns, horizontal = 1))
            assertNull(gridTarget(0, 22, columns, vertical = -1))
            assertEquals(columns, gridTarget(0, 22, columns, vertical = 1))
            assertNull(gridTarget(21, 22, columns, vertical = 1))
            assertNull(gridTarget(0, 0, columns, horizontal = 1))
            assertEquals(columns, firstRowForTarget(columns * density.rows, 0, columns, density.rows))
            assertEquals(0, firstRowForTarget(columns, 0, columns, density.rows))
        }
        assertNull(gridTarget(4, 7, 5, vertical = 1))
        assertNull(gridTarget(6, 7, 5, horizontal = 1))
    }

    @Test fun cachedSearchIsCaseInsensitiveAndRejectsStaleOrBlankQueries() {
        val index = BrowserCatalog.create(listOf(channel("News World"), channel("Music"), channel("Local NEWS")))
        val browser = BrowserController().apply { load(index, emptyList()); open(BrowserDestination.SEARCH) }
        assertTrue(browser.search("").isEmpty())
        assertTrue(browser.search("   ").isEmpty())
        assertEquals(listOf("News World", "Local NEWS"), browser.search(" news ").map { it.channel.name })
        browser.query("Music")
        browser.searchResults("news", browser.search("news"))
        assertTrue(browser.state.value.items.isEmpty())
        browser.searchResults("Music", browser.search("Music"))
        assertEquals("Music", browser.state.value.items.single().entry!!.channel.name)
        assertTrue(browser.search("missing").isEmpty())
    }

    @Test fun favoritesUsePlaylistOrderAndRetainUnavailableReferences() {
        val index = BrowserCatalog.create(listOf(channel("One"), channel("Two"), channel("Three")))
        val favorites = linkedSetOf(index.channels[2].id, "missing", index.channels[0].id)
        val browser = BrowserController().apply {
            load(index, emptyList()); preferences(GridDensity.STANDARD, favorites); open(BrowserDestination.FAVORITES)
        }
        assertEquals(listOf("One", "Three"), browser.state.value.items.map { it.entry!!.channel.name })
        browser.focusGrid(index.channels[2].id)
        browser.preferences(GridDensity.COMPACT, favorites - index.channels[2].id)
        assertEquals(listOf("One"), browser.state.value.items.map { it.entry!!.channel.name })
        assertEquals(setOf("missing", index.channels[0].id), browser.state.value.favorites)
        assertEquals(0, restoredIndex(browser.state.value.items, browser.state.value.bookmark.focusedId, 1))
        browser.open(BrowserDestination.CATEGORIES)
        assertNull(browser.state.value.location.categoryId)
        assertEquals("Recently viewed", browser.state.value.items.first().category!!.name)
    }
}
