package com.example.iptv.ui.browser

import com.example.iptv.data.model.M3UChannel
import org.junit.Assert.*
import org.junit.Test

class AdultFilterTest {
    private fun item(name: String, group: String, vod: Boolean = false, stream: String = name) =
        M3UChannel(name, group, null, "https://provider.example/$stream", null, null, null, vod)

    @Test fun matchingGroupsAreHiddenAcrossSectionsRecentsFavoritesAndLateSearchWithoutLosingIds() {
        val source = listOf(item("TV", "TR News"), item("Hidden TV", "TR aDuLt"),
            item("Film", "Films", true), item("Hidden film", "Adults", true),
            item("Adult in name", "XXX", stream = "adult/path"),
            item("Shared safe", "TR News", stream = "duplicate"), item("Shared hidden", "ADULT", stream = "duplicate"))
        val catalog = BrowserCatalog.create(source)
        val browser = BrowserController().apply { load(catalog, source) }
        val ids = catalog.channels.map { it.id }.toSet()
        browser.preferences(GridDensity.COMPACT, ids)
        assertEquals(listOf("Recently viewed", "TR News", "XXX"), browser.state.value.categories.map { it.name })
        assertEquals(3, browser.state.value.categories.first().channels.size)
        assertEquals(listOf(2, 1), browser.state.value.categories.drop(1).map { it.channels.size })
        browser.open(BrowserDestination.VOD)
        assertEquals(listOf("Recently viewed", "Films"), browser.state.value.categories.map { it.name })
        assertEquals(listOf("Film"), browser.state.value.categories.first().channels.map { it.channel.name })
        browser.open(BrowserDestination.FAVORITES)
        val allowed = listOf(source[0], source[2], source[4], source[5])
        assertEquals(allowed, browser.state.value.items.map { it.entry!!.channel })
        assertEquals(ids, browser.state.value.favorites)
        browser.query("i")
        val late = browser.search("i")
        browser.open(BrowserDestination.SEARCH)
        browser.searchResults("i", late)
        assertEquals(allowed.filter { it.name.contains("i", true) }, browser.state.value.items.map { it.entry!!.channel })
        browser.preferences(GridDensity.COMPACT, ids, false)
        assertEquals(source.filter { it.name.contains("i", true) }, browser.state.value.items.map { it.entry!!.channel })
        browser.preferences(GridDensity.COMPACT, ids, true)
        browser.searchResults("i", late)
        assertTrue(browser.state.value.items.none { it.entry!!.channel.groupTitle.contains("adult", true) })
        browser.preferences(GridDensity.COMPACT, ids, false)
        browser.open(BrowserDestination.FAVORITES)
        assertEquals(catalog.channels.map { it.id }, browser.state.value.items.map { it.id })
        browser.open(BrowserDestination.CATEGORIES)
        assertEquals(5, browser.state.value.categories.first().channels.size)
        assertEquals(ids, catalog.channels.map { it.id }.toSet())
    }

    @Test fun hidingThePreviousCategoryRecoversMatchingRootAndRefreshReappliesVisibility() {
        val hidden = item("Film", "Adult films", true)
        val safe = item("TV", "News")
        val original = BrowserCatalog.create(listOf(hidden, safe))
        val browser = BrowserController().apply { load(original, listOf(hidden)) }
        browser.preferences(GridDensity.STANDARD, original.channels.map { it.id }.toSet(), false)
        browser.open(BrowserDestination.VOD)
        browser.focusGrid("group:Adult films")
        browser.openCategory("group:Adult films")
        browser.focusGrid(original.channels[0].id)
        browser.open(BrowserDestination.FAVORITES)
        browser.open(BrowserDestination.SEARCH)
        browser.query("Film"); browser.searchResults("Film", browser.search("Film"))
        browser.open(BrowserDestination.SETTINGS)
        browser.focusArea(BrowserFocus.ADULT_FILTER)
        browser.preferences(GridDensity.STANDARD, browser.state.value.favorites, true)
        assertEquals(BrowserFocus.ADULT_FILTER, browser.state.value.bookmark.area)
        browser.back()
        assertTrue(browser.state.value.items.isEmpty())
        assertEquals(BrowserFocus.QUERY, browser.state.value.bookmark.area)
        browser.back(); browser.back()
        assertEquals(BrowserLocation(BrowserDestination.VOD), browser.state.value.location)
        assertEquals(listOf("recent"), browser.state.value.items.map { it.id })
        assertEquals("recent", browser.state.value.bookmark.focusedId)
        browser.openCategory("recent")
        assertTrue(browser.state.value.items.isEmpty())
        assertEquals(BrowserFocus.SIDEBAR, browser.state.value.bookmark.area)
        browser.load(BrowserCatalog.create(listOf(hidden.copy(name = "Changed"), safe)), listOf(hidden))
        assertTrue(browser.state.value.items.isEmpty())
        browser.preferences(GridDensity.STANDARD, browser.state.value.favorites, false)
        assertEquals("Changed", browser.state.value.items.single().entry!!.channel.name)
        browser.reset()
        browser.load(original, listOf(hidden))
        browser.open(BrowserDestination.VOD)
        assertEquals(listOf("recent"), browser.state.value.items.map { it.id })
        assertTrue(browser.state.value.categories.single().channels.isEmpty())
    }
}
