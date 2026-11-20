package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ProjectDraft
import com.example.data.model.SourceType
import com.example.engine.ApkForgeEngine
import com.example.engine.ForgeProgressEvent
import com.example.engine.KeystoreManager
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("APKForge", appName)
    }

    @Test
    fun `forge and verify signed apk end to end`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keystoreManager = KeystoreManager(context)
        val generatedKey = keystoreManager.createKeystore(
            alias = "test_release",
            commonName = "Test Studio",
            organization = "APKForge Test",
            validityYears = 25
        )

        val engine = ApkForgeEngine(context, keystoreManager)
        val draft = ProjectDraft(
            appName = "Pocket Calc",
            packageName = "com.test.pocketcalc",
            versionName = "2.4.1",
            versionCode = 7,
            sourceType = SourceType.HTML_BUNDLE,
            htmlContent = "<html><body><h1>Hello APKForge</h1></body></html>",
            badgeLabel = "PC"
        )

        val events = engine.forgeApk(draft, generatedKey.keystorePath, generatedKey.alias).toList()
        val complete = events.filterIsInstance<ForgeProgressEvent.Complete>().firstOrNull()
        assertTrue("Expected Complete event, got: $events", complete != null)
        requireNotNull(complete)

        assertTrue(complete.outputFile.exists())
        assertTrue(complete.fileSizeBytes > 10_000L)
        assertTrue(complete.isVerified)

        val report = engine.inspectApk(complete.outputFile)
        assertEquals("com.test.pocketcalc", report.packageName)
        assertEquals("Pocket Calc", report.appLabel)
        assertEquals("2.4.1", report.versionName)
        assertEquals(7, report.versionCode)
        assertTrue("APK signature should verify", report.isSignatureVerified)
        assertTrue("v2 signature should verify", report.v2Verified)
        assertTrue("resources.arsc must be 4-byte aligned", report.resourcesAligned4Byte)
    }
}
