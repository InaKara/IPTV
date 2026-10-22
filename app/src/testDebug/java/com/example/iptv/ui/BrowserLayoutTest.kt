package com.example.iptv.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.browser.GridDensity
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.theme.IPTVTheme
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowserLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allDensitiesFitAndTileAndSidebarHaveWholeAmberFocusWithoutScaling() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        val store = ViewModelStore().apply { put("browser", vm) }
        val channels = (0..39).map { M3UChannel("Channel $it", "Live", null, "https://provider.example/$it", null, null, null, false) }
        lateinit var root: View
        compose.setContent { root = LocalView.current; IPTVTheme { BrowserScreen(channels, { _, _ -> }, vm) } }
        fun screenshot(name: String): Bitmap {
            // Native view drawing must follow the frame that applies the focus color.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            return compose.runOnIdle {
                Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    root.invalidate()
                    root.draw(Canvas(bitmap))
                    val file = File("build/reports/$name.png")
                    file.parentFile!!.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Live").fetchSemanticsNodes().isNotEmpty() }
            screenshot("browser-categories")
            compose.onNodeWithText("Live").performClick()
            compose.waitUntil(5000) { vm.browser.state.value.items.size == 40 }
            val grid = compose.onNodeWithTag("browser_grid").fetchSemanticsNode().boundsInRoot
            val before = compose.onNodeWithTag("tile_3").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("tile_3").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("tile_3").assertIsFocused()
            val focused = screenshot("browser-channels")
            assertEquals(before, compose.onNodeWithTag("tile_3").fetchSemanticsNode().boundsInRoot)
            assertEquals(0xffffd15c.toInt(), focused.getPixel((before.left + 8).roundToInt(), (before.top + 8).roundToInt()))
            compose.onNodeWithTag("sidebar_CATEGORIES").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused()
            val sidebar = compose.onNodeWithTag("sidebar_CATEGORIES").fetchSemanticsNode().boundsInRoot
            val sidebarImage = screenshot("browser-sidebar-focus")
            assertEquals(0xffffd15c.toInt(), sidebarImage.getPixel((sidebar.left + 8).roundToInt(), (sidebar.top + 8).roundToInt()))
            assertEquals(0xff191d1e.toInt(), sidebarImage.getPixel((before.left + 8).roundToInt(), (before.top + 8).roundToInt()))
            compose.onNodeWithTag("sidebar_CATEGORIES").assertIsSelected()
            for (density in listOf(GridDensity.STANDARD, GridDensity.LARGE, GridDensity.COMPACT)) {
                compose.runOnIdle { vm.setDensity(density) }
                compose.waitUntil(5000) { vm.browser.state.value.density == density }
                val bounds = (0 until density.columns * density.rows).map { index ->
                    compose.onNodeWithTag("tile_$index").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                }
                assertEquals(density.columns, bounds.map { it.left.roundToInt() }.distinct().size)
                assertEquals(density.rows, bounds.map { it.top.roundToInt() }.distinct().size)
                assertTrue(bounds.all { it.left >= grid.left - 1 && it.top >= grid.top - 1 && it.right <= grid.right + 1 && it.bottom <= grid.bottom + 1 })
                assertTrue(bounds.all { kotlin.math.abs(it.height - bounds.first().height) <= 1 && kotlin.math.abs(it.width - bounds.first().width) <= 1 })
                compose.onNodeWithTag("tile_${density.columns * density.rows}").assertIsNotDisplayed()
            }
        } finally { compose.runOnIdle { store.clear() }; Dispatchers.resetMain() }
    }
}
