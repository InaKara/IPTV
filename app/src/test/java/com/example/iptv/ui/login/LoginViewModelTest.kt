package com.example.iptv.ui.login

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.diagnostics.StartupTimings
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
class LoginViewModelTest {
    @Test fun oneAttemptSavesSubmittedSnapshotEvenWhenFormIsEdited() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val credentials = CredentialsManager(app)
        credentials.save("https://provider.example", "submitted", "submitted-password")
        val result = CompletableDeferred<Result<List<M3UChannel>>>()
        var calls = 0
        val repository = object : ChannelRepository {
            override suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>> {
                calls++
                assertEquals("submitted", username)
                return result.await()
            }
        }
        val vm = LoginViewModel(repository, credentials, RecentlyWatchedManager(app))
        val store = ViewModelStore().apply { put("login", vm) }
        try {
            vm.onConnect()
            vm.onConnect()
            runCurrent()
            assertEquals(1, calls)
            vm.username = "edited"
            vm.password = "edited-password"
            result.complete(Result.success(listOf(M3UChannel("News", "Live", null, "https://provider.example/1", null, null, null, false))))
            withContext(Dispatchers.Default) {
                withTimeout(5000) { vm.uiState.first { it is LoginUiState.Success } }
            }
            assertEquals("submitted", credentials.username)
            assertEquals("submitted-password", credentials.password)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun clearingViewModelCancelsLoginWithoutErrorState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = RuntimeEnvironment.getApplication()
        val credentials = CredentialsManager(app)
        credentials.save("https://provider.example", "demo", "synthetic")
        val timingLines = mutableListOf<String>()
        val timing = StartupTimings(true, output = { timingLines.add(it) })
        var cancelled = false
        val repository = object : ChannelRepository {
            override suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>> {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val vm = LoginViewModel(repository, credentials, RecentlyWatchedManager(app), timing)
        val store = ViewModelStore().apply { put("login", vm) }
        try {
            vm.onConnect()
            runCurrent()
            store.clear()
            runCurrent()
            assertTrue(cancelled)
            assertFalse(vm.uiState.value is LoginUiState.Error)
            assertEquals(1, timingLines.count { "phase=cancelled" in it })
            assertNull(timing.current())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
