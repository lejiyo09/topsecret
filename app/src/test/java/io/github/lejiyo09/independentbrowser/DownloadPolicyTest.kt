package io.github.lejiyo09.independentbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test fun pathTraversalIsStripped() {
        assertEquals("passwd", DownloadPolicy.sanitizeFileName("../../etc/passwd"))
        assertEquals("evil.txt", DownloadPolicy.sanitizeFileName("..\\..\\evil.txt"))
        assertEquals("hidden", DownloadPolicy.sanitizeFileName(".hidden"))
    }

    @Test fun illegalCharactersAreReplaced() {
        assertEquals("a_b_c_.pdf", DownloadPolicy.sanitizeFileName("a:b*c?.pdf"))
        assertEquals("tab_name.txt", DownloadPolicy.sanitizeFileName("tab\tname.txt"))
    }

    @Test fun emptyNamesGetDefault() {
        assertEquals("download", DownloadPolicy.sanitizeFileName(""))
        assertEquals("download", DownloadPolicy.sanitizeFileName(null))
        assertEquals("download", DownloadPolicy.sanitizeFileName("..."))
    }

    @Test fun longNamesKeepExtension() {
        val name = DownloadPolicy.sanitizeFileName("x".repeat(300) + ".pdf")
        assertEquals(120, name.length)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test fun executablesAreFlagged() {
        assertTrue(DownloadPolicy.isPotentiallyDangerous("app-release.APK"))
        assertTrue(DownloadPolicy.isPotentiallyDangerous("invoice.pdf.exe"))
        assertFalse(DownloadPolicy.isPotentiallyDangerous("report.pdf"))
        assertFalse(DownloadPolicy.isPotentiallyDangerous("README"))
    }

    @Test fun onlyNetworkUrlsAreDownloadable() {
        assertTrue(DownloadPolicy.isDownloadableUrl("https://example.com/a.zip"))
        assertFalse(DownloadPolicy.isDownloadableUrl("blob:https://example.com/123"))
        assertFalse(DownloadPolicy.isDownloadableUrl("data:text/plain,hi"))
        assertTrue(DownloadPolicy.isInsecure("http://example.com/a.zip"))
        assertFalse(DownloadPolicy.isInsecure("https://example.com/a.zip"))
    }
}
