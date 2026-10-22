package com.example.iptv.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.iptv.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AndroidContractTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    @Test fun compiledManifestHasNamedTvLauncherWithoutTouchRequirement() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
        assertEquals("Aypi Tivi", app.packageManager.getApplicationLabel(info.applicationInfo).toString())
        assertTrue(info.activities.single { it.name.endsWith(".SplashActivity") }.exported)
        assertFalse(info.activities.single { it.name.endsWith(".MainActivity") }.exported)
        val features = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures
        assertTrue(features.any { it.name == "android.software.leanback" && it.flags != 0 })
        assertTrue(features.any { it.name == "android.hardware.touchscreen" && it.flags == 0 })
        assertEquals(26, info.applicationInfo.minSdkVersion)
    }

    @Test fun compiledBackupPolicyExcludesPrivatePreferences() {
        val info = app.packageManager.getApplicationInfo(app.packageName, 0)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        for (resource in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
            val parser = app.resources.getXml(resource)
            var exclusions = 0
            parser.use {
                while (it.next() != XmlPullParser.END_DOCUMENT) {
                    if (it.eventType == XmlPullParser.START_TAG && it.name == "exclude") {
                        assertEquals("sharedpref", it.getAttributeValue(null, "domain"))
                        assertEquals(".", it.getAttributeValue(null, "path"))
                        exclusions++
                    }
                }
            }
            assertEquals(if (resource == R.xml.backup_rules) 1 else 2, exclusions)
        }
    }

    @Test fun classicBrandingResourcesAreUsedByLaunchAndBanner() {
        val info = app.packageManager.getApplicationInfo(app.packageName, 0)
        assertEquals(R.drawable.app_logo, info.icon)
        assertEquals(R.drawable.tv_banner, info.banner)
        app.resources.openRawResource(R.drawable.app_logo).use {
            assertEquals(0xff, it.read())
            assertEquals(0xd8, it.read())
        }
        for (resource in listOf(R.drawable.splash_background, R.drawable.tv_banner)) {
            val referenced = mutableSetOf<Int>()
            app.resources.getXml(resource).use {
                while (it.next() != XmlPullParser.END_DOCUMENT) {
                    if (it.eventType == XmlPullParser.START_TAG && it.name == "item") {
                        referenced.add(it.getAttributeResourceValue(androidNamespace, "drawable", 0))
                    }
                }
            }
            assertTrue(R.drawable.app_logo in referenced)
            assertTrue(R.drawable.brand_name in referenced)
        }
    }
}
