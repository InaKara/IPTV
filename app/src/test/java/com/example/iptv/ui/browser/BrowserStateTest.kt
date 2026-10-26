package com.example.iptv.ui.browser

import com.example.iptv.data.model.M3UChannel
import org.junit.Assert.*
import org.junit.Test

class BrowserStateTest {
    private fun channel(name: String, group: String = "Group", stream: String = name) =
        M3UChannel(name, group, null, "https://provider.example/$stream", null, "duplicate-tvg-id", null, false)

    @Test fun freshFavoritesKeepVisibleCanonicalOrderAndLateReferencesAtRoot() {
        val index = BrowserCatalog.create(listOf(channel("One"), channel("Hidden", "Adult films"), channel("Three")))
        val ids = linkedSetOf(index.channels[2].id, index.channels[1].id, "missing", "cached-api", index.channels[0].id)
        val browser = BrowserController()
        assertEquals(BrowserDestination.FAVORITES, browser.state.value.location.destination)
        assertEquals(BrowserDestination.FAVORITES.ordinal, browser.state.value.bookmark.sidebarIndex)
        assertEquals(BrowserFocus.SIDEBAR, browser.state.value.bookmark.area)
        assertFalse(browser.state.value.canGoBack); assertFalse(browser.back())
        browser.preferences(GridDensity.STANDARD, ids)
        browser.load(index, emptyList())
        assertEquals(listOf("One", "Three"), browser.state.value.items.map { it.entry!!.channel.name })
        assertEquals(ids, browser.state.value.favorites)
        browser.supplemental(listOf(BrowserChannel("cached-api", channel("Cached", "Films").copy(isVod = true))))
        assertEquals(listOf("One", "Three", "Cached"), browser.state.value.items.map { it.entry!!.channel.name })
        assertEquals(BrowserFocus.SIDEBAR, browser.state.value.bookmark.area)
        assertFalse(browser.state.value.canGoBack)
        browser.preferences(GridDensity.STANDARD, ids, false)
        assertEquals(listOf("One", "Hidden", "Three", "Cached"), browser.state.value.items.map { it.entry!!.channel.name })
        browser.supplemental(emptyList())
        browser.preferences(GridDensity.STANDARD, setOf(index.channels[1].id), true)
        assertTrue(browser.state.value.items.isEmpty())
        assertEquals(BrowserDestination.FAVORITES, browser.state.value.location.destination)
        assertFalse(browser.back())
        browser.preferences(GridDensity.STANDARD, ids, true)
        browser.open(BrowserDestination.CATEGORIES); browser.open(BrowserDestination.FAVORITES)
        assertEquals("Ordinary Favorites still enters its populated grid", BrowserFocus.GRID, browser.state.value.bookmark.area)
        assertTrue(browser.back())
    }

    @Test fun freshAndSectionFavoritesKeepDistinctBackTargetsAcrossNestedReturnsAndReset() {
        val index = BrowserCatalog.create((0..50).map { channel("Channel$it") })
        val browser = BrowserController()
        browser.load(index, emptyList()); browser.preferences(GridDensity.COMPACT, index.channels.map { it.id }.toSet())
        browser.focusGrid(index.channels[40].id); browser.scroll(30, 7)
        val favoriteBookmark = browser.state.value.bookmark
        browser.open(BrowserDestination.SEARCH); browser.open(BrowserDestination.SETTINGS)
        assertTrue(browser.back()); assertTrue(browser.back())
        assertEquals(favoriteBookmark, browser.state.value.bookmark)
        assertFalse(browser.state.value.canGoBack); assertFalse(browser.back())
        browser.openLibraryDetails(); browser.closeLibraryDetails()
        browser.open(BrowserDestination.SEARCH); browser.open(BrowserDestination.FAVORITES)
        assertFalse("An auxiliary return cannot invent a predecessor for initial Favorites", browser.back())
        browser.open(BrowserDestination.CATEGORIES); browser.openCategory("group:Group")
        browser.focusGrid(index.channels[41].id); browser.scroll(25, 9)
        val section = browser.state.value.location; val sectionBookmark = browser.state.value.bookmark
        browser.open(BrowserDestination.FAVORITES); browser.open(BrowserDestination.FAVORITES)
        assertTrue(browser.state.value.canGoBack); assertTrue(browser.back())
        assertEquals(section, browser.state.value.location); assertEquals(sectionBookmark, browser.state.value.bookmark)
        browser.load(index, emptyList())
        assertEquals(section, browser.state.value.location); assertEquals(sectionBookmark, browser.state.value.bookmark)
        browser.reset(); browser.load(index, emptyList())
        assertEquals(BrowserDestination.FAVORITES, browser.state.value.location.destination)
        assertTrue(browser.state.value.items.isEmpty()); assertFalse(browser.state.value.canGoBack)
        assertEquals(BrowserDestination.FAVORITES.ordinal, browser.state.value.bookmark.sidebarIndex)
        assertEquals(BrowserDestination.FAVORITES, BrowserController().state.value.location.destination)
        assertEquals("Unrelated Live TV fallback locations remain explicit", BrowserDestination.CATEGORIES, BrowserLocation().destination)
    }

    @Test fun hierarchyAndNestedAuxiliaryBackPreserveSeparateBookmarks() {
        val index = BrowserCatalog.create((0..60).map { channel("Channel$it") })
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR); load(index, emptyList()) }
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
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR); load(index, emptyList()); openCategory(index.categories.first { it.name == "Group" }.id) }
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
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR); load(index, emptyList()); open(BrowserDestination.SEARCH) }
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
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR);
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

    @Test fun refreshRebuildsSearchRetainsBookmarksAndRecoversRemovedCategory() {
        val source = listOf(channel("News"), channel("Music"))
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR); load(BrowserCatalog.create(source), emptyList()) }
        browser.openCategory("group:Group")
        val focused = browser.state.value.items.last().id
        browser.focusGrid(focused); browser.scroll(1, 3)
        browser.open(BrowserDestination.SEARCH); browser.query("News")
        browser.load(BrowserCatalog.create(listOf(source[0].copy(name = "News HD"), source[1])), emptyList())
        assertEquals("News", browser.state.value.query)
        assertEquals("News HD", browser.state.value.items.single().entry!!.channel.name)
        browser.back()
        assertEquals("group:Group", browser.state.value.location.categoryId)
        assertEquals(focused, browser.state.value.bookmark.focusedId)
        assertEquals(3, browser.state.value.bookmark.scrollOffset)
        browser.load(BrowserCatalog.create(listOf(channel("Other", "Other"))), emptyList())
        assertNull(browser.state.value.location.categoryId)
        assertEquals(listOf("Recently viewed", "Other"), browser.state.value.items.map { it.category!!.name })
    }
}
