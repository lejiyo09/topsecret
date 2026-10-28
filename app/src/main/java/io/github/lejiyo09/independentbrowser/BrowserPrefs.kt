package io.github.lejiyo09.independentbrowser

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BrowserPrefs(context: Context) {
    private val sp: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val searchEngine: SearchEngine
        get() = SearchEngine.fromKey(sp.getString(KEY_SEARCH_ENGINE, null))

    val homePage: String
        get() = UrlInput.toNavigableUrl(sp.getString(KEY_HOME_PAGE, null), searchEngine)
            ?.takeIf { UrlInput.isWebUrl(it) }
            ?: searchEngine.homeUrl

    var desktopMode: Boolean
        get() = sp.getBoolean(KEY_DESKTOP_MODE, true)
        set(value) = sp.edit { putBoolean(KEY_DESKTOP_MODE, value) }

    val javaScriptEnabled: Boolean get() = sp.getBoolean(KEY_JAVASCRIPT, true)
    val thirdPartyCookies: Boolean get() = sp.getBoolean(KEY_THIRD_PARTY_COOKIES, false)
    val restoreTabs: Boolean get() = sp.getBoolean(KEY_RESTORE_TABS, true)
    val clearOnExit: Boolean get() = sp.getBoolean(KEY_CLEAR_ON_EXIT, false)

    fun saveTabs(urls: List<String>, selected: Int) {
        val json = JSONObject()
            .put("selected", selected)
            .put("urls", JSONArray(urls))
        sp.edit { putString(KEY_SAVED_TABS, json.toString()) }
    }

    fun loadTabs(): Pair<List<String>, Int> {
        val raw = sp.getString(KEY_SAVED_TABS, null) ?: return emptyList<String>() to 0
        return try {
            val json = JSONObject(raw)
            val array = json.optJSONArray("urls") ?: JSONArray()
            val urls = (0 until array.length()).map { array.getString(it) }.filter { UrlInput.isWebUrl(it) }
            urls to json.optInt("selected", 0)
        } catch (e: JSONException) {
            emptyList<String>() to 0
        }
    }

    fun clearSavedTabs() = sp.edit { remove(KEY_SAVED_TABS) }

    companion object {
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_HOME_PAGE = "home_page"
        const val KEY_DESKTOP_MODE = "desktop_mode"
        const val KEY_JAVASCRIPT = "javascript"
        const val KEY_THIRD_PARTY_COOKIES = "third_party_cookies"
        const val KEY_RESTORE_TABS = "restore_tabs"
        const val KEY_CLEAR_ON_EXIT = "clear_on_exit"
        private const val KEY_SAVED_TABS = "saved_tabs"
    }
}
