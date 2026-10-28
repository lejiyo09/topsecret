package io.github.lejiyo09.independentbrowser

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.util.Locale

enum class SearchEngine(val key: String, private val queryTemplate: String, val homeUrl: String) {
    GOOGLE("google", "https://www.google.com/search?q=%s", "https://www.google.com/"),
    DUCKDUCKGO("duckduckgo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/"),
    BING("bing", "https://www.bing.com/search?q=%s", "https://www.bing.com/"),
    NAVER("naver", "https://search.naver.com/search.naver?query=%s", "https://www.naver.com/");

    fun searchUrl(query: String): String =
        queryTemplate.replace("%s", URLEncoder.encode(query, "UTF-8"))

    companion object {
        fun fromKey(key: String?): SearchEngine = entries.firstOrNull { it.key == key } ?: GOOGLE
    }
}

/** Turns what the user typed into the address bar into something the WebView can load. */
object UrlInput {
    private val WHITESPACE = Regex("\\s")

    // host.tld[:port][/path] where the TLD starts with a letter, so "3.14" stays a search.
    private val HOST_LIKE = Regex(
        "^(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?\\.)+\\p{L}[\\p{L}\\p{N}-]*\\.?(?::\\d{1,5})?(?:[/?#].*)?$"
    )
    private val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:[/?#].*)?$")
    private val LOCALHOST = Regex("^localhost(?::\\d{1,5})?(?:[/?#].*)?$", RegexOption.IGNORE_CASE)

    /** Schemes the WebView itself is allowed to navigate to; anything else is handed off or blocked. */
    private val IN_PAGE_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")

    /** Returns the URL to load, or null when the input is empty. */
    fun toNavigableUrl(input: String?, engine: SearchEngine): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)
        val hasSpace = WHITESPACE.containsMatchIn(text)

        if (!hasSpace && (lower.startsWith("http://") || lower.startsWith("https://"))) return text
        if (lower == "about:blank") return "about:blank"
        if (hasSpace) return engine.searchUrl(text)
        if (IPV4.matches(text) || LOCALHOST.matches(text)) return "http://$text"
        if (HOST_LIKE.matches(text)) return "https://$text"
        return engine.searchUrl(text)
    }

    fun isWebUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.ROOT) ?: return false
        return lower.startsWith("https://") || lower.startsWith("http://")
    }

    fun isHttps(url: String?): Boolean = url?.lowercase(Locale.ROOT)?.startsWith("https://") == true

    fun isInPageScheme(scheme: String?): Boolean = scheme?.lowercase(Locale.ROOT) in IN_PAGE_SCHEMES

    /**
     * True when both URLs point at the same document, ignoring an http->https upgrade,
     * host case, a missing trailing "/" and the #fragment.
     */
    fun sameDocument(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (a == b) return true
        return try {
            val x = URI(a)
            val y = URI(b)
            x.host != null && x.host.equals(y.host, ignoreCase = true) &&
                x.port == y.port &&
                x.rawPath.orEmpty().ifEmpty { "/" } == y.rawPath.orEmpty().ifEmpty { "/" } &&
                x.rawQuery == y.rawQuery
        } catch (e: URISyntaxException) {
            false
        }
    }

    /** What the address bar shows for a loaded URL. */
    fun displayText(url: String?): String = if (url.isNullOrEmpty() || url == "about:blank") "" else url
}

object UserAgents {
    private val ANDROID_PLATFORM = Regex("\\(Linux; Android [^)]*\\)")

    /**
     * Desktop variant of the WebView user agent ("request desktop site").
     * Keeps the "wv" token so the app still identifies itself as an embedded WebView.
     */
    fun desktop(defaultUserAgent: String): String =
        defaultUserAgent
            .replace(ANDROID_PLATFORM, "(X11; Linux x86_64; wv)")
            .replace(" Mobile Safari/", " Safari/")
}
