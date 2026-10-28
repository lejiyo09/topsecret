package io.github.lejiyo09.independentbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputTest {
    private val google = SearchEngine.GOOGLE

    @Test fun emptyInputGivesNull() {
        assertNull(UrlInput.toNavigableUrl("   ", google))
        assertNull(UrlInput.toNavigableUrl(null, google))
    }

    @Test fun fullUrlsAreKept() {
        assertEquals("https://example.com/a?b=c", UrlInput.toNavigableUrl("https://example.com/a?b=c", google))
        assertEquals("http://example.com", UrlInput.toNavigableUrl("  http://example.com ", google))
    }

    @Test fun bareDomainsGetHttps() {
        assertEquals("https://naver.com", UrlInput.toNavigableUrl("naver.com", google))
        assertEquals("https://www.example.co.kr/path", UrlInput.toNavigableUrl("www.example.co.kr/path", google))
        assertEquals("https://example.com:8443/x", UrlInput.toNavigableUrl("example.com:8443/x", google))
        assertEquals("https://한국.kr", UrlInput.toNavigableUrl("한국.kr", google))
    }

    @Test fun localAddressesUseHttp() {
        assertEquals("http://localhost:8080", UrlInput.toNavigableUrl("localhost:8080", google))
        assertEquals("http://192.168.0.1/admin", UrlInput.toNavigableUrl("192.168.0.1/admin", google))
    }

    @Test fun textBecomesSearch() {
        assertEquals("https://www.google.com/search?q=hello+world", UrlInput.toNavigableUrl("hello world", google))
        assertEquals("https://www.google.com/search?q=3.14", UrlInput.toNavigableUrl("3.14", google))
        assertEquals(
            "https://search.naver.com/search.naver?query=%EB%82%A0%EC%94%A8",
            UrlInput.toNavigableUrl("날씨", SearchEngine.NAVER)
        )
        // Pasting an http URL with spaces is treated as a search, not navigation.
        assertTrue(UrlInput.toNavigableUrl("https://a.com b", google)!!.startsWith("https://www.google.com/search?q="))
    }

    @Test fun dangerousSchemesAreNotNavigated() {
        assertTrue(UrlInput.toNavigableUrl("javascript:alert(1)", google)!!.startsWith("https://www.google.com/search"))
        assertTrue(UrlInput.toNavigableUrl("file:///etc/passwd", google)!!.startsWith("https://www.google.com/search"))
    }

    @Test fun schemeHelpers() {
        assertTrue(UrlInput.isWebUrl("HTTPS://X.COM"))
        assertFalse(UrlInput.isWebUrl("about:blank"))
        assertTrue(UrlInput.isInPageScheme("blob"))
        assertFalse(UrlInput.isInPageScheme("intent"))
        assertFalse(UrlInput.isInPageScheme("file"))
        assertEquals("", UrlInput.displayText("about:blank"))
    }

    @Test fun searchEngineFallsBackToGoogle() {
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromKey("unknown"))
        assertEquals(SearchEngine.NAVER, SearchEngine.fromKey("naver"))
    }

    @Test fun desktopUserAgentKeepsWebViewMarker() {
        val mobile = "Mozilla/5.0 (Linux; Android 13; sdk_gphone64_x86_64 Build/TE1A; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36"
        val desktop = UserAgents.desktop(mobile)
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/120.0.6099.230 Safari/537.36",
            desktop
        )
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
    }
}
