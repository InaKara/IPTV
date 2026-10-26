package com.example.iptv.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
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
class HomeBehaviorTest {
    private fun channel(name: String, group: String) =
        M3UChannel(name, group, null, "https://provider.example/$name", null, null, null, false)

    @Test fun categoriesAreGroupedWithRecentThenTrThenAlphabetical() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = RuntimeEnvironment.getApplication()
        val manager = RecentlyWatchedManager(app)
        val vm = HomeViewModel(manager)
        vm.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); vm.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        val store = ViewModelStore().apply { put("home", vm) }
        try {
            vm.loadChannels(listOf(channel("one", "Zulu"), channel("two", "tr sport"),
                channel("three", "Alpha"), channel("four", "TR News"), channel("five", "Alpha")))
            val state = withContext(Dispatchers.Default) {
                withTimeout(5000) { vm.uiState.first { it is HomeUiState.Success } as HomeUiState.Success }
            }
            assertEquals(listOf("Recently viewed", "TR News", "tr sport", "Alpha", "Zulu"), state.groups.map { it.name })
            assertTrue(state.groups.first().channels.isEmpty())
            assertEquals(listOf("three", "five"), state.groups.single { it.name == "Alpha" }.channels.map { it.name })
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun returningRefreshesRecentMetadataWithoutResettingCategory() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val manager = RecentlyWatchedManager(app)
        val channels = listOf(channel("one", "Alpha"), channel("two", "Beta"))
        manager.activate("https://provider.example", "demo", channels)
        val vm = HomeViewModel(manager)
        vm.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); vm.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        val store = ViewModelStore().apply { put("home", vm) }
        suspend fun state(predicate: (HomeUiState.Success) -> Boolean) = withContext(Dispatchers.Default) {
            withTimeout(5000) { vm.uiState.first { it is HomeUiState.Success && predicate(it) } as HomeUiState.Success }
        }
        try {
            vm.loadChannels(channels)
            state { true }
            vm.browser.openCategory("group:Beta")
            manager.addChannel(channels.first())
            vm.loadChannels(channels)
            val refreshed = state { it.groups.first().channels.isNotEmpty() }
            assertEquals("group:Beta", vm.browser.state.value.location.categoryId)
            assertEquals("two", vm.browser.state.value.items.single().entry!!.channel.name)
            assertEquals(listOf(channels.first()), refreshed.groups.first().channels)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
