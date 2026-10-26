package com.example.iptv.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.iptv.SplashScreenContent
import com.example.iptv.ui.browser.*
import com.example.iptv.ui.home.BrowserControl
import com.example.iptv.ui.theme.IPTVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FocusTestOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
    init { lifecycle.currentState = Lifecycle.State.RESUMED }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FocusLifecycleTest {
    @get:Rule val compose = createComposeRule()
    @Before fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    @Test fun queuedFocusWaitsForPlacementAndOldTargetsCannotReceiveItAfterPauseOrRemoval() {
        val owner = FocusTestOwner()
        val first = FocusRequester(); val second = FocusRequester()
        var visible by mutableStateOf(false)
        var revision by mutableStateOf(0)
        lateinit var focus: FocusRestoration
        var restored: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                focus = rememberFocusRestoration()
                LaunchedEffect(revision, focus.version) {
                    val requestedRevision = revision
                    if (revision > 0) focus.restore(if (revision == 1) first else second, onFocused = { restored = true }) { revision == requestedRevision }
                }
                Column {
                    if (visible) BrowserControl(Modifier.size(80.dp).attachedFocusRequester(first, focus).testTag("first"), 1f, onClick = {}) {}
                    BrowserControl(Modifier.size(80.dp).attachedFocusRequester(second, focus).testTag("second"), 1f, onClick = {}) {}
                }
            }
        }
        compose.runOnIdle { revision = 1 }
        assertNull(restored)
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("first").assertIsFocused()
        assertEquals(true, restored)
        compose.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            assertFalse(focus.request(first))
            revision = 2
        }
        compose.onNodeWithTag("second").assertIsNotFocused()
        compose.runOnIdle { visible = false; owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("second").assertIsFocused()
        compose.runOnIdle { assertFalse(focus.request(first)) }
        compose.runOnIdle { revision = 1 } // First is absent; this request must wait.
        compose.waitForIdle()
        compose.runOnIdle { revision = 0; visible = true } // The old command is obsolete before placement.
        compose.onNodeWithTag("second").assertIsFocused()
        compose.onNodeWithTag("first").assertIsNotFocused()
    }

    @Test fun splashKeepsTwoForegroundSecondsAndCannotLaunchAfterStopOrDisposal() {
        val owner = FocusTestOwner()
        var visible by mutableStateOf(true)
        var calls = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            IPTVTheme { if (visible) SplashScreenContent { calls++ } }
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(4000)
        assertEquals(0, calls)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(1900)
        assertEquals(0, calls)
        compose.mainClock.advanceTimeBy(200)
        assertEquals(1, calls)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(4000)
        assertEquals(1, calls)
        compose.mainClock.autoAdvance = true
    }
}
