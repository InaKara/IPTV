package com.example.iptv.ui.login

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.R
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.storage.*
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
class CachedStartupTest {
    private val channels = listOf(M3UChannel("News", "Live", null, "https://provider.example/1", null, null, null, false))
    private val timestamp = 100_000_000L
    private class MemorySnapshots(var value: ChannelSnapshot?) : ChannelSnapshots {
        var writes = 0
        var rejectWrite = false
        override suspend fun read(account: ChannelAccount) = value
        override suspend fun write(account: ChannelAccount, snapshot: ChannelSnapshot) {
            if (rejectWrite) error("private storage error")
            writes++; value = snapshot
        }
    }
    private class Provider : ChannelRepository {
        var calls = 0
        val pending = mutableListOf<CompletableDeferred<Result<List<M3UChannel>>>>()
        override suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>> {
            calls++
            return CompletableDeferred<Result<List<M3UChannel>>>().also { pending.add(it) }.await()
        }
    }
    private suspend fun settled(vm: LoginViewModel) = withContext(Dispatchers.Default) {
        withTimeout(5000) { vm.uiState.first { it is LoginUiState.Success && !it.refreshing || it is LoginUiState.Error } }
    }
    private fun credentials(): CredentialsManager {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        return CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
    }
    private fun model(provider: Provider, credentials: CredentialsManager, cache: MemorySnapshots) =
        LoginViewModel(provider, credentials, RecentlyWatchedManager(RuntimeEnvironment.getApplication()), snapshots = cache)
            .apply { now = { timestamp } }

    @Test fun freshStartupSkipsNetworkButManualRefreshIsSingleFlightAndUpdatesSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val provider = Provider(); val cache = MemorySnapshots(ChannelSnapshot(channels, timestamp))
        val vm = model(provider, credentials(), cache)
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.onForeground(); settled(vm); vm.onBrowseReady(); vm.onForeground(); runCurrent()
            assertEquals(0, provider.calls)
            vm.refresh(); vm.refresh(); runCurrent()
            assertEquals(1, provider.calls)
            assertEquals(channels, (vm.uiState.value as LoginUiState.Success).channels)
            provider.pending.single().complete(Result.success(channels.map { it.copy(name = "Updated") }))
            val refreshed = settled(vm) as LoginUiState.Success
            assertEquals("Updated", refreshed.channels.single().name)
            assertEquals(timestamp, refreshed.downloadedAt)
            assertEquals(1, cache.writes)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun staleStartupWaitsForBrowseAndFailedRefreshRetainsDataAndSuccessTime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val provider = Provider(); val cache = MemorySnapshots(ChannelSnapshot(channels, timestamp - ChannelSnapshot.MAX_AGE_MS - 1))
        val vm = model(provider, credentials(), cache)
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.onForeground(); val saved = settled(vm) as LoginUiState.Success
            assertEquals(0, provider.calls)
            vm.onBrowseReady(); vm.onBrowseReady(); runCurrent()
            assertEquals(1, provider.calls)
            provider.pending[0].complete(Result.failure(IllegalStateException("private error")))
            val failed = settled(vm) as LoginUiState.Success
            assertEquals(saved.channels, failed.channels); assertEquals(saved.downloadedAt, failed.downloadedAt)
            assertEquals(R.string.login_failed, failed.messageRes); assertEquals(0, cache.writes)
            runCurrent(); assertEquals(1, provider.calls)
            vm.onForeground(); runCurrent(); assertEquals(2, provider.calls)
            provider.pending[1].complete(Result.success(emptyList()))
            val empty = settled(vm) as LoginUiState.Success
            assertEquals(saved.downloadedAt, empty.downloadedAt)
            assertEquals(R.string.login_empty_playlist, empty.messageRes)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun missingSnapshotDownloadsAutomaticallyAndInitialFailureCanRetryFromSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val provider = Provider(); val cache = MemorySnapshots(null)
        val vm = model(provider, credentials(), cache)
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.onForeground(); runCurrent(); assertEquals(1, provider.calls)
            provider.pending[0].complete(Result.failure(IllegalStateException("private")))
            assertTrue(settled(vm) is LoginUiState.Error)
            vm.onForeground(); runCurrent(); assertEquals(1, provider.calls)
            vm.refresh(); runCurrent(); assertEquals(2, provider.calls)
            provider.pending[1].complete(Result.success(channels)); assertTrue(settled(vm) is LoginUiState.Success)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun missingCredentialsOpenSetupWithoutNetwork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val provider = Provider(); val credentials = credentials().apply { save("", "", "") }
        val vm = model(provider, credentials, MemorySnapshots(null))
        val store = ViewModelStore().apply { put("startup", vm) }
        try { vm.onForeground(); runCurrent(); assertEquals(LoginUiState.Setup, vm.uiState.value); assertEquals(0, provider.calls) }
        finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun cancelledAccountEditAndLateResponseCannotReplaceWorkingAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val provider = Provider(); val credentials = credentials()
        val vm = model(provider, credentials, MemorySnapshots(ChannelSnapshot(channels, timestamp)))
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.onForeground(); val original = settled(vm) as LoginUiState.Success
            vm.username = "changed"; vm.onConnect(); runCurrent()
            vm.cancelAccountEdit(); provider.pending[0].complete(Result.success(channels.map { it.copy(name = "Late") }))
            runCurrent()
            assertEquals("demo", credentials.username); assertEquals("demo", vm.username)
            assertEquals(original.channels, (vm.uiState.value as LoginUiState.Success).channels)
            vm.username = "replacement"; vm.onConnect(); runCurrent()
            // Replacement waits for the cancelled edit's off-thread history rollback.
            withContext(Dispatchers.Default) { withTimeout(5000) { while (provider.calls < 2) delay(1) } }
            assertEquals(2, provider.calls)
            provider.pending[1].complete(Result.success(channels.map { it.copy(name = "Replacement") }))
            val replacement = settled(vm) as LoginUiState.Success
            assertEquals("replacement", credentials.username)
            assertNotEquals(original.accountKey, replacement.accountKey)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedSnapshotWriteRejectsAccountEditAndPreservesWorkingSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val provider = Provider(); val credentials = credentials()
        val cache = MemorySnapshots(ChannelSnapshot(channels, timestamp)).apply { rejectWrite = true }
        val vm = model(provider, credentials, cache)
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.onForeground(); settled(vm)
            vm.username = "changed"; vm.onConnect(); runCurrent()
            provider.pending[0].complete(Result.success(channels.map { it.copy(name = "Rejected") }))
            val failed = settled(vm) as LoginUiState.Success
            assertEquals(channels, failed.channels); assertEquals("demo", credentials.username)
            assertEquals(R.string.login_failed, failed.messageRes); assertEquals(channels, cache.value!!.channels)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun incompatibleEditCancelsOldResponseBeforeItCanCommit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val credentials = credentials()
        val cache = MemorySnapshots(ChannelSnapshot(channels, timestamp))
        val pending = mutableListOf<CompletableDeferred<Result<List<M3UChannel>>>>()
        val provider = object : ChannelRepository {
            override suspend fun getChannels(serverUrl: String, username: String, password: String) =
                withContext(NonCancellable) {
                    CompletableDeferred<Result<List<M3UChannel>>>().also { pending.add(it) }.await()
                }
        }
        val vm = LoginViewModel(provider, credentials, RecentlyWatchedManager(RuntimeEnvironment.getApplication()), snapshots = cache)
            .apply { now = { timestamp } }
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.onForeground(); settled(vm)
            vm.username = "obsolete"; vm.onConnect(); runCurrent()
            vm.username = "selected"; vm.onConnect(); runCurrent()
            pending[0].complete(Result.success(channels.map { it.copy(name = "Late obsolete") }))
            withContext(Dispatchers.Default) { withTimeout(5000) { while (pending.size < 2) delay(1) } }
            assertEquals("demo", credentials.username); assertEquals(0, cache.writes)
            pending[1].complete(Result.success(channels.map { it.copy(name = "Selected") }))
            assertEquals("Selected", (settled(vm) as LoginUiState.Success).channels.single().name)
            assertEquals("selected", credentials.username); assertEquals(1, cache.writes)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
