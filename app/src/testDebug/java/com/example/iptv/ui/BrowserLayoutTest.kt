package com.example.iptv.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.example.iptv.di.AppModule
import coil.Coil
import okhttp3.OkHttpClient
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

    @Test fun logosUseRemainingTileSpaceAndKeepWideTallAndSvgAspectAcrossDensities() {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        // A plain dispatcher keeps TestMainDispatcher.immediate dynamic across Robolectric
        // sandboxes. Caching a Handler MainCoroutineDispatcher strands later Coil tests.
        Dispatchers.setMain(object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext) =
                android.os.Looper.myLooper() != main.looper
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { main.post(block) }
        })
        val app = RuntimeEnvironment.getApplication()
        val client = OkHttpClient()
        val loader = AppModule.provideImageLoader(app, client)
        Coil.setImageLoader(loader)
        fun png(w: Int, h: Int): ByteArray = java.io.ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(32, 224, 64))
            }.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val wide = png(80, 40); val tall = png(40, 80)
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40"><rect width="80" height="40" fill="#20e040"/></svg>""".toByteArray()
        val server = okhttp3.mockwebserver.MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val bytes = when (request.path) { "/tall.png" -> tall; "/wide.svg" -> svg; else -> wide }
                return okhttp3.mockwebserver.MockResponse().setBody(okio.Buffer().write(bytes))
                    .setHeader("Content-Type", if (request.path == "/wide.svg") "image/svg+xml" else "image/png")
            }
        }
        server.start()
        val vm = HomeViewModel(RecentlyWatchedManager(app))
        val store = ViewModelStore().apply { put("browser", vm) }
        val channels = listOf("wide.png", "tall.png", "wide.svg").mapIndexed { index, path ->
            M3UChannel("Logo $index", "Local", server.url("/$path").toString(), "https://provider.example/$index", null, null, null, false)
        }
        lateinit var root: View
        compose.setContent { root = LocalView.current; IPTVTheme { BrowserScreen(channels, { _, _ -> }, vm) } }
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Local").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Local").performClick()
            for (density in listOf(GridDensity.STANDARD, GridDensity.LARGE, GridDensity.COMPACT)) {
                compose.runOnIdle { vm.setDensity(density) }
                val nodes = compose.onAllNodes(SemanticsMatcher("channel artwork") {
                    it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith("channel_logo_")
                }, useUnmergedTree = true)
                compose.waitUntil(5000) { nodes.fetchSemanticsNodes().size == 3 }
                compose.mainClock.advanceTimeBy(400)
                fun artworkPixels(index: Int): Pair<Int, Int>? {
                    val bounds = nodes[index].fetchSemanticsNode().boundsInRoot
                    val bitmap = compose.runOnIdle { Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) } }
                    val ink = mutableListOf<Pair<Int, Int>>()
                    for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt())
                        for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
                            val color = bitmap.getPixel(x, y)
                            if (Color.green(color) > 180 && Color.red(color) < 80 && Color.blue(color) < 150) ink.add(x to y)
                        }
                    return if (ink.isEmpty()) null else
                        (ink.maxOf { it.first } - ink.minOf { it.first } + 1) to (ink.maxOf { it.second } - ink.minOf { it.second } + 1)
                }
                compose.waitUntil(5000) { (0..2).all { artworkPixels(it) != null } }
                val scale = compose.onNodeWithTag("browser").fetchSemanticsNode().boundsInRoot.width / 1672f
                for (index in 0..2) {
                    val bounds = nodes[index].fetchSemanticsNode().boundsInRoot
                    assertTrue("Artwork area must grow beyond old 84-unit cap", bounds.height > 90 * scale)
                    val (w, h) = artworkPixels(index)!!
                    assertEquals(if (index == 1) 0.5 else 2.0, w.toDouble() / h, 0.07)
                    val ratio = if (index == 1) 0.5f else 2f
                    val fittedHeight = minOf(bounds.height, bounds.width / ratio)
                    assertTrue("The image must fit the larger area", h >= fittedHeight - 3)
                    compose.onNodeWithText("Logo $index").assertIsDisplayed()
                }
            }
        } finally {
            compose.runOnIdle { store.clear() }; Coil.reset(); loader.shutdown()
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.close(); Dispatchers.resetMain()
        }
    }

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
            for (name in listOf("VOD", "FAVORITES", "SEARCH", "SETTINGS")) {
                val control = compose.onNodeWithTag("sidebar_$name")
                val bounds = control.fetchSemanticsNode().boundsInRoot
                control.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                control.assertIsFocused()
                val image = screenshot("browser-sidebar-${name.lowercase()}-focus")
                assertEquals(bounds, control.fetchSemanticsNode().boundsInRoot)
                assertEquals(0xffffd15c.toInt(), image.getPixel((bounds.left + 8).roundToInt(), (bounds.top + 8).roundToInt()))
                compose.onNodeWithTag("sidebar_CATEGORIES").assertIsSelected()
            }
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
            compose.runOnIdle { vm.browser.open(com.example.iptv.ui.browser.BrowserDestination.SETTINGS) }
            compose.onNodeWithTag("density_COMPACT").performKeyInput { pressKey(Key.DirectionDown) }
            val filter = compose.onNodeWithTag("adult_filter").assertIsFocused().assertIsOn().assertIsDisplayed()
            val filterBounds = filter.fetchSemanticsNode().boundsInRoot
            val settingsImage = screenshot("browser-adult-filter-focus")
            assertEquals(0xffffd15c.toInt(), settingsImage.getPixel((filterBounds.left + 8).roundToInt(), (filterBounds.top + 8).roundToInt()))
            assertTrue(filterBounds.bottom < settingsImage.height)
            filter.performKeyInput { pressKey(Key.DirectionCenter) }
            filter.assertIsFocused().assertIsOff()
        } finally { compose.runOnIdle { store.clear() }; Dispatchers.resetMain() }
    }
}
