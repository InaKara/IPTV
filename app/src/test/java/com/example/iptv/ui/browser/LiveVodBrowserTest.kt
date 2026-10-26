package com.example.iptv.ui.browser

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.parser.M3UParser
import org.junit.Assert.*
import org.junit.Test

class LiveVodBrowserTest {
    private fun item(name: String, vod: Boolean, group: String = "Mixed") =
        M3UChannel(name, group, null, "https://provider.example/$name", null, null, null, vod)

    @Test fun parsedMixedGroupsKeepTypeCountsOrderAndSharedFavoriteSearchIdentities() {
        val source = M3UParser().parse("""
            #EXTM3U
            #EXTINF:-1 group-title="TR Mixed",Live item A
            https://provider.example/live/a.ts
            #EXTINF:-1 group-title="TR Mixed",Film item A
            https://provider.example/movie/a.mp4
            #EXTINF:-1 group-title="News",News item
            https://provider.example/live/news.ts
            #EXTINF:-1 group-title="TR Mixed",Live item B
            https://provider.example/live/b.ts
            #EXTINF:-1 group-title="TR Mixed",Film item B
            https://provider.example/movie/b.mp4
            #EXTINF:-1 group-title="Series",Series item
            https://provider.example/series/episode.mkv
        """.trimIndent().byteInputStream())
        val catalog = BrowserCatalog.create(source)
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR); load(catalog, listOf(source[4], source[0], source[2])) }
        val live = browser.state.value.categories
        assertEquals(listOf("Recently viewed", "TR Mixed", "News"), live.map { it.name })
        assertEquals(listOf("Live item A", "News item"), live.first().channels.map { it.channel.name })
        assertEquals(listOf("Live item A", "Live item B"), live[1].channels.map { it.channel.name })
        browser.openCategory("group:TR Mixed")
        assertEquals(listOf(catalog.channels[0].id, catalog.channels[3].id), browser.state.value.items.map { it.id })
        browser.open(BrowserDestination.VOD)
        val vod = browser.state.value.categories
        assertEquals(listOf("Recently viewed", "TR Mixed", "Series"), vod.map { it.name })
        assertEquals(listOf("Film item B"), vod.first().channels.map { it.channel.name })
        assertEquals(2, vod[1].channels.size)
        browser.openCategory("group:TR Mixed")
        assertEquals(listOf(catalog.channels[1].id, catalog.channels[4].id), browser.state.value.items.map { it.id })
        assertEquals(catalog.channels.map { it.id }.toSet(),
            (live.drop(1) + vod.drop(1)).flatMap { it.channels }.map { it.id }.toSet())
        browser.preferences(GridDensity.STANDARD, linkedSetOf(catalog.channels[4].id, catalog.channels[0].id, "unavailable"))
        browser.open(BrowserDestination.FAVORITES)
        assertEquals(listOf("Live item A", "Film item B"), browser.state.value.items.map { it.entry!!.channel.name })
        assertTrue("unavailable" in browser.state.value.favorites)
        assertEquals(source, browser.search("item").map { it.channel })
    }

    @Test fun matchingSectionRootsAndNestedReturnsRetainIndependentBookmarks() {
        val source = (0..29).flatMap { listOf(item("TV$it", false), item("Film$it", true)) }
        val catalog = BrowserCatalog.create(source)
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR); load(catalog, emptyList()) }
        assertEquals(BrowserDestination.CATEGORIES, browser.state.value.location.destination)
        assertEquals(BrowserFocus.SIDEBAR, browser.state.value.bookmark.area)
        assertEquals(0, browser.state.value.bookmark.sidebarIndex)
        browser.focusGrid("group:Mixed"); browser.scroll(1, 2)
        browser.openCategory("group:Mixed")
        val live = browser.state.value.items[21].id
        browser.focusGrid(live); browser.scroll(20, 4)
        val liveLocation = browser.state.value.location
        browser.open(BrowserDestination.VOD)
        browser.focusGrid("group:Mixed"); browser.scroll(1, 6)
        browser.openCategory("group:Mixed")
        val film = browser.state.value.items[25].id
        browser.focusGrid(film); browser.scroll(20, 8)
        assertNotEquals(liveLocation.key, browser.state.value.location.key)
        browser.open(BrowserDestination.FAVORITES)
        browser.open(BrowserDestination.FAVORITES)
        browser.open(BrowserDestination.SEARCH)
        browser.query("Film"); browser.searchResults("Film", browser.search("Film"))
        browser.open(BrowserDestination.SETTINGS)
        browser.back(); assertEquals(BrowserDestination.SEARCH, browser.state.value.location.destination)
        browser.back(); assertEquals(BrowserDestination.FAVORITES, browser.state.value.location.destination)
        browser.back(); assertEquals(BrowserLocation(BrowserDestination.VOD, "group:Mixed"), browser.state.value.location)
        assertEquals(film, browser.state.value.bookmark.focusedId)
        assertEquals(8, browser.state.value.bookmark.scrollOffset)
        browser.back()
        assertEquals(BrowserLocation(BrowserDestination.VOD), browser.state.value.location)
        assertEquals("group:Mixed", browser.state.value.bookmark.focusedId)
        assertEquals(6, browser.state.value.bookmark.scrollOffset)
        assertFalse(browser.back())
        browser.open(BrowserDestination.CATEGORIES)
        assertEquals(2, browser.state.value.bookmark.scrollOffset)
        browser.openCategory("group:Mixed")
        assertEquals(live, browser.state.value.bookmark.focusedId)
        assertEquals(4, browser.state.value.bookmark.scrollOffset)
        assertTrue(browser.state.value.items.all { it.entry!!.channel.isVod.not() })
        browser.back(); assertFalse(browser.back())
    }

    @Test fun refreshReclassifiesWithoutLosingFavoriteIdsAndEmptySectionResetIsSafe() {
        val television = item("TV", false)
        val film = item("Film", true)
        val original = BrowserCatalog.create(listOf(television, film))
        val browser = BrowserController().apply { open(BrowserDestination.CATEGORIES); focusArea(BrowserFocus.SIDEBAR);
            load(original, listOf(film)); preferences(GridDensity.LARGE, setOf(original.channels[1].id))
            open(BrowserDestination.VOD); openCategory("group:Mixed"); focusGrid(original.channels[1].id)
        }
        val renamed = film.copy(name = "Film renamed")
        browser.load(BrowserCatalog.create(listOf(television, renamed)), listOf(film))
        assertEquals(original.channels[1].id, browser.state.value.items.single().id)
        assertEquals("Film renamed", browser.state.value.items.single().entry!!.channel.name)
        val reclassified = renamed.copy(isVod = false)
        browser.load(BrowserCatalog.create(listOf(television, reclassified)), listOf(film))
        assertEquals(BrowserLocation(BrowserDestination.VOD), browser.state.value.location)
        assertEquals(listOf("Recently viewed"), browser.state.value.categories.map { it.name })
        assertTrue(browser.state.value.categories.single().channels.isEmpty())
        browser.openCategory("recent"); assertTrue(browser.state.value.items.isEmpty())
        browser.open(BrowserDestination.FAVORITES)
        assertEquals(original.channels[1].id, browser.state.value.items.single().id)
        browser.back()
        assertEquals(BrowserLocation(BrowserDestination.VOD, "recent"), browser.state.value.location)
        browser.open(BrowserDestination.CATEGORIES)
        assertEquals("Film renamed", browser.state.value.categories.first().channels.single().channel.name)
        browser.reset()
        assertTrue(browser.search("Film").isEmpty())
        browser.preferences(GridDensity.STANDARD, emptySet())
        assertTrue(browser.state.value.items.isEmpty())
        browser.load(original, emptyList())
        assertEquals(BrowserLocation(BrowserDestination.FAVORITES), browser.state.value.location)
        assertEquals(BrowserDestination.FAVORITES.ordinal, browser.state.value.bookmark.sidebarIndex)
        assertFalse(browser.state.value.canGoBack)
        assertEquals(BrowserFocus.SIDEBAR, browser.state.value.bookmark.area)
        assertTrue(browser.state.value.favorites.isEmpty())
    }
}
