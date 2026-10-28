package io.github.lejiyo09.independentbrowser

import java.util.Locale

/** Pure rules for naming and warning about downloads; kept free of Android types for unit tests. */
object DownloadPolicy {
    private const val MAX_NAME_LENGTH = 120
    private val ILLEGAL_CHARS = Regex("[\\u0000-\\u001F\\u007F\"*/:<>?\\\\|]")

    private val DANGEROUS_EXTENSIONS = setOf(
        "apk", "apks", "xapk", "aab", "exe", "msi", "msix", "bat", "cmd", "com", "scr", "pif",
        "ps1", "vbs", "vbe", "js", "jse", "wsf", "hta", "jar", "sh", "run", "deb", "rpm",
        "dmg", "pkg", "app", "crx", "reg", "lnk", "dll", "cpl", "inf"
    )

    fun sanitizeFileName(raw: String?): String {
        val base = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        var name = ILLEGAL_CHARS.replace(base, "_").trim().trimStart('.').trim()
        if (name.isEmpty()) name = "download"
        if (name.length > MAX_NAME_LENGTH) {
            val ext = extensionOf(name)
            val suffix = if (ext.isNotEmpty() && ext.length <= 16) ".$ext" else ""
            name = name.take(MAX_NAME_LENGTH - suffix.length) + suffix
        }
        return name
    }

    fun extensionOf(name: String): String =
        if (name.contains('.')) name.substringAfterLast('.').lowercase(Locale.ROOT) else ""

    fun isPotentiallyDangerous(name: String): Boolean = extensionOf(name) in DANGEROUS_EXTENSIONS

    /** DownloadManager can only fetch real network URLs (not blob:/data:). */
    fun isDownloadableUrl(url: String): Boolean = UrlInput.isWebUrl(url)

    fun isInsecure(url: String): Boolean = url.lowercase(Locale.ROOT).startsWith("http://")
}
