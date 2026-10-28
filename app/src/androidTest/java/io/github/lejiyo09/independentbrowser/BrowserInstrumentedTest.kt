package io.github.lejiyo09.independentbrowser

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.CookieManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs on an Android emulator in GitHub Actions. Tests that need the network use
 * example.com / badssl.com and wait up to [NETWORK_TIMEOUT_MS].
 */
@RunWith(AndroidJUnit4::class)
class BrowserInstrumentedTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Start every test from a clean, deterministic state (no restored tabs).
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("independentbrowser://open?url=about%3Ablank"))
            .setClass(context, MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun startsWithOneTab() {
        scenario.onActivity { assertEquals(1, it.tabCount) }
    }

    @Test
    fun addressBarLoadsRealWebsite() {
        scenario.onActivity { it.submitAddress("example.com") }
        waitUntil("example.com title") {
            var title = ""
            scenario.onActivity { title = it.currentTab?.webView?.title.orEmpty() }
            title.contains("Example Domain")
        }
        scenario.onActivity {
            assertEquals("https://example.com/", it.currentTab!!.webView.url)
        }
    }

    @Test
    fun visitIsRecordedInAppHistory() {
        scenario.onActivity { it.submitAddress("https://example.com/") }
        waitUntil("history entry") {
            var found = false
            scenario.onActivity { activity -> found = activity.database.history().any { e -> e.url == "https://example.com/" } }
            found
        }
    }

    @Test
    fun searchTextGoesToSearchEngine() {
        scenario.onActivity { it.submitAddress("independent browser test") }
        waitUntil("search page") {
            var url = ""
            scenario.onActivity { url = it.currentTab?.webView?.url.orEmpty() }
            Uri.parse(url).host?.contains("google") == true
        }
    }

    @Test
    fun keyboardShortcutsManageTabs() {
        scenario.onActivity { activity ->
            assertTrue(activity.handleShortcut(ctrlKey(KeyEvent.KEYCODE_T)))
            assertEquals(2, activity.tabCount)
            assertTrue(activity.handleShortcut(ctrlKey(KeyEvent.KEYCODE_T)))
            assertEquals(3, activity.tabCount)
            assertTrue(activity.handleShortcut(ctrlKey(KeyEvent.KEYCODE_W)))
            assertEquals(2, activity.tabCount)
            val before = activity.currentTab
            assertTrue(activity.handleShortcut(ctrlKey(KeyEvent.KEYCODE_TAB)))
            assertTrue(before !== activity.currentTab)
            // Ordinary typing is not swallowed.
            assertFalse(activity.handleShortcut(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
        }
    }

    @Test
    fun closingLastTabLeavesOneBlankTab() {
        scenario.onActivity { activity ->
            activity.closeTab(activity.currentTab!!)
            assertEquals(1, activity.tabCount)
        }
    }

    @Test
    fun invalidCertificateIsBlocked() {
        scenario.onActivity { it.loadInCurrentTab("https://expired.badssl.com/") }
        waitUntil("certificate error page") {
            var blocked: String? = null
            scenario.onActivity { blocked = it.currentTab?.blockedUrl }
            blocked != null
        }
        scenario.onActivity {
            assertTrue(it.currentTab!!.blockedUrl!!.contains("expired.badssl.com"))
            // The blocked page never reaches the history list.
            assertTrue(it.database.history().none { e -> e.url.contains("expired.badssl.com") })
        }
    }

    @Test
    fun bookmarksAndHistoryStorage() {
        val db = BrowserDatabase(ApplicationProvider.getApplicationContext(), name = null)
        db.recordVisit("https://a.example/", "A", now = 1)
        db.recordVisit("https://a.example/", "A2", now = 2) // same page again -> updated, not duplicated
        db.recordVisit("https://b.example/", "B", now = 3)
        db.recordVisit("about:blank", "ignored", now = 4)
        assertEquals(listOf("https://b.example/", "https://a.example/"), db.history().map { it.url })
        assertEquals("A2", db.history("a.example").single().title)

        assertTrue(db.addBookmark("https://b.example/", "B"))
        assertTrue(db.isBookmarked("https://b.example/"))
        db.removeBookmark("https://b.example/")
        assertFalse(db.isBookmarked("https://b.example/"))

        db.clearHistory()
        assertTrue(db.history().isEmpty())
        db.close()
    }

    @Test
    fun clearingDataRemovesCookies() {
        val cookies = CookieManager.getInstance()
        val set = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookies.setCookie("https://example.com", "session=abc; Max-Age=3600") { set.countDown() }
        }
        assertTrue(set.await(10, TimeUnit.SECONDS))
        assertNotNull(cookies.getCookie("https://example.com"))

        val done = CountDownLatch(1)
        scenario.onActivity { activity ->
            DataCleaner.clear(activity, activity.database, emptyList(), DataCleaner.Options.ALL) { done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertNull(cookies.getCookie("https://example.com"))
    }

    @Test
    fun webDataLivesInsideThisAppsPrivateStorage() {
        scenario.onActivity { it.submitAddress("https://example.com/") }
        waitUntil("page load") {
            var title = ""
            scenario.onActivity { title = it.currentTab?.webView?.title.orEmpty() }
            title.contains("Example Domain")
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val webViewDir = File(context.applicationInfo.dataDir, "app_webview")
        assertTrue("WebView profile should be in the app sandbox: $webViewDir", webViewDir.isDirectory)
        assertTrue(webViewDir.absolutePath.contains(context.packageName))
    }

    private fun ctrlKey(code: Int): KeyEvent {
        val now = SystemClock.uptimeMillis()
        return KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
    }

    private fun waitUntil(what: String, timeoutMs: Long = NETWORK_TIMEOUT_MS, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(250)
        }
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $what")
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 45_000L
    }
}
