package io.github.lejiyo09.independentbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.TextUtils
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import io.github.lejiyo09.independentbrowser.databinding.ActivityMainBinding
import io.github.lejiyo09.independentbrowser.databinding.ItemTabBinding
import java.text.DateFormat

class MainActivity : AppCompatActivity(), TabHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BrowserPrefs
    internal lateinit var database: BrowserDatabase
        private set
    private val downloads = DownloadHandler(this)

    private val tabs = mutableListOf<BrowserTab>()
    private val tabViews = mutableMapOf<Long, ItemTabBinding>()
    internal var currentTab: BrowserTab? = null
        private set
    internal val tabCount: Int get() = tabs.size
    private var nextTabId = 1L
    private val recentlyClosed = ArrayDeque<String>()
    private var bookmarkCheckedUrl: String? = null
    private var bookmarked = false

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: Pair<PermissionRequest, List<String>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileChooserCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            fileChooserCallback = null
        }

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val (request, resources) = pendingWebPermission ?: return@registerForActivityResult
            pendingWebPermission = null
            val allowed = resources.filter { isGranted(androidPermissionFor(it)) }
            if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
        }

    private val entryListLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val url = result.data?.getStringExtra(EntryListActivity.EXTRA_URL)
            if (result.resultCode != RESULT_OK || url == null) return@registerForActivityResult
            if (result.data?.getBooleanExtra(EntryListActivity.EXTRA_NEW_TAB, false) == true) newTab(url) else loadInCurrentTab(url)
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            applySettingsToAllTabs()
            if (result.resultCode == SettingsActivity.RESULT_CLEAR_DATA) showClearDataDialog()
        }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val tab = currentTab
            when {
                fullscreenView != null -> onExitFullscreen()
                binding.findBar.isVisible -> hideFind()
                tab != null && tab.webView.canGoBack() -> tab.webView.goBack()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBarsAndIme()
        prefs = BrowserPrefs(this)
        database = BrowserDatabase(this)

        CookieManager.getInstance().setAcceptCookie(true)
        setupToolbar()
        setupFindBar()
        onBackPressedDispatcher.addCallback(this, backCallback)

        if (prefs.clearOnExit && !startupClearDone) {
            startupClearDone = true
            prefs.clearSavedTabs()
            DataCleaner.clear(this, database, emptyList(), DataCleaner.Options.ALL) { openInitialTabs(intent) }
        } else {
            openInitialTabs(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        currentTab?.webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        currentTab?.webView?.onPause()
        CookieManager.getInstance().flush()
        persistTabs()
    }

    override fun onDestroy() {
        val clearNow = isFinishing && prefs.clearOnExit
        if (clearNow) {
            prefs.clearSavedTabs()
            DataCleaner.clear(applicationContext, database, tabs.map { it.webView }, DataCleaner.Options.ALL) {}
        }
        fileChooserCallback?.onReceiveValue(null)
        tabs.forEach { destroyWebView(it.webView) }
        tabs.clear()
        database.close()
        super.onDestroy()
    }

    private fun openInitialTabs(intent: Intent?) {
        if (handleViewIntent(intent)) return
        if (prefs.restoreTabs && !prefs.clearOnExit) {
            val (urls, selected) = prefs.loadTabs()
            urls.forEach { newTab(it, foreground = false) }
            if (tabs.isNotEmpty()) {
                switchTo(tabs[selected.coerceIn(tabs.indices)])
                return
            }
        }
        newTab(prefs.homePage)
    }

    /** Opens web links, shared text and .html files (from the ChromeOS Files app). */
    private fun handleViewIntent(intent: Intent?): Boolean {
        intent ?: return false
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return false
                when (data.scheme?.lowercase()) {
                    "http", "https" -> {
                        newTab(data.toString())
                        return true
                    }
                    LAUNCH_SCHEME -> {
                        val target = data.getQueryParameter("url")
                        if (target != null && UrlInput.isWebUrl(target)) {
                            newTab(target)
                            return true
                        }
                        return tabs.isNotEmpty()
                    }
                    "content" -> {
                        openLocalHtml(data)
                        return true
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val url = UrlInput.toNavigableUrl(intent.getStringExtra(Intent.EXTRA_TEXT), prefs.searchEngine)
                if (url != null) {
                    newTab(url)
                    return true
                }
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra("query")
                if (!query.isNullOrBlank()) {
                    newTab(prefs.searchEngine.searchUrl(query))
                    return true
                }
            }
        }
        return false
    }

    private fun openLocalHtml(uri: Uri) {
        val tab = newTab(null)
        Thread {
            val html = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytesCompat(MAX_LOCAL_HTML_BYTES)
                    String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (tab !in tabs) return@runOnUiThread
                if (html == null) {
                    Toast.makeText(this, R.string.local_file_failed, Toast.LENGTH_SHORT).show()
                } else {
                    // Loaded with an opaque origin: the file cannot read this app's cookies or other files.
                    tab.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            }
        }.start()
    }

    private fun persistTabs() {
        if (!prefs.restoreTabs || prefs.clearOnExit) {
            prefs.clearSavedTabs()
            return
        }
        val urls = mutableListOf<String>()
        var selected = 0
        tabs.forEach { tab ->
            val url = tab.deferredUrl ?: tab.blockedUrl ?: tab.webView.url ?: tab.url
            if (UrlInput.isWebUrl(url)) {
                if (tab === currentTab) selected = urls.size
                urls += url
            }
        }
        prefs.saveTabs(urls, selected)
    }

    // ------------------------------------------------------------------ tabs

    @SuppressLint("ClickableViewAccessibility")
    internal fun newTab(url: String?, foreground: Boolean = true, focusAddress: Boolean = false): BrowserTab {
        val webView = createWebView()
        val tab = BrowserTab(nextTabId++, webView)
        webView.webViewClient = TabWebViewClient(tab, this)
        webView.webChromeClient = TabChromeClient(tab, this)
        webView.setDownloadListener { downloadUrl, userAgent, disposition, mimeType, length ->
            downloads.onDownloadRequested(downloadUrl, userAgent, disposition, mimeType, length, tab.webView.url)
        }
        webView.setFindListener { active, count, _ -> if (tab === currentTab) updateFindCount(active, count) }
        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isCtrlPressedCompat()) {
                tab.modifierClickAt = SystemClock.uptimeMillis()
            }
            false
        }
        registerForContextMenu(webView)

        val insertAt = currentTab?.let { tabs.indexOf(it) + 1 }?.takeIf { it > 0 } ?: tabs.size
        tabs.add(insertAt, tab)
        if (url != null) {
            if (foreground) load(tab, url) else tab.deferredUrl = url
        }
        if (foreground) switchTo(tab, focusAddress) else renderTabStrip()
        return tab
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        settings.apply {
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            // Pages must never read local files or other apps' content providers.
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
        }
        applyPrefs(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyPrefs(webView: WebView) {
        webView.settings.javaScriptEnabled = prefs.javaScriptEnabled
        webView.settings.userAgentString =
            if (prefs.desktopMode) UserAgents.desktop(WebSettings.getDefaultUserAgent(this)) else null
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, prefs.thirdPartyCookies)
    }

    private fun applySettingsToAllTabs() {
        tabs.forEach { applyPrefs(it.webView) }
        updateToolbar()
    }

    private fun load(tab: BrowserTab, url: String) {
        tab.deferredUrl = null
        tab.pendingUrl = url
        tab.webView.loadUrl(url)
        if (tab === currentTab) updateToolbar()
    }

    internal fun loadInCurrentTab(url: String) {
        val tab = currentTab ?: newTab(null)
        load(tab, url)
        tab.webView.requestFocus()
    }

    internal fun switchTo(tab: BrowserTab, focusAddress: Boolean = false) {
        val previous = currentTab
        if (previous !== tab) {
            if (binding.findBar.isVisible) hideFind()
            previous?.webView?.let {
                binding.webContainer.removeView(it)
                it.onPause()
            }
        }
        currentTab = tab
        if (tab.webView.parent !== binding.webContainer) {
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
            binding.webContainer.addView(tab.webView)
        }
        tab.webView.onResume()
        tab.deferredUrl?.let { load(tab, it) }
        renderTabStrip()
        updateToolbar()
        if (focusAddress || tab.isBlank) focusAddressBar() else tab.webView.requestFocus()
    }

    internal fun closeTab(tab: BrowserTab, replaceIfLast: Boolean = true) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        if (tab === currentTab && fullscreenView != null) onExitFullscreen()
        tabs.removeAt(index)
        (tab.deferredUrl ?: tab.webView.url)?.takeIf { UrlInput.isWebUrl(it) }?.let {
            recentlyClosed.addLast(it)
            while (recentlyClosed.size > MAX_RECENTLY_CLOSED) recentlyClosed.removeFirst()
        }
        binding.webContainer.removeView(tab.webView)
        destroyWebView(tab.webView)

        if (tab === currentTab) {
            currentTab = null
            val opener = tab.openerId?.let { id -> tabs.firstOrNull { it.id == id } }
            when {
                opener != null -> switchTo(opener)
                tabs.isNotEmpty() -> switchTo(tabs[index.coerceAtMost(tabs.size - 1)])
                replaceIfLast -> newTab(null, focusAddress = true)
                else -> renderTabStrip()
            }
        } else {
            renderTabStrip()
        }
    }

    private fun destroyWebView(webView: WebView) {
        webView.stopLoading()
        webView.setOnTouchListener(null)
        unregisterForContextMenu(webView)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun reopenClosedTab() {
        val url = recentlyClosed.removeLastOrNull() ?: return
        newTab(url)
    }

    private fun cycleTab(delta: Int) {
        val tab = currentTab ?: return
        if (tabs.size < 2) return
        val index = (tabs.indexOf(tab) + delta).mod(tabs.size)
        switchTo(tabs[index])
    }

    private fun selectTab(index: Int) {
        if (index in tabs.indices) switchTo(tabs[index])
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderTabStrip() {
        val strip = binding.tabStrip
        strip.removeAllViews()
        tabViews.clear()
        tabs.forEach { tab ->
            val item = ItemTabBinding.inflate(layoutInflater, strip, false)
            tabViews[tab.id] = item
            item.root.setOnClickListener { switchTo(tab) }
            // Middle-click closes a tab, like desktop browsers.
            item.root.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                    closeTab(tab)
                    true
                } else {
                    false
                }
            }
            item.tabClose.setOnClickListener { closeTab(tab) }
            strip.addView(item.root)
            bindTabItem(tab, item)
        }
        val selected = currentTab?.let { tabViews[it.id]?.root }
        if (selected != null) {
            binding.tabScroll.post {
                val scroll = binding.tabScroll
                if (selected.left < scroll.scrollX || selected.right > scroll.scrollX + scroll.width) {
                    scroll.smoothScrollTo(selected.left, 0)
                }
            }
        }
    }

    private fun bindTabItem(tab: BrowserTab, itemBinding: ItemTabBinding? = null) {
        val item = itemBinding ?: tabViews[tab.id] ?: return
        val title = tabTitle(tab)
        item.tabTitle.text = title
        item.root.isSelected = tab === currentTab
        item.root.tooltipText = title
        item.tabClose.contentDescription = getString(R.string.close_tab_named, title)
        val icon = tab.favicon
        if (icon != null) {
            item.tabIcon.imageTintList = null
            item.tabIcon.setImageBitmap(icon)
        } else {
            item.tabIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
            item.tabIcon.setImageResource(R.drawable.ic_globe)
        }
    }

    private fun tabTitle(tab: BrowserTab): String = when {
        tab.blockedUrl != null -> getString(R.string.tab_security_error)
        tab.deferredUrl != null -> tab.deferredUrl!!
        tab.title.isNotBlank() && tab.title != "about:blank" -> tab.title
        tab.shownUrl.isNotEmpty() -> tab.shownUrl
        else -> getString(R.string.tab_blank)
    }

    // ------------------------------------------------------------------ toolbar

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { currentTab?.webView?.takeIf { it.canGoBack() }?.goBack() }
        binding.btnForward.setOnClickListener { currentTab?.webView?.takeIf { it.canGoForward() }?.goForward() }
        binding.btnReload.setOnClickListener { reloadOrStop() }
        binding.btnHome.setOnClickListener { loadInCurrentTab(prefs.homePage) }
        binding.btnNewTab.setOnClickListener { newTab(null, focusAddress = true) }
        binding.btnBookmark.setOnClickListener { toggleBookmark() }
        binding.btnMenu.setOnClickListener { showMainMenu(it) }
        binding.btnSecurity.setOnClickListener { showSiteInfo() }

        binding.urlBar.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            when {
                actionId == EditorInfo.IME_ACTION_GO || (isEnter && event?.action == KeyEvent.ACTION_DOWN) -> {
                    submitAddress(binding.urlBar.text.toString())
                    true
                }
                isEnter -> true
                else -> false
            }
        }
        binding.urlBar.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) updateToolbar() }
    }

    internal fun submitAddress(text: String) {
        val url = UrlInput.toNavigableUrl(text, prefs.searchEngine) ?: return
        hideKeyboard(binding.urlBar)
        binding.urlBar.clearFocus()
        loadInCurrentTab(url)
    }

    private fun reloadOrStop() {
        val tab = currentTab ?: return
        when {
            tab.loading -> tab.webView.stopLoading()
            tab.blockedUrl != null -> load(tab, tab.blockedUrl!!)
            else -> tab.webView.reload()
        }
    }

    private fun focusAddressBar() {
        binding.urlBar.requestFocus()
        binding.urlBar.selectAll()
        showKeyboard(binding.urlBar)
    }

    private fun updateToolbar() {
        val tab = currentTab ?: return
        val webView = tab.webView
        if (!binding.urlBar.hasFocus()) binding.urlBar.setText(tab.shownUrl)

        binding.btnBack.isEnabled = webView.canGoBack()
        binding.btnBack.alpha = if (binding.btnBack.isEnabled) 1f else 0.35f
        binding.btnForward.isEnabled = webView.canGoForward()
        binding.btnForward.alpha = if (binding.btnForward.isEnabled) 1f else 0.35f

        binding.btnReload.setImageResource(if (tab.loading) R.drawable.ic_close else R.drawable.ic_refresh)
        val reloadLabel = getString(if (tab.loading) R.string.stop else R.string.reload)
        binding.btnReload.contentDescription = reloadLabel
        binding.btnReload.tooltipText = reloadLabel

        binding.progress.progress = tab.progress
        binding.progress.visibility = if (tab.loading && tab.progress < 100) View.VISIBLE else View.INVISIBLE

        val shown = tab.shownUrl
        val (icon, tint) = when {
            tab.blockedUrl != null -> R.drawable.ic_warning to R.color.danger
            UrlInput.isHttps(shown) -> R.drawable.ic_lock to R.color.secure
            UrlInput.isWebUrl(shown) -> R.drawable.ic_warning to R.color.text_secondary
            else -> R.drawable.ic_search to R.color.text_secondary
        }
        binding.btnSecurity.setImageResource(icon)
        binding.btnSecurity.imageTintList = ContextCompat.getColorStateList(this, tint)

        val pageUrl = webView.url?.takeIf { tab.blockedUrl == null }
        if (pageUrl != bookmarkCheckedUrl) {
            bookmarkCheckedUrl = pageUrl
            bookmarked = database.isBookmarked(pageUrl)
        }
        binding.btnBookmark.setImageResource(if (bookmarked) R.drawable.ic_star else R.drawable.ic_star_border)
        val bookmarkLabel = getString(if (bookmarked) R.string.bookmark_remove else R.string.bookmark_add)
        binding.btnBookmark.contentDescription = bookmarkLabel
        binding.btnBookmark.tooltipText = bookmarkLabel
    }

    private fun toggleBookmark() {
        val tab = currentTab ?: return
        val url = tab.webView.url
        if (tab.blockedUrl != null || url == null || !UrlInput.isWebUrl(url)) {
            Toast.makeText(this, R.string.bookmark_not_web, Toast.LENGTH_SHORT).show()
            return
        }
        if (database.isBookmarked(url)) {
            database.removeBookmark(url)
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
        } else {
            database.addBookmark(url, tab.title)
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        }
        bookmarkCheckedUrl = null
        updateToolbar()
    }

    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.menu.findItem(R.id.action_desktop).isChecked = prefs.desktopMode
        popup.menu.findItem(R.id.action_reopen_tab).isEnabled = recentlyClosed.isNotEmpty()
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_tab -> newTab(null, focusAddress = true)
                R.id.action_reopen_tab -> reopenClosedTab()
                R.id.action_bookmarks -> openEntryList(EntryListActivity.MODE_BOOKMARKS)
                R.id.action_history -> openEntryList(EntryListActivity.MODE_HISTORY)
                R.id.action_find -> showFind()
                R.id.action_zoom_in -> currentTab?.webView?.zoomIn()
                R.id.action_zoom_out -> currentTab?.webView?.zoomOut()
                R.id.action_desktop -> toggleDesktopMode()
                R.id.action_clear_data -> showClearDataDialog()
                R.id.action_settings -> settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                R.id.action_about -> showAbout()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun toggleDesktopMode() {
        prefs.desktopMode = !prefs.desktopMode
        tabs.forEach { applyPrefs(it.webView) }
        currentTab?.webView?.reload()
    }

    private fun openEntryList(mode: String) {
        entryListLauncher.launch(EntryListActivity.intent(this, mode))
    }

    private fun resetZoom() {
        val tab = currentTab ?: return
        if (tab.defaultScale > 0f && tab.currentScale > 0f) {
            val factor = tab.defaultScale / tab.currentScale
            if (factor in 0.02f..50f) tab.webView.zoomBy(factor)
        }
    }

    // ------------------------------------------------------------------ dialogs

    private fun showSiteInfo() {
        val tab = currentTab ?: return
        val shown = tab.shownUrl
        val message = when {
            tab.blockedUrl != null -> getString(R.string.site_info_blocked, tab.blockedReason.orEmpty())
            UrlInput.isHttps(shown) -> {
                val cert = tab.webView.certificate
                val base = getString(R.string.site_info_secure)
                if (cert != null) base + "\n\n" + describeCertificate(cert) else base
            }
            UrlInput.isWebUrl(shown) -> getString(R.string.site_info_insecure)
            else -> getString(R.string.site_info_other)
        }
        AlertDialog.Builder(this)
            .setTitle(Uri.parse(shown).host ?: getString(R.string.site_info))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun describeCertificate(cert: SslCertificate): String {
        val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
        return getString(
            R.string.site_info_cert,
            cert.issuedTo.cName.orEmpty(),
            (cert.issuedBy.oName?.takeIf { it.isNotBlank() } ?: cert.issuedBy.cName).orEmpty(),
            cert.validNotBeforeDate?.let(format::format).orEmpty(),
            cert.validNotAfterDate?.let(format::format).orEmpty(),
        )
    }

    internal fun showClearDataDialog() {
        val labels = arrayOf(
            getString(R.string.clear_data_history),
            getString(R.string.clear_data_cookies),
            getString(R.string.clear_data_cache),
            getString(R.string.clear_data_forms),
        )
        val checked = booleanArrayOf(true, true, true, false)
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_data_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.clear_data_confirm) { _, _ ->
                val options = DataCleaner.Options(checked[0], checked[1], checked[2], checked[3])
                DataCleaner.clear(this, database, tabs.map { it.webView }, options) {
                    if (options.history) recentlyClosed.clear()
                    bookmarkCheckedUrl = null
                    updateToolbar()
                    AlertDialog.Builder(this)
                        .setMessage(R.string.clear_data_done)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        val webViewVersion = WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "?"
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME, webViewVersion))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ------------------------------------------------------------------ find in page

    private fun setupFindBar() {
        binding.findInput.doAfterTextChanged { text ->
            val webView = currentTab?.webView ?: return@doAfterTextChanged
            if (text.isNullOrEmpty()) {
                webView.clearMatches()
                binding.findCount.text = ""
            } else {
                webView.findAllAsync(text.toString())
            }
        }
        binding.findInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                currentTab?.webView?.findNext(!event.isShiftPressed)
                true
            } else {
                keyCode == KeyEvent.KEYCODE_ENTER
            }
        }
        binding.findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentTab?.webView?.findNext(true)
                true
            } else {
                false
            }
        }
        binding.findPrev.setOnClickListener { currentTab?.webView?.findNext(false) }
        binding.findNext.setOnClickListener { currentTab?.webView?.findNext(true) }
        binding.findClose.setOnClickListener { hideFind() }
    }

    private fun showFind() {
        binding.findBar.isVisible = true
        binding.findInput.requestFocus()
        binding.findInput.selectAll()
        showKeyboard(binding.findInput)
        val query = binding.findInput.text?.toString().orEmpty()
        if (query.isNotEmpty()) currentTab?.webView?.findAllAsync(query)
    }

    private fun hideFind() {
        if (!binding.findBar.isVisible) return
        currentTab?.webView?.clearMatches()
        binding.findBar.isVisible = false
        binding.findCount.text = ""
        hideKeyboard(binding.findInput)
        currentTab?.webView?.requestFocus()
    }

    private fun updateFindCount(active: Int, count: Int) {
        binding.findCount.text = if (count == 0) "0/0" else getString(R.string.find_count, active + 1, count)
    }

    // ------------------------------------------------------------------ keyboard & mouse

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && handleShortcut(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Desktop-browser keyboard shortcuts for Chromebook keyboards. Returns true if handled. */
    internal fun handleShortcut(event: KeyEvent): Boolean {
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed
        val alt = event.isAltPressed
        val key = event.keyCode
        val webView = currentTab?.webView

        if (key == KeyEvent.KEYCODE_ESCAPE) {
            when {
                fullscreenView != null -> onExitFullscreen()
                binding.urlBar.hasFocus() -> {
                    binding.urlBar.clearFocus()
                    hideKeyboard(binding.urlBar)
                    updateToolbar()
                    webView?.requestFocus()
                }
                binding.findBar.isVisible -> hideFind()
                currentTab?.loading == true -> webView?.stopLoading()
                else -> return false
            }
            return true
        }

        when {
            ctrl && !alt && key == KeyEvent.KEYCODE_T -> if (shift) reopenClosedTab() else newTab(null, focusAddress = true)
            ctrl && !alt && key == KeyEvent.KEYCODE_N -> newTab(null, focusAddress = true)
            ctrl && key == KeyEvent.KEYCODE_W -> currentTab?.let { closeTab(it) } ?: return false
            ctrl && key == KeyEvent.KEYCODE_TAB -> cycleTab(if (shift) -1 else 1)
            ctrl && key == KeyEvent.KEYCODE_PAGE_DOWN -> cycleTab(1)
            ctrl && key == KeyEvent.KEYCODE_PAGE_UP -> cycleTab(-1)
            ctrl && !shift && key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8 -> selectTab(key - KeyEvent.KEYCODE_1)
            ctrl && !shift && key == KeyEvent.KEYCODE_9 -> selectTab(tabs.size - 1)
            (ctrl && key == KeyEvent.KEYCODE_L) || (alt && key == KeyEvent.KEYCODE_D) || key == KeyEvent.KEYCODE_F6 ->
                focusAddressBar()
            (ctrl && key == KeyEvent.KEYCODE_R) || key == KeyEvent.KEYCODE_F5 || key == KeyEvent.KEYCODE_REFRESH ->
                if (currentTab?.blockedUrl != null) reloadOrStop() else webView?.reload()
            alt && key == KeyEvent.KEYCODE_DPAD_LEFT -> webView?.takeIf { it.canGoBack() }?.goBack()
            (alt && key == KeyEvent.KEYCODE_DPAD_RIGHT) || key == KeyEvent.KEYCODE_FORWARD ->
                webView?.takeIf { it.canGoForward() }?.goForward()
            ctrl && !shift && key == KeyEvent.KEYCODE_D -> toggleBookmark()
            ctrl && !shift && key == KeyEvent.KEYCODE_H -> openEntryList(EntryListActivity.MODE_HISTORY)
            ctrl && shift && key == KeyEvent.KEYCODE_O -> openEntryList(EntryListActivity.MODE_BOOKMARKS)
            ctrl && !shift && key == KeyEvent.KEYCODE_F -> showFind()
            ctrl && (key == KeyEvent.KEYCODE_EQUALS || key == KeyEvent.KEYCODE_PLUS || key == KeyEvent.KEYCODE_NUMPAD_ADD) ->
                webView?.zoomIn()
            ctrl && (key == KeyEvent.KEYCODE_MINUS || key == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) -> webView?.zoomOut()
            ctrl && !shift && key == KeyEvent.KEYCODE_0 -> resetZoom()
            ctrl && shift && (key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL) -> showClearDataDialog()
            else -> return false
        }
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val webView = v as? WebView ?: return
        val tab = tabs.firstOrNull { it.webView === webView } ?: return
        val hit = webView.hitTestResult
        val extra = hit.extra ?: return

        fun add(titleRes: Int, action: () -> Unit) {
            menu.add(titleRes).setOnMenuItemClickListener {
                action()
                true
            }
        }

        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                menu.setHeaderTitle(extra)
                if (UrlInput.isWebUrl(extra)) {
                    add(R.string.ctx_open_new_tab) { newTab(extra) }
                    add(R.string.ctx_open_background_tab) { newTab(extra, foreground = false) }
                    add(R.string.ctx_download_link) {
                        downloads.onDownloadRequested(extra, webView.settings.userAgentString, null, null, -1, webView.url)
                    }
                }
                add(R.string.ctx_copy_link) { copyToClipboard(extra) }
            }
            WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                menu.setHeaderTitle(extra.take(200))
                if (hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    add(R.string.ctx_open_link_new_tab) {
                        val handler = Handler(Looper.getMainLooper()) { msg ->
                            msg.data.getString("url")?.takeIf { UrlInput.isWebUrl(it) }?.let { newTab(it) }
                            true
                        }
                        webView.requestFocusNodeHref(handler.obtainMessage())
                    }
                }
                if (UrlInput.isWebUrl(extra)) {
                    add(R.string.ctx_open_image) { newTab(extra) }
                    add(R.string.ctx_save_image) {
                        downloads.onDownloadRequested(extra, webView.settings.userAgentString, null, null, -1, webView.url)
                    }
                    add(R.string.ctx_copy_image) { copyToClipboard(extra) }
                }
            }
            WebView.HitTestResult.EMAIL_TYPE, WebView.HitTestResult.PHONE_TYPE, WebView.HitTestResult.GEO_TYPE -> {
                menu.setHeaderTitle(extra)
                add(R.string.ctx_copy) { copyToClipboard(extra) }
            }
        }
        if (tab !== currentTab) menu.clear()
    }

    private fun copyToClipboard(text: String) {
        getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(text, text))
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ TabHost

    override fun onTabStateChanged(tab: BrowserTab) {
        if (tab !in tabs) return
        bindTabItem(tab)
        if (tab === currentTab) updateToolbar()
    }

    override fun onVisited(tab: BrowserTab, url: String) {
        if (tab !in tabs || tab.blockedUrl != null) return
        database.recordVisit(url, tab.webView.title?.takeIf { it != url })
    }

    override fun onTitleReceived(tab: BrowserTab, title: String) {
        if (tab !in tabs) return
        tab.webView.url?.let { database.updateTitle(it, title) }
        bindTabItem(tab)
    }

    override fun onOpenInNewTab(tab: BrowserTab, url: String) {
        if (tab in tabs) newTab(url, foreground = false)
    }

    override fun onExternalNavigation(tab: BrowserTab, uri: Uri, hasGesture: Boolean) {
        if (tab !in tabs) return
        ExternalLinks.handle(this, uri, hasGesture) { fallback -> load(tab, fallback) }
    }

    override fun onMainFrameSslError(tab: BrowserTab, url: String, error: SslError) {
        if (tab !in tabs) return
        val reason = getString(
            when (error.primaryError) {
                SslError.SSL_EXPIRED -> R.string.ssl_error_expired
                SslError.SSL_NOTYETVALID -> R.string.ssl_error_not_yet_valid
                SslError.SSL_IDMISMATCH -> R.string.ssl_error_mismatch
                SslError.SSL_UNTRUSTED -> R.string.ssl_error_untrusted
                SslError.SSL_DATE_INVALID -> R.string.ssl_error_date_invalid
                else -> R.string.ssl_error_generic
            }
        )
        tab.blockedUrl = url
        tab.blockedReason = reason
        tab.loading = false
        tab.pendingUrl = null
        tab.webView.loadDataWithBaseURL("about:blank", errorPage(url, reason), "text/html", "UTF-8", null)
        onTabStateChanged(tab)
    }

    private fun errorPage(url: String, reason: String): String {
        val title = TextUtils.htmlEncode(getString(R.string.ssl_error_title))
        val body = TextUtils.htmlEncode(getString(R.string.ssl_error_body))
        return """
            <!doctype html><html lang="ko"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="light dark">
            <title>$title</title>
            <style>
              body{font-family:sans-serif;max-width:640px;margin:10vh auto;padding:0 24px;line-height:1.6}
              h1{font-size:1.4em;color:#c5221f} code{word-break:break-all;opacity:.8}
            </style></head><body>
            <h1>&#9888; $title</h1>
            <p>$body</p>
            <p><b>${TextUtils.htmlEncode(reason)}</b></p>
            <p><code>${TextUtils.htmlEncode(url)}</code></p>
            </body></html>
        """.trimIndent()
    }

    override fun onRenderProcessGone(tab: BrowserTab) {
        if (tab !in tabs) return
        val url = tab.webView.url ?: tab.deferredUrl
        val wasCurrent = tab === currentTab
        closeTab(tab, replaceIfLast = false)
        newTab(url?.takeIf { UrlInput.isWebUrl(it) }, foreground = wasCurrent || tabs.isEmpty())
        Toast.makeText(this, R.string.renderer_restarted, Toast.LENGTH_SHORT).show()
    }

    override fun onPopupRequested(opener: BrowserTab, resultMsg: Message): Boolean {
        if (opener !in tabs) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = newTab(null)
        popup.openerId = opener.id
        transport.webView = popup.webView
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseRequested(tab: BrowserTab) = closeTab(tab)

    override fun onFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            fileChooserCallback = null
            false
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val wanted = request.resources.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }
        if (wanted.isEmpty()) {
            request.deny()
            return
        }
        val names = wanted.joinToString(", ") {
            getString(if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) R.string.permission_camera else R.string.permission_microphone)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(getString(R.string.permission_message, request.origin.host ?: request.origin.toString(), names))
            .setPositiveButton(R.string.permission_allow) { _, _ ->
                val missing = wanted.map(::androidPermissionFor).filterNot(::isGranted)
                if (missing.isEmpty()) {
                    request.grant(wanted.toTypedArray())
                } else {
                    pendingWebPermission = request to wanted
                    mediaPermissionLauncher.launch(missing.toTypedArray())
                }
            }
            .setNegativeButton(R.string.permission_deny) { _, _ -> request.deny() }
            .setOnCancelListener { request.deny() }
            .show()
    }

    private fun androidPermissionFor(resource: String): String =
        if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        binding.fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        binding.fullscreenContainer.isVisible = true
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onExitFullscreen() {
        val view = fullscreenView ?: return
        fullscreenView = null
        binding.fullscreenContainer.removeView(view)
        binding.fullscreenContainer.isVisible = false
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
    }

    // ------------------------------------------------------------------ helpers

    private fun showKeyboard(view: View) {
        view.post { getSystemService<InputMethodManager>()?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard(view: View) {
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun MotionEvent.isCtrlPressedCompat(): Boolean = (metaState and KeyEvent.META_CTRL_ON) != 0

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        const val LAUNCH_SCHEME = "independentbrowser"
        private const val MAX_RECENTLY_CLOSED = 25
        private const val MAX_LOCAL_HTML_BYTES = 5 * 1024 * 1024

        /** "Clear on restart" runs once per process, not on every activity re-creation. */
        private var startupClearDone = false
    }
}
