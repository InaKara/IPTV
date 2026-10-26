package com.example.iptv.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.content.ComponentName
import android.util.TypedValue
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.text.BasicText
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iptv.R
import com.example.iptv.SplashActivity
import com.example.iptv.SplashScreenContent
import com.example.iptv.ui.login.*
import com.example.iptv.ui.theme.IPTVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalTestApi::class)
class StartupPresentationTest {
    @get:Rule val compose = createComposeRule()

    private data class LaunchArtwork(val image: Bitmap, val logo: Rect, val title: Rect)

    private fun capture(view: View): Bitmap {
        val root = view.rootView
        return Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    private fun nativeLaunch(width: Int, height: Int): LaunchArtwork {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val activity = app.packageManager.getActivityInfo(ComponentName(app, SplashActivity::class.java), 0)
        val theme = app.resources.newTheme().apply { applyStyle(activity.theme, true) }
        val background = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowBackground, background, true))
        val drawable = app.resources.getDrawable(background.resourceId, theme) as LayerDrawable
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(image))
        return LaunchArtwork(image, Rect(drawable.getDrawable(1).bounds), Rect(drawable.getDrawable(2).bounds))
    }

    private fun pixels(image: Bitmap, area: Rect): IntArray = IntArray(area.width() * area.height()).also {
        image.getPixels(it, 0, area.width(), area.left, area.top, area.width(), area.height())
    }

    private fun titleInk(image: Bitmap, area: Rect): Rect {
        val ink = org.robolectric.RuntimeEnvironment.getApplication().getColor(R.color.startup_ink)
        val bounds = Rect()
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            if (image.getPixel(x, y) == ink) bounds.union(x, y, x + 1, y + 1)
        }
        assertFalse("The actual rendered title must contain visible burgundy lettering", bounds.isEmpty)
        return bounds
    }

    private fun assertBrandingMatchesLaunch(actual: Bitmap, launch: LaunchArtwork) {
        assertEquals("Actual title ink must retain its initial position/size", titleInk(launch.image, launch.title), titleInk(actual, launch.title))
        assertArrayEquals("Actual title shape/lettering must match the native launch", pixels(launch.image, launch.title), pixels(actual, launch.title))
        val branding = Rect(0, 0, actual.width, launch.title.bottom)
        assertArrayEquals("Logo/title must remain still through the native/Compose handoff", pixels(launch.image, branding), pixels(actual, branding))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun largerCreamStartupHasContrastingTextAndStableErrorActionBounds() {
        var state: LoginUiState by mutableStateOf(LoginUiState.Loading)
        var root: View? = null
        compose.setContent {
            root = LocalView.current
            IPTVTheme { StartupContent(state, R.string.reading_saved_channels) {} }
        }
        compose.waitForIdle()
        val launch = nativeLaunch(root!!.rootView.width, root!!.rootView.height)
        assertEquals(320, launch.logo.width()); assertEquals(launch.logo.width(), launch.logo.height())
        compose.runOnIdle { assertBrandingMatchesLaunch(capture(root!!), launch) }
        compose.runOnIdle { state = LoginUiState.Error(R.string.login_failed) }
        compose.runOnIdle { assertBrandingMatchesLaunch(capture(root!!), launch) }
        val retry = compose.onNodeWithText("Open Settings").assertIsDisplayed().assertIsFocused().fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithTag("startup_branding").fetchSemanticsNode().boundsInRoot
        assertTrue(retry.bottom <= bounds.bottom)
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val cream = app.getColor(R.color.startup_background)
        val ink = app.getColor(R.color.startup_ink)
        assertTrue(ColorUtils.calculateContrast(ink, cream) > 7.0)
        compose.runOnIdle {
            val view = root!!.rootView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            assertEquals(cream, image.getPixel(2, 2))
            val output = java.io.File("build/reports/ux2/startup-error.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val initial = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            app.getDrawable(R.drawable.splash_background)!!.apply { setBounds(0, 0, 960, 540); draw(Canvas(initial)) }
            assertEquals(cream, initial.getPixel(0, 0))
            assertTrue("Startup title uses burgundy rather than white", (370 until 420).any { y ->
                (380 until 580).any { x -> val pixel = initial.getPixel(x, y); Color.red(pixel) in 60..110 && Color.green(pixel) < 35 && Color.blue(pixel) < 45 }
            })
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun automaticPhasesKeepLogoStillAndFailureOpensExplicitSettingsRetry() {
        var state: LoginUiState by mutableStateOf(LoginUiState.Loading)
        var status by mutableStateOf(R.string.reading_saved_channels)
        var root: View? = null
        compose.setContent {
            root = LocalView.current
            IPTVTheme { StartupContent(state, status) {
                AccountSettingsContent("https://provider.example", "demo", "synthetic", state,
                    {}, {}, {}, { state = LoginUiState.Loading }, {})
            } }
        }
        compose.onNodeWithTag("startup_artwork").assertIsDisplayed()
        val launch = nativeLaunch(root!!.rootView.width, root!!.rootView.height)
        for ((resource, text) in listOf(R.string.reading_saved_channels to "Reading saved channels…",
            R.string.connecting to "Connecting…", R.string.loading_channels to "Loading channels…")) {
            compose.runOnIdle { status = resource }
            compose.runOnIdle { assertBrandingMatchesLaunch(capture(root!!), launch) }
            val line = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(line.top > launch.title.bottom)
            compose.onNodeWithText("Save and load").assertDoesNotExist()
            compose.onNodeWithText("Server URL").assertDoesNotExist()
        }
        compose.runOnIdle { state = LoginUiState.Error(R.string.login_failed) }
        compose.onNodeWithTag("startup_status").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Save and load").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) { throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(), error) }
        compose.onNodeWithText("Save and load").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Server URL").assertIsDisplayed()
        compose.onNodeWithTag("startup_branding").assertDoesNotExist()
        // Explicit setup stays mounted during its request rather than flashing back to startup.
        compose.onNodeWithText("Connecting…").assertIsFocused()
    }

    @Test fun missingAccountStartsInSettingsWithoutExposingPassword() {
        compose.setContent { IPTVTheme { StartupContent(LoginUiState.Setup, R.string.reading_saved_channels) {
            AccountSettingsContent("", "", "synthetic", LoginUiState.Setup, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("Save and load").assertIsFocused()
        compose.onNodeWithText("Refresh channel list").assertIsDisplayed()
        compose.onNodeWithTag("startup_branding").assertDoesNotExist()
        compose.onNodeWithText("synthetic").assertDoesNotExist()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeLaunchSplashAndLoadingShareTitlePixelsAndInitialGeometry() {
        checkStageContinuity(fontScale = 1f, compact = false, evidence = "normal")
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun highDensityAndLargeTextKeepTitleFixedAndErrorActionVisible() {
        checkStageContinuity(fontScale = 1.3f, compact = false, evidence = "high-density")
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w568dp-h320dp-land-mdpi")
    fun compactLayoutFitsTheSameNativeAndComposeBrandingWithLargeText() {
        checkStageContinuity(fontScale = 1.3f, compact = true, evidence = "compact")
    }

    private fun checkStageContinuity(fontScale: Float, compact: Boolean, evidence: String) {
        var stage by mutableIntStateOf(0)
        var root: View? = null
        compose.setContent {
            root = LocalView.current
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                IPTVTheme {
                    if (stage == 0) SplashScreenContent {}
                    else StartupContent(if (stage == 4) LoginUiState.Error(R.string.login_failed) else LoginUiState.Loading,
                        when (stage) { 1 -> R.string.reading_saved_channels; 2 -> R.string.connecting; else -> R.string.loading_channels }) {
                        BasicText("Settings destination", Modifier.testTag("startup_settings_destination"))
                    }
                }
            }
        }
        compose.waitForIdle()
        val view = root!!.rootView
        val launch = nativeLaunch(view.width, view.height)
        val density = view.resources.displayMetrics.density
        val titleWidth = ((if (compact) 100 else 200) * density).toInt()
        val titleHeight = ((if (compact) 20 else 40) * density).toInt()
        val titleTop = view.height / 2 + ((if (compact) 16 else 104) * density).toInt()
        assertEquals("Preserve initial title placement, without compensating by moving launch artwork",
            Rect((view.width - titleWidth) / 2, titleTop, (view.width + titleWidth) / 2, titleTop + titleHeight), launch.title)
        assertEquals(((if (compact) 160 else 320) * density).toInt(), launch.logo.width())
        assertTrue(launch.logo.top >= 0)
        assertTrue(launch.logo.bottom < launch.title.top)
        for (next in 0..4) {
            compose.runOnIdle { stage = next }
            compose.onNodeWithContentDescription("Aypi Tivi").assertIsDisplayed()
            compose.runOnIdle { assertBrandingMatchesLaunch(capture(view), launch) }
        }
        val action = compose.onNodeWithText("Open Settings").assertIsDisplayed().assertIsFocused().fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("startup_status").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Status remains below title", status.top > launch.title.bottom)
        assertTrue("Error action must fit with larger text", action.bottom <= view.height)
        assertTrue("Status and error action must not overlap", action.top >= status.bottom)
        compose.runOnIdle {
            val output = java.io.File("build/reports/st1/$evidence-error.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { capture(view).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Open Settings").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("startup_settings_destination").assertIsDisplayed()
        compose.onNodeWithTag("startup_branding").assertDoesNotExist()
    }
}
