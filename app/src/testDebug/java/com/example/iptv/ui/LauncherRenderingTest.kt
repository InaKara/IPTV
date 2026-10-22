package com.example.iptv.ui

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import com.example.iptv.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherRenderingTest {
    private fun render(drawable: Drawable, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(it))
        }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun assertClassic(expected: IntArray, actual: IntArray) {
        assertEquals(expected.size, actual.size)
        // Density PNGs resample the same artwork; permit filtering differences at edges.
        val difference = expected.indices.sumOf {
            abs(Color.red(expected[it]) - Color.red(actual[it])) +
                abs(Color.green(expected[it]) - Color.green(actual[it])) +
                abs(Color.blue(expected[it]) - Color.blue(actual[it]))
        }.toDouble() / (expected.size * 3)
        assertTrue("Launcher artwork must match the classic image (mean RGB difference $difference)", difference < 12)
    }

    @Test fun applicationAndLauncherActivityRenderClassicArtworkAndBanner() {
        val app = RuntimeEnvironment.getApplication()
        val pm = app.packageManager
        val info = pm.getApplicationInfo(app.packageName, 0)
        val activity = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
            .activities.single { it.name.endsWith(".SplashActivity") }
        val classic = pixels(render(app.getDrawable(R.drawable.app_logo)!!, 64, 64))
        assertClassic(classic, pixels(render(info.loadIcon(pm), 64, 64)))
        assertClassic(classic, pixels(render(activity.loadIcon(pm), 64, 64)))
        val banner = render(activity.loadBanner(pm)!!, 320, 180)
        // The orange classic artwork must actually render, not merely be referenced in XML.
        assertTrue(pixels(banner).count { Color.red(it) > 140 && Color.green(it) in 60..180 && Color.blue(it) < 100 } > 500)
    }
}
