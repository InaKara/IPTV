package com.example.iptv.ui.home

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.browser.BrowserDestination
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class SubmittedSearchTest {
    @Test fun editingDoesNotSubmitAndCancelClearAndObsoleteNavigationKeepResultsSafe() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        val store = ViewModelStore().apply { put("home", vm) }
        try {
            vm.loadChannels(listOf("News", "Film").map {
                M3UChannel(it, "Group", null, "https://provider.example/$it", null, null, null, false)
            })
            withContext(Dispatchers.Default) { withTimeout(5000) { vm.uiState.first { it is HomeUiState.Success } } }
            vm.browser.open(BrowserDestination.SEARCH)
            for (draft in listOf("N", "Ne", "New", "News")) vm.editSearch(draft)
            runCurrent()
            assertEquals("", vm.browser.state.value.query)
            assertFalse(vm.browser.state.value.searching)
            assertTrue(vm.browser.state.value.items.isEmpty())
            vm.submitSearch()
            withContext(Dispatchers.Default) { withTimeout(5000) { vm.browser.state.first { !it.searching && it.items.size == 1 } } }
            assertEquals("News", vm.browser.state.value.query)
            val result = vm.browser.state.value.items.single()
            vm.browser.focusGrid(result.id); vm.browser.scroll(0, 7)
            vm.editSearch("Film"); vm.cancelSearchDraft()
            assertEquals("News", vm.browser.state.value.draftQuery)
            assertEquals(result.id, vm.browser.state.value.bookmark.focusedId)
            assertEquals(7, vm.browser.state.value.bookmark.scrollOffset)
            vm.editSearch("")
            assertTrue(vm.browser.state.value.items.isEmpty()); assertEquals("", vm.browser.state.value.query)
            vm.editSearch("Film"); vm.submitSearch()
            vm.browser.open(BrowserDestination.SETTINGS); vm.cancelSearch()
            // Main work is queued: cancellation definitely precedes worker launch/publication.
            runCurrent()
            assertFalse(vm.browser.state.value.searching)
            assertEquals(BrowserDestination.SETTINGS, vm.browser.state.value.location.destination)
            vm.browser.back()
            assertTrue("Abandoned search cannot publish after returning", vm.browser.state.value.items.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
