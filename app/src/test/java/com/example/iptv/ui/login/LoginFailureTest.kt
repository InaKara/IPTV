package com.example.iptv.ui.login

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.example.iptv.R
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class LoginFailureTest {
    @Test fun missingFieldsDoNotCallProviderAndFailureAllowsRetryWithoutLeakingDetails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = RuntimeEnvironment.getApplication()
        val credentials = CredentialsManager(app)
        credentials.save("https://provider.example", "saved", "saved-password")
        var calls = 0
        val repository = object : ChannelRepository {
            override suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>> {
                calls++
                return if (calls == 1) Result.failure(IOException("https://provider.example/private?password=synthetic"))
                else Result.success(emptyList())
            }
        }
        val vm = LoginViewModel(repository, credentials, RecentlyWatchedManager(app))
        val store = ViewModelStore().apply { put("login", vm) }
        try {
            vm.username = ""
            vm.onConnect()
            assertEquals(LoginUiState.Error(R.string.login_missing_fields), vm.uiState.value)
            assertEquals(0, calls)
            vm.username = "attempted"
            vm.onConnect()
            runCurrent()
            assertEquals(LoginUiState.Error(R.string.login_failed), vm.uiState.value)
            vm.onConnect()
            runCurrent()
            assertEquals(2, calls)
            assertEquals(LoginUiState.Error(R.string.login_empty_playlist), vm.uiState.value)
            assertEquals("saved", credentials.username)
            assertEquals("saved-password", credentials.password)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
