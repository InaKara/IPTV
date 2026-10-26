package com.example.iptv.ui

import android.app.Application
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelStore
import com.example.iptv.BuildConfig
import com.example.iptv.R
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.diagnostics.VerboseLogging
import com.example.iptv.ui.browser.GridDensity
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.login.AccountSettingsContent
import com.example.iptv.ui.login.LoginUiState
import com.example.iptv.ui.theme.IPTVTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.TimeZone
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class SettingsSoftwareInfoTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var home: HomeViewModel
    private lateinit var back: OnBackPressedDispatcher
    private val store = ViewModelStore()
    private val originalZone = TimeZone.getDefault()
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        home = HomeViewModel(RecentlyWatchedManager(app), logging = VerboseLogging(app))
        home.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); home.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        store.put("home", home)
        val channels = listOf(M3UChannel("Fixture", "Live", null, "https://fixture.invalid/1", null, null, null, false))
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IPTVTheme {
                BrowserScreen(channels, { _, _ -> fail("Settings must never start playback") }, home,
                    accountSettings = { modifier, focus, leave ->
                        AccountSettingsContent("https://provider.example", "demo", "synthetic", LoginUiState.Success(channels),
                            {}, {}, {}, {}, {}, modifier, focus, requestInitialFocus = false, onLeave = leave)
                    })
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("browser").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { TimeZone.setDefault(originalZone); compose.runOnIdle { store.clear() } }
    private fun openSettings() { compose.onNodeWithTag("sidebar_SETTINGS").performClick() }

    @Test fun versionAndPackagedTimestampStayFixedAcrossTimezoneAndReentry() {
        val app = RuntimeEnvironment.getApplication()
        val stamp = app.getString(R.string.software_build_timestamp)
        assertTrue(stamp.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}")))
        val parsed = LocalDateTime.parse(stamp, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT))
        assertEquals(0, parsed.second)
        val version = app.getString(R.string.software_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        val built = app.getString(R.string.software_built, stamp)
        openSettings()
        compose.onNodeWithTag("software_version").assertTextEquals(version)
        compose.onNodeWithTag("software_built").assertTextEquals(built)
        compose.runOnIdle {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            back.onBackPressed()
        }
        openSettings()
        compose.onNodeWithTag("software_version").assertTextEquals(version)
        compose.onNodeWithTag("software_built").assertTextEquals(built)
    }
    @Test fun softwareBlockFitsDensitiesWithoutAddingRemoteFocusStops() {
        for (density in GridDensity.values()) {
            compose.runOnIdle { home.setDensity(density) }
            openSettings()
            val screen = compose.onNodeWithTag("browser").getUnclippedBoundsInRoot()
            val info = compose.onNodeWithTag("software_info").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(info.left >= screen.left && info.right <= screen.right && info.bottom <= screen.bottom)
            for (tag in listOf("software_version", "software_built")) {
                val node = compose.onNodeWithTag(tag).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount); assertFalse(layouts.single().hasVisualOverflow)
                assertFalse(node.fetchSemanticsNode().config.contains(SemanticsActions.RequestFocus))
                node.assertHasNoClickAction()
            }
            compose.onNodeWithTag("adult_filter").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("verbose_logging").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("verbose_logging").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithText("Save and load").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithTag("density_${density.name}").assertIsFocused()
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithTag("browser_heading").assertTextEquals("Live TV")
            compose.onNodeWithTag("software_info").assertDoesNotExist()
        }
    }
}
