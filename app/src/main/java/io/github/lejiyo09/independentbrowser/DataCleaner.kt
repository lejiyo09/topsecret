package io.github.lejiyo09.independentbrowser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase

/**
 * Deletes data that this app itself stores. It cannot touch Chrome's data (other app sandbox)
 * nor anything kept on websites' servers.
 */
object DataCleaner {

    data class Options(
        val history: Boolean,
        val cookiesAndSiteData: Boolean,
        val cache: Boolean,
        val formData: Boolean,
    ) {
        companion object {
            val ALL = Options(history = true, cookiesAndSiteData = true, cache = true, formData = true)
        }
    }

    /** Must be called on the main thread; [onDone] runs on the main thread. */
    fun clear(
        context: Context,
        database: BrowserDatabase,
        openWebViews: List<WebView>,
        options: Options,
        onDone: () -> Unit,
    ) {
        if (options.history) {
            database.clearHistory()
            openWebViews.forEach { it.clearHistory() }
        }
        if (options.cache) {
            // The HTTP cache is shared by all WebViews of the app, so one call clears it.
            val webView = openWebViews.firstOrNull() ?: WebView(context)
            webView.clearCache(true)
            if (openWebViews.isEmpty()) webView.destroy()
        }
        if (options.formData) {
            val db = WebViewDatabase.getInstance(context)
            @Suppress("DEPRECATION")
            db.clearFormData()
            db.clearHttpAuthUsernamePassword()
        }
        if (options.cookiesAndSiteData) {
            WebStorage.getInstance().deleteAllData()
            GeolocationPermissions.getInstance().clearAll()
            val cookies = CookieManager.getInstance()
            cookies.removeAllCookies {
                cookies.flush()
                onDone()
            }
        } else {
            onDone()
        }
    }
}
