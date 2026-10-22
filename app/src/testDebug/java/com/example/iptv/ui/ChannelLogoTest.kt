package com.example.iptv.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil.EventListener
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.iptv.di.AppModule
import com.example.iptv.ui.home.ChannelLogo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelLogoTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40" viewBox="0 0 80 40"><rect width="80" height="40" fill="#20e040"/></svg>"""

    private fun png(): ByteArray = ByteArrayOutputStream().use { bytes ->
        Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(32, 224, 64)) }
            .compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bytes.toByteArray()
    }

    private fun response(bytes: ByteArray, mime: String) = MockResponse()
        .setBody(Buffer().write(bytes)).setHeader("Content-Type", mime).setHeader("Cache-Control", "public, max-age=3600")

    private suspend fun load(loader: ImageLoader, url: String): SuccessResult = withTimeout(5000) {
        val result = loader.execute(ImageRequest.Builder(app).data(url).size(80, 40).allowHardware(false).build())
        assertTrue("The local fixture must decode through the production loader", result is SuccessResult)
        result as SuccessResult
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun localRasterAndSvgDecodeWithCorrectAspectAndPixels() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { MockWebServer().use { server ->
            server.enqueue(response(png(), "image/png"))
            server.enqueue(response(svg.toByteArray(), "image/svg+xml"))
            val client = OkHttpClient()
            val loader = AppModule.provideImageLoader(app, client)
            try {
                for (path in listOf("/raster.png", "/vector.svg")) {
                    val drawable = load(loader, server.url(path).toString()).drawable
                    assertEquals(80, drawable.intrinsicWidth)
                    assertEquals(40, drawable.intrinsicHeight)
                    val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, 80, 40)
                    drawable.draw(Canvas(bitmap))
                    assertEquals(Color.rgb(32, 224, 64), bitmap.getPixel(40, 20))
                    assertEquals(Color.rgb(32, 224, 64), bitmap.getPixel(1, 1))
                }
                assertEquals(2, server.requestCount)
            } finally { loader.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        } } finally { Dispatchers.resetMain() }
    }

    @Test fun repeatLogoLoadsUseMemoryThenDiskWithoutAnotherRequest() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { MockWebServer().use { server ->
            server.enqueue(response(svg.toByteArray(), "image/svg+xml"))
            val client = OkHttpClient()
            val loader = AppModule.provideImageLoader(app, client)
            try {
                val url = server.url("/cached.svg").toString()
                assertEquals(DataSource.NETWORK, load(loader, url).dataSource)
                assertEquals(DataSource.MEMORY_CACHE, load(loader, url).dataSource)
                loader.memoryCache!!.clear()
                assertEquals(DataSource.DISK, load(loader, url).dataSource)
                assertEquals(1, server.requestCount)
            } finally { loader.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        } } finally { Dispatchers.resetMain() }
    }

    @Test fun missingBlankFailedAndUnsupportedImagesRenderTheSameCenteredFallback() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(response("invalid image".toByteArray(), "image/unsupported"))
            val client = OkHttpClient()
            val errors = AtomicInteger()
            val loader = AppModule.provideImageLoader(app, client).newBuilder().eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) { errors.incrementAndGet() }
            }).build()
            try {
                val urls = listOf(null, "  ", server.url("/missing.png").toString(), server.url("/unsupported.bin").toString())
                lateinit var rootView: View
                compose.setContent {
                    rootView = LocalView.current
                    Row {
                        urls.forEachIndexed { index, url ->
                            ChannelLogo(url, Modifier.size(72.dp, 48.dp).background(androidx.compose.ui.graphics.Color.Black)
                                .testTag("logo$index"), loader)
                        }
                    }
                }
                compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); errors.get() >= 4 }
                // Compose 1.6 captureToImage waits for a hardware VSYNC callback that
                // Robolectric cannot deliver. Draw the real view into a native Canvas.
                val screenshot = compose.runOnIdle {
                    Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888).also {
                        rootView.draw(Canvas(it))
                    }
                }
                val images = urls.indices.map {
                    val bounds = compose.onNodeWithTag("logo$it").fetchSemanticsNode().boundsInRoot
                    Bitmap.createBitmap(screenshot, bounds.left.roundToInt(), bounds.top.roundToInt(),
                        bounds.width.roundToInt(), bounds.height.roundToInt())
                }
                images.drop(1).forEach { assertArrayEquals(pixels(images.first()), pixels(it)) }
                val image = images.first()
                val ink = pixels(image).withIndex().filter { Color.red(it.value) > 80 }
                assertTrue("Fallback must actually draw", ink.size > 30)
                assertEquals(image.height / 2.0, ink.map { it.index / image.width }.average(), 1.0)
                assertEquals(image.width / 2.0, ink.map { it.index % image.width }.average(), 3.0)
                assertFalse(pixels(image).any { Color.blue(it) > 230 })
                assertEquals(2, server.requestCount)
            } finally { loader.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }
}
