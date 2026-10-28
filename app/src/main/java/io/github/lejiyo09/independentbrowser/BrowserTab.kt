package io.github.lejiyo09.independentbrowser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class BrowserTab(val id: Long, val webView: WebView) {
    var url: String = ""
    var title: String = ""
    var favicon: Bitmap? = null
    var progress: Int = 100
    var loading: Boolean = false

    /** URL most recently requested for the main frame (set before the page commits). */
    var pendingUrl: String? = null

    /** URL to load the first time the tab is shown (restored background tabs). */
    var deferredUrl: String? = null

    /** Set while the tab shows the "certificate error" page instead of [blockedUrl]. */
    var blockedUrl: String? = null
    var blockedReason: String? = null

    var openerId: Long? = null
    var defaultScale: Float = 0f
    var currentScale: Float = 0f

    /** Uptime of the last Ctrl+click on the page, used to open that link in a new tab. */
    var modifierClickAt: Long = 0L

    val isBlank: Boolean
        get() = deferredUrl == null && blockedUrl == null && pendingUrl == null && UrlInput.displayText(url).isEmpty()

    /** The address the user should see for this tab. */
    val shownUrl: String
        get() = blockedUrl ?: deferredUrl ?: UrlInput.displayText(url).ifEmpty { UrlInput.displayText(pendingUrl) }

    fun consumeModifierClick(): Boolean {
        val recent = SystemClock.uptimeMillis() - modifierClickAt < 1500
        modifierClickAt = 0L
        return recent
    }
}

/** What a tab's WebView callbacks need from the browser window. */
interface TabHost {
    fun onTabStateChanged(tab: BrowserTab)
    fun onVisited(tab: BrowserTab, url: String)
    fun onTitleReceived(tab: BrowserTab, title: String)
    fun onOpenInNewTab(tab: BrowserTab, url: String)
    fun onExternalNavigation(tab: BrowserTab, uri: Uri, hasGesture: Boolean)
    fun onMainFrameSslError(tab: BrowserTab, url: String, error: SslError)
    fun onRenderProcessGone(tab: BrowserTab)
    fun onPopupRequested(opener: BrowserTab, resultMsg: Message): Boolean
    fun onCloseRequested(tab: BrowserTab)
    fun onFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun onPermissionRequest(request: PermissionRequest)
    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitFullscreen()
}

class TabWebViewClient(private val tab: BrowserTab, private val host: TabHost) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (UrlInput.isInPageScheme(uri.scheme)) {
            if (request.isForMainFrame) {
                if (request.hasGesture() && !request.isRedirect && tab.consumeModifierClick() && UrlInput.isWebUrl(uri.toString())) {
                    host.onOpenInNewTab(tab, uri.toString())
                    return true
                }
                tab.pendingUrl = uri.toString()
            }
            return false
        }
        // Sub-frames never get to launch other apps.
        if (request.isForMainFrame) host.onExternalNavigation(tab, uri, request.hasGesture())
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (url != "about:blank") {
            tab.blockedUrl = null
            tab.blockedReason = null
        }
        tab.url = url
        tab.pendingUrl = null
        tab.loading = true
        tab.favicon = favicon
        tab.defaultScale = 0f
        host.onTabStateChanged(tab)
    }

    override fun onPageFinished(view: WebView, url: String) {
        tab.loading = false
        tab.progress = 100
        tab.pendingUrl = null
        view.title?.takeIf { it.isNotBlank() && it != url }?.let { tab.title = it }
        host.onTabStateChanged(tab)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        tab.url = url
        if (!isReload) host.onVisited(tab, url)
        host.onTabStateChanged(tab)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Certificate errors are never bypassed.
        handler.cancel()
        val failed = error.url
        val isMainFrame = failed == tab.pendingUrl || failed == view.url || failed == tab.url ||
            (tab.pendingUrl != null && sameHost(failed, tab.pendingUrl))
        if (isMainFrame) host.onMainFrameSslError(tab, failed, error)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.onRenderProcessGone(tab)
        return true
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        tab.currentScale = newScale
        if (tab.defaultScale == 0f) tab.defaultScale = newScale
    }

    private fun sameHost(a: String?, b: String?): Boolean {
        val hostA = a?.let { Uri.parse(it).host } ?: return false
        return hostA.equals(b?.let { Uri.parse(it).host }, ignoreCase = true)
    }
}

class TabChromeClient(private val tab: BrowserTab, private val host: TabHost) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        tab.progress = newProgress
        host.onTabStateChanged(tab)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (title.isNullOrBlank()) return
        tab.title = title
        host.onTitleReceived(tab, title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        tab.favicon = icon
        host.onTabStateChanged(tab)
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
        // Popups opened without a click are blocked.
        isUserGesture && host.onPopupRequested(tab, resultMsg)

    override fun onCloseWindow(window: WebView) = host.onCloseRequested(tab)

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.onFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) = host.onPermissionRequest(request)

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = host.onEnterFullscreen(view, callback)

    override fun onHideCustomView() = host.onExitFullscreen()
}
