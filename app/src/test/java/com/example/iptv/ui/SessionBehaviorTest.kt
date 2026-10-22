package com.example.iptv.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class SessionBehaviorTest {
    @Test fun browsingRetainsSessionAndDuplicatePositionsAcrossControllerRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val manager = RecentlyWatchedManager(RuntimeEnvironment.getApplication())
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = SessionViewModel(manager) as T
        }
        try {
            val vm = ViewModelProvider(store, factory)[SessionViewModel::class.java]
            val a = M3UChannel("Same", "Group", null, "https://provider.example/a", null, null, null, false)
            val b = a.copy(streamUrl = "https://provider.example/b")
            val channels = listOf(a, a.copy(), b)
            vm.login(channels)
            vm.select(1, channels)
            vm.step(1)
            assertEquals(b, vm.state.value.selected)
            vm.step(1)
            assertEquals(0, vm.state.value.selectedIndex)
            val recreated = ViewModelProvider(store, factory)[SessionViewModel::class.java]
            assertSame(vm, recreated)
            recreated.browse()
            assertNull(recreated.state.value.selected)
            assertEquals(channels, recreated.state.value.channels)
            assertEquals(channels, recreated.state.value.category)
            recreated.select(-1, channels)
            assertNull(recreated.state.value.selected)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
